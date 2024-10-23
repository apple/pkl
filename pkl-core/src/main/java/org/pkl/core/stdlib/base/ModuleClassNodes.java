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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.net.URI;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklName;

@PklName("Module")
public final class ModuleClassNodes {
  private ModuleClassNodes() {}

  public abstract static class relativePathTo extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected VmList eval(VmObjectLike self, VmObjectLike other) {
      var selfKey = VmUtils.getModuleInfo(self).getModuleKey();
      var selfUri = selfKey.getUri();

      if (!other.isModuleObject()) {
        throw exceptionBuilder()
            .evalError("expectedModule")
            // No meaningful SourceSection available, in this case.
            .build();
      }
      var otherKey = VmUtils.getModuleInfo(other).getModuleKey();
      var otherUri = otherKey.getUri();

      var index = selfUri.toString().lastIndexOf('/');
      if (index != -1) {
        var baseUri = URI.create(selfUri.toString().substring(0, index + 1));
        var relativizedUri = baseUri.relativize(otherUri);
        if (!relativizedUri.isAbsolute()) {
          var pathElements = relativizedUri.getPath().split("/");
          return VmList.create(pathElements, pathElements.length - 1);
        }
      }

      throw exceptionBuilder()
          .evalError("noDescendentPathBetweenModules", selfUri, otherUri)
          .build();
    }
  }
}
