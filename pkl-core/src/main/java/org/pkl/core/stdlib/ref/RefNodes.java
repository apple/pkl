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
package org.pkl.core.stdlib.ref;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmReference;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod3Node;

public class RefNodes {
  public abstract static class Reference extends ExternalMethod3Node {
    @Specialization
    protected VmReference eval(
        VirtualFrame frame, VmTyped self, VmTyped domain, VmClass clazz, Object data) {
      return new VmReference(domain, clazz, data);
    }
  }
}
