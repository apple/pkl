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
package org.pkl.core.stdlib.benchmark;

import static org.pkl.core.stdlib.benchmark.BenchmarkUtils.runBenchmark;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.PathElement;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod0Node;
import org.pkl.core.util.MutableReference;
import org.pkl.core.util.Nullable;

public final class OutputBenchmarkNodes {
  public abstract static class run extends ExternalMethod0Node {
    @TruffleBoundary
    @Specialization
    protected VmTyped eval(VmTyped self) {
      var module = (VmTyped) VmUtils.readMember(self, Identifier.SOURCE_MODULE);
      var moduleInfo = VmUtils.getModuleInfo(module);
      var moduleKey = new UncachedModuleKey(moduleInfo.getModuleKey());
      var blackhole = new MutableReference<String>(null);
      var language = VmLanguage.get(this);
      return runBenchmark(
          self,
          (repetitions) -> {
            for (long i = 0; i < repetitions; i++) {
              // Evaluate module in the same evaluator that evaluates this benchmark.
              // Alternatively, we could use a separate evaluator.
              var uncachedModule = language.loadModule(moduleKey);
              var output = VmUtils.readMember(uncachedModule, Identifier.OUTPUT);
              blackhole.set(VmUtils.readTextProperty(output));
            }
            return blackhole.get();
          });
    }
  }

  private static final class UncachedModuleKey implements ModuleKey {
    private final ModuleKey delegate;

    public UncachedModuleKey(ModuleKey delegate) {
      this.delegate = delegate;
    }

    @Override
    public URI getUri() {
      return delegate.getUri();
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      var resolvedDelegate = delegate.resolve(securityManager);
      return new ResolvedModuleKey() {
        @Override
        public ModuleKey getOriginal() {
          return UncachedModuleKey.this;
        }

        @Override
        public URI getUri() {
          return resolvedDelegate.getUri();
        }

        @Override
        public String loadSource() throws IOException {
          return resolvedDelegate.loadSource();
        }
      };
    }

    @Override
    public boolean isCached() {
      // don't cache module in memory (otherwise output will only be computed once)
      return false;
    }

    @Override
    public @Nullable Path getFileCacheLocation() {
      // file caching is ok
      return delegate.getFileCacheLocation();
    }

    @Override
    public boolean hasHierarchicalUris() {
      return delegate.hasHierarchicalUris();
    }

    @Override
    public boolean isLocal() {
      return delegate.isLocal();
    }

    @Override
    public boolean isGlobbable() {
      return delegate.isGlobbable();
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI uri)
        throws IOException, SecurityManagerException {
      return delegate.hasElement(securityManager, uri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      return delegate.listElements(securityManager, baseUri);
    }
  }
}
