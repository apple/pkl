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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
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

  static final char[] EMPTY_SOURCE = new char[0];
  static final FullSpan ZERO_SPAN = new FullSpan(0, 0, 0, 0, 0, 0);

  record SpanData(FullSpan span, @Nullable String sourceUri) {}

  private record SourceLocationData(int line, int column, @Nullable String sourceUri) {}

  private static final VmObjectFactory<SourceLocationData> sourceLocationFactory =
      new VmObjectFactory<SourceLocationData>(SyntaxModule::getSourceLocationClass)
          .addIntProperty("line", SourceLocationData::line)
          .addIntProperty("column", SourceLocationData::column)
          .addStringProperty(
              "displayUri", sl -> displayUri(sl.sourceUri(), position(sl.line(), sl.column())));

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
                      position(sd.span().lineBegin(), sd.span().colBegin())
                          + "-"
                          + position(sd.span().lineEnd(), sd.span().colEnd())));

  private static String position(int line, int column) {
    return line + ":" + column;
  }

  private static String displayUri(@Nullable String sourceUri, String position) {
    return sourceUri == null ? position : sourceUri + "#" + position;
  }

  /** Extra storage backing a Pkl {@code GenericNode} instance. */
  static final class GenericNodeData {
    final Node node;
    final char[] source;
    @Nullable VmTyped parentVm;
    VmList childrenVm;
    @Nullable VmTyped spanVm;

    GenericNodeData(Node node, char[] source, VmList childrenVm, @Nullable VmTyped spanVm) {
      this.node = node;
      this.source = source;
      this.childrenVm = childrenVm;
      this.spanVm = spanVm;
    }
  }

  static final VmObjectFactory<GenericNodeData> genericNodeFactory =
      new VmObjectFactory<GenericNodeData>(SyntaxModule::getGenericNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase(Locale.ROOT))
          .addListProperty("children", nd -> nd.childrenVm)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty(
              "text",
              nd ->
                  nd.node.children.isEmpty() || nd.node.type == NodeType.STRING_CHARS
                      ? nd.node.text(nd.source)
                      : VmNull.withoutDefault())
          .addProperty("span", nd -> VmNull.lift(nd.spanVm));

  /** Rebuild a node from {@code template} (its type, span, text) with new children. */
  static VmTyped rebuild(VmTyped template, Object[] newChildrenVm) {
    var nodeType =
        NodeType.valueOf(
            ((String) VmUtils.readMember(template, Identifier.TYPE)).toUpperCase(Locale.ROOT));
    var spanVm = optSpan(template);
    var span = readSpan(spanVm);

    var childJavaNodes = new ArrayList<Node>(newChildrenVm.length);
    for (var child : newChildrenVm) {
      // constructed (storage-less) children have no meaningful span
      childJavaNodes.add(convertVmToNode((VmTyped) child, span));
    }
    var javaNode =
        makeJavaNode(nodeType, span, childJavaNodes, VmUtils.readMember(template, Identifier.TEXT));

    var childrenVm = VmList.create(newChildrenVm);
    var result =
        genericNodeFactory.create(new GenericNodeData(javaNode, EMPTY_SOURCE, childrenVm, spanVm));

    // wire up the parent back-reference
    for (var child : newChildrenVm) {
      var childVm = (VmTyped) child;
      if (childVm.hasExtraStorage()) {
        ((GenericNodeData) childVm.getExtraStorage()).parentVm = result;
      }
    }
    return result;
  }

  /**
   * Convert a Pkl {@code GenericNode} to a generic {@link Node}, reusing the parse-time node when
   * present.
   *
   * <p>{@code fallbackSpan} is used for constructed nodes (and their descendants) that carry no
   * meaningful span of their own, so that a subtree spliced into reused siblings lines up with
   * them.
   */
  static Node convertVmToNode(VmTyped nodeVm, FullSpan fallbackSpan) {
    // a node still carrying its parse-time storage is verbatim from `parse`: reuse it wholesale
    if (nodeVm.hasExtraStorage()) {
      return ((GenericNodeData) nodeVm.getExtraStorage()).node;
    }

    var typeStr = (String) VmUtils.readMember(nodeVm, Identifier.TYPE);
    var nodeType = NodeType.valueOf(typeStr.toUpperCase(Locale.ROOT));

    var ownSpan = readSpan(optSpan(nodeVm));
    // a constructed node that did not set its own span inherits the insertion point's span
    var span = ownSpan.equals(ZERO_SPAN) ? fallbackSpan : ownSpan;

    var childrenVm = (VmList) VmUtils.readMember(nodeVm, Identifier.CHILDREN);
    var children = new ArrayList<Node>(childrenVm.getLength());
    for (var i = 0; i < childrenVm.getLength(); i++) {
      children.add(convertVmToNode((VmTyped) childrenVm.get(i), span));
    }

    return makeJavaNode(nodeType, span, children, VmUtils.readMember(nodeVm, Identifier.TEXT));
  }

  private static @Nullable VmTyped optSpan(VmTyped nodeVm) {
    return VmUtils.readMember(nodeVm, Identifier.SPAN) instanceof VmTyped spanVm ? spanVm : null;
  }

  private static FullSpan readSpan(@Nullable VmTyped spanVm) {
    if (spanVm == null) {
      return ZERO_SPAN;
    }
    var start = (VmTyped) VmUtils.readMember(spanVm, Identifier.START);
    var end = (VmTyped) VmUtils.readMember(spanVm, Identifier.END);
    return new FullSpan(
        0,
        0,
        readPosition(start, Identifier.LINE),
        readPosition(start, Identifier.COLUMN),
        readPosition(end, Identifier.LINE),
        readPosition(end, Identifier.COLUMN));
  }

  private static int readPosition(VmTyped sourceLocationVm, Identifier name) {
    return ((Long) VmUtils.readMember(sourceLocationVm, name)).intValue();
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
