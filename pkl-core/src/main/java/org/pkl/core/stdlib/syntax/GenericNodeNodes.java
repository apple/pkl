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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.LoopNode;
import java.util.ArrayDeque;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2NodeGen;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.syntax.SyntaxNodes.GenericNodeData;

/** Backs {@code pkl.syntax#GenericNode.fold} and {@code pkl.syntax#GenericNode.transform}. */
public final class GenericNodeNodes {
  private GenericNodeNodes() {}

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyAccumulate = ApplyVmFunction2NodeGen.create();

    @Specialization
    protected Object eval(VmTyped self, Object initial, VmFunction operator) {
      var pending = new ArrayDeque<VmTyped>();
      pending.push(self);
      var result = initial;
      var visited = 0;
      while (!pending.isEmpty()) {
        var node = pending.pop();
        result = applyAccumulate.execute(operator, result, node);
        var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN);
        for (var i = children.getLength() - 1; i >= 0; i--) {
          pending.push((VmTyped) children.get(i));
        }
        visited += 1;
      }
      LoopNode.reportLoopCount(this, visited);
      return result;
    }
  }

  public abstract static class transform extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyOperator = ApplyVmFunction1Node.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction operator) {
      var result = transformNode(self, operator);
      // the root of the returned tree has no parent
      if (result.hasExtraStorage()) {
        ((GenericNodeData) result.getExtraStorage()).parentVm = null;
      }
      return result;
    }

    private VmTyped transformNode(VmTyped self, VmFunction operator) {
      var stack = new ArrayDeque<Descent>();
      var pending = self;
      var visited = 0;

      for (; ; ) {
        var transformed = applyOperator.execute(operator, pending);
        assert transformed instanceof VmPair;
        var pair = (VmPair) transformed;
        visited += 1;

        var node = (VmTyped) pair.getFirst();
        var descend = (Boolean) pair.getSecond();
        if (descend) {
          var childrenVm = (VmList) VmUtils.readMember(node, Identifier.CHILDREN);
          if (childrenVm.getLength() != 0) {
            var descent = new Descent(node, childrenVm);
            stack.push(descent);
            pending = descent.currentChild();
            continue;
          }
        }

        var done = node;
        Descent descent;
        while ((descent = stack.peek()) != null) {
          if (descent.acceptChild(done)) {
            pending = descent.currentChild();
            break;
          }
          stack.pop();
          done = descent.finish();
        }
        if (descent == null) {
          LoopNode.reportLoopCount(this, visited);
          return done;
        }
      }
    }
  }

  /**
   * A node whose children are being transformed, plus the transformed children collected so far.
   */
  private static final class Descent {
    private final VmTyped node;
    private final VmList childrenVm;
    private final Object[] newChildren;
    private int index;
    private boolean changed;

    Descent(VmTyped node, VmList childrenVm) {
      this.node = node;
      this.childrenVm = childrenVm;
      this.newChildren = new Object[childrenVm.getLength()];
    }

    VmTyped currentChild() {
      return (VmTyped) childrenVm.get(index);
    }

    /** Records the transformed current child; returns whether further children remain. */
    boolean acceptChild(VmTyped newChild) {
      changed |= newChild != childrenVm.get(index);
      newChildren[index] = newChild;
      return ++index < newChildren.length;
    }

    VmTyped finish() {
      // reuse the node (and its extra storage) untouched when nothing below changed
      return changed ? SyntaxNodes.rebuild(node, newChildren) : node;
    }
  }
}
