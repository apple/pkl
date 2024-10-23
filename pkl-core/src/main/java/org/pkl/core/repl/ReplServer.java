/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.repl;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.polyglot.Context;
import org.pkl.core.*;
import org.pkl.core.SecurityManager;
import org.pkl.core.ast.*;
import org.pkl.core.ast.builder.AstBuilder;
import org.pkl.core.ast.member.*;
import org.pkl.core.ast.repl.ResolveClassMemberNode;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.http.HttpClient;
import org.pkl.core.module.*;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.parser.LexParseException;
import org.pkl.core.parser.Parser;
import org.pkl.core.parser.antlr.PklParser;
import org.pkl.core.parser.antlr.PklParser.*;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.repl.ReplRequest.Eval;
import org.pkl.core.repl.ReplRequest.Load;
import org.pkl.core.repl.ReplRequest.Reset;
import org.pkl.core.repl.ReplResponse.EvalError;
import org.pkl.core.repl.ReplResponse.EvalSuccess;
import org.pkl.core.repl.ReplResponse.InvalidRequest;
import org.pkl.core.resource.ResourceReader;
import org.pkl.core.runtime.*;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.MutableReference;
import org.pkl.core.util.Nullable;

public class ReplServer implements AutoCloseable {
  private final IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();
  private final Context polyglotContext;
  private final VmLanguage language;
  private final ReplState replState;
  private final Path workingDir;
  private final SecurityManager securityManager;
  private final ModuleResolver moduleResolver;
  private final VmExceptionRenderer errorRenderer;
  private final PackageResolver packageResolver;
  private final @Nullable ProjectDependenciesManager projectDependenciesManager;

  public ReplServer(
      SecurityManager securityManager,
      HttpClient httpClient,
      Logger logger,
      Collection<ModuleKeyFactory> moduleKeyFactories,
      Collection<ResourceReader> resourceReaders,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      @Nullable Path moduleCacheDir,
      @Nullable DeclaredDependencies projectDependencies,
      @Nullable String outputFormat,
      Path workingDir,
      StackFrameTransformer frameTransformer) {

    this.workingDir = workingDir;
    this.securityManager = securityManager;
    this.moduleResolver = new ModuleResolver(moduleKeyFactories);
    this.errorRenderer = new VmExceptionRenderer(new StackTraceRenderer(frameTransformer));
    replState = new ReplState(createEmptyReplModule(BaseModule.getModuleClass().getPrototype()));

    var languageRef = new MutableReference<VmLanguage>(null);
    packageResolver = PackageResolver.getInstance(securityManager, httpClient, moduleCacheDir);
    projectDependenciesManager =
        projectDependencies == null
            ? null
            : new ProjectDependenciesManager(projectDependencies, moduleResolver, securityManager);
    polyglotContext =
        VmUtils.createContext(
            () -> {
              languageRef.set(VmLanguage.get(null));
              var vmContext = VmContext.get(null);
              vmContext.initialize(
                  new VmContext.Holder(
                      frameTransformer,
                      securityManager,
                      httpClient,
                      moduleResolver,
                      new ResourceManager(securityManager, resourceReaders),
                      logger,
                      environmentVariables,
                      externalProperties,
                      moduleCacheDir,
                      outputFormat,
                      packageResolver,
                      projectDependenciesManager));
            });
    language = languageRef.get();
  }

  public List<ReplResponse> handleRequest(ReplRequest request) {
    polyglotContext.enter();
    try {
      if (request instanceof Eval eval) {
        return handleEval(eval);
      }

      if (request instanceof Load load) {
        return handleLoad(load);
      }

      if (request instanceof ReplRequest.Completion completion) {
        return handleCompletion(completion);
      }

      if (request instanceof Reset) {
        return handleReset();
      }

      return List.of(
          new InvalidRequest("Unsupported request type: " + request.getClass().getSimpleName()));
    } catch (Exception e) {
      return List.of(new ReplResponse.InternalError(e));
    } finally {
      polyglotContext.leave();
    }
  }

  @Override
  public void close() {
    polyglotContext.close(true);
    try {
      packageResolver.close();
    } catch (IOException ignored) {
    }
  }

  private List<ReplResponse> handleEval(Eval request) {
    var results =
        evaluate(
            replState, request.id, request.text, request.evalDefinitions, request.forceResults);
    return results.stream()
        .map(
            result ->
                result instanceof ReplResponse response
                    ? response
                    : new EvalSuccess(render(result)))
        .collect(Collectors.toList());
  }

