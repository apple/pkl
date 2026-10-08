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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.parser.syntax.generic.FullSpan;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

public final class SyntaxNodes {
  private SyntaxNodes() {}

  static final FullSpan ZERO_SPAN = new FullSpan(0, 0, 0, 0, 0, 0);

  record SpanData(FullSpan span, @Nullable String sourceUri) {}

  private record SourceLocationData(int line, int column, @Nullable String sourceUri) {}

  private static final VmObjectFactory<SourceLocationData> sourceLocationFactory =
      new VmObjectFactory<SourceLocationData>(SyntaxModule::getSourceLocationClass)
          .addIntProperty("line", SourceLocationData::line)
          .addIntProperty("column", SourceLocationData::column)
          .addStringProperty(
              "displayUri", sl -> displayUri(sl.sourceUri(), sl.line(), sl.column()));

  static final VmObjectFactory<SpanData> spanFactory =
      new VmObjectFactory<SpanData>(SyntaxModule::getSpanClass)
          .addTypedProperty(
              "start",
              sd ->
                  sourceLocationFactory.create(
                      new SourceLocationData(
                          sd.span().lineBegin(), sd.span().colBegin(), sd.sourceUri())))
          .addTypedProperty(
              "end",
              sd ->
                  sourceLocationFactory.create(
                      new SourceLocationData(
                          sd.span().lineEnd(), sd.span().colEnd(), sd.sourceUri())))
          .addStringProperty(
              "displayUri",
              sd ->
                  displayUri(
                      sd.sourceUri(),
                      sd.span().lineBegin(),
                      sd.span().colBegin(),
                      sd.span().lineEnd(),
                      sd.span().colEnd()));

  private static String displayUri(@Nullable String sourceUri, int line, int column) {
    return displayUri(sourceUri, line, column, line, column);
  }

  @TruffleBoundary
  private static String displayUri(
      @Nullable String sourceUri, int startLine, int startColumn, int endLine, int endColumn) {
    if (sourceUri == null) {
      return "";
    }
    return VmUtils.getDisplayUri(
        sourceUri,
        startLine,
        startColumn,
        endLine,
        endColumn,
        VmContext.get(null).getFrameTransformer());
  }

  /** Extra storage backing a Pkl {@code GenericNode} parsed from source. */
  static final class GenericNodeData {
    final Node node;
    // the source {@link #node} was parsed from
    final char[] source;
    // the URI of {@link #source}, or {@code null} if unknown
    private final @Nullable String sourceUri;
    private final @Nullable VmTyped parentVm;

    // the object this storage backs, so that lazily created children can be parented at it
    private @Nullable VmTyped selfVm;

    GenericNodeData(
        Node node, char[] source, @Nullable String sourceUri, @Nullable VmTyped parentVm) {
      this.node = node;
      this.source = source;
      this.sourceUri = sourceUri;
      this.parentVm = parentVm;
    }

    String text() {
      return node.text(source);
    }

    @ExplodeLoop
    VmList children() {
      var childNodes = node.children;
      if (childNodes.isEmpty()) {
        return VmList.EMPTY;
      }
      var builder = VmList.EMPTY.builder();
      for (var childNode : childNodes) {
        builder.add(createNode(new GenericNodeData(childNode, source, sourceUri, selfVm)));
      }
      return builder.build();
    }

    VmTyped span() {
      return spanFactory.create(new SpanData(node.span, sourceUri));
    }
  }

  static final VmObjectFactory<GenericNodeData> genericNodeFactory =
      new VmObjectFactory<GenericNodeData>(SyntaxModule::getGenericNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase(Locale.ROOT))
          .addListProperty("children", GenericNodeData::children)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty("text", GenericNodeData::text)
          .addProperty("span", GenericNodeData::span);

  /** Create the Pkl {@code GenericNode} backed by {@code data}. */
  static VmTyped createNode(GenericNodeData data) {
    var result = genericNodeFactory.create(data);
    data.selfVm = result;
    return result;
  }

  /**
   * A node to rebuild with new children.
   *
   * <p>Each of {@code children} is either a {@code Rebuild} or a node to keep as it is.
   */
  record Rebuild(VmTyped basis, Object[] children) {}

  /** Extra storage backing a Pkl {@code GenericNode} rebuilt from {@link #basis}. */
  private static final class BuiltNodeData {
    private final VmTyped basis;
    private final @Nullable VmTyped parentVm;

    // set right after the node is created, so that the children can be parented at it
    private VmList children = VmList.EMPTY;

    private BuiltNodeData(VmTyped basis, @Nullable VmTyped parentVm) {
      this.basis = basis;
      this.parentVm = parentVm;
    }
  }

  private static final VmObjectFactory<BuiltNodeData> builtNodeFactory =
      new VmObjectFactory<BuiltNodeData>(SyntaxModule::getGenericNodeClass)
          .addStringProperty("type", bd -> (String) VmUtils.readMember(bd.basis, Identifier.TYPE))
          .addListProperty("children", bd -> bd.children)
          .addProperty("parent", bd -> VmNull.lift(bd.parentVm))
          .addProperty("text", bd -> VmUtils.readMember(bd.basis, Identifier.TEXT))
          .addProperty("span", bd -> VmUtils.readMember(bd.basis, Identifier.SPAN));

