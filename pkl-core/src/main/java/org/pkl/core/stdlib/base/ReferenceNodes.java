/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmReference;
import org.pkl.core.runtime.VmValue;
import org.pkl.core.stdlib.ExternalMethod0Node;

public class ReferenceNodes {
  private ReferenceNodes() {}

  public abstract static class getRootValue extends ExternalMethod0Node {
    @Specialization
    protected VmValue eval(VmReference self) {
      return self.getRootValue();
    }
  }

  public abstract static class getPath extends ExternalMethod0Node {
    @Specialization
    protected VmList eval(VmReference self) {
      return VmList.create(self.getPath());
    }
  }
}
