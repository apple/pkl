/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import static org.pkl.core.PClassInfo.pklBaseUri;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.pkl.core.FeatureFlag;
import org.pkl.core.Loggers;
import org.pkl.core.SecurityManagers;
import org.pkl.core.StackFrameTransformers;
import org.pkl.core.evaluatorSettings.TraceMode;
import org.pkl.core.http.HttpClient;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.resource.ResourceReader;
import org.pkl.core.resource.ResourceReaders;

public abstract class StdLibModule {
  private static final Map<FeatureFlag, Boolean> stdLibFeatureFlags = Map.of();

  @TruffleBoundary
  protected static void loadModule(URI uri, VmTyped instance) {
    doLoad(uri, instance);
  }

  private static void doLoad(URI uri, VmTyped instance) {
    VmUtils.createContext(
            () -> {
              var vmContext = VmContext.get(null);
              var isPklBaseModule = uri.equals(pklBaseUri);
              var resourceReaders =
                  isPklBaseModule
                      // needed when initializing `pkl:base` because of
                      // `read("prop:pkl.outputFormat")`
                      ? List.of(ResourceReaders.externalProperty())
                      : List.<ResourceReader>of();
              vmContext.initialize(
                  new VmContext.Holder(
                      StackFrameTransformers.defaultTransformer,
                      SecurityManagers.defaultManager,
                      HttpClient.dummyClient(),
                      new ModuleResolver(List.of(ModuleKeyFactories.standardLibrary)),
                      new ResourceManager(SecurityManagers.defaultManager, resourceReaders),
                      Loggers.noop(),
                      Map.of(),
                      Map.of(),
                      null,
                      null,
                      null,
                      null,
                      TraceMode.COMPACT,
                      false,
                      stdLibFeatureFlags));
              var language = VmLanguage.get(null);
              var moduleKey = ModuleKeys.standardLibrary(uri);
              var source = VmUtils.loadSource((ResolvedModuleKey) moduleKey);
              language.initializeModule(
                  moduleKey,
                  (ResolvedModuleKey) moduleKey,
                  vmContext.getModuleResolver(),
                  source,
                  instance,
                  null);
              // evaluate eagerly to increase thread safety
              // (stdlib module objects are statically shared singletons when running on JVM)
              // and ensure compile-time evaluation in AOT mode
              instance.force(false, true);

              // seed base module's `output` members; `output` contains truffle nodes that
              // need to be initialized statically (e.g. LetExprNode, TypeTestNode).
              // additionally, its `cachedMembers` is not thread-safe and needs to be initialized
              // statically.
              if (isPklBaseModule) {
                var output = VmUtils.readModuleOutput(instance);
                output.force(false, true);
              }
            })
        .close();
  }
}
