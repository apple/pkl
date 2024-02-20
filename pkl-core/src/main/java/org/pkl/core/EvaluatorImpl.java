/**
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
package org.pkl.core;

import com.oracle.truffle.api.TruffleStackTrace;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.internal.ToStringNodeGen;
import org.pkl.core.module.ModuleKeyFactory;
import org.pkl.core.module.ProjectDependenciesManager;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.resource.ResourceReader;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.ModuleResolver;
import org.pkl.core.runtime.ResourceManager;
import org.pkl.core.runtime.TestResults;
import org.pkl.core.runtime.TestRunner;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmStackOverflowException;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.runtime.VmValue;
import org.pkl.core.runtime.VmValueRenderer;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

public class EvaluatorImpl implements Evaluator {
  protected final StackFrameTransformer frameTransformer;
  protected final ModuleResolver moduleResolver;
  protected final Context polyglotContext;
  protected final @Nullable Duration timeout;
  protected final @Nullable ScheduledExecutorService timeoutExecutor;
  protected final SecurityManager securityManager;
  protected final BufferedLogger logger;
  protected final PackageResolver packageResolver;
  private final VmValueRenderer vmValueRenderer = VmValueRenderer.singleLine(1000);

  public EvaluatorImpl(
      StackFrameTransformer transformer,
      SecurityManager manager,
      Logger logger,
      Collection<ModuleKeyFactory> factories,
      Collection<ResourceReader> readers,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      @Nullable Duration timeout,
      @Nullable Path moduleCacheDir,
      @Nullable DeclaredDependencies projectDependencies,
      @Nullable String outputFormat) {

    securityManager = manager;
    frameTransformer = transformer;
    moduleResolver = new ModuleResolver(factories);
    this.logger = new BufferedLogger(logger);
    packageResolver = PackageResolver.getInstance(securityManager, moduleCacheDir);
    polyglotContext =
        VmUtils.createContext(
            () -> {
              VmContext vmContext = VmContext.get(null);
              vmContext.initialize(
                  new VmContext.Holder(
                      transformer,
                      manager,
                      moduleResolver,
                      new ResourceManager(manager, readers),
                      this.logger,
                      environmentVariables,
                      externalProperties,
                      moduleCacheDir,
                      outputFormat,
                      packageResolver,
                      projectDependencies == null
                          ? null
                          : new ProjectDependenciesManager(projectDependencies)));
            });
    this.timeout = timeout;
    // NOTE: would probably make sense to share executor between evaluators
    // (blocked on https://github.com/oracle/graal/issues/1230)
    timeoutExecutor =
        timeout == null
            ? null
            : Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                  Thread t = new Thread(runnable, "Pkl Timeout Scheduler");
                  t.setDaemon(true);
                  return t;
                });
  }

  @Override
  public PModule evaluate(ModuleSource moduleSource) {
    return doEvaluate(
        moduleSource,
        (module) -> {
          module.force(false);
          return (PModule) module.export();
        });
  }

  @Override
  public String evaluateOutputText(ModuleSource moduleSource) {
    return doEvaluate(
        moduleSource,
        (module) -> {
          var output = (VmTyped) VmUtils.readMember(module, Identifier.OUTPUT);
          return VmUtils.readTextProperty(output);
        });
  }

  @Override
  public Object evaluateOutputValue(ModuleSource moduleSource) {
    return doEvaluate(
        moduleSource,
        (module) -> {
          var output = (VmTyped) VmUtils.readMember(module, Identifier.OUTPUT);
          var value = VmUtils.readMember(output, Identifier.VALUE);
          if (value instanceof VmValue) {
            var vmValue = (VmValue) value;
            vmValue.force(false);
            return vmValue.export();
          }
          return value;
        });
  }

  @Override
  public Map<String, FileOutput> evaluateOutputFiles(ModuleSource moduleSource) {
    return doEvaluate(
        moduleSource,
        (module) -> {
          var output = (VmTyped) VmUtils.readMember(module, Identifier.OUTPUT);
          var filesOrNull = VmUtils.readMember(output, Identifier.FILES);
          if (filesOrNull instanceof VmNull) {
            return Map.of();
          }
          var files = (VmMapping) filesOrNull;
          var result = new LinkedHashMap<String, FileOutput>();
          files.forceAndIterateMemberValues(
              (key, member, value) -> {
                assert member.isEntry();
                result.put((String) key, new FileOutputImpl(this, (VmTyped) value));
                return true;
              });
          return result;
        });
  }

  @Override
  public Object evaluateExpression(ModuleSource moduleSource, String expression) {
    // optimization: if the expression is `output.text` or `output.value` (the common cases), read
    // members directly instead of creating new truffle nodes.
    if (expression.equals("output.text")) {
      return evaluateOutputText(moduleSource);
    }
    if (expression.equals("output.value")) {
      return evaluateOutputValue(moduleSource);
    }
    return doEvaluate(
        moduleSource,
        (module) -> {
          var expressionResult =
              VmUtils.evaluateExpression(module, expression, securityManager, moduleResolver);
          if (expressionResult instanceof VmValue) {
            var value = (VmValue) expressionResult;
            value.force(false);
            return value.export();
          }
          return expressionResult;
        });
  }

  @Override
  public String evaluateExpressionString(ModuleSource moduleSource, String expression) {
    // optimization: if the expression is `output.text` (the common case), read members
    // directly
    // instead of creating new truffle nodes.
    if (expression.equals("output.text")) {
      return evaluateOutputText(moduleSource);
    }
    return doEvaluate(
        moduleSource,
        (module) -> {
          var expressionResult =
              VmUtils.evaluateExpression(module, expression, securityManager, moduleResolver);
          var toStringNode =
              ToStringNodeGen.create(
                  VmUtils.unavailableSourceSection(), new ConstantValueNode(expressionResult));
          var stringified = toStringNode.executeGeneric(VmUtils.createEmptyMaterializedFrame());
          return (String) stringified;
        });
  }

  @Override
  public ModuleSchema evaluateSchema(ModuleSource moduleSource) {
    return doEvaluate(moduleSource, (module) -> module.getModuleInfo().getModuleSchema(module));
  }

  @Override
  public TestResults evaluateTest(ModuleSource moduleSource, boolean overwrite) {
    return doEvaluate(
        moduleSource,
        (module) -> {
          var testRunner = new TestRunner(logger, frameTransformer, overwrite);
          return testRunner.run(module);
        });
  }

  @Override
  public <T> T evaluateOutputValueAs(ModuleSource moduleSource, PClassInfo<T> classInfo) {
    return doEvaluate(
        moduleSource,
        (module) -> {
          var output = (VmTyped) VmUtils.readMember(module, Identifier.OUTPUT);
          var value = VmUtils.readMember(output, Identifier.VALUE);
          var valueClassInfo = VmUtils.getClass(value).getPClassInfo();
          if (valueClassInfo.equals(classInfo)) {
            if (value instanceof VmValue) {
              var vmValue = (VmValue) value;
              vmValue.force(false);
              //noinspection unchecked
              return (T) vmValue.export();
            }
            //noinspection unchecked
            return (T) value;
          }
          throw moduleOutputValueTypeMismatch(module, classInfo, value, output);
        });
  }

  @Override
  public void close() {
    // if currently executing, blocks until cancellation has completed (see
    // https://github.com/oracle/graal/issues/1230)
    polyglotContext.close(true);
    try {
      packageResolver.close();
    } catch (IOException ignored) {
    }

    if (timeoutExecutor != null) {
      timeoutExecutor.shutdown();
    }
  }

  String evaluateOutputText(VmTyped fileOutput) {
    return doEvaluate(() -> VmUtils.readTextProperty(fileOutput));
  }

  @SuppressWarnings("removal")
  private <T> T doEvaluate(Supplier<T> supplier) {
    @Nullable TimeoutTask timeoutTask = null;
    logger.clear();
    if (timeout != null) {
      assert timeoutExecutor != null;
      timeoutTask = new TimeoutTask();
      timeoutExecutor.schedule(timeoutTask, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    polyglotContext.enter();
    T evalResult;
    // There is a chance that a timeout is triggered just when evaluation completes on its own.
    // In this case, if evaluation completed normally or with an expected exception (VmException),
    // nevertheless report the timeout. (For technical reasons, timeout implies that evaluator is
    // being closed,
    // which needs to be communicated to the client.)
    // If evaluation completed with an unexpected exception (translated to PklBugException) or
    // error,
    // report that instead of the timeout so as not to swallow a fundamental problem.
    try {
      evalResult = supplier.get();
    } catch (VmStackOverflowException e) {
      if (isPklBug(e)) {
        throw new VmExceptionBuilder()
            .bug("Stack overflow")
            .withCause(e.getCause())
            .build()
            .toPklException(frameTransformer);
      }
      handleTimeout(timeoutTask);
      throw e.toPklException(frameTransformer);
    } catch (VmException e) {
      handleTimeout(timeoutTask);
      throw e.toPklException(frameTransformer);
    } catch (Exception e) {
      throw new PklBugException(e);
    } catch (ExceptionInInitializerError e) {
      if (!(e.getCause() instanceof VmException)) {
        throw new PklBugException(e);
      }
      var pklException = ((VmException) e.getCause()).toPklException(frameTransformer);
      var error = new ExceptionInInitializerError(pklException);
      error.setStackTrace(e.getStackTrace());
      throw new PklBugException(error);
    } catch (ThreadDeath e) {
      if (e.getClass()
          .getName()
          .equals("com.oracle.truffle.polyglot.PolyglotEngineImpl$CancelExecution")) {
        // Truffle cancelled evaluation in response to polyglotContext.close(true) triggered by
        // TimeoutTask
        handleTimeout(timeoutTask);
        throw PklBugException.unreachableCode();
      } else {
        throw e;
      }
    } finally {
      try {
        polyglotContext.leave();
      } catch (IllegalStateException ignored) {
        // happens if evaluation has already been cancelled with polyglotContext.close(true)
      }
    }

    handleTimeout(timeoutTask);
    return evalResult;
  }

  protected <T> T doEvaluate(ModuleSource moduleSource, Function<VmTyped, T> doEvaluate) {
    return doEvaluate(
        () -> {
          var moduleKey = moduleResolver.resolve(moduleSource);
          var module = VmLanguage.get(null).loadModule(moduleKey);
          return doEvaluate.apply(module);
        });
  }

  private void handleTimeout(@Nullable TimeoutTask timeoutTask) {
    if (timeoutTask == null || timeoutTask.cancel()) return;

    assert timeout != null;
    // TODO: use a different exception type so that clients can tell apart timeouts from other
    // errors
    throw new PklException(
        ErrorMessages.create(
            "evaluationTimedOut", (timeout.getSeconds() + timeout.getNano() / 1_000_000_000d)));
  }

  private VmException moduleOutputValueTypeMismatch(
      VmTyped module, PClassInfo<?> expectedClassInfo, Object value, VmTyped output) {
    var moduleUri = module.getModuleInfo().getModuleKey().getUri();
    var builder =
        new VmExceptionBuilder()
            .evalError(
                "invalidModuleOutputValue",
                expectedClassInfo.getDisplayName(),
                VmUtils.getClass(value).getPClassInfo().getDisplayName(),
                moduleUri);
    var outputValueMember = output.getMember(Identifier.VALUE);
    assert outputValueMember != null;
    var uriOfValueMember = outputValueMember.getSourceSection().getSource().getURI();
    // If `value` was explicitly re-assigned, show that in the stack trace.
    // Otherwise, show the module header.
    if (!uriOfValueMember.equals(PClassInfo.pklBaseUri)) {
      return builder
          .withSourceSection(outputValueMember.getBodySection())
          .withMemberName("value")
          .build();
    } else {
      // if the module does not extend or amend anything, suggest amending the module URI
      if (module.getParent() != null
          && module.getParent().getVmClass().equals(BaseModule.getModuleClass())
          && expectedClassInfo.isModuleClass()) {
        builder.withHint(
            String.format(
                "Try adding `amends %s` to the module header.",
                vmValueRenderer.render(expectedClassInfo.getModuleUri().toString())));
      }
      return builder
          .withSourceSection(module.getModuleInfo().getHeaderSection())
          .withMemberName(module.getModuleInfo().getModuleName())
          .build();
    }
  }

  private boolean isPklBug(VmStackOverflowException e) {
    // There's no good way to tell if a StackOverflowError came from Pkl, or from our
    // implementation.
    // This is a simple heuristic; it's pretty likely that any stack overflow error that occurs
    // if there's less than 100 truffle frames is due to our own doing.
    var truffleStackTraceElements = TruffleStackTrace.getStackTrace(e);
    return truffleStackTraceElements != null && truffleStackTraceElements.size() < 100;
  }

  // ScheduledFuture.cancel() is problematic, so let's handle cancellation on our own
  private final class TimeoutTask implements Runnable {
    // both fields guarded by synchronizing on `this`
    private boolean started = false;
    private boolean cancelled = false;

    @Override
    public void run() {
      synchronized (this) {
        if (cancelled) return;

        started = true;
      }

      // may take a while
      close();
    }

    /** Returns `true` if this task was successfully cancelled before it had started. */
    public synchronized boolean cancel() {
      if (started) return false;

      cancelled = true;
      return true;
    }
  }
}