  @SuppressWarnings("StatementWithEmptyBody")
  private List<Object> evaluate(
      ReplState replState,
      String requestId,
      String text,
      boolean evalDefinitions,
      boolean forceResults) {
    var parser = new Parser();
    PklParser.ReplInputContext replInputContext;
    var uri = URI.create("repl:" + requestId);

    try {
      replInputContext = parser.parseReplInput(text);
    } catch (LexParseException.IncompleteInput e) {
      return List.of(new ReplResponse.IncompleteInput(e.getMessage()));
    } catch (LexParseException e) {
      var exception = VmUtils.toVmException(e, text, uri, uri.toString());
      var errorMessage = errorRenderer.render(exception);
      return List.of(new EvalError(errorMessage));
    }

    var results = new ArrayList<>();
    var module = ModuleKeys.synthetic(uri, workingDir.toUri(), uri, text, false);
    ResolvedModuleKey resolved;
    try {
      resolved = module.resolve(securityManager);
    } catch (SecurityManagerException e) {
      throw new VmExceptionBuilder().withCause(e).build();
    } catch (IOException e) {
      // resolving a synthetic module should never cause IOException
      throw new AssertionError(e);
    }

    var builder =
        new AstBuilder(
            VmUtils.loadSource(resolved),
            language,
            replState.module.getModuleInfo(),
            moduleResolver);
    var childrenExceptEof =
        replInputContext.children.subList(0, replInputContext.children.size() - 1);

    for (var tree : childrenExceptEof) {
      try {
        if (tree instanceof ExprContext) {
          var exprNode = (ExpressionNode) tree.accept(builder);
          evaluateExpr(replState, exprNode, forceResults, results);
        } else if (tree instanceof ImportClauseContext importClause) {
          addStaticModuleProperty(builder.visitImportClause(importClause));
        } else if (tree instanceof ClassPropertyContext classProperty) {
          var propertyNode = builder.visitClassProperty(classProperty);
          var property = addModuleProperty(propertyNode);
          if (evalDefinitions) {
            evaluateMemberDef(replState, property, forceResults, results);
          }
        } else if (tree instanceof ClazzContext clazz) {
          addStaticModuleProperty(builder.visitClazz(clazz));
        } else if (tree instanceof TypeAliasContext typeAlias) {
          addStaticModuleProperty(builder.visitTypeAlias(typeAlias));
        } else if (tree instanceof ClassMethodContext classMethod) {
          addModuleMethodDef(builder.visitClassMethod(classMethod));
        } else if (tree instanceof ModuleDeclContext) {
          // do nothing for now
        } else if (tree instanceof TerminalNode && tree.toString().equals(",")) {
          // do nothing
        } else {
          results.add(
              new ReplResponse.InternalError(new IllegalStateException("Unexpected parse result")));
        }
      } catch (VmException e) {
        // TODO: patch stack trace for constants
        results.add(new EvalError(errorRenderer.render(e)));
      }
    }

    return results;
  }

  private void addStaticModuleProperty(ObjectMember property) {
    replState.module.getPrototype().addProperty(property);
  }

  private ObjectMember addModuleProperty(UnresolvedPropertyNode propertyNode) {
    var needToCreateNewModuleToEnforceLateBinding =
        !propertyNode.isLocal() && replState.module.hasMember(propertyNode.getName());

    if (needToCreateNewModuleToEnforceLateBinding) {
      replState.module = createEmptyReplModule(replState.module);
    }

    var resolveNode =
        new ResolveClassMemberNode(
            language, new FrameDescriptor(), propertyNode, replState.module.getVmClass());

    var property =
        (ClassProperty)
            callNode.call(resolveNode.getCallTarget(), replState.module, replState.module);

    replState.module.getVmClass().addProperty(property);
    return property.getInitializer();
  }

  private void addModuleMethodDef(UnresolvedMethodNode methodNode) {
    var needToCreateNewModuleToEnforceLateBinding =
        !methodNode.isLocal()
            && replState.module.getVmClass().hasDeclaredMethod(methodNode.getName());

    if (needToCreateNewModuleToEnforceLateBinding) {
      replState.module = createEmptyReplModule(replState.module);
    }

    var resolveNode =
        new ResolveClassMemberNode(
            language, new FrameDescriptor(), methodNode, replState.module.getVmClass());

    var method =
        (ClassMethod)
            callNode.call(resolveNode.getCallTarget(), replState.module, replState.module);

    replState.module.getVmClass().addMethod(method);
  }

  private void evaluateExpr(
      ReplState replState, ExpressionNode exprNode, boolean forceResults, List<Object> results) {

    var rootNode =
        new SimpleRootNode(
            language, new FrameDescriptor(), exprNode.getSourceSection(), "", exprNode);

    var result = callNode.call(rootNode.getCallTarget(), replState.module, replState.module);

    if (forceResults) VmValue.force(result, false);
    results.add(result);
  }

  private void evaluateMemberDef(
      ReplState replState, ObjectMember memberDef, boolean forceResults, List<Object> results) {

    var result =
        memberDef.getConstantValue() != null
            ? memberDef.getConstantValue()
            : callNode.call(memberDef.getCallTarget(), replState.module, replState.module);

    if (forceResults) VmValue.force(result, false);
    results.add(result);
  }

