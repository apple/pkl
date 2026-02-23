/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.core.runtime;

import static org.pkl.core.runtime.VmUtils.REPL_TEXT;
import static org.pkl.core.runtime.VmUtils.REPL_TEXT_URI;

import com.oracle.truffle.api.frame.FrameDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.CommandSpec;
import org.pkl.core.CommandSpec.Argument;
import org.pkl.core.CommandSpec.BooleanFlag;
import org.pkl.core.CommandSpec.CompletionCandidates.Fixed;
import org.pkl.core.CommandSpec.CountedFlag;
import org.pkl.core.CommandSpec.Option.BadValue;
import org.pkl.core.CommandSpec.Option.MissingOption;
import org.pkl.core.FileOutput;
import org.pkl.core.PNull;
import org.pkl.core.PklBugException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.expression.literal.AmendModuleNodeGen;
import org.pkl.core.ast.expression.literal.PropertiesLiteralNodeGen;
import org.pkl.core.ast.expression.unary.ImportNode;
import org.pkl.core.ast.member.ClassProperty;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.ast.member.ModuleNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

/** Runs commands. */
public final class CommandSpecParser {

  private final ModuleResolver moduleResolver;
  private final SecurityManager securityManager;
  private final StackFrameTransformer frameTransformer;
  private final boolean color;
  private final Set<String> reservedFlagNames;
  private final Set<String> reservedFlagShortNames;
  private final Function<VmTyped, FileOutput> makeFileOutput;

  public CommandSpecParser(
      ModuleResolver moduleResolver,
      SecurityManager securityManager,
      StackFrameTransformer frameTransformer,
      boolean color,
      Set<String> reservedFlagNames,
      Set<String> reservedFlagShortNames,
      Function<VmTyped, FileOutput> makeFileOutput) {
    this.moduleResolver = moduleResolver;
    this.securityManager = securityManager;
    this.frameTransformer = frameTransformer;
    this.color = color;
    this.reservedFlagNames = reservedFlagNames;
    this.reservedFlagShortNames = reservedFlagShortNames;
    this.makeFileOutput = makeFileOutput;
  }

  public CommandSpec parse(VmTyped command) {
    VmUtils.checkAmends(command, CommandModule.getModule().getVmClass());
    checkPropertyIsUndefined(command, Identifier.OPTIONS);
    checkPropertyIsUndefined(command, Identifier.PARENT);

    var optionsClass = getOptionsClass(command);
    var commandInfo =
        VmUtils.checkAmends(
            VmUtils.readMember(command, Identifier.COMMAND), CommandModule.getCommandInfoClass());
    var commandName = (String) VmUtils.readMember(commandInfo, Identifier.NAME);
    var optionSpecs = collectOptions(optionsClass);

    return new CommandSpec(
        commandName,
        exportNullableString(commandInfo, Identifier.DESCRIPTION),
        (Boolean) VmUtils.readMember(commandInfo, Identifier.HIDE),
        (Boolean) VmUtils.readMember(commandInfo, Identifier.NOOP),
        optionSpecs,
        collectSubcommands(commandInfo),
        (options, parent) ->
            new CommandSpec.State(
                buildExecutionModule(
                    command,
                    buildObject(optionsClass, options),
                    // NB: these next two lines are the only place where we lose type safety.
                    // SubcommandState is type-erased to Object in the public API to hide internals.
                    // Consumers of this API must ensure CommandSpec.apply is only ever passed an
                    // instance of CommandSpec.State previously returned from a prior invocation
                    // of CommandSpec.apply.
                    parent == null ? null : (SubcommandState) parent.contents()),
                (it) -> handleErrors(() -> evaluateResult(command, (SubcommandState) it))));
  }

  private List<CommandSpec> collectSubcommands(VmTyped commandInfo) {
    var subcommands = new ArrayList<CommandSpec>();
    var subcommandNames = new HashSet<String>();
    var subcommandsProperty = (VmObject) VmUtils.readMember(commandInfo, Identifier.SUBCOMMANDS);
    subcommandsProperty.force(false, false);
    subcommandsProperty.iterateAlreadyForcedMemberValues(
        (key, member, value) -> {
          var spec = parse((VmTyped) value);
          if (subcommandNames.contains(spec.name())) {
            throw exceptionBuilder()
                .withSourceSection(member.getSourceSection())
                .evalError(
                    "commandSubcommandConflict",
                    VmUtils.readMember(commandInfo, Identifier.NAME),
                    spec.name())
                .build();
          }
          subcommandNames.add(spec.name());
          subcommands.add(spec);
          return true;
        });

    return subcommands;
  }

  // region options handling

  private VmClass getOptionsClass(VmTyped command) {
    var optionsProperty = command.getVmClass().getProperty(Identifier.OPTIONS);
    if (optionsProperty == null) {
      // at this point we've asserted the command extends pkl:Command
      throw PklBugException.unreachableCode();
    }
    var optionsPropertyTypeNode = optionsProperty.getTypeNode();
    if (optionsPropertyTypeNode == null) {
      // at this point we've asserted the options property exists and that it is neither amended nor
      // assigned
      // the only possibility here is that it has a type annotation, otherwise this wouldn't parse
      throw PklBugException.unreachableCode();
    }
    var optionsTypeNode = optionsPropertyTypeNode.getTypeNode();
    if (optionsTypeNode instanceof TypeNode.TypedTypeNode) {
      return BaseModule.getTypedClass();
    }
    if (!(optionsTypeNode instanceof TypeNode.ClassTypeNode node)) {
      throw exceptionBuilder()
          .withSourceSection(optionsTypeNode.getSourceSection())
          .evalError(
              "commandOptionsTypeNotClass", optionsTypeNode.getSourceSection().getCharacters())
          .build();
    }
    var clazz = node.getVmClass();
    if (clazz.isAbstract()) {
      throw exceptionBuilder()
          .withSourceSection(clazz.getHeaderSection())
          .evalError("commandOptionsTypeAbstractClass", clazz.getQualifiedName())
          .build();
    }
    return clazz;
  }

