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
package org.pkl.core.stdlib.benchmark;

import static org.pkl.core.stdlib.benchmark.BenchmarkUtils.runBenchmark;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.net.URI;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.MutableReference;

public final class ParserBenchmarkNodes {
  public abstract static class run extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected VmTyped eval(VmTyped self) {
      var sourceText = (String) VmUtils.readMember(self, Identifier.SOURCE_TEXT);
      var moduleUri = (String) VmUtils.readMember(self, Identifier.SOURCE_URI);
      ModuleKey moduleKey;
      if (moduleUri.equals("repl:text")) {
        moduleKey = ModuleKeys.synthetic(URI.create(moduleUri), sourceText);
      } else {
        moduleKey =
            ModuleKeys.cached(
                VmContext.get(this).getModuleResolver().resolve(IoUtils.createUri(moduleUri)),
                sourceText);
      }
      var blackhole = new MutableReference<VmTyped>(null);
      var language = VmLanguage.get(this);
      return runBenchmark(
          self,
          (repetitions) -> {
            for (long i = 0; i < repetitions; i++) {
              blackhole.set(language.loadModule(moduleKey));
            }
            return blackhole.get();
          });
    }
  }
}
