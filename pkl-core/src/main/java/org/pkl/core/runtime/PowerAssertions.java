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
package org.pkl.core.runtime;

import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.pkl.core.ast.ConstantValueNode;
import org.pkl.core.ast.expression.member.InferParentWithinMethodNode;
import org.pkl.core.ast.expression.member.InferParentWithinObjectMethodNode;
import org.pkl.core.ast.expression.member.InferParentWithinPropertyNode;
import org.pkl.core.ast.expression.member.InvokeMethodDirectNode;
import org.pkl.core.ast.type.GetParentForTypeNode;
import org.pkl.core.util.AnsiStringBuilder;
import org.pkl.core.util.AnsiStringBuilder.AnsiCode;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.SyntaxHighlighter;
import org.pkl.parser.Lexer;
import org.pkl.parser.Parser;
import org.pkl.parser.Token;
import org.pkl.parser.syntax.Expr;
import org.pkl.parser.syntax.Expr.BinaryOperatorExpr;
import org.pkl.parser.syntax.Expr.BoolLiteralExpr;
import org.pkl.parser.syntax.Expr.FloatLiteralExpr;
import org.pkl.parser.syntax.Expr.FunctionLiteralExpr;
import org.pkl.parser.syntax.Expr.IntLiteralExpr;
import org.pkl.parser.syntax.Expr.LetExpr;
import org.pkl.parser.syntax.Expr.NullLiteralExpr;
import org.pkl.parser.syntax.Expr.QualifiedAccessExpr;
import org.pkl.parser.syntax.Expr.StringLiteralExpr;
import org.pkl.parser.syntax.Expr.SubscriptExpr;
import org.pkl.parser.syntax.Expr.SuperAccessExpr;
import org.pkl.parser.syntax.Expr.SuperSubscriptExpr;
import org.pkl.parser.syntax.Expr.TypeCheckExpr;
import org.pkl.parser.syntax.Expr.UnqualifiedAccessExpr;
import org.pkl.parser.syntax.ObjectMember.ForGenerator;
import org.pkl.parser.syntax.ObjectMember.MemberPredicate;
import org.pkl.parser.syntax.StringPart.StringChars;
import org.pkl.parser.syntax.StringPart.StringInterpolation;

public class PowerAssertions {
  private PowerAssertions() {}

  /**
   * Power assertions can be enabled/disabled via CLI flags (--power-assertions /
   * --no-power-assertions) or via EvaluatorBuilder.setPowerAssertions().
   */
  public static boolean isEnabled() {
    return VmContext.get(null).getPowerAssertions();
  }

  private static final VmValueRenderer vmValueRenderer = VmValueRenderer.singleLine(100);
  private static final Parser parser = new Parser();

  public static void render(
      AnsiStringBuilder out,
      String indent,
      SourceSection sourceSection,
      Map<Node, List<Object>> trackedValues,
      @Nullable Consumer<AnsiStringBuilder> firstFrameSuffix) {
    out.appendSandboxed(
        () -> {
          var lines = lines(sourceSection);
          var layerEntries = getLayerEntries(trackedValues, sourceSection);
          var indentation =
              lines.size() == 1
                  ? 0
                  : Collections.min(
                      lines.stream()
                          .skip(1)
                          .map((it) -> leadingWhitespace(it.getCharacters()))
                          .toList());
          var sourceLines = lines(sourceSection);
          var renderedMarkers = false;
          for (var i = 0; i < sourceLines.size(); i++) {
            if (renderedMarkers) {
              out.append("\n\n");
            }
            var line = sourceLines.get(i);
            var offset = i == 0 ? line.getStartColumn() - 1 : indentation;
            renderedMarkers =
                renderLine(
                    out, indent, line, layerEntries, offset, i == 0 ? firstFrameSuffix : null);
          }
        });
  }

  private static boolean isInForGeneratorOrLambdaOrPredicate(
      org.pkl.parser.syntax.Node myNode, Expr rootExpr) {
    var parent = myNode.parent();
    while (parent != null) {
      if (parent instanceof FunctionLiteralExpr) {
        // okay to show power assert if this lambda is the root constraint expr (this was a lambda
        // passed in as a a constraint)
        return !parent.equals(rootExpr);
      }
      if (parent instanceof MemberPredicate) {
        return true;
      }
      // okay if it's in expr section of for generator
      if (parent instanceof ForGenerator forGenerator) {
        return !forGenerator.getExpr().span().contains(myNode.span());
      }
      parent = parent.parent();
    }
    return false;
  }