  private Iterable<CommandSpec.Option> collectOptions(VmClass optionsClass) {
    CommandSpec.Argument lastRepeatedArg = null;
    EconomicMap<String, CommandSpec.Option> opts = EconomicMap.create();

    var clazz = optionsClass;
    while (clazz != null) {
      for (var prop : clazz.getDeclaredProperties()) {
        var name = prop.getName().toString();
        if (VmModifier.isLocalOrExternalOrAbstractOrFixedOrConst(prop.getModifiers())
            || opts.containsKey(name)) continue;

        VmTyped flagAnnotation = null;
        VmTyped argAnnotation = null;
        for (var annotation : prop.getAllAnnotations(true)) {
          if (annotation.getVmClass().isSubclassOf(CommandModule.getBaseFlagClass())) {
            if (flagAnnotation != null) continue;
            flagAnnotation = annotation;
          } else if (annotation.getVmClass() == CommandModule.getArgumentClass()) {
            if (argAnnotation != null) continue;
            argAnnotation = annotation;
          }
        }

        if (flagAnnotation != null && argAnnotation != null) {
          throw exceptionBuilder()
              .withSourceSection(prop.getHeaderSection())
              .evalError("commandOptionBothFlagAndArgument", prop.getName())
              .build();
        }

        if (argAnnotation != null) {
          var arg = collectArgument(prop, argAnnotation);
          opts.put(arg.name(), arg);
          if (arg.repeated()) {
            if (lastRepeatedArg == null) {
              lastRepeatedArg = arg;
            } else {
              throw exceptionBuilder()
                  .withSourceSection(optionsClass.getHeaderSection())
                  .evalError("commandArgumentsMultipleRepeated", lastRepeatedArg.name(), arg.name())
                  .build();
            }
          }
        } else {
          CommandSpec.Option flag;
          if (flagAnnotation == null
              || flagAnnotation.getVmClass() == CommandModule.getFlagClass()) {
            flag = collectFlag(prop, flagAnnotation);
          } else if (flagAnnotation.getVmClass() == CommandModule.getBooleanFlagClass()) {
            flag = collectBooleanFlag(prop, flagAnnotation);
          } else if (flagAnnotation.getVmClass() == CommandModule.getCountedFlagClass()) {
            flag = collectCountedFlag(prop, flagAnnotation);
          } else {
            throw PklBugException.unreachableCode();
          }
          opts.put(flag.name(), flag);
        }
      }
      clazz = clazz.getSuperclass();
    }

    return opts.getValues();
  }

  private void checkFlagNames(ClassProperty prop, String name, @Nullable String shortName) {
    for (var reserved : reservedFlagNames) {
      if (reserved.equals(name)) {
        throw exceptionBuilder()
            .withSourceSection(prop.getHeaderSection())
            .evalError("commandFlagNameCollision", prop.getName(), "name", "")
            .build();
      }
    }
    for (var reserved : reservedFlagShortNames) {
      if (reserved.equals(shortName)) {
        throw exceptionBuilder()
            .withSourceSection(prop.getHeaderSection())
            .evalError(
                "commandFlagNameCollision", prop.getName(), "short name", "`" + shortName + "` ")
            .build();
      }
    }
  }

  private CommandSpec.Flag collectFlag(ClassProperty prop, @Nullable VmTyped flagAnnotation) {
    var name = prop.getName().toString();
    var behavior = new OptionBehavior(flagAnnotation, true).resolve(prop, false);
    String shortName = null;
    boolean hide = false;
    if (flagAnnotation != null) {
      shortName = exportNullableString(flagAnnotation, Identifier.SHORT_NAME);
      hide = (Boolean) VmUtils.readMember(flagAnnotation, Identifier.HIDE);
    }
    checkFlagNames(prop, name, shortName);

    return new CommandSpec.Flag(
        name,
        VmUtils.exportDocComment(prop.getDocComment()),
        !behavior.isOptional(),
        behavior.getEach(),
        behavior.getAll(),
        behavior.getCompletionCandidates(),
        shortName,
        behavior.getMetavar(),
        hide,
        (behavior.getDefaultValue() == COMPLEX_DEFAULT_EXPRESSION
                || behavior.getDefaultValue() == null)
            ? null
            : behavior.getDefaultValue().toString());
  }

  private CommandSpec.BooleanFlag collectBooleanFlag(ClassProperty prop, VmTyped flagAnnotation) {
    var name = prop.getName().toString();
    var shortName = exportNullableString(flagAnnotation, Identifier.SHORT_NAME);
    checkFlagNames(prop, name, shortName);

    // assert type is Boolean
    var typeInfo = resolveType(prop);
    if (!(typeInfo.getFirst() instanceof TypeNode.BooleanTypeNode)) {
      throw exceptionBuilder()
          .withSourceSection(prop.getHeaderSection())
          .evalError(
              "commandFlagInvalidType",
              prop.getName(),
              "BooleanFlag",
              typeInfo.getFirst().getSourceSection().getCharacters(),
              "Boolean")
          .build();
    }

    return new BooleanFlag(
        name,
        VmUtils.exportDocComment(prop.getDocComment()),
        shortName,
        (Boolean) VmUtils.readMember(flagAnnotation, Identifier.HIDE),
        (Boolean) getDefaultValue(prop, false));
  }

