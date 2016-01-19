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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.pkl.core.Loggers;
import org.pkl.core.SecurityManagers;
import org.pkl.core.StackFrameTransformers;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;

public abstract class StdLibModule {
  @TruffleBoundary
  protected static void loadModule(URI uri, VmTyped instance) {
    // evaluate eagerly to increase thread safety
    // (stdlib module objects are statically shared singletons when running on JVM)
    // and ensure compile-time evaluation in AOT mode
    VmUtils.createContext(
            () -> {
              var vmContext = VmContext.get(null);
              vmContext.initialize(
                  new VmContext.Holder(
                      StackFrameTransformers.defaultTransformer,
                      SecurityManagers.defaultManager,
                      new ModuleResolver(List.of(ModuleKeyFactories.standardLibrary)),
                      new ResourceManager(SecurityManagers.defaultManager, List.of()),
                      Loggers.noop(),
                      Map.of(),
                      Map.of(),
                      null,
                      null,
                      null,
                      null));
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
            })
        .close();
  }
}
