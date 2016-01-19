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
package org.pkl.core.stdlib.reflect;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod1Node;

// TODO: reflect.Class, reflect.Module, etc., should have identity-based equality
//       (but not clear how to achieve this w/o adding support for user-defined equality)
public final class ClassNodes {
  private ClassNodes() {}

  public abstract static class isSubclassOf extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected boolean eval(VmTyped self, VmTyped other) {
      return ((VmClass) self.getExtraStorage()).isSubclassOf(((VmClass) other.getExtraStorage()));
    }
  }
}