  private CommandSpec.CountedFlag collectCountedFlag(ClassProperty prop, VmTyped flagAnnotation) {
    var name = prop.getName().toString();
    var shortName = exportNullableString(flagAnnotation, Identifier.SHORT_NAME);
    checkFlagNames(prop, name, shortName);

    // assert type is integral
    var typeInfo = resolveType(prop);
    if (typeInfo.getFirst().getVmClass() != BaseModule.getIntClass() || typeInfo.getSecond()) {
      throw exceptionBuilder()
          .withSourceSection(prop.getHeaderSection())
          .evalError(
              "commandFlagInvalidType",
              prop.getName(),
              "CountedFlag",
              typeInfo.getFirst().getSourceSection().getCharacters(),
              "Int")
          .build();
    }

    if (getDefaultValue(prop, true) != null) {
      throw exceptionBuilder()
          .withSourceSection(prop.getHeaderSection())
          .evalError("commandOptionUnexpectedDefaultValue", prop.getName(), "Argument")
          .build();
    }

    return new CountedFlag(
        name,
        VmUtils.exportDocComment(prop.getDocComment()),
        shortName,
        (Boolean) VmUtils.readMember(flagAnnotation, Identifier.HIDE));
  }

  private Argument collectArgument(ClassProperty prop, VmTyped argAnnotation) {
    var behavior = new OptionBehavior(argAnnotation, false).resolve(prop, true);
    if (behavior.getDefaultValue() != null) {
      throw exceptionBuilder()
          .withSourceSection(prop.getHeaderSection())
          .evalError("commandOptionUnexpectedDefaultValue", prop.getName(), "Argument")
          .build();
    } else if (behavior.isOptional() && !behavior.getMultiple()) {
      throw exceptionBuilder()
          .withSourceSection(prop.getHeaderSection())
          .evalError("commandArgumentUnexpectedNonRepeatedNullableType", prop.getName())
          .build();
    }

    return new CommandSpec.Argument(
        prop.getName().toString(),
        VmUtils.exportDocComment(prop.getDocComment()),
        behavior.getEach(),
        behavior.getAll(),
        behavior.getCompletionCandidates(),
        behavior.getMultiple());
  }

  /** Unwrap nullables, constraints, and aliases and return Pair(underlying type, is nullable) */
  private Pair<TypeNode, Boolean> resolveType(ClassProperty prop) {
    var propertyTypeNode = prop.getTypeNode();
    if (propertyTypeNode != null) {
      return resolveType(propertyTypeNode.getTypeNode());
    }
    throw exceptionBuilder()
        .withSourceSection(prop.getHeaderSection())
        .evalError("commandOptionNoTypeAnnotation", prop.getName())
        .build();
  }

  /** Unwrap nullables, constraints, and aliases and return Pair(underlying type, is nullable) */
  private Pair<TypeNode, Boolean> resolveType(TypeNode typeNode) {
    var isNullable = false;
    while (true) {
      if (typeNode instanceof TypeNode.NullableTypeNode nullableTypeNode) {
        isNullable = true;
        typeNode = nullableTypeNode.getElementTypeNode();
      } else if (typeNode instanceof TypeNode.ConstrainedTypeNode constrainedTypeNode) {
        typeNode = constrainedTypeNode.getChildTypeNode();
      } else if (typeNode instanceof TypeNode.TypeAliasTypeNode typeAliasTypeNode) {
        if (typeAliasTypeNode.getVmTypeAlias() == BaseModule.getCharTypeAlias()) break;
        typeNode = typeAliasTypeNode.getAliasedTypeNode();
        break;
      } else {
        break;
      }
    }

    return Pair.of(typeNode, isNullable);
  }

  // This sigil used to indicate that an option has a default value that is a non-constant expr.
  // These values cause a flag to be marked optional, but are not shown in CLI help.
  // Thus, they are not evaluated at spec parse time.
  private static final Object COMPLEX_DEFAULT_EXPRESSION = new Object() {};

  private @Nullable Object getDefaultValue(ClassProperty prop, boolean requireExplicit) {
    // if the default is a constant, surface it to the CLI layer informationally
    var constantValue = prop.getInitializer().getConstantValue();
    if (constantValue != null) {
      // Map/List/Set literals may be constants if all their arguments are constants
      // return the complex default sigil in that case!!
      // only primitives returned here (non-VmValues: Float, Long, String, Boolean)
      return constantValue instanceof VmValue ? COMPLEX_DEFAULT_EXPRESSION : constantValue;
    }

    // if the default is defined, we're not required but don't evaluate the expression
    // return the sigil value to indicate this
    var bodyNode = prop.getInitializer().getMemberNode();
    if (bodyNode != null && !(bodyNode.getBodyNode() instanceof DefaultPropertyBodyNode)) {
      return COMPLEX_DEFAULT_EXPRESSION;
    }

    // otherwise, if type-based defaults are allowed
    // (eg. for @Flag, for string literal and string literal unions)
    // attempt to discover the type's default
    if (!requireExplicit) {
      var resolved = resolveType(prop);
      if (resolved.getSecond()) {
        // wrapped by a nullable, no default value
        return null;
      }
      if (resolved.getFirst() instanceof TypeNode.UnionOfStringLiteralsTypeNode union
          && union.getUnionDefault() != null) {
        return union.getUnionDefault();
      } else if (resolved.getFirst() instanceof TypeNode.StringLiteralTypeNode literal) {
        return literal.getLiteral();
      }
    }

    // otherwise we have no default!
    return null;
  }

  private class OptionBehavior {
    private @Nullable BiFunction<String, URI, Object> each;
    private @Nullable Function<List<Object>, Object> all;
    private @Nullable Boolean multiple;
    private @Nullable String metavar;
    private @Nullable CommandSpec.CompletionCandidates completionCandidates;
    private @Nullable Object defaultValue = null;
    private boolean isNullable = false;

