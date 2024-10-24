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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmList;
import org.pkl.core.stdlib.ExternalMethod1Node;

public final class FunctionNodes {
  private FunctionNodes() {}

  public abstract static class applyToList extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmFunction self, VmList argList) {
      var argCount = argList.getLength();

      var args = new Object[2 + argCount];
      args[0] = self.getThisValue();
      args[1] = self;

      var i = 2;
      for (var arg : argList) {
        args[i++] = arg;
      }

      return callNode.call(self.getCallTarget(), args);
    }
  }
}