  /**
   * Build the tree rooted at {@code root}, with its children replaced by {@code children} if not
   * {@code null}.
   *
   * <p>The returned root has no parent, and every other node is parented in the returned tree. The
   * tree {@code root} is left untouched.
   */
  @TruffleBoundary
  static VmTyped rebuild(VmTyped root, Object @Nullable [] children) {
    return children == null ? reparent(root, null) : build(root, children, null);
  }

  private static VmTyped build(VmTyped basis, Object[] children, @Nullable VmTyped parent) {
    var data = new BuiltNodeData(basis, parent);
    var result = builtNodeFactory.create(data);
    var builtChildren = new Object[children.length];
    for (var i = 0; i < children.length; i++) {
      builtChildren[i] =
          children[i] instanceof Rebuild rebuild
              ? build(rebuild.basis(), rebuild.children(), result)
              : reparent((VmTyped) children[i], result);
    }
    // A node and its children point at each other, so neither can be complete before the other is
    // created. This node is therefore created first, without children, so its children can be built
    // with it as their parent, and only then are they set here.
    // This is safe because factory properties are evaluated lazily, on first read, and no Pkl code
    // can see this node before `build` returns, so `children` can't be read before it's set.
    data.children = VmList.create(builtChildren);
    return result;
  }

  /** Copy {@code node} and its subtree to sit below {@code parent}. */
  private static VmTyped reparent(VmTyped node, @Nullable VmTyped parent) {
    // a parsed node's subtree is created lazily from its parse-time node, so only the node is
    // copied
    if (node.hasExtraStorage() && node.getExtraStorage() instanceof GenericNodeData data) {
      return createNode(new GenericNodeData(data.node, data.source, data.sourceUri, parent));
    }
    var children = (VmList) VmUtils.readMember(node, Identifier.CHILDREN);
    return build(node, children.toArray(), parent);
  }

  /**
   * Convert a Pkl {@code GenericNode} to a generic {@link Node}, reusing the parse-time node when
   * present.
   *
   * <p>{@code fallbackSpan} is used for constructed nodes (and their descendants) that carry no
   * meaningful span of their own, so that a subtree spliced into reused siblings lines up with
   * them.
   */
  @TruffleBoundary
  static Node convertVmToNode(VmTyped nodeVm, FullSpan fallbackSpan, IndirectCallNode callNode) {
    if (nodeVm.hasExtraStorage()) {
      var storage = nodeVm.getExtraStorage();
      // a node still carrying its parse-time storage is verbatim from `parse`: reuse it wholesale
      if (storage instanceof GenericNodeData data) {
        materializeText(data.node, data.source);
        return data.node;
      }
    }

    var typeStr = (String) VmUtils.readMember(nodeVm, Identifier.TYPE, callNode);
    var nodeType = NodeType.valueOf(typeStr.toUpperCase(Locale.ROOT));

    var ownSpan = readSpan(optSpan(nodeVm, callNode), callNode);
    // a constructed node that did not set its own span inherits the insertion point's span
    var span = ownSpan.equals(ZERO_SPAN) ? fallbackSpan : ownSpan;

    var childrenVm = (VmList) VmUtils.readMember(nodeVm, Identifier.CHILDREN, callNode);
    var children = new ArrayList<Node>(childrenVm.getLength());
    for (var i = 0; i < childrenVm.getLength(); i++) {
      children.add(convertVmToNode((VmTyped) childrenVm.get(i), span, callNode));
    }

    return makeJavaNode(
        nodeType, span, children, VmUtils.readMember(nodeVm, Identifier.TEXT, callNode));
  }

  private static @Nullable VmTyped optSpan(VmTyped nodeVm, IndirectCallNode callNode) {
    return (VmTyped) VmNull.unwrap(VmUtils.readMember(nodeVm, Identifier.SPAN, callNode));
  }

  private static FullSpan readSpan(@Nullable VmTyped spanVm, IndirectCallNode callNode) {
    if (spanVm == null) {
      return ZERO_SPAN;
    }
    var start = (VmTyped) VmUtils.readMember(spanVm, Identifier.START, callNode);
    var end = (VmTyped) VmUtils.readMember(spanVm, Identifier.END, callNode);
    return new FullSpan(
        0,
        0,
        readPosition(start, Identifier.LINE, callNode),
        readPosition(start, Identifier.COLUMN, callNode),
        readPosition(end, Identifier.LINE, callNode),
        readPosition(end, Identifier.COLUMN, callNode));
  }

  private static int readPosition(
      VmTyped sourceLocationVm, Identifier name, IndirectCallNode callNode) {
    return ((Long) VmUtils.readMember(sourceLocationVm, name, callNode)).intValue();
  }

  private static Node makeJavaNode(
      NodeType nodeType, FullSpan span, List<Node> children, Object textObj) {
    var node = children.isEmpty() ? new Node(nodeType, span) : new Node(nodeType, span, children);
    if (textObj instanceof String text) {
      node.setText(text);
    }
    return node;
  }

  /**
   * Materialize the text of the nodes in {@code node}'s subtree that the formatter reads directly.
   *
   * <p>{@link org.pkl.formatter.Formatter#format(Node)} has no access to the source, so a subtree
   * reused verbatim from a parse must carry its own text by the time it is handed over.
   */
  private static void materializeText(Node node, char[] source) {
    // `string_constant` is read by the formatter but is not a leaf
    if (node.children.isEmpty() || node.type == NodeType.STRING_CONSTANT) {
      node.text(source);
    }
    for (var child : node.children) {
      materializeText(child, source);
    }
  }
}