    private OptionBehavior(
        @Nullable BiFunction<String, URI, Object> each,
        @Nullable Function<List<Object>, Object> all,
        @Nullable Boolean multiple,
        @Nullable String metavar,
        @Nullable CommandSpec.CompletionCandidates completionCandidates) {
      this.each = each;
      this.all = all;
      this.multiple = multiple;
      this.metavar = metavar;
      this.completionCandidates = completionCandidates;
    }

    public OptionBehavior(@Nullable VmTyped annotation, boolean hasMetavar) {
      this(
          annotation == null
              ? null
              : VmUtils.readMember(annotation, Identifier.CONVERT) instanceof VmFunction func
                  ? (rawValue, workingDirUri) ->
                      handleBadValue(() -> handleImports(func.apply(rawValue), workingDirUri))
                  : null,
          annotation == null
              ? null
              : VmUtils.readMember(annotation, Identifier.TRANSFORM_ALL) instanceof VmFunction func
                  ? (it) -> handleBadValue(() -> func.apply(VmList.create(it)))
                  : null,
          annotation == null
              ? null
              : VmUtils.readMember(annotation, Identifier.MULTIPLE) instanceof Boolean bool
                  ? bool
                  : null,
          annotation == null
              ? null
              : hasMetavar ? exportNullableString(annotation, Identifier.METAVAR) : null,
          annotation == null ? null : exportCompletionCandidates(annotation));
    }

    public OptionBehavior resolve(ClassProperty prop, boolean requireExplicitDefault) {
      var resolved = resolveType(prop);
      var typeNode = resolved.getFirst();
      isNullable = resolved.getSecond();
      defaultValue = CommandSpecParser.this.getDefaultValue(prop, requireExplicitDefault);
      if (isNullable && defaultValue != null) {
        throw exceptionBuilder()
            .evalError("commandOptionTypeNullableWithDefaultValue", prop.getName())
            .withSourceSection(prop.getHeaderSection())
            .build();
      }

      resolve(prop, typeNode);
      return this;
    }

    private void resolve(ClassProperty prop, TypeNode typeNode) {
      if (resolvePrimitive(typeNode)) {
        return;
      }
      if (typeNode instanceof TypeNode.ListingTypeNode listingTypeNode) {
        handleElement(listingTypeNode.getValueTypeNode(), prop);
        if (multiple == null) multiple = true;
        if (all == null)
          all =
              !multiple
                  ? this::allChooseLast
                  : (values) -> {
                    if (values.isEmpty()) return null;
                    var builder = new VmObjectBuilder();
                    values.forEach(builder::addElement);
                    return builder.toListing();
                  };
      } else if (typeNode instanceof TypeNode.MappingTypeNode mappingTypeNode) {
        assert mappingTypeNode.getKeyTypeNode() != null;
        handleEntry(mappingTypeNode.getKeyTypeNode(), mappingTypeNode.getValueTypeNode(), prop);
        if (multiple == null) multiple = true;
        if (all == null)
          all =
              !multiple
                  ? this::allChooseLast
                  : (values) -> {
                    if (values.isEmpty()) return null;
                    var builder = new VmObjectBuilder();
                    values.forEach(
                        (entry) ->
                            builder.addEntry(
                                ((VmPair) entry).getFirst(), ((VmPair) entry).getSecond()));
                    return builder.toMapping();
                  };
      } else if (typeNode instanceof TypeNode.ListTypeNode listTypeNode) {
        handleElement(listTypeNode.getElementTypeNode(), prop);
        if (multiple == null) multiple = true;
        if (all == null)
          all =
              !multiple
                  ? this::allChooseLast
                  : (values) -> values.isEmpty() ? null : VmList.create(values);
      } else if (typeNode instanceof TypeNode.SetTypeNode setTypeNode) {
        handleElement(setTypeNode.getElementTypeNode(), prop);
        if (multiple == null) multiple = true;
        if (all == null)
          all =
              !multiple
                  ? this::allChooseLast
                  : (values) -> values.isEmpty() ? null : VmSet.create(values);
      } else if (typeNode instanceof TypeNode.MapTypeNode mapTypeNode) {
        handleEntry(mapTypeNode.getKeyTypeNode(), mapTypeNode.getValueTypeNode(), prop);
        if (multiple == null) multiple = true;
        if (all == null)
          all =
              !multiple
                  ? this::allChooseLast
                  : (values) -> {
                    if (values.isEmpty()) return null;
                    var builder = VmMap.builder();
                    values.forEach(
                        (entry) ->
                            builder.add(((VmPair) entry).getFirst(), ((VmPair) entry).getSecond()));
                    return builder.build();
                  };
      } else if (typeNode instanceof TypeNode.PairTypeNode pairTypeNode) {
        handleEntry(pairTypeNode.getFirstTypeNode(), pairTypeNode.getSecondTypeNode(), prop);
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
      } else if (typeNode instanceof TypeNode.FinalClassTypeNode finalClassTypeNode
          && (finalClassTypeNode.getVmClass() == BaseModule.getListingClass()
              || finalClassTypeNode.getVmClass() == BaseModule.getMappingClass()
              || finalClassTypeNode.getVmClass() == BaseModule.getListClass()
              || finalClassTypeNode.getVmClass() == BaseModule.getSetClass()
              || finalClassTypeNode.getVmClass() == BaseModule.getMapClass()
              || finalClassTypeNode.getVmClass() == BaseModule.getPairClass())) {
        // if a supported type is provided without type arguments
        throw exceptionBuilder()
            .withSourceSection(prop.getHeaderSection())
            .evalError(
                "commandOptionUnsupportedType",
                prop.getName(),
                "",
                typeNode.getSourceSection().getCharacters())
            .withHint(
                finalClassTypeNode.getVmClass().getSimpleName()
                    + " options must provide "
                    + switch (finalClassTypeNode.getVmClass().getTypeParameterCount()) {
                      case 1 -> "one type argument.";
                      case 2 -> "two type arguments.";
                      default -> throw PklBugException.unreachableCode();
                    })
            .build();
      } else if (each == null && all == null) {
        // if another type and no transform functions are provided, that's an error
        throw exceptionBuilder()
            .withSourceSection(prop.getHeaderSection())
            .evalError(
                "commandOptionUnsupportedType",
                prop.getName(),
                "",
                typeNode.getSourceSection().getCharacters())
            .withHint("Use a supported type or define a transformEach and/or transformAll function")
            .build();
      } else {
        // if we have at least one transform then allow the type and fill in reasonable defaults
        if (each == null) each = (rawValue, workingDirUri) -> rawValue;
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_VALUE;
      }
    }

