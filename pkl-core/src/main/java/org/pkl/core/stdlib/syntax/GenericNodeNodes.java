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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
import org.pkl.core.stdlib.syntax.SyntaxNodes.Rebuild;

/** Backs the methods of {@code pkl.syntax#GenericNode}. */
public final class GenericNodeNodes {
  private GenericNodeNodes() {}

  public abstract static class fold extends ExternalMethod2Node {
    @Child private ApplyVmFunction2Node applyAccumulate = ApplyVmFunction2NodeGen.create();
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmTyped self, Object initial, VmFunction operator) {
      var pending = new ArrayDeque<VmTyped>();
      pending.push(self);
      var result = initial;
      var visited = 0;
      while (!pending.isEmpty()) {
        var node = pending.pop();
        result = applyAccumulate.execute(operator, result, node);
        var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN, callNode);
        for (var i = children.getLength() - 1; i >= 0; i--) {
          pending.push((VmTyped) children.get(i));
        }
        visited += 1;
      }
      LoopNode.reportLoopCount(this, visited);
      return result;
    }
  }

  public abstract static class findChild extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate) {
      var result = findFirstChild(self, new PredicateMatcher(predicate, applyPredicate), callNode);
      if (result == null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("cannotFindMatchingChildNode").build();
      }
      return result;
    }
  }

  public abstract static class findChildOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmTyped self, VmFunction predicate) {
      return VmNull.lift(
          findFirstChild(self, new PredicateMatcher(predicate, applyPredicate), callNode));
    }
  }

  public abstract static class findChildren extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmList eval(VmTyped self, VmFunction predicate) {
      return VmList.create(
          findMatchingChildren(
              this, self, new PredicateMatcher(predicate, applyPredicate), callNode));
    }
  }

  public abstract static class findChildOfType extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self, String type) {
      var result = findFirstChild(self, new TypeMatcher(type), callNode);
      if (result == null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("cannotFindChildNodeOfType", type).build();
      }
      return result;
    }
  }

  public abstract static class findChildOfTypeOrNull extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected Object eval(VmTyped self, String type) {
      return VmNull.lift(findFirstChild(self, new TypeMatcher(type), callNode));
    }
  }

  public abstract static class findChildrenOfType extends ExternalMethod1Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmList eval(VmTyped self, String type) {
      return VmList.create(findMatchingChildren(this, self, new TypeMatcher(type), callNode));
    }
  }

  public abstract static class replaceChild extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate, VmFunction replacer) {
      var matcher = new PredicateMatcher(predicate, applyPredicate);
      return replaceMatchingChildren(self, matcher, true, replacer, callNode);
    }
  }

  public abstract static class replaceChildren extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate, VmFunction replacer) {
      var matcher = new PredicateMatcher(predicate, applyPredicate);
      return replaceMatchingChildren(self, matcher, false, replacer, callNode);
    }
  }

  public abstract static class replaceChildOfType extends ExternalMethod2Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self, String type, VmFunction replacer) {
      return replaceMatchingChildren(self, new TypeMatcher(type), true, replacer, callNode);
    }
  }

  public abstract static class replaceChildrenOfType extends ExternalMethod2Node {
    @Child private IndirectCallNode callNode = IndirectCallNode.create();

    @Specialization
    protected VmTyped eval(VmTyped self, String type, VmFunction replacer) {
      return replaceMatchingChildren(self, new TypeMatcher(type), false, replacer, callNode);
    }
  }

  public abstract static class findParent extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmTyped eval(VmTyped self, VmFunction predicate) {
      var result = findFirstParent(this, self, new PredicateMatcher(predicate, applyPredicate));
      if (result == null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("cannotFindMatchingParentNode").build();
      }
      return result;
    }
  }

  public abstract static class findParentOrNull extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected Object eval(VmTyped self, VmFunction predicate) {
      return VmNull.lift(
          findFirstParent(this, self, new PredicateMatcher(predicate, applyPredicate)));
    }
  }

  public abstract static class findParentOfType extends ExternalMethod1Node {
    @Specialization
    protected VmTyped eval(VmTyped self, String type) {
      var result = findFirstParent(this, self, new TypeMatcher(type));
      if (result == null) {
        CompilerDirectives.transferToInterpreter();
        throw exceptionBuilder().evalError("cannotFindParentNodeOfType", type).build();
      }
      return result;
    }
  }

  public abstract static class findParentOfTypeOrNull extends ExternalMethod1Node {
    @Specialization
    protected Object eval(VmTyped self, String type) {
      return VmNull.lift(findFirstParent(this, self, new TypeMatcher(type)));
    }
  }

  public abstract static class findParents extends ExternalMethod1Node {
    @Child private ApplyVmFunction1Node applyPredicate = ApplyVmFunction1Node.create();

    @Specialization
    protected VmList eval(VmTyped self, VmFunction predicate) {
      var matcher = new PredicateMatcher(predicate, applyPredicate);
      var matches = VmList.EMPTY.builder();
      var visited = 0;
      for (var node = parentOf(self); node != null; node = parentOf(node)) {
        visited += 1;
        if (matcher.matches(node)) {
          matches.add(node);
        }
      }
      LoopNode.reportLoopCount(this, visited);
      return matches.build();
    }
  }

  public abstract static class hasParent extends ExternalMethod1Node {
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

  private static @Nullable VmTyped findFirstChild(
      VmTyped self, NodeMatcher matcher, IndirectCallNode callNode) {
    var pending = new ArrayDeque<VmTyped>();
    pushChildren(pending, self, callNode);
    while (!pending.isEmpty()) {
      var node = pending.pop();
      if (matcher.matches(node)) {
        return node;
      }
      pushChildren(pending, node, callNode);
    }
    return null;
  }

  /**
   * Collect the descendants of {@code self} matching {@code matcher}, searching depth-first in
   * pre-order.
   *
   * <p>A match is searched for further matches, so a match may contain another.
   */
  private static List<VmTyped> findMatchingChildren(
      Node owner, VmTyped self, NodeMatcher matcher, IndirectCallNode callNode) {

    List<VmTyped> matches = new ArrayList<>();
    var pending = new ArrayDeque<VmTyped>();
    pushChildren(pending, self, callNode);
    var visited = 0;
    while (!pending.isEmpty()) {
      var node = pending.pop();
      visited += 1;
      if (matcher.matches(node)) {
        matches.add(node);
      }
      pushChildren(pending, node, callNode);
    }
    LoopNode.reportLoopCount(owner, visited);
    return matches;
  }

  private static void pushChildren(
      ArrayDeque<VmTyped> pending, VmTyped node, IndirectCallNode callNode) {
    var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN, callNode);
    for (var i = children.getLength() - 1; i >= 0; i--) {
      pending.push((VmTyped) children.get(i));
    }
  }

  /**
   * Returns {@code self} rebuilt with the descendants matching {@code matcher} replaced by {@code
   * replacer}'s results, searching depth-first in pre-order and stopping at the first match if
   * {@code firstOnly}.
   *
   * <p>A match is not searched for further matches.
   */
  private static VmTyped replaceMatchingChildren(
      VmTyped self,
      NodeMatcher matcher,
      boolean firstOnly,
      VmFunction replacer,
      IndirectCallNode callNode) {

    var search = new ReplaceSearch(matcher, firstOnly, replacer, callNode);
    return SyntaxNodes.rebuild(self, search.replaceBelow(self));
  }

  /** Finds and replaces the matches of a {@code GenericNode.replaceChild*} call. */
  private static final class ReplaceSearch {
    private final NodeMatcher matcher;
    private final boolean firstOnly;
    private final VmFunction replacer;
    private final IndirectCallNode callNode;

    private boolean done;

    private ReplaceSearch(
        NodeMatcher matcher, boolean firstOnly, VmFunction replacer, IndirectCallNode callNode) {
      this.matcher = matcher;
      this.firstOnly = firstOnly;
      this.replacer = replacer;
      this.callNode = callNode;
    }

    /**
     * Returns the children of {@code node} with the matches below it replaced, or {@code null} if
     * nothing below it matches.
     *
     * <p>Each element is either a {@link Rebuild} or a node to keep as it is.
     */
    @TruffleBoundary
    private Object @Nullable [] replaceBelow(VmTyped node) {
      var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN, callNode);
      Object[] result = null;
      for (var i = 0; i < children.getLength() && !done; i++) {
        var child = (VmTyped) children.get(i);
        Object replacement;
        if (matcher.matches(child)) {
          replacement = replacer.apply(child);
          done = firstOnly;
        } else {
          var grandchildren = replaceBelow(child);
          replacement = grandchildren == null ? null : new Rebuild(child, grandchildren);
        }
        if (replacement != null) {
          if (result == null) {
            result = children.toArray();
          }
          result[i] = replacement;
        }
      }
      return result;
    }
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

  private static @Nullable VmTyped parentOf(VmTyped node) {
    return (VmTyped) VmNull.unwrap(VmUtils.readMember(node, Identifier.PARENT));
  }
}
