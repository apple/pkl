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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmFunction;
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
                      position(sd.span().lineBegin(), sd.span().colBegin())
                          + "-"
                          + position(sd.span().lineEnd(), sd.span().colEnd()),
                      sd.span().lineBegin(),
                      sd.span().colBegin(),
                      sd.span().lineEnd(),
                      sd.span().colEnd()));

  private static String position(int line, int column) {
    return "L" + line + "C" + column;
  }

  private static String displayUri(@Nullable String sourceUri, int line, int column) {
    return displayUri(sourceUri, position(line, column), line, column, line, column);
  }

  @TruffleBoundary
  private static String displayUri(
      @Nullable String sourceUri,
      String fragment,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn) {
    if (sourceUri == null) {
      return fragment;
    }
    var transformed =
        VmUtils.getDisplayUri(
            sourceUri,
            startLine,
            startColumn,
            endLine,
            endColumn,
            VmContext.get(null).getFrameTransformer());
    return transformed.equals(sourceUri) ? sourceUri + "#" + fragment : transformed;
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

    VmList children() {
      var childNodes = node.children;
      if (childNodes.isEmpty()) {
        return VmList.EMPTY;
      }
      var children = new Object[childNodes.size()];
      for (var i = 0; i < children.length; i++) {
        children[i] = createNode(new GenericNodeData(childNodes.get(i), source, sourceUri, selfVm));
      }
      return VmList.create(children);
    }

    VmTyped span() {
      return spanFactory.create(new SpanData(node.span, sourceUri));
    }

    boolean isLeaf() {
      return node.children.isEmpty();
    }
  }

  static final VmObjectFactory<GenericNodeData> genericNodeFactory =
      new VmObjectFactory<GenericNodeData>(SyntaxModule::getGenericNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase(Locale.ROOT))
          .addListProperty("children", GenericNodeData::children)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty("text", GenericNodeData::text)
          .addProperty("span", GenericNodeData::span)
          .addProperty("isLeaf", GenericNodeData::isLeaf);

  /** Create the Pkl {@code GenericNode} backed by {@code data}. */
  static VmTyped createNode(GenericNodeData data) {
    var result = genericNodeFactory.create(data);
    data.selfVm = result;
    return result;
  }

  /**
   * What a node becomes in a tree being built.
   *
   * <p>{@code below} is the rewriter to apply to {@link #node}'s children, or {@code null} to leave
   * them as they are.
   */
  record Rewrite(VmTyped node, @Nullable Rewriter below) {}

  /** Decides what each node of a tree being built becomes. */
  interface Rewriter {
    /**
     * Rewrite {@code basis}, which sits at the position {@code input} presents.
     *
     * <p>{@code input} is what a Pkl callback is given: {@code basis} positioned in the tree being
     * built, so that the callback sees the parents the result will have.
     */
    Rewrite rewrite(VmTyped basis, VmTyped input);
  }

  /** Present the result of a Pkl rewrite callback at the position it was returned for. */
  private static Rewrite unwrapRewriteRoot(VmTyped result) {
    if (result.hasExtraStorage()
        && result.getExtraStorage() instanceof ViewData view
        && view.parentVm == null) {
      return new Rewrite(view.basis, view.rewriter);
    }
    return new Rewrite(result, null);
  }

  /** Applies the operator of {@code GenericNode.transform}. */
  static final class OperatorRewriter implements Rewriter {
    private final VmFunction operator;

    OperatorRewriter(VmFunction operator) {
      this.operator = operator;
    }

    @Override
    public Rewrite rewrite(VmTyped basis, VmTyped input) {
      // the operator recurses on its own, so whatever it returns is final for this position
      var rewrite = unwrapRewriteRoot((VmTyped) operator.apply(input));
      // an unchanged node is presented directly; wrapping its input view would only add a layer
      return rewrite.node() == input ? new Rewrite(basis, rewrite.below()) : rewrite;
    }
  }

  /** Replaces a fixed set of nodes, as {@code GenericNode.replaceChild*Where} do. */
  static final class TargetRewriter implements Rewriter {
    // an identity set: `==` on a Pkl GenericNode is a deep structural compare, so two distinct
    // nodes with the same content are equal to each other
    private final Set<VmTyped> targets;
    private final VmFunction replacer;

    TargetRewriter(Set<VmTyped> targets, VmFunction replacer) {
      this.targets = targets;
      this.replacer = replacer;
    }

    @Override
    public Rewrite rewrite(VmTyped basis, VmTyped input) {
      if (!targets.contains(basis)) {
        return new Rewrite(basis, this);
      }
      // a replacement is not searched for further matches, so a match nested in a match is not
      // visited
      return unwrapRewriteRoot((VmTyped) replacer.apply(input));
    }
  }

  /** A set that holds nodes by identity rather than by their content. */
  static Set<VmTyped> newNodeSet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  /**
   * Extra storage backing a Pkl {@code GenericNode} that presents another node at a position in a
   * tree being built by a rewrite.
   */
  static final class ViewData {
    final VmTyped basis;
    private final @Nullable Rewriter rewriter;
    private final @Nullable VmTyped parentVm;

    private @Nullable VmTyped selfVm;

    ViewData(VmTyped basis, @Nullable Rewriter rewriter, @Nullable VmTyped parentVm) {
      this.basis = basis;
      this.rewriter = rewriter;
      this.parentVm = parentVm;
    }

    /** Whether this view presents its basis unchanged, all the way down. */
    boolean isPassThrough() {
      return rewriter == null;
    }

    VmList children() {
      var basisChildren = (VmList) VmUtils.readMember(basis, Identifier.CHILDREN);
      if (basisChildren.isEmpty()) {
        return VmList.EMPTY;
      }
      var children = new Object[basisChildren.getLength()];
      for (var i = 0; i < children.length; i++) {
        children[i] = child((VmTyped) basisChildren.get(i));
      }
      return VmList.create(children);
    }

    private VmTyped child(VmTyped basisChild) {
      var rewriter = this.rewriter;
      // a pass-through view only repositions its basis' children
      var input = createView(new ViewData(basisChild, null, selfVm));
      if (rewriter == null) {
        return input;
      }
      var rewrite = rewriter.rewrite(basisChild, input);
      return createView(new ViewData(rewrite.node(), rewrite.below(), selfVm));
    }
  }

  private static final VmObjectFactory<ViewData> viewFactory =
      new VmObjectFactory<ViewData>(SyntaxModule::getGenericNodeClass)
          .addStringProperty("type", vd -> (String) VmUtils.readMember(vd.basis, Identifier.TYPE))
          .addListProperty("children", ViewData::children)
          .addProperty("parent", vd -> VmNull.lift(vd.parentVm))
          .addProperty("text", vd -> VmUtils.readMember(vd.basis, Identifier.TEXT))
          .addProperty("span", vd -> VmUtils.readMember(vd.basis, Identifier.SPAN))
          .addProperty("isLeaf", vd -> VmUtils.readMember(vd.basis, Identifier.IS_LEAF));

  /** Create the Pkl {@code GenericNode} backed by {@code data}. */
  static VmTyped createView(ViewData data) {
    var result = viewFactory.create(data);
    data.selfVm = result;
    return result;
  }

  /**
   * Build the tree rooted at {@code self} with {@code targets} replaced by {@code replacer}'s
   * results.
   *
   * <p>As with {@code transform}, the returned root has no parent and the tree {@code self} belongs
   * to is left untouched.
   */
  static VmTyped replaceTargets(VmTyped self, Set<VmTyped> targets, VmFunction replacer) {
    var rewriter = targets.isEmpty() ? null : new TargetRewriter(targets, replacer);
    return createView(new ViewData(self, rewriter, null));
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
  static Node convertVmToNode(VmTyped nodeVm, FullSpan fallbackSpan) {
    if (nodeVm.hasExtraStorage()) {
      var storage = nodeVm.getExtraStorage();
      // a node still carrying its parse-time storage is verbatim from `parse`: reuse it wholesale
      if (storage instanceof GenericNodeData data) {
        materializeText(data.node, data.source);
        return data.node;
      }
      // a pass-through view presents its basis unchanged
      if (storage instanceof ViewData view && view.isPassThrough()) {
        return convertVmToNode(view.basis, fallbackSpan);
      }
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
    return (VmTyped) VmNull.unwrap(VmUtils.readMember(nodeVm, Identifier.SPAN));
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

  /**
   * Materialize the text of the nodes in {@code node}'s subtree that the formatter reads directly.
   *
   * <p>{@link org.pkl.formatter.Formatter#format(Node)} has no access to the source, so a subtree
   * reused verbatim from a parse must carry its own text by the time it is handed over.
   */
  private static void materializeText(Node node, char[] source) {
    // `string_chars` is read by the formatter but is not always a leaf
    if (node.children.isEmpty() || node.type == NodeType.STRING_CHARS) {
      node.text(source);
    }
    for (var child : node.children) {
      materializeText(child, source);
    }
  }
}