  private static boolean isLiteral(org.pkl.parser.syntax.Node parserNode) {
    if (parserNode instanceof IntLiteralExpr
        || parserNode instanceof FloatLiteralExpr
        || parserNode instanceof BoolLiteralExpr
        || parserNode instanceof NullLiteralExpr) {
      return true;
    }
    if (parserNode instanceof StringLiteralExpr stringLiteralExpr) {
      return !stringLiteralExpr.hasInterpolation();
    }
    return false;
  }

  // tells if this method is call to a literal value
  // treats method calls like `List(1, 2, 3)` as literal values.
  private static boolean isLiteral(Node truffleNode, org.pkl.parser.syntax.Node parserNode) {
    if (isLiteral(parserNode)) {
      return true;
    }
    if (truffleNode instanceof ConstantValueNode) {
      if (parserNode instanceof UnqualifiedAccessExpr unqualifiedAccessExpr) {
        // Assumption: if we have both ConstantValueNode, and the parser node is
        // UnqualifiedAccessExpr with arguments, then this must be a `List()`, `Map()`, etc.
        //
        // Note: reading a local property whose value is a constant will also turn into a
        // ConstantValueNode.
        return unqualifiedAccessExpr.getArgumentList() != null;
      }
      return true;
    }
    // assumption: only calls to methods within the base module are direct method calls.
    if (!(truffleNode instanceof InvokeMethodDirectNode)) {
      return false;
    }
    var accessExpr = (UnqualifiedAccessExpr) parserNode;
    // assumption: "literal" values are method calls that are uppercased. e.g. IntSeq(..), Pair(..)
    if (!Character.isUpperCase(accessExpr.getIdentifier().getValue().charAt(0))) {
      return false;
    }
    var argsList = accessExpr.getArgumentList();
    assert argsList != null;
    return argsList.getArguments().stream().allMatch(PowerAssertions::isLiteral);
  }

  private static boolean shouldHide(
      Node truffleNode, org.pkl.parser.syntax.Node parserNode, Object value, Expr rootExpr) {
    // literal values are self-evident in their source code
    if (isLiteral(truffleNode, parserNode)) {
      return true;
    }
    if (
    // let expressions will already show the resolved value in the expression body; showing the
    // let as well simply adds noise.
    parserNode instanceof LetExpr
        // we'll already show the expression within string interpolation
        || parserNode instanceof StringInterpolation
        || parserNode instanceof StringChars) {
      return true;
    }
    if (isInForGeneratorOrLambdaOrPredicate(parserNode, rootExpr)) {
      return true;
    }
    if (value instanceof VmFunction) {
      return true;
    }
    // implicit behavior around `new`
    return truffleNode instanceof GetParentForTypeNode
        || truffleNode instanceof InferParentWithinMethodNode
        || truffleNode instanceof InferParentWithinObjectMethodNode
        || truffleNode instanceof InferParentWithinPropertyNode;
  }