    private OptionBehavior resolveTypeArgument(
        ClassProperty prop, TypeNode typeNode, String typeArgumentName) {
      if (resolvePrimitive(typeNode)) {
        return this;
      }

      if (each == null) {
        // if another type and no convert function is provided, that's an error
        throw exceptionBuilder()
            .withSourceSection(prop.getHeaderSection())
            .evalError(
                "commandOptionUnsupportedType",
                prop.getName(),
                typeArgumentName + " ",
                typeNode.getSourceSection().getCharacters())
            .withHint("Use a supported type or define a transformEach and/or transformAll function")
            .build();
      } else if (metavar == null) {
        // if we have a convert function then allow the type and set a reasonable metavar default
        metavar = METAVAR_VALUE;
        // all and multiple don't matter since they're ignored for type args
      }

      return this;
    }

    private boolean resolvePrimitive(TypeNode typeNode) {
      if (typeNode instanceof TypeNode.NumberTypeNode) {
        if (each == null)
          each =
              (rawValue, workingDirUri) -> {
                try {
                  return Long.parseLong(rawValue);
                } catch (NumberFormatException e) {
                  try {
                    return Double.parseDouble(rawValue);
                  } catch (NumberFormatException e2) {
                    throw BadValue.invalid(rawValue, METAVAR_NUMBER);
                  }
                }
              };
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_NUMBER;
        return true;
      } else if (typeNode instanceof TypeNode.FloatTypeNode) {
        if (each == null)
          each =
              (rawValue, workingDirUri) -> {
                try {
                  return Double.parseDouble(rawValue);
                } catch (NumberFormatException e) {
                  throw BadValue.invalid(rawValue, METAVAR_FLOAT);
                }
              };
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_FLOAT;
        return true;
      } else if (typeNode instanceof TypeNode.IntTypeNode) {
        if (each == null) each = eachLong(Long.MIN_VALUE, Long.MAX_VALUE, METAVAR_INT);
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_INT;
        return true;
      } else if (typeNode instanceof TypeNode.Int8TypeAliasTypeNode) {
        if (each == null) each = eachLong(Byte.MIN_VALUE, Byte.MAX_VALUE, METAVAR_INT8);
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_INT8;
        return true;
      } else if (typeNode instanceof TypeNode.Int16TypeAliasTypeNode) {
        if (each == null) each = eachLong(Short.MIN_VALUE, Short.MAX_VALUE, METAVAR_INT16);
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_INT16;
        return true;
      } else if (typeNode instanceof TypeNode.Int32TypeAliasTypeNode) {
        if (each == null) each = eachLong(Integer.MIN_VALUE, Integer.MAX_VALUE, METAVAR_INT32);
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_INT32;
        return true;
      } else if (typeNode instanceof TypeNode.UIntTypeAliasTypeNode uIntTypeAliasTypeNode) {
        var mask = uIntTypeAliasTypeNode.getMask();
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (mask == 0x000000000000FFFFL) {
          if (each == null) each = eachLong(0, 0x000000000000FFFFL, METAVAR_UINT16);
          if (metavar == null) metavar = METAVAR_UINT16;
        } else if (mask == 0x00000000FFFFFFFFL) {
          if (each == null) each = eachLong(0, 0x00000000FFFFFFFFL, METAVAR_UINT32);
          if (metavar == null) metavar = METAVAR_UINT32;
        } else {
          if (each == null) each = eachLong(0, Long.MAX_VALUE, METAVAR_UINT);
          if (metavar == null) metavar = METAVAR_UINT;
        }
        return true;
      } else if (typeNode instanceof TypeNode.UInt8TypeAliasTypeNode) {
        if (each == null) each = eachLong(0, 0x00000000000000FFL, METAVAR_UINT8);
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_UINT8;
        return true;
      } else if (typeNode instanceof TypeNode.BooleanTypeNode) {
        if (each == null)
          each =
              (rawValue, workingDirUri) -> {
                var value = rawValue.toLowerCase();
                if (TRUE_VALUES.contains(value)) {
                  return true;
                } else if (FALSE_VALUES.contains(value)) {
                  return false;
                }
                throw BadValue.invalid(rawValue, "boolean");
              };
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_BOOLEAN;
        return true;
      } else if (typeNode instanceof TypeNode.StringTypeNode) {
        if (each == null) each = (rawValue, workingDirUri) -> rawValue;
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_STRING;
        return true;
      } else if (typeNode instanceof TypeNode.TypeAliasTypeNode typeAliasTypeNode
          && typeAliasTypeNode.getVmTypeAlias() == BaseModule.getCharTypeAlias()) {
        if (each == null)
          each =
              (rawValue, workingDirUri) -> {
                if (rawValue.length() != 1) throw BadValue.invalid(rawValue, METAVAR_CHAR);
                return rawValue;
              };
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = METAVAR_CHAR;
        return true;
      } else if (typeNode
          instanceof TypeNode.UnionOfStringLiteralsTypeNode unionOfStringLiteralsTypeNode) {
        var choices = unionOfStringLiteralsTypeNode.getStringLiterals().stream().sorted();
        if (each == null)
          each =
              (rawValue, workingDirUri) -> {
                if (!unionOfStringLiteralsTypeNode.getStringLiterals().contains(rawValue)) {
                  throw BadValue.invalidChoice(rawValue, choices.toList());
                }
                return rawValue;
              };
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = "[" + choices.collect(Collectors.joining(", ")) + "]";
        if (completionCandidates == null)
          completionCandidates = new Fixed(unionOfStringLiteralsTypeNode.getStringLiterals());
        return true;
      } else if (typeNode instanceof TypeNode.StringLiteralTypeNode stringLiteralTypeNode) {
        var choice = stringLiteralTypeNode.getLiteral();
        if (each == null)
          each =
              (rawValue, workingDirUri) -> {
                if (!rawValue.equals(choice)) {
                  throw BadValue.invalidChoice(rawValue, choice);
                }
                return rawValue;
              };
        if (all == null) all = this::allChooseLast;
        if (multiple == null) multiple = false;
        if (metavar == null) metavar = "[" + choice + "]";
        if (completionCandidates == null) completionCandidates = new Fixed(Set.of(choice));
        return true;
      }
      return false;
    }

