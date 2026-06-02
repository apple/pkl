/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.evaluatorsettings;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.net.URI;
import java.net.URISyntaxException;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod3Node;
import org.pkl.core.util.PathResolver;
import org.pkl.core.util.PathResolvers;

public class EvaluatorSettingsNodes {

  public abstract static class resolvePath extends ExternalMethod3Node {
    @TruffleBoundary
    private URI toUri(String baseUri) {
      try {
        var uri = new URI(baseUri);
        // guaranteed by Pkl
        assert uri.getScheme().equals("file");
        return uri;
      } catch (URISyntaxException e) {
        throw exceptionBuilder().evalError("invalidUri", baseUri).build();
      }
    }

    private PathResolver getPathResolver(boolean forWindows) {
      return forWindows ? PathResolvers.forWindows() : PathResolvers.forPosix();
    }

    @Specialization
    protected String eval(VmTyped ignored, String uriStr, String path, boolean forWindows) {
      var uri = toUri(uriStr);
      var baseUri = uri.resolve(".");
      var resolver = getPathResolver(forWindows);
      return resolver.resolvePath(baseUri, path);
    }
  }
}
