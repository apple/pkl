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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import org.pkl.core.ast.ObjectToMixinNode;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod0Node;

public final class ObjectNodes {
  private ObjectNodes() {}

  public abstract static class toMixin extends ExternalMethod0Node {
    @Specialization
    protected VmFunction eval(VmObject self) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      var rootNode = new ObjectToMixinNode(self, new FrameDescriptor());
      return new VmFunction(
          VmUtils.createEmptyMaterializedFrame(),
          null,
          1,
          rootNode,
          null);
    }
  }
}
