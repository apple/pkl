/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.CommandSpec;
import org.pkl.core.CommandSpec.OptionType;
import org.pkl.core.CommandSpec.OptionType.Collection;
import org.pkl.core.CommandSpec.OptionType.Enum;
import org.pkl.core.CommandSpec.OptionType.Primitive;
import org.pkl.core.FileOutput;
import org.pkl.core.PNull;
import org.pkl.core.PType;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.SimpleRootNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.expression.literal.AmendModuleNodeGen;
import org.pkl.core.ast.expression.literal.PropertiesLiteralNodeGen;
import org.pkl.core.ast.expression.unary.ImportNode;
import org.pkl.core.ast.member.ClassProperty;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.ast.member.ModuleNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.PropertyTypeNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.ast.type.UnresolvedTypeNode;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

/** Runs commands. */
public final class CommandSpecParser {

  private final ModuleResolver moduleResolver;
  private final SecurityManager securityManager;
  private final Function<VmTyped, FileOutput> makeFileOutput;

  public CommandSpecParser(
      ModuleResolver moduleResolver,
      SecurityManager securityManager,
      Function<VmTyped, FileOutput> makeFileOutput) {
    this.moduleResolver = moduleResolver;
    this.securityManager = securityManager;
    this.makeFileOutput = makeFileOutput;
  }

  public CommandSpec parse(VmTyped commandModule) {
    checkAmends(commandModule, CommandModule.getModule().getVmClass());
    checkPropertyIsUndefined(commandModule, Identifier.OPTIONS);
    checkPropertyIsUndefined(commandModule, Identifier.PARENT);

    var optionsClass = getOptionsClass(commandModule);
    var commandInfo =
        checkAmends(
            VmUtils.readMember(commandModule, Identifier.COMMAND),
            CommandModule.getCommandInfoClass());
    var commandName = (String) VmUtils.readMember(commandInfo, Identifier.NAME);
    var optionSpecs = collectOptions(optionsClass);

    return new CommandSpec(
        commandName,
        exportNullableString(commandInfo, Identifier.DESCRIPTION),
        (Boolean) VmUtils.readMember(commandInfo, Identifier.HIDE),
        (Boolean) VmUtils.readMember(commandInfo, Identifier.NOOP),
        optionSpecs.getFirst(),
        optionSpecs.getSecond(),
        collectSubcommands(commandInfo),
        (options, parent) ->
            new CommandSpec.State(
                buildExecutionModule(
                    commandModule,
                    buildObject(optionsClass, options),
                    parent == null ? null : (SubcommandState) parent.contents()),
                (it) -> evaluateResult(commandModule, (SubcommandState) it)));
  }

  private static @Nullable String exportNullableString(VmObjectLike value, Object key) {
    var result = VmValue.export(VmUtils.readMember(value, key));
    return result instanceof PNull ? null : (String) result;
  }

