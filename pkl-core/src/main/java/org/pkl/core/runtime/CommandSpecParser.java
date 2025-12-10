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

import com.oracle.truffle.api.frame.FrameDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.graalvm.collections.EconomicMap;
import org.organicdesign.fp.collections.ImList;
import org.organicdesign.fp.collections.PersistentVector;
import org.pkl.core.CommandSpec;
import org.pkl.core.FileOutput;
import org.pkl.core.PNull;
import org.pkl.core.PklBugException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.expression.literal.AmendModuleNode;
import org.pkl.core.ast.expression.literal.AmendModuleNodeGen;
import org.pkl.core.ast.expression.unary.ImportNode;
import org.pkl.core.ast.member.DefaultPropertyBodyNode;
import org.pkl.core.ast.member.ModuleNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

/** Runs commands. */
public final class CommandSpecParser {

  private final SecurityManager securityManager;
  private final Function<VmTyped, FileOutput> makeFileOutput;

  public CommandSpecParser(
      SecurityManager securityManager, Function<VmTyped, FileOutput> makeFileOutput) {
    this.securityManager = securityManager;
    this.makeFileOutput = makeFileOutput;
  }

  public CommandSpec parse(VmTyped commandModule) {
    return parse(commandModule, PersistentVector.empty());
  }

  private CommandSpec parse(VmTyped commandModule, ImList<String> commandPath) {
    checkAmends(commandModule, CommandModule.getModule().getVmClass());
    checkPropertyIsUndefined(commandModule, Identifier.OPTIONS);
    checkPropertyIsUndefined(commandModule, Identifier.PARENT);

    var optionsClass = getOptionsClass(commandModule);
    var commandInfo =
        checkAmends(
            VmUtils.readMember(commandModule, Identifier.COMMAND),
            CommandModule.getCommandInfoClass());
    var commandName = (String) VmUtils.readMember(commandInfo, Identifier.NAME);
    var newCommandPath = commandPath.append(commandName);
    //noinspection unchecked
    var aliases =
        (List<String>) VmValue.export(VmUtils.readMember(commandInfo, Identifier.ALIASES));
    var optionSpecs = collectOptions(optionsClass);

    return new CommandSpec(
        commandName,
        aliases,
        exportNullableString(commandInfo, Identifier.DESCRIPTION),
        (Boolean) VmUtils.readMember(commandInfo, Identifier.HIDE),
        (Boolean) VmUtils.readMember(commandInfo, Identifier.NOOP),
        optionSpecs.getFirst(),
        optionSpecs.getSecond(),
        collectSubcommands(commandInfo, newCommandPath),
        (options, parent) ->
            new CommandSpec.State(
                buildExecutionModule(
                    newCommandPath,
                    commandModule,
                    buildObject(optionsClass, options),
                    parent == null ? null : (AmendModuleNode) parent.moduleNode()),
                (it) -> evaluateResult((AmendModuleNode) it)));
  }
  
  private static @Nullable String exportNullableString(VmObjectLike value, Object key) {
    var result = VmValue.export(VmUtils.readMember(value, key));
      return result instanceof PNull ? null : (String) result;
  }

  private VmClass getOptionsClass(VmTyped commandModule) {
    var optionsProperty = commandModule.getVmClass().getProperty(Identifier.OPTIONS);
    if (optionsProperty == null) {
      throw new PklBugException("no property `options` found in pkl:Command sub-module");
    }
    var optionsPropertyTypeNode = optionsProperty.getTypeNode();
    if (optionsPropertyTypeNode == null) {
      throw new RuntimeException(
          "no type annotation on `options` property in pkl:Command sub-module");
    }
    var optionsTypeNode = optionsPropertyTypeNode.getTypeNode();
    if (!(optionsTypeNode instanceof TypeNode.ClassTypeNode node)) {
      throw new RuntimeException(
          "type annotation on `options` property in pkl:Command sub-module is not a class type");
    }
    var clazz = node.getVmClass();
    if (clazz.isAbstract()) {
      throw new RuntimeException(
          "class of `options` property in pkl:Command sub-module must not be abstact");
    }
    return clazz;
  }

  // name from property name
  // description from doc comment
  // required if not nullable
  // parse: when specified, don't validate for known option types
  // args:
  // * multiple may be specified
  // * no List allowed if command has children
  // * only last arg may be a List
  // types:
  // * Primitives:
  //   * Number, Float, Int, UInt, Int8, Int16, Int32, UInt8, UInt16, UInt32
  //   * Boolean: generate --no-<name> aliase
  //   * String, Char
  // * List<Primitive>, Set<Primitive>
  // * Map<Primitive, Primitive|List<Primitive>|Set<Primitive>>
  // * TODO later: Duration, DataSize (in format "<value>.?<unit>", case-insensitive)