    private static final String METAVAR_NUMBER = "number";
    private static final String METAVAR_FLOAT = "float";
    private static final String METAVAR_INT = "int";
    private static final String METAVAR_INT8 = "int8";
    private static final String METAVAR_INT16 = "int16";
    private static final String METAVAR_INT32 = "int32";
    private static final String METAVAR_UINT = "uint";
    private static final String METAVAR_UINT8 = "uint8";
    private static final String METAVAR_UINT16 = "uint16";
    private static final String METAVAR_UINT32 = "uint32";
    private static final String METAVAR_BOOLEAN = "[true|false]";
    private static final String METAVAR_STRING = "text";
    private static final String METAVAR_CHAR = "char";
    private static final String METAVAR_VALUE = "value";

    private static final Set<String> TRUE_VALUES = Set.of("true", "t", "1", "yes", "y", "on");
    private static final Set<String> FALSE_VALUES = Set.of("false", "f", "0", "no", "n", "off");

    /** Sets each and metavar if they're not set */
    private void handleElement(TypeNode valueType, ClassProperty prop) {
      if (each != null && metavar != null) return;
      var transformValue =
          new OptionBehavior(each, all, multiple, metavar, completionCandidates)
              .resolveTypeArgument(prop, resolveType(valueType).getFirst(), "element");
      each = transformValue.getEach();
      metavar = transformValue.getMetavar();
    }

    /** Sets each and metavar if they're not set */
    private void handleEntry(TypeNode keyType, TypeNode valueType, ClassProperty prop) {
      if (each != null && metavar != null) return;
      var transformKey =
          new OptionBehavior(each, all, multiple, metavar, completionCandidates)
              .resolveTypeArgument(prop, resolveType(keyType).getFirst(), "key");
      var transformValue =
          new OptionBehavior(each, all, multiple, metavar, completionCandidates)
              .resolveTypeArgument(prop, resolveType(valueType).getFirst(), "value");
      if (each == null)
        each =
            (rawValue, workingDirUri) -> {
              var split = rawValue.split("=", 2);
              if (split.length != 2) {
                throw BadValue.badKeyValue(rawValue);
              }
              return new VmPair(
                  transformKey.getEach().apply(split[0], workingDirUri),
                  transformValue.getEach().apply(split[1], workingDirUri));
            };
      if (metavar == null) metavar = transformKey.getMetavar() + "=" + transformValue.getMetavar();
    }

    private @Nullable Object allChooseLast(List<Object> values) {
      if (!values.isEmpty()) return values.get(values.size() - 1);
      if (isOptional()) return null;
      throw new MissingOption();
    }

    private static BiFunction<String, URI, Object> eachLong(long min, long max, String typeName) {
      return (rawValue, workingDirUri) -> {
        try {
          var longValue = Long.parseLong(rawValue);
          if (longValue >= min && longValue <= max) {
            return longValue;
          }
          throw BadValue.invalid(rawValue, typeName);
        } catch (NumberFormatException e) {
          throw BadValue.invalid(rawValue, typeName);
        }
      };
    }

    private static @Nullable CommandSpec.CompletionCandidates exportCompletionCandidates(
        VmTyped annotation) {
      var value = VmUtils.readMember(annotation, Identifier.COMPLETION_CANDIDATES);
      if (value instanceof VmNull) return null;
      if (value.equals("paths")) return CommandSpec.CompletionCandidates.PATH;

      // otherwise value is Listing<String> so will export to List<String>
      if (!(value instanceof VmListing vmListing)) throw PklBugException.unreachableCode();
      var result = new HashSet<String>(vmListing.getLength());
      vmListing.forceAndIterateMemberValues((key, member, val) -> result.add((String) val));
      return new Fixed(result);
    }

    public BiFunction<String, URI, Object> getEach() {
      assert each != null;
      return each;
    }

    public Function<List<Object>, Object> getAll() {
      assert all != null;
      return all;
    }

    public Boolean getMultiple() {
      assert multiple != null;
      return multiple;
    }