  private List<ReplResponse> handleLoad(Load request) {
    try {
      var uri = IoUtils.resolve(workingDir.toUri(), request.uri);
      var moduleToLoad = moduleResolver.resolve(uri);
      var loadedModule = language.loadModule(moduleToLoad);
      replState.module =
          createReplModule(
              loadedModule.getVmClass().getDeclaredProperties(),
              loadedModule.getVmClass().getDeclaredMethods(),
              loadedModule.getMembers(),
              loadedModule.getParent());
      return List.of();
    } catch (VmException e) {
      return List.of(new EvalError(errorRenderer.render(e)));
    }
  }

  private List<ReplResponse> handleCompletion(ReplRequest.Completion request) {
    var members = new HashSet<String>();

    if (IoUtils.isWhitespace(request.text)) {
      collectMembers(members, BaseModule.getModule());
      collectMembers(members, replState.module);
      return List.of(new ReplResponse.Completion(members));
    }

    List<Object> results;

    // make sure completion request never affects repl state
    var tempModule =
        new ReplState(
            createReplModule(
                replState.module.getVmClass().getDeclaredProperties(),
                replState.module.getVmClass().getDeclaredMethods(),
                replState.module.getMembers(),
                replState.module.getParent()));
    results = evaluate(tempModule, request.id, request.text, false, false);

    if (results.isEmpty()) {
      return List.of(ReplResponse.Completion.EMPTY);
    }

    var lastResult = results.get(results.size() - 1);
    if (lastResult instanceof EvalError || lastResult instanceof ReplResponse.IncompleteInput) {
      return List.of(ReplResponse.Completion.EMPTY);
    }

    assert !(lastResult instanceof ReplResponse);

    VmObjectLike composite;
    if (lastResult instanceof VmObjectLike objectLike) {
      composite = objectLike;
    } else {
      composite = VmUtils.getClass(lastResult).getPrototype();
    }

    collectMembers(members, composite);
    return List.of(new ReplResponse.Completion(members));
  }

  private void collectMembers(Set<String> members, VmObjectLike composite) {
    composite.iterateMembers(
        (key, prop) -> {
          if (key instanceof Identifier) {
            members.add(key.toString());
          }
          return true;
        });
    composite
        .getVmClass()
        .visitMethodDefsTopDown(
            fun -> members.add(fun.getName() + (fun.getParameterCount() == 0 ? "()" : "(")));
  }

  private List<ReplResponse> handleReset() {
    replState.module = createEmptyReplModule(BaseModule.getModuleClass().getPrototype());
    return List.of();
  }

  private VmTyped createEmptyReplModule(@Nullable VmTyped parent) {
    return createReplModule(List.of(), List.of(), EconomicMaps.create(), parent);
  }

  private VmTyped createReplModule(
      Iterable<ClassProperty> propertyDefs,
      Iterable<ClassMethod> methodDefs,
      UnmodifiableEconomicMap<Object, ObjectMember> moduleMembers,
      @Nullable VmTyped parent) {
    var uri = URI.create("repl:repl");
    var classInfo = PClassInfo.get("repl", "module", uri);
    var moduleKey = ModuleKeys.synthetic(uri, workingDir.toUri(), uri, "", false);
    ResolvedModuleKey resolvedModuleKey;
    try {
      resolvedModuleKey = moduleKey.resolve(securityManager);
    } catch (IOException | SecurityManagerException e) {
      // should never happen
      throw new RuntimeException(e);
    }
    var moduleInfo =
        new ModuleInfo(
            VmUtils.unavailableSourceSection(),
            VmUtils.unavailableSourceSection(),
            null,
            "repl",
            moduleKey,
            resolvedModuleKey,
            false);
    var module =
        new VmTyped(
            VmUtils.createEmptyMaterializedFrame(),
            null, // set by initSuperclass()
            null,
            moduleMembers);
    module.setExtraStorage(moduleInfo);
    var clazz =
        new VmClass(
            VmUtils.unavailableSourceSection(),
            VmUtils.unavailableSourceSection(),
            null,
            List.of(),
            VmModifier.NONE,
            classInfo,
            List.of(),
            module);
    if (parent != null) {
      var superclass = parent.getVmClass();
      var supertypeNode = TypeNode.forClass(VmUtils.unavailableSourceSection(), superclass);
      clazz.initSupertype(supertypeNode, superclass);
    }
    clazz.addProperties(propertyDefs);
    clazz.addMethods(methodDefs);
    return module;
  }

  private String render(Object value) {
    return VmValueRenderer.multiLine(Integer.MAX_VALUE).render(value);
  }

  private static class ReplState {
    VmTyped module;

    public ReplState(VmTyped module) {
      this.module = module;
    }
  }
}
