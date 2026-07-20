/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ast.lambda.ApplyVmFunction1Node;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.formatter.Formatter;
import org.pkl.formatter.GrammarVersion;
import org.pkl.parser.syntax.generic.FullSpan;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

public final class SyntaxNodes {
  private SyntaxNodes() {}

  private static final char[] EMPTY_SOURCE = new char[0];
  private static final FullSpan ZERO_SPAN = new FullSpan(0, 0, 0, 0, 0, 0);

  /** Extra storage backing a Pkl {@code Node} instance. */
  static final class NodeData {
    final Node node;
    final char[] source;
    @Nullable VmTyped parentVm;
    VmList childrenVm;
    VmTyped spanVm;

    NodeData(Node node, char[] source, VmList childrenVm, VmTyped spanVm) {
      this.node = node;
      this.source = source;
      this.childrenVm = childrenVm;
      this.spanVm = spanVm;
    }
  }

  private static final VmObjectFactory<NodeData> nodeFactory =
      new VmObjectFactory<NodeData>(SyntaxModule::getNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase(Locale.ROOT))
          .addListProperty("children", nd -> nd.childrenVm)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty(
              "text",
              nd ->
                  nd.node.children.isEmpty() || nd.node.type == NodeType.STRING_CHARS
                      ? nd.node.text(nd.source)
                      : VmNull.withoutDefault())
          .addTypedProperty("span", nd -> nd.spanVm);

  public abstract static class formatToString extends ExternalMethod2Node {
    @Specialization
    @TruffleBoundary
    protected String eval(VmTyped self, VmTyped nodeVm, String grammarVersion) {
      var node = convertVmToNode(nodeVm, ZERO_SPAN);
      return new Formatter(GrammarVersion.valueOf(grammarVersion)).format(node);
    }
  }

  public abstract static class walk extends ExternalMethod2Node {
    @Child private ApplyVmFunction1Node applyVisit = ApplyVmFunction1Node.create();

    @Specialization
    @TruffleBoundary
    protected VmTyped eval(VmTyped self, VmTyped node, VmFunction visit) {
      var result = walkNode(node, visit);
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
      return changed ? rebuild(node, newChildren) : node;
    }
  }

  /** Rebuild a node from {@code template} (its type, span, text) with new children. */
  private static VmTyped rebuild(VmTyped template, Object[] newChildrenVm) {
    var nodeType =
        NodeType.valueOf(
            ((String) VmUtils.readMember(template, Identifier.TYPE)).toUpperCase(Locale.ROOT));
    var spanVm = (VmTyped) VmUtils.readMember(template, Identifier.SPAN);
    var span = readSpan(spanVm);

    var childJavaNodes = new ArrayList<Node>(newChildrenVm.length);
    for (var child : newChildrenVm) {
      // constructed (storage-less) children have no meaningful span; anchor them to this
      // node's span so the formatter's line-break heuristics stay consistent with reused
      // siblings (which keep their original spans).
      childJavaNodes.add(convertVmToNode((VmTyped) child, span));
    }
    var javaNode =
        makeJavaNode(nodeType, span, childJavaNodes, VmUtils.readMember(template, Identifier.TEXT));

    var childrenVm = VmList.create(newChildrenVm);
    var result = nodeFactory.create(new NodeData(javaNode, EMPTY_SOURCE, childrenVm, spanVm));

    // wire up the parent back-reference
    for (var child : newChildrenVm) {
      var childVm = (VmTyped) child;
      if (childVm.hasExtraStorage()) {
        ((NodeData) childVm.getExtraStorage()).parentVm = result;
      }
    }
    return result;
  }

  /**
   * Convert a Pkl node to a generic {@link Node}, reusing the parse-time node when present.
   *
   * <p>{@code fallbackSpan} is used for constructed nodes (and their descendants) that carry no
   * meaningful span of their own, so that a subtree spliced into reused siblings lines up with
   * them.
   */
  private static Node convertVmToNode(VmTyped nodeVm, FullSpan fallbackSpan) {
    // a node still carrying its parse-time storage is verbatim from `parse`: reuse it wholesale
    if (nodeVm.hasExtraStorage()) {
      return ((NodeData) nodeVm.getExtraStorage()).node;
    }

    var typeStr = (String) VmUtils.readMember(nodeVm, Identifier.TYPE);
    var nodeType = NodeType.valueOf(typeStr.toUpperCase(Locale.ROOT));

    var ownSpan = readSpan((VmTyped) VmUtils.readMember(nodeVm, Identifier.SPAN));
    // a constructed node that did not set its own span inherits the insertion point's span
    var span = ownSpan.equals(ZERO_SPAN) ? fallbackSpan : ownSpan;

    var childrenVm = (VmList) VmUtils.readMember(nodeVm, Identifier.CHILDREN);
    var children = new ArrayList<Node>(childrenVm.getLength());
    for (var i = 0; i < childrenVm.getLength(); i++) {
      children.add(convertVmToNode((VmTyped) childrenVm.get(i), span));
    }

    return makeJavaNode(nodeType, span, children, VmUtils.readMember(nodeVm, Identifier.TEXT));
  }

  private static FullSpan readSpan(VmTyped spanVm) {
    var lineStart = ((Long) VmUtils.readMember(spanVm, Identifier.LINE_START)).intValue();
    var colStart = ((Long) VmUtils.readMember(spanVm, Identifier.COL_START)).intValue();
    var lineEnd = ((Long) VmUtils.readMember(spanVm, Identifier.LINE_END)).intValue();
    var colEnd = ((Long) VmUtils.readMember(spanVm, Identifier.COL_END)).intValue();
    return new FullSpan(0, 0, lineStart, colStart, lineEnd, colEnd);
  }

  private static Node makeJavaNode(
      NodeType nodeType, FullSpan span, List<Node> children, Object textObj) {
    var node = children.isEmpty() ? new Node(nodeType, span) : new Node(nodeType, span, children);
    if (textObj instanceof String text) {
      node.setText(text);
    }
    return node;
  }
}