  // tries to find the parser node for this node
  private static @Nullable org.pkl.parser.syntax.Node findParserNode(
      Node node, @Nullable org.pkl.parser.syntax.Node parserNode, int offset) {
    if (!node.getSourceSection().isAvailable()) {
      return null;
    }
    if (parserNode == null) {
      return null;
    }
    var span = parserNode.span();
    var charIndex = span.charIndex() + offset;
    var ss = node.getSourceSection();
    if (charIndex == ss.getCharIndex() && span.length() == ss.getCharLength()) {
      return parserNode;
    }
    var children = parserNode.children();
    if (children == null) {
      return null;
    }
    for (var child : children) {
      var found = findParserNode(node, child, offset);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static Collection<LayerEntry> getLayerEntries(
      Map<Node, List<Object>> trackedValues, SourceSection sourceSection) {
    var exprNode = parser.parseExpressionInput(sourceSection.getCharacters().toString());
    // it's possible that two nodes can turn into identical `SourceEntry`s; ensure these entries are
    // distinct by using a set.
    var ret = new LinkedHashSet<LayerEntry>();
    for (var entry : trackedValues.entrySet()) {
      var truffleNode = entry.getKey();
      var values = entry.getValue();
      var parserNode = findParserNode(truffleNode, exprNode, sourceSection.getCharIndex());
      if (parserNode == null) {
        continue;
      }
      // this node was executed multiple times; not sure how to best show this in a power assert
      // graph
      if (values.size() > 1) {
        continue;
      }
      var value = values.get(0);
      if (shouldHide(truffleNode, parserNode, value, exprNode)) {
        continue;
      }
      ret.add(SourceEntry.create(truffleNode, parserNode, value));
    }
    return ret;
  }

  private static boolean canFit(Deque<LayerEntry> layer, LayerEntry elem) {
    if (layer.isEmpty()) {
      return true;
    }
    var nextEntry = layer.getFirst();
    return elem.startColumn() + elem.length() + 1 < nextEntry.startColumn();
  }

  private static boolean canFitMarker(Deque<LayerEntry> layer, LayerEntry elem) {
    if (layer.isEmpty()) {
      return true;
    }
    var nextEntry = layer.getFirst();
    return elem.startColumn() < nextEntry.startColumn();
  }

  private static List<Collection<LayerEntry>> buildLayers(
      Collection<LayerEntry> layerEntries, SourceSection line) {
    var sortedSections =
        layerEntries.stream()
            .filter((it) -> it.startLine() == line.getStartLine())
            .sorted(Comparator.comparingInt((it) -> -it.startColumn()))
            .collect(Collectors.toCollection(LinkedList::new));
    if (sortedSections.isEmpty()) {
      return Collections.emptyList();
    }

    var layers = new ArrayList<Collection<LayerEntry>>();

    // first layer is all markers
    layers.add(firstLayerMarkers(sortedSections));

    while (!sortedSections.isEmpty()) {
      var layer = new ArrayDeque<LayerEntry>();
      var iter = sortedSections.iterator();
      while (iter.hasNext()) {
        var next = iter.next();
        if (canFit(layer, next)) {
          layer.addFirst(next);
          iter.remove();
        } else if (canFitMarker(layer, next)) {
          layer.addFirst(next.toMarker());
        }
      }
      layers.add(layer);
    }
    return layers;
  }

  private static Collection<LayerEntry> firstLayerMarkers(List<LayerEntry> entries) {
    var layer = new ArrayDeque<LayerEntry>();
    LayerEntry prevEntry = null;
    for (var entry : entries) {
      if (prevEntry != null && prevEntry.startColumn() == entry.startColumn()) {
        continue;
      }
      layer.addFirst(entry.toMarker());
      prevEntry = entry;
    }
    return layer;
  }

  private static List<SourceSection> lines(SourceSection sourceSection) {
    var startLine = sourceSection.getStartLine();
    var endLine = sourceSection.getEndLine();
    if (!sourceSection.isAvailable() || startLine == endLine) {
      return Collections.singletonList(sourceSection);
    }

    var result = new ArrayList<SourceSection>();
    var source = sourceSection.getSource();
    var charIndex = sourceSection.getCharIndex();
    var endCharIndex = charIndex + sourceSection.getCharLength();

    for (var lineNumber = startLine; lineNumber <= endLine; lineNumber++) {
      var lineSection = source.createSection(lineNumber);
      var lineStartChar = lineSection.getCharIndex();
      var lineEndChar = lineStartChar + lineSection.getCharLength();

      var sectionStartChar = Math.max(charIndex, lineStartChar);
      var sectionEndChar = Math.min(endCharIndex, lineEndChar);

      if (sectionStartChar < sectionEndChar) {
        result.add(source.createSection(sectionStartChar, sectionEndChar - sectionStartChar));
      }
    }

    return result;
  }

  public static String trimLeadingWhitespace(String str, int n) {
    var i = 0;

    while (i < str.length() && i < n && Character.isWhitespace(str.charAt(i))) {
      i++;
    }

    return str.substring(i);
  }

  private static boolean renderLine(
      AnsiStringBuilder out,
      String indent,
      SourceSection line,
      Collection<LayerEntry> layerEntries,
      int trimStart,
      @Nullable Consumer<AnsiStringBuilder> lineSuffix) {
    var layers = buildLayers(layerEntries, line);
    var content = trimLeadingWhitespace(line.getCharacters().toString(), trimStart);
    out.append(indent).append(content);
    if (lineSuffix != null) {
      lineSuffix.accept(out);
    }
    out.append('\n');
    if (layers.isEmpty()) {
      return false;
    }
    for (var i = 0; i < layers.size(); i++) {
      var layer = layers.get(i);
      out.append(indent);
      var cursor = 0;
      for (var entry : layer) {
        var currentOffset = entry.startColumn() - 1 - trimStart - cursor;
        out.append(" ".repeat(currentOffset));
        entry.appendTo(out);
        cursor += currentOffset + entry.length();
      }
      if (i < layers.size() - 1) {
        out.append('\n');
      }
    }
    return true;
  }

  private static int leadingWhitespace(CharSequence src) {
    var result = 0;
    for (var i = 0; i < src.length(); i++) {
      var c = src.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      result++;
    }
    return result;
  }

  private sealed interface LayerEntry permits SourceEntry, MarkerEntry {

    /** 1-based (because {@link SourceSection}'s is too). */
    int startLine();

    /** 1-based (because {@link SourceSection}'s is too). */
    int startColumn();

    int length();

    void appendTo(AnsiStringBuilder builder);

    default MarkerEntry toMarker() {
      return new MarkerEntry(startLine(), startColumn());
    }
  }

  private record SourceEntry(int startLine, int startColumn, String src) implements LayerEntry {
    static SourceEntry create(
        Node truffleNode, org.pkl.parser.syntax.Node parserNode, Object value) {
      var effectiveSourceSection =
          truffleNode
              .getSourceSection()
              .getSource()
              .createSection(getCharIndex(truffleNode, parserNode), 1);
      return new SourceEntry(
          effectiveSourceSection.getStartLine(),
          effectiveSourceSection.getStartColumn(),
          vmValueRenderer.render(value));
    }

    // create a subsection that points to the operator
    //
    // 1 + 1
    //   |
    //
    // or in the case of qualified access, the very next character after qualified access
    //
    // foo.bar
    //     |
    private static int getCharIndex(Node truffleNode, org.pkl.parser.syntax.Node parserNode) {
      if (truffleNode instanceof WrapperNode wrapperNode) {
        truffleNode = wrapperNode.getDelegateNode();
      }
      var originalSource = truffleNode.getSourceSection();
      if (!originalSource.isAvailable()) {
        return originalSource.getCharIndex();
      }
      var exprText = originalSource.getCharacters().toString();
      var skip = 0;
      if (parserNode instanceof BinaryOperatorExpr binaryOperatorExpr) {
        skip =
            binaryOperatorExpr.getLeft().span().stopIndexExclusive()
                - parserNode.span().charIndex();
      } else if (parserNode instanceof TypeCheckExpr typeCheckExpr) {
        skip = typeCheckExpr.getExpr().span().stopIndexExclusive() - parserNode.span().charIndex();
      } else if (parserNode instanceof SubscriptExpr subscriptExpr) {
        skip = subscriptExpr.getExpr().span().stopIndexExclusive() - parserNode.span().charIndex();
      } else if (parserNode instanceof SuperAccessExpr
          || parserNode instanceof SuperSubscriptExpr) {
        skip = "super".length();
      } else if (parserNode instanceof QualifiedAccessExpr qualifiedAccessExpr) {
        skip =
            qualifiedAccessExpr.getExpr().span().stopIndexExclusive()
                - parserNode.span().charIndex();
      }
      if (skip == 0) {
        return originalSource.getCharIndex();
      }
      // need to lex the source again because the parse tree doesn't carry the operator's position
      var lexer = new Lexer(exprText.substring(skip));
      var nextToken = lexer.next();
      while (!nextToken.isOperator()) {
        nextToken = lexer.next();
      }
      var span = lexer.span();
      if (nextToken == Token.DOT || nextToken == Token.QDOT) {
        return originalSource.getCharIndex() + skip + span.charIndex() + span.length();
      }
      return originalSource.getCharIndex() + skip + span.charIndex();
    }

    @Override
    public int length() {
      return src.length();
    }

    @Override
    public void appendTo(AnsiStringBuilder builder) {
      SyntaxHighlighter.writeTo(builder, src);
    }
  }

  private record MarkerEntry(int startLine, int startColumn) implements LayerEntry {
    @Override
    public int length() {
      return 1;
    }

    @Override
    public void appendTo(AnsiStringBuilder builder) {
      builder.append(AnsiCode.FAINT, "│");
    }

    @Override
    public MarkerEntry toMarker() {
      return this;
    }
  }
}
