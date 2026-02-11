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
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmReference;
import org.pkl.core.stdlib.ExternalPropertyNode;

public class ReferenceAccessNodes {
  private ReferenceAccessNodes() {}

  public abstract static class isProperty extends ExternalPropertyNode {

    @Specialization
    protected boolean eval(VmReference.Access self) {
      return self.isProperty();
    }
  }

  public abstract static class property extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmReference.Access self) {
      if (!self.isProperty()) {
        return VmNull.withoutDefault();
      }
      return self.getProperty();
    }
  }

  public abstract static class isSubscript extends ExternalPropertyNode {

    @Specialization
    protected boolean eval(VmReference.Access self) {
      return self.isSubscript();
    }
  }

  public abstract static class key extends ExternalPropertyNode {
    @Specialization
    protected Object eval(VmReference.Access self) {
      if (!self.isSubscript()) {
        return VmNull.withoutDefault();
      }
      return self.getKey();
    }
  }
}