  private Pair<List<CommandSpec.Flag>, List<CommandSpec.Argument>> collectOptions(
      VmClass optionsClass) {
    var flags = new HashMap<String, CommandSpec.Flag>();
    var args = new HashMap<String, CommandSpec.Argument>();

    var clazz = optionsClass;
    while (clazz != null) {
      for (var prop : clazz.getDeclaredProperties()) {
        var name = prop.getName().toString();
        if (prop.isLocal()
            || clazz.isHiddenProperty(prop.getName())
            || prop.isExternal()
            || flags.containsKey(name)
            || args.containsKey(name)) continue;

        var typeNode = prop.getTypeNode();
        if (typeNode == null) {
          throw new RuntimeException("missing type annotation for property `TODO`");
        }

        VmTyped flagAnnotation = null;
        VmTyped argAnnotation = null;
        for (var annotation : prop.getAnnotations()) {
          if (annotation.getVmClass() == CommandModule.getFlagClass()) {
            if (flagAnnotation != null) {
              throw new RuntimeException(
                  "found more than one @Flag annotations for property `TODO`");
            }
            flagAnnotation = annotation;
          } else if (annotation.getVmClass() == CommandModule.getArgumentClass()) {
            if (argAnnotation != null) {
              throw new RuntimeException(
                  "found more than one @Argument annotations for property `TODO`");
            }
            argAnnotation = annotation;
          }
        }

        if (flagAnnotation != null && argAnnotation != null) {
          throw new RuntimeException(
              "found both @Flag and @Argument annotations for property `TODO`");
        } else if (argAnnotation != null) {
          args.put(
              name,
              new CommandSpec.Argument(
                  name,
                  typeNode.export(), // TODO
                  getParseFunction(argAnnotation),
                  VmUtils.exportDocComment(prop.getDocComment())));
        } else if (flagAnnotation != null) {
          flags.put(
              name,
              new CommandSpec.Flag(
                  name,
                  exportNullableString(flagAnnotation, Identifier.SHORT_NAME),
                  exportNullableString(flagAnnotation, Identifier.SEPARATOR),
                  exportNullableString(flagAnnotation, Identifier.KEY_VALUE_SEPARATOR),
                  typeNode.export(), // TODO
                  null, // TODO
                  getParseFunction(flagAnnotation),
                  VmUtils.exportDocComment(prop.getDocComment()),
                  false,
                  false));
        } else {
          flags.put(
              name,
              new CommandSpec.Flag(
                  name,
                  null,
                  null,
                  "=",
                  typeNode.export(), // TODO
                  null, // TODO
                  null,
                  VmUtils.exportDocComment(prop.getDocComment()),
                  false,
                  false));
        }
      }
      clazz = clazz.getSuperclass();
    }

    return Pair.of(new ArrayList<>(flags.values()), new ArrayList<>(args.values()));
  }

  private @Nullable CommandSpec.ParseOptionFunction getParseFunction(VmTyped annotation) {
    return null;
  }

  private List<CommandSpec> collectSubcommands(VmTyped commandInfo, ImList<String> commandPath) {
    return List.of(); // TODO
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
      // TODO transform value types for collections

      var identifier = Identifier.get(prop.getKey());
      members.put(identifier, VmUtils.createSyntheticObjectProperty(identifier, "", value));
    }
    return new VmTyped(
        VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, members);
  }

  /**
   * Synthesize a module that amends the command module and sets options and parent.
   *
   * <p>The return value is suitable as an argument to buildExecutionModule or evaluateResult.
   */
  private AmendModuleNode buildExecutionModule(
      List<String> names, VmTyped module, VmTyped options, @Nullable AmendModuleNode parent) {
    var uriString = "cmd:/" + String.join("/", names);
    var syntheticModule = ModuleKeys.synthetic(URI.create(uriString), "");
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
            uriString,
            syntheticModule,
            resolvedModule,
            true);
    var language = VmLanguage.get(null);

    EconomicMap<Object, ObjectMember> properties = EconomicMaps.create(parent != null ? 2 : 1);
    properties.put(
        Identifier.OPTIONS, VmUtils.createSyntheticObjectProperty(Identifier.OPTIONS, "", options));

    if (parent != null) {
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
              parent,
              parentProperty.getTypeNode()));
    }

    return AmendModuleNodeGen.create(
        VmUtils.unavailableSourceSection(),
        language,
        new ExpressionNode[] {},
        properties,
        moduleInfo,
        new ImportNode(
            language,
            VmUtils.unavailableSourceSection(),
            resolvedModule,
            module.getModuleInfo().getResolvedModuleKey().getUri()));
  }

  /** Given a synthesized module, evaluate it and return the output bytes/files */
  private CommandSpec.Result evaluateResult(AmendModuleNode amendModuleNode) {
    var language = VmLanguage.get(null);
    var context = VmContext.get(null);

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
                    _source,
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
            .adhocEvalError("Command modules must not assign or amend the `%s` property", name)
            .withSourceSection(member.getSourceSection())
            .build();
        } else if (!(memberNode.getBodyNode() instanceof DefaultPropertyBodyNode)) {
          throw new VmExceptionBuilder()
            .adhocEvalError("Command modules must not assign or amend the `%s` property", name)
            .withSourceSection(memberNode.getSourceSection())
            .build();
        }
      }
      value = value.getParent();
    }
  }
}
