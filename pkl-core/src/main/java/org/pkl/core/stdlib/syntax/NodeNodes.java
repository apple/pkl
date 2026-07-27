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
package org.pkl.core.stdlib.syntax;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.syntax.SyntaxNodes.NodeData;

public final class NodeNodes {
  private NodeNodes() {}

  public abstract static class walk extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyVisit = ApplyVmFunction1Node.create();

    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmFunction visit) {
      var result = walkNode(self, visit);
      // the root of the returned tree has no parent
      if (result.hasExtraStorage()) {
        ((NodeData) result.getExtraStorage()).parentVm = null;
      }
      return result;
    }

    private VmTyped walkNode(VmTyped nodeVm, VmFunction visit) {
      var visited = applyVisit.execute(visit, nodeVm);

      VmTyped node;
      boolean descend;
      if (visited instanceof VmPair pair) {
        node = (VmTyped) pair.getFirst();
        descend = (Boolean) pair.getSecond();
      } else {
        // `null`: leave this node unchanged and keep descending
        node = nodeVm;
        descend = true;
      }
      if (!descend) {
        return node;
      }

      var childrenVm = (VmList) VmUtils.readMember(node, Identifier.CHILDREN);
      var length = childrenVm.getLength();
      if (length == 0) {
        return node;
      }

      var newChildren = new Object[length];
      var changed = false;
      for (var i = 0; i < length; i++) {
        var child = (VmTyped) childrenVm.get(i);
        var newChild = walkNode(child, visit);
        newChildren[i] = newChild;
        changed |= newChild != child;
      }
      // reuse the node (and its extra storage) untouched when nothing below changed
      return changed ? SyntaxNodes.rebuild(node, newChildren) : node;
    }
  }
}