    public String getMetavar() {
      assert metavar != null;
      return metavar;
    }

    public @Nullable CommandSpec.CompletionCandidates getCompletionCandidates() {
      return completionCandidates;
    }

    public @Nullable Object getDefaultValue() {
      return defaultValue;
    }

    public boolean isOptional() {
      return isNullable || defaultValue != null;
    }
  }

  // endregion
  // region evaluation path

  /**
   * Given a map, construct a typed value of the given class.
   *
   * <p>Transforms List into VmList, Set into VmSet, and Map into VmMap.
   */
  private VmTyped buildObject(VmClass clazz, Map<String, Object> properties) {
    EconomicMap<Object, ObjectMember> members = EconomicMaps.create(properties.size());
    for (var prop : properties.entrySet()) {
      var key = prop.getKey();
      var value = prop.getValue();
      if (value == null) continue;
      var identifier = Identifier.get(key);
      members.put(identifier, VmUtils.createSyntheticObjectProperty(identifier, "", value));
    }
    return new VmTyped(
        VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, members);
  }

  private record SubcommandState(VmTyped module, EconomicMap<Object, ObjectMember> members) {}

  /**
   * Synthesize a module that amends the command module and sets options and parent.
   *
   * <p>The return value is suitable as an argument to buildExecutionModule or evaluateResult.
   */
  private SubcommandState buildExecutionModule(
      VmTyped module, VmTyped options, @Nullable SubcommandState parent) {
    EconomicMap<Object, ObjectMember> properties = EconomicMaps.create(parent != null ? 2 : 1);
    properties.put(
        Identifier.OPTIONS, VmUtils.createSyntheticObjectProperty(Identifier.OPTIONS, "", options));

    if (parent != null) {
      var language = VmLanguage.get(null);
      var amendParent =
          PropertiesLiteralNodeGen.create(
              VmUtils.unavailableSourceSection(),
              language,
              "",
              false,
              null,
              new UnresolvedTypeNode[] {},
              parent.members,
              new ImportNode(
                  language,
                  VmUtils.unavailableSourceSection(),
                  parent.module.getModuleInfo().getResolvedModuleKey(),
                  parent.module.getModuleInfo().getModuleKey().getUri()));

      var parentProperty = module.getVmClass().getProperty(Identifier.PARENT);
      assert parentProperty != null;
      properties.put(
          Identifier.PARENT,
          VmUtils.createObjectProperty(
              language,
              VmUtils.unavailableSourceSection(),
              VmUtils.unavailableSourceSection(),
              Identifier.PARENT,
              "",
              new FrameDescriptor(),
              VmModifier.NONE,
              amendParent,
              parentProperty.getTypeNode()));
    }

    return new SubcommandState(module, properties);
  }

  /** Given a synthesized module, evaluate it and return the output bytes/files */
  private CommandSpec.Result evaluateResult(VmTyped module, SubcommandState parent) {
    var language = VmLanguage.get(null);
    var context = VmContext.get(null);

    var syntheticModule = ModuleKeys.synthetic(REPL_TEXT_URI, "");
    ResolvedModuleKey resolvedModule;
    try {
      resolvedModule = syntheticModule.resolve(securityManager);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SecurityManagerException e) {
      throw new VmExceptionBuilder().withCause(e).build();
    }
    var moduleInfo =
        new ModuleInfo(
            VmUtils.unavailableSourceSection(),
            VmUtils.unavailableSourceSection(),
            null,
            REPL_TEXT,
            syntheticModule,
            resolvedModule,
            true);

    var amendModuleNode =
        AmendModuleNodeGen.create(
            VmUtils.unavailableSourceSection(),
            language,
            new ExpressionNode[] {},
            parent.members,
            moduleInfo,
            new ImportNode(
                language,
                VmUtils.unavailableSourceSection(),
                resolvedModule,
                module.getModuleInfo().getResolvedModuleKey().getUri()));

    var moduleNode =
        new ModuleNode(
            language,
            VmUtils.unavailableSourceSection(),
            amendModuleNode.getModuleInfo().getModuleName(),
            amendModuleNode);

    var evaluated =
        context
            .getModuleCache()
            .getOrLoad(
                amendModuleNode.getModuleInfo().getModuleKey(),
                context.getSecurityManager(),
                context.getModuleResolver(),
                VmUtils::createEmptyModule,
                ((moduleKey1,
                    resolvedModuleKey,
                    moduleResolver,
                    source,
                    emptyModule,
                    importNode) -> {
                  moduleNode.getCallTarget().call(emptyModule, emptyModule);
                  MinPklVersionChecker.check(emptyModule, importNode);
                }),
                null);

    var output = VmUtils.readModuleOutput(evaluated);
    return new CommandSpec.Result(
        VmUtils.readBytesProperty(output).export(),
        VmUtils.readFilesProperty(output, makeFileOutput));
  }

  // endregion
  // region dynamic import handling

  private static boolean isImport(VmTyped value) {
    return value.getVmClass() == CommandModule.getImportClass();
  }

  private static boolean isImport(Object value) {
    return value instanceof VmTyped vmTyped
        && vmTyped.getVmClass() == CommandModule.getImportClass();
  }

  // handle errors from convert/transformAll and correctly format them for the CLI
  private Object handleBadValue(Supplier<Object> f) {
    try {
      return handleErrors(f);
    } catch (Throwable e) {
      // add a newline so this prints nicely under "Error: invalid value for <name>:"
      throw new BadValue("\n" + e.getMessage());
    }
  }