  private VmClass getOptionsClass(VmTyped commandModule) {
    var optionsProperty = commandModule.getVmClass().getProperty(Identifier.OPTIONS);
    if (optionsProperty == null) {
      throw exceptionBuilder()
          .cannotFindMember(commandModule, Identifier.OPTIONS)
          .withSourceSection(commandModule.getVmClass().getSourceSection())
          .build();
    }
    var optionsPropertyTypeNode = optionsProperty.getTypeNode();
    if (optionsPropertyTypeNode == null) {
      throw exceptionBuilder()
          .withSourceSection(optionsProperty.getSourceSection())
          .evalError("commandOptionNoTypeAnnotation", "options", " in `pkl:Command` subclass")
          .build();
    }
    var optionsTypeNode = optionsPropertyTypeNode.getTypeNode();
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

  private Pair<List<CommandSpec.Flag>, List<CommandSpec.Argument>> collectOptions(
      VmClass optionsClass) {
    var flags = new ArrayList<CommandSpec.Flag>();
    var flagNames = new HashSet<String>();
    var args = new ArrayList<CommandSpec.Argument>();
    var argNames = new HashSet<String>();

    var clazz = optionsClass;
    while (clazz != null) {
      for (var prop : clazz.getDeclaredProperties()) {
        var name = prop.getName().toString();
        if (prop.isLocal()
            || clazz.isHiddenProperty(prop.getName())
            || prop.isExternal()
            || flagNames.contains(name)
            || argNames.contains(name)) continue;

        VmTyped flagAnnotation = null;
        VmTyped argAnnotation = null;
        for (var annotation : prop.getAllAnnotations()) {
          if (annotation.getVmClass() == CommandModule.getFlagClass()) {
            if (flagAnnotation != null) continue;
            flagAnnotation = annotation;
          } else if (annotation.getVmClass() == CommandModule.getArgumentClass()) {
            if (argAnnotation != null) continue;
            argAnnotation = annotation;
          }
        }

        var defaultValue = getDefaultValue(prop);
        if (flagAnnotation != null && argAnnotation != null) {
          throw exceptionBuilder()
              .withSourceSection(prop.getSourceSection())
              .evalError("commandOptionBothFlagAndArgument", prop.getName())
              .build();
        } else if (argAnnotation != null) {
          if (defaultValue != null) {
            throw exceptionBuilder()
                .withSourceSection(prop.getSourceSection())
                .evalError("commandArgumentUnexpectedDefaultValue", prop.getName())
                .build();
          }
          var parseFunction = getParseFunction(argAnnotation);
          var type = getOptionType(prop, parseFunction, null);
          if (type instanceof OptionType.Map) {
            throw exceptionBuilder()
                .withSourceSection(prop.getSourceSection())
                .evalError("commandArgumentUnexpectedMapType", prop.getName())
                .build();
          }
          argNames.add(name);
          args.add(
              new CommandSpec.Argument(
                  name, type, parseFunction, VmUtils.exportDocComment(prop.getDocComment())));
        } else {
          String shortName = null;
          CommandSpec.ParseOptionFunction parseFunction = null;
          boolean hide = false;
          if (flagAnnotation != null) {
            shortName = exportNullableString(flagAnnotation, Identifier.SHORT_NAME);
            parseFunction = getParseFunction(flagAnnotation);
            hide = (Boolean) VmUtils.readMember(flagAnnotation, Identifier.HIDE);
          }
          if ("help".equals(name) || "h".equals(shortName)) {
            throw exceptionBuilder()
                .withSourceSection(prop.getSourceSection())
                .evalError("commandFlagHelpCollision", prop.getName())
                .build();
          }

          flagNames.add(name);
          flags.add(
              new CommandSpec.Flag(
                  name,
                  shortName,
                  getOptionType(prop, parseFunction, defaultValue),
                  defaultValue,
                  parseFunction,
                  VmUtils.exportDocComment(prop.getDocComment()),
                  hide));
        }
      }
      clazz = clazz.getSuperclass();
    }

    String multipleArgName = null;
    for (var arg : args) {
      if (arg.type() instanceof Collection) {
        if (multipleArgName != null) {
          throw exceptionBuilder()
              .withSourceSection(optionsClass.getSourceSection())
              .evalError("commandArgumentsMultipleListOrSet")
              .build();
        }
        multipleArgName = arg.name();
      }
    }

    return Pair.of(flags, args);
  }

  private @Nullable Object getDefaultValue(ClassProperty prop) {
    var constantValue = prop.getInitializer().getConstantValue();
    if (constantValue != null) return VmValue.export(constantValue);

    var bodyNode = prop.getInitializer().getMemberNode();
    if (bodyNode == null || bodyNode.getBodyNode() instanceof DefaultPropertyBodyNode) {
      var typeNode = prop.getTypeNode();
      if (typeNode == null) {
        throw exceptionBuilder()
            .withSourceSection(prop.getSourceSection())
            .evalError("commandOptionNoTypeAnnotation", prop.getName(), "")
            .withHint("Option properties require type annotations.")
            .build();
      }

      if (stripConstraints(typeNode.export()) instanceof PType.Union union
          && union.getDefaultElement() != null) {
        var elements = union.getElementTypes();
        for (var element : elements) {
          if (!(element instanceof PType.StringLiteral)) {
            throw exceptionBuilder()
                .withSourceSection(prop.getSourceSection())
                .evalError(
                    "commandOptionUnsupportedType",
                    prop.getName(),
                    "",
                    typeNode.getSourceSection().getCharacters())
                .build();
          }
        }
        return ((PType.StringLiteral) union.getDefaultElement()).getLiteral();
      }

      return null;
    }

    var language = VmLanguage.get(null);
    var rootNode =
        new SimpleRootNode(
            language,
            new FrameDescriptor(),
            bodyNode.getSourceSection(),
            "",
            bodyNode.getBodyNode());
    var callNode = Truffle.getRuntime().createIndirectCallNode();
    var value =
        callNode.call(rootNode.getCallTarget(), prop.getOwner(), prop.getOwner(), prop.getName());
    VmValue.force(value, false);
    return VmValue.export(value);
  }

  private OptionType getOptionType(
      ClassProperty prop,
      CommandSpec.@Nullable ParseOptionFunction parseFunction,
      @Nullable Object defaultValue) {
    var typeNode = prop.getTypeNode();
    if (typeNode == null) {
      throw exceptionBuilder()
          .withSourceSection(prop.getSourceSection())
          .evalError("commandOptionNoTypeAnnotation", prop.getName(), "")
          .build();
    }
    var type = stripConstraints(typeNode.export());
    var required = defaultValue == null;
    if (type instanceof PType.Nullable nullable) {
      if (!required) {
        throw exceptionBuilder()
            .withSourceSection(prop.getSourceSection())
            .evalError("commandOptionTypeNullableWithDefaultValue", prop.getName())
            .build();
      }
      required = false;
      type = nullable.getBaseType();
    }
    if (parseFunction != null) {
      return new Primitive(Primitive.Type.STRING, required);
    }

    return getOptionType(prop, typeNode, type, required);
  }

  private OptionType getOptionType(
      ClassProperty prop, PropertyTypeNode typeNode, PType type, boolean required) {
    type = stripConstraints(type);
    if (type instanceof PType.Class classType) {
      var clazz = classType.getPClass();
      if (clazz == BaseModule.getNumberClass().export()) {
        return new Primitive(Primitive.Type.NUMBER, required);
      } else if (clazz == BaseModule.getFloatClass().export()) {
        return new Primitive(Primitive.Type.FLOAT, required);
      } else if (clazz == BaseModule.getIntClass().export()) {
        return new Primitive(Primitive.Type.INT, required);
      } else if (clazz == BaseModule.getBooleanClass().export()) {
        return new Primitive(Primitive.Type.BOOLEAN, required);
      } else if (clazz == BaseModule.getStringClass().export()) {
        return new Primitive(Primitive.Type.STRING, required);
      } else if (clazz == BaseModule.getListClass().export()) {
        var elementType = getOptionType(prop, typeNode, classType.getTypeArguments().get(0), true);
        if (!(elementType instanceof Primitive || elementType instanceof Enum)) {
          throw exceptionBuilder()
              .withSourceSection(prop.getSourceSection())
              .evalError("commandOptionUnsupportedType", prop.getName(), "element ", elementType)
              .build();
        }
        return new Collection(Collection.Type.LIST, elementType, required);
      } else if (clazz == BaseModule.getSetClass().export()) {
        var elementType = getOptionType(prop, typeNode, classType.getTypeArguments().get(0), true);
        if (!(elementType instanceof Primitive || elementType instanceof Enum)) {
          throw exceptionBuilder()
              .withSourceSection(prop.getSourceSection())
              .evalError("commandOptionUnsupportedType", prop.getName(), "element ", elementType)
              .build();
        }
        return new Collection(Collection.Type.SET, elementType, required);
      } else if (clazz == BaseModule.getMapClass().export()) {
        var keyType = getOptionType(prop, typeNode, classType.getTypeArguments().get(0), true);
        if (!(keyType instanceof Primitive || keyType instanceof Enum)) {
          throw exceptionBuilder()
              .withSourceSection(prop.getSourceSection())
              .evalError("commandOptionUnsupportedType", prop.getName(), "key ", keyType)
              .build();
        }
        var valueType = getOptionType(prop, typeNode, classType.getTypeArguments().get(1), true);
        if (!(valueType instanceof Primitive || valueType instanceof Enum)) {
          throw exceptionBuilder()
              .withSourceSection(prop.getSourceSection())
              .evalError("commandOptionUnsupportedType", prop.getName(), "value ", valueType)
              .build();
        }
        return new OptionType.Map(keyType, valueType, required);
      }
    } else if (type instanceof PType.Alias aliasType) {
      var alias = aliasType.getTypeAlias();
      if (alias == BaseModule.getUIntTypeAlias().export()) {
        return new Primitive(Primitive.Type.UINT, required);
      } else if (alias == BaseModule.getUInt8TypeAlias().export()) {
        return new Primitive(Primitive.Type.UINT8, required);
      } else if (alias == BaseModule.getUInt16TypeAlias().export()) {
        return new Primitive(Primitive.Type.UINT16, required);
      } else if (alias == BaseModule.getUInt32TypeAlias().export()) {
        return new Primitive(Primitive.Type.UINT32, required);
      } else if (alias == BaseModule.getInt8TypeAlias().export()) {
        return new Primitive(Primitive.Type.INT8, required);
      } else if (alias == BaseModule.getInt16TypeAlias().export()) {
        return new Primitive(Primitive.Type.INT16, required);
      } else if (alias == BaseModule.getInt32TypeAlias().export()) {
        return new Primitive(Primitive.Type.INT32, required);
      } else if (alias == BaseModule.getCharTypeAlias().export()) {
        return new Primitive(Primitive.Type.CHAR, required);
      }
    } else if (type instanceof PType.Union union) {
      var elements = union.getElementTypes();
      var choices = new ArrayList<String>(elements.size());
      for (var element : elements) {
        if (!(element instanceof PType.StringLiteral stringLiteral)) {
          throw exceptionBuilder()
              .withSourceSection(prop.getSourceSection())
              .evalError(
                  "commandOptionUnsupportedType",
                  prop.getName(),
                  "",
                  typeNode.getSourceSection().getCharacters())
              .build();
        }
        choices.add(stringLiteral.getLiteral());
      }
      return new OptionType.Enum(choices, required);
    }

    throw exceptionBuilder()
        .withSourceSection(prop.getSourceSection())
        .evalError(
            "commandOptionUnsupportedType",
            prop.getName(),
            "",
            typeNode.getSourceSection().getCharacters())
        .build();
  }

  private static PType stripConstraints(PType type) {
    if (type instanceof PType.Constrained constrained) {
      return constrained.getBaseType();
    }
    return type;
  }

  private @Nullable CommandSpec.ParseOptionFunction getParseFunction(VmTyped annotation) {
    var func = VmUtils.readMember(annotation, Identifier.PARSE);
    if (func instanceof VmNull) {
      return null;
    } else if (func.equals("import")) {
      return new ParseFunction(true, this::importParse);
    } else if (func.equals("import*")) {
      return new ParseFunction(true, this::importGlobParse);
    }
    return new ParseFunction(false, ((VmFunction) func)::apply);
  }

  private record ParseFunction(boolean isImport, Function<String, Object> func)
      implements CommandSpec.ParseOptionFunction {
    @Override
    public Object parse(String value) {
      return func.apply(value);
    }
  }

  private Object importParse(String uriString) {
    var moduleKey = moduleResolver.resolve(URI.create(uriString));
    return VmLanguage.get(null).loadModule(moduleKey);
  }

  private Object importGlobParse(String globPattern) {
    var language = VmLanguage.get(null);
    var importUri = URI.create(globPattern);
    var globModuleKey = moduleResolver.resolve(importUri);

    try {
      if (!globModuleKey.isGlobbable()) {
        throw exceptionBuilder()
            .evalError("cannotGlobUri", importUri, importUri.getScheme())
            .build();
      }
      var resolvedElements =
          GlobResolver.resolveGlob(securityManager, globModuleKey, null, null, globPattern);

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
          .evalError("invalidGlobPattern", globPattern)
          .withHint(e.getMessage())
          .build();
    }
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

  /**
   * Given a map, construct a typed value of the given class.
   *
   * <p>Transforms List into VmList, Set into VmSet, and Map into VmMap.
   */
  private VmTyped buildObject(VmClass clazz, Map<String, Object> properties) {
    EconomicMap<Object, ObjectMember> members = EconomicMaps.create(properties.size());
    for (var prop : properties.entrySet()) {
      var value = prop.getValue();
      if (value == null) continue;
      if (value instanceof List<?> list) {
        value = VmList.create(list);
      } else if (value instanceof Set<?> set) {
        value = VmSet.create(set);
      } else if (value instanceof Map<?, ?> map) {
        //noinspection unchecked
        value = VmMap.create((Map<Object, Object>) map);
      }
      var identifier = Identifier.get(prop.getKey());
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
    options.force(false, true);
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

  /** Check that a value is a VmTyped and that it inherits from the given class */
  private VmTyped checkAmends(Object value, VmClass clazz) {
    if (!(value instanceof VmTyped typed)) {
      throw new VmExceptionBuilder().typeMismatch(value, clazz).build();
    }

    var valueClass = typed.getVmClass();
    while (valueClass != clazz) {
      valueClass = valueClass.getSuperclass();
      if (valueClass == null) {
        throw new VmExceptionBuilder().typeMismatch(value, clazz).build();
      }
    }

    return typed;
  }

  /** Check a value and its parents to see if any assign/amend the given property */
  private void checkPropertyIsUndefined(VmTyped value, Identifier name) {
    while (value != null) {
      var member = value.getMember(name);
      if (member != null) {
        var memberNode = member.getMemberNode();
        if (memberNode == null) {
          throw new VmExceptionBuilder()
              .evalError("commandMustNotAssignOrAmendProperty", name)
              .withSourceSection(member.getSourceSection())
              .build();
        } else if (!(memberNode.getBodyNode() instanceof DefaultPropertyBodyNode)) {
          throw new VmExceptionBuilder()
              .evalError("commandMustNotAssignOrAmendProperty", name)
              .withSourceSection(memberNode.getSourceSection())
              .build();
        }
      }
      value = value.getParent();
    }
  }

  private VmExceptionBuilder exceptionBuilder() {
    return new VmExceptionBuilder();
  }
}
