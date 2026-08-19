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
import com.oracle.truffle.api.nodes.Node;
import java.util.ArrayDeque;
import java.util.Set;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2Node;
import org.pkl.core.ast.lambda.ApplyVmFunction2NodeGen;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.syntax.SyntaxNodes.ViewData;

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

  public abstract static class findChildWhere extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmTyped self, VmFunction predicate) {
      var matches = findMatches(this, self, predicate, applyPredicate, true);
      return matches.isEmpty() ? VmNull.withoutDefault() : matches.iterator().next();
    }
  }

  public abstract static class replaceChildWhere extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate, VmFunction replacer) {
      var targets = findMatches(this, self, predicate, applyPredicate, true);
      return SyntaxNodes.replaceTargets(self, targets, replacer);
    }
  }

  public abstract static class replaceChildrenWhere extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate, VmFunction replacer) {
      var targets = findMatches(this, self, predicate, applyPredicate, false);
      return SyntaxNodes.replaceTargets(self, targets, replacer);
    }
  }

  /**
   * Collect the descendants of {@code self} matching {@code predicate}, searching depth-first in
   * pre-order and stopping at the first match if {@code firstOnly}.
   *
   * <p>Matching eagerly keeps a search independent of the order in which a lazily built tree is
   * read: "the first match in pre-order" is not something a rewrite could decide as it goes.
   */
  private static Set<VmTyped> findMatches(
      Node owner,
      VmTyped self,
      VmFunction predicate,
      ApplyVmFunction1Node applyPredicate,
      boolean firstOnly) {

    var matches = SyntaxNodes.newNodeSet();
    var pending = new ArrayDeque<VmTyped>();
    pushChildren(pending, self);
    var visited = 0;
    while (!pending.isEmpty()) {
      var node = pending.pop();
      visited += 1;
      if (applyPredicate.executeBoolean(predicate, node)) {
        matches.add(node);
        if (firstOnly) break;
      }
      pushChildren(pending, node);
    }
    LoopNode.reportLoopCount(owner, visited);
    return matches;
  }

  private static void pushChildren(ArrayDeque<VmTyped> pending, VmTyped node) {
    var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN);
    for (var i = children.getLength() - 1; i >= 0; i--) {
      pending.push((VmTyped) children.get(i));
    }
  }

  public abstract static class transform extends ExternalMethod1Node {
    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction operator) {
      // the operator is applied to a child only when that child is read, it is never applied to
      // `self`, so an operator that recurses with `node.transform(operator)` terminates
      return SyntaxNodes.createView(
          new ViewData(self, new SyntaxNodes.OperatorRewriter(operator), null));
    }
  }
}