  private <T> T handleErrors(Supplier<T> f) {
    try {
      return f.get();
    } catch (VmStackOverflowException e) {
      if (VmUtils.isPklBug(e)) {
        throw new VmExceptionBuilder()
            .bug("Stack overflow")
            .withCause(e.getCause())
            .build()
            .toPklException(frameTransformer, color);
      }
      throw e.toPklException(frameTransformer, color);
    } catch (VmException e) {
      throw e.toPklException(frameTransformer, color);
    } catch (Exception e) {
      throw new PklBugException(e);
    }
  }

  // for convert, handle imports by replacing Command.Import values
  // with imported module or Mapping<String, Module> values
  // Command.Import instances in returned Pair, List, Set, or Map values are replaced as well
  // other types or nested instances of the above are not affected
  private Object handleImports(Object result, URI workingDirUri) {
    if (result instanceof VmTyped vmTyped && isImport(vmTyped)) {
      return handleImport(vmTyped, workingDirUri);
    } else if (result instanceof VmPair vmPair) {
      if (!isImport(vmPair.getFirst()) && !isImport(vmPair.getSecond())) {
        return vmPair;
      }
      return new VmPair(
          isImport(vmPair.getFirst())
              ? handleImport((VmTyped) vmPair.getFirst(), workingDirUri)
              : vmPair.getFirst(),
          isImport(vmPair.getSecond())
              ? handleImport((VmTyped) vmPair.getSecond(), workingDirUri)
              : vmPair.getSecond());
    } else if (result instanceof VmCollection vmCollection) {
      for (var elem : vmCollection) {
        if (isImport(elem)) {
          var builder = vmCollection.builder();
          vmCollection.forEach(
              it -> builder.add(isImport(it) ? handleImport((VmTyped) it, workingDirUri) : it));
          return builder.build();
        }
      }
      return vmCollection;
    } else if (result instanceof VmMap vmMap) {
      for (var entry : vmMap) {
        if (isImport(entry.getKey()) || isImport(entry.getValue())) {
          var builder = VmMap.builder();
          vmMap.forEach(
              it ->
                  builder.add(
                      isImport(it.getKey())
                          ? handleImport((VmTyped) it.getKey(), workingDirUri)
                          : it.getKey(),
                      isImport(it.getValue())
                          ? handleImport((VmTyped) it.getValue(), workingDirUri)
                          : it.getValue()));
          return builder.build();
        }
      }
    }
    return result;
  }

  private Object handleImport(VmTyped mport, URI workingDirUri) {
    var moduleName = (String) VmUtils.readMember(mport, Identifier.URI);
    String uriString;
    // Ported from org.pkl.cli.commons.cli.commands.BaseOptions:
    try {
      // Can't just use URI constructor, because URI(null, null, "C:/foo/bar", null) turns
      // into `URI("C", null, "/foo/bar", null)`.
      var modulePath = Path.of(moduleName);
      var uri =
          IoUtils.isUriLike(moduleName)
              ? new URI(moduleName)
              : IoUtils.isWindowsAbsolutePath(moduleName)
                  ? modulePath.toUri()
                  : new URI(null, null, IoUtils.toNormalizedPathString(modulePath), null);
      uriString =
          uri.isAbsolute() ? uri.toString() : IoUtils.resolve(workingDirUri, uri).toString();
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidModuleUri", moduleName)
          .withHint(e.getReason())
          .build();
    }

    var isGlob = (Boolean) VmUtils.readMember(mport, Identifier.GLOB);
    var importUri = URI.create(uriString);
    var language = VmLanguage.get(null);

    // non-glob
    if (!isGlob) {
      var moduleKey = moduleResolver.resolve(importUri);
      return language.loadModule(moduleKey);
    }

    // glob
    var globModuleKey = moduleResolver.resolve(importUri);

    try {
      if (!globModuleKey.isGlobbable()) {
        throw exceptionBuilder()
            .evalError("cannotGlobUri", importUri, importUri.getScheme())
            .build();
      }
      var resolvedElements =
          GlobResolver.resolveGlob(securityManager, globModuleKey, null, null, uriString);

      var builder = new VmObjectBuilder(resolvedElements.size());
      for (var entry : resolvedElements.entrySet()) {
        var moduleKey = moduleResolver.resolve(entry.getValue().uri());
        builder.addEntry(entry.getKey(), language.loadModule(moduleKey));
      }
      return builder.toMapping(resolvedElements);
    } catch (IOException e) {
      throw exceptionBuilder().evalError("ioErrorResolvingGlob", importUri).withCause(e).build();
    } catch (ExternalReaderProcessException e) {
      throw exceptionBuilder().evalError("externalReaderFailure").withCause(e).build();
    } catch (SecurityManagerException e) {
      throw exceptionBuilder().withCause(e).build();
    } catch (InvalidGlobPatternException e) {
      throw exceptionBuilder()
          .evalError("invalidGlobPattern", uriString)
          .withHint(e.getMessage())
          .build();
    }
  }

  // endregion
  // region utilities

  private static @Nullable String exportNullableString(VmObjectLike value, Object key) {
    var result = VmValue.export(VmUtils.readMember(value, key));
    return result instanceof PNull ? null : (String) result;
  }

  /** Check a value and its parents to see if any assign/amend the given property */
  private void checkPropertyIsUndefined(VmTyped value, Identifier name) {
    var member = VmUtils.findMember(value, name);
    if (member == null) return;

    var memberNode = member.getMemberNode();
    var sourceSection =
        memberNode == null
            ? member.getSourceSection()
            : memberNode.getBodyNode() instanceof DefaultPropertyBodyNode
                ? null
                : memberNode.getSourceSection();

    if (sourceSection != null) {
      throw exceptionBuilder()
          .evalError("commandMustNotAssignOrAmendProperty", name)
          .withSourceSection(sourceSection)
          .build();
    }
  }

  private VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder();
  }

  // endregion
}
