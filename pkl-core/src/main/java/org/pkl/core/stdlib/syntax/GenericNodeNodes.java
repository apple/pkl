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
import org.jspecify.annotations.Nullable;
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
import org.pkl.core.stdlib.syntax.SyntaxNodes.NodeSet;
import org.pkl.core.stdlib.syntax.SyntaxNodes.ViewData;

/** Backs the methods of {@code pkl.syntax#GenericNode}. */
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
      return VmNull.lift(
          findFirstChild(this, self, new PredicateMatcher(predicate, applyPredicate)));
    }
  }

  public abstract static class findChildrenWhere extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmList eval(VmTyped self, VmFunction predicate) {
      return findChildren(this, self, new PredicateMatcher(predicate, applyPredicate), false);
    }
  }

  public abstract static class findChildOfType extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmTyped self, String type) {
      return VmNull.lift(findFirstChild(this, self, new TypeMatcher(type)));
    }
  }

  public abstract static class findChildrenOfType extends ExternalMethod1Node {
    @Specialization
    protected VmList eval(VmTyped self, String type) {
      return findChildren(this, self, new TypeMatcher(type), false);
    }
  }

  public abstract static class replaceChildWhere extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate, VmFunction replacer) {
      var matcher = new PredicateMatcher(predicate, applyPredicate);
      return SyntaxNodes.replaceTargets(self, findTargets(this, self, matcher, true), replacer);
    }
  }

  public abstract static class replaceChildrenWhere extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate, VmFunction replacer) {
      var matcher = new PredicateMatcher(predicate, applyPredicate);
      return SyntaxNodes.replaceTargets(self, findTargets(this, self, matcher, false), replacer);
    }
  }

  public abstract static class replaceChildOfType extends ExternalMethod2Node {
    @Specialization
    protected VmTyped eval(VmTyped self, String type, VmFunction replacer) {
      var targets = findTargets(this, self, new TypeMatcher(type), true);
      return SyntaxNodes.replaceTargets(self, targets, replacer);
    }
  }

  public abstract static class replaceChildrenOfType extends ExternalMethod2Node {
    @Specialization
    protected VmTyped eval(VmTyped self, String type, VmFunction replacer) {
      var targets = findTargets(this, self, new TypeMatcher(type), false);
      return SyntaxNodes.replaceTargets(self, targets, replacer);
    }
  }

  public abstract static class findParentWhere extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmTyped self, VmFunction predicate) {
      return VmNull.lift(
          findFirstParent(this, self, new PredicateMatcher(predicate, applyPredicate)));
    }
  }

  public abstract static class findParentsWhere extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmList eval(VmTyped self, VmFunction predicate) {
      var matcher = new PredicateMatcher(predicate, applyPredicate);
      return findParents(this, self, matcher);
    }
  }

  public abstract static class hasParentWhere extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected boolean eval(VmTyped self, VmFunction predicate) {
      return findFirstParent(this, self, new PredicateMatcher(predicate, applyPredicate)) != null;
    }
  }

  public abstract static class hasParentOfType extends ExternalMethod1Node {
    @Specialization
    protected boolean eval(VmTyped self, String type) {
      return findFirstParent(this, self, new TypeMatcher(type)) != null;
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

  /** Decides whether a node is a match for a search. */
  private interface NodeMatcher {
    boolean matches(VmTyped node);
  }

  /** Matches the nodes a Pkl predicate accepts. */
  private record PredicateMatcher(VmFunction predicate, ApplyVmFunction1Node applyPredicate)
      implements NodeMatcher {

    @Override
    public boolean matches(VmTyped node) {
      return applyPredicate.executeBoolean(predicate, node);
    }
  }

  /** Matches the nodes of a given type. */
  private record TypeMatcher(String type) implements NodeMatcher {
    @Override
    public boolean matches(VmTyped node) {
      return type.equals(VmUtils.readMember(node, Identifier.TYPE));
    }
  }

  private static @Nullable VmTyped findFirstChild(Node owner, VmTyped self, NodeMatcher matcher) {
    var matches = findChildren(owner, self, matcher, true);
    return matches.isEmpty() ? null : (VmTyped) matches.getFirst();
  }

  /**
   * Collect the descendants of {@code self} matching {@code matcher}, searching depth-first in
   * pre-order and stopping at the first match if {@code firstOnly}.
   *
   * <p>A match is searched for further matches, so a match may contain another.
   */
  private static VmList findChildren(
      Node owner, VmTyped self, NodeMatcher matcher, boolean firstOnly) {

    var matches = VmList.EMPTY.builder();
    var pending = new ArrayDeque<VmTyped>();
    pushChildren(pending, self);
    var visited = 0;
    while (!pending.isEmpty()) {
      var node = pending.pop();
      visited += 1;
      if (matcher.matches(node)) {
        matches.add(node);
        if (firstOnly) break;
      }
      pushChildren(pending, node);
    }
    LoopNode.reportLoopCount(owner, visited);
    return matches.build();
  }

  private static void pushChildren(ArrayDeque<VmTyped> pending, VmTyped node) {
    var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN);
    for (var i = children.getLength() - 1; i >= 0; i--) {
      pending.push((VmTyped) children.get(i));
    }
  }

  private static NodeSet findTargets(
      Node owner, VmTyped self, NodeMatcher matcher, boolean firstOnly) {

    return NodeSet.of(findChildren(owner, self, matcher, firstOnly));
  }

  private static @Nullable VmTyped findFirstParent(Node owner, VmTyped self, NodeMatcher matcher) {
    VmTyped result = null;
    var visited = 0;
    for (var node = parentOf(self); node != null; node = parentOf(node)) {
      visited += 1;
      if (matcher.matches(node)) {
        result = node;
        break;
      }
    }
    LoopNode.reportLoopCount(owner, visited);
    return result;
  }

  private static VmList findParents(Node owner, VmTyped self, NodeMatcher matcher) {
    var matches = VmList.EMPTY.builder();
    var visited = 0;
    for (var node = parentOf(self); node != null; node = parentOf(node)) {
      visited += 1;
      if (matcher.matches(node)) {
        matches.add(node);
      }
    }
    LoopNode.reportLoopCount(owner, visited);
    return matches.build();
  }

  private static @Nullable VmTyped parentOf(VmTyped node) {
    return (VmTyped) VmNull.unwrap(VmUtils.readMember(node, Identifier.PARENT));
  }
}
