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
package org.pkl.formatter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.pkl.parser.syntax.Operator;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

final class Builder {

  private int id = 0;
  private final char[] source;
  private boolean noNewlines = false;
  private final GrammarVersion grammarVersion;

  Builder(String sourceText, GrammarVersion grammarVersion) {
    this.source = sourceText.toCharArray();
    this.grammarVersion = grammarVersion;
  }

  FormatNode format(Node node) {
    return switch (node.type) {
      case MODULE -> formatModule(node);
      case DOC_COMMENT -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case DOC_COMMENT_LINE -> formatDocComment(node);
      case LINE_COMMENT,
          BLOCK_COMMENT,
          TERMINAL,
          MODIFIER,
          IDENTIFIER,
          STRING_CHARS,
          STRING_ESCAPE,
          INT_LITERAL_EXPR,
          FLOAT_LITERAL_EXPR,
          BOOL_LITERAL_EXPR,
          THIS_EXPR,
          OUTER_EXPR,
          MODULE_EXPR,
          NULL_EXPR,
          MODULE_TYPE,
          UNKNOWN_TYPE,
          NOTHING_TYPE,
          SHEBANG,
          OPERATOR ->
          new Text(node.text(source));
      case STRING_NEWLINE -> mustForceLine();
      case MODULE_DECLARATION -> formatModuleDeclaration(node);
      case MODULE_DEFINITION -> formatModuleDefinition(node);
      case SINGLE_LINE_STRING_LITERAL_EXPR -> formatSingleLineString(node);
      case MULTI_LINE_STRING_LITERAL_EXPR -> formatMultilineString(node);
      case ANNOTATION -> formatAnnotation(node);
      case TYPEALIAS -> formatTypealias(node);
      case TYPEALIAS_HEADER -> formatTypealiasHeader(node);
      case TYPEALIAS_BODY -> formatTypealiasBody(node);
      case MODIFIER_LIST -> formatModifierList(node);
      case PARAMETER_LIST -> formatParameterList(node, null);
      case PARAMETER_LIST_ELEMENTS -> formatParameterListElements(node);
      case TYPE_PARAMETER_LIST -> formatTypeParameterList(node);
      case TYPE_PARAMETER_LIST_ELEMENTS -> formatParameterListElements(node);
      case TYPE_PARAMETER -> new Group(newId(), formatGeneric(node.children, spaceOrLine()));
      case PARAMETER -> formatParameter(node);
      case EXTENDS_CLAUSE, AMENDS_CLAUSE -> formatAmendsExtendsClause(node);
      case IMPORT_LIST -> formatImportList(node);
      case IMPORT -> formatImport(node);
      case IMPORT_ALIAS -> new Group(newId(), formatGeneric(node.children, spaceOrLine()));
      case CLASS -> formatClass(node);
      case CLASS_HEADER -> formatClassHeader(node);
      case CLASS_HEADER_EXTENDS -> formatClassHeaderExtends(node);
      case CLASS_BODY -> formatClassBody(node);
      case CLASS_BODY_ELEMENTS -> formatClassBodyElements(node);
      case CLASS_PROPERTY, OBJECT_PROPERTY, OBJECT_ENTRY -> formatClassProperty(node);
      case CLASS_PROPERTY_HEADER, OBJECT_PROPERTY_HEADER -> formatClassPropertyHeader(node);
      case CLASS_PROPERTY_HEADER_BEGIN, OBJECT_PROPERTY_HEADER_BEGIN ->
          formatClassPropertyHeaderBegin(node);
      case CLASS_PROPERTY_BODY, OBJECT_PROPERTY_BODY -> formatClassPropertyBody(node);
      case CLASS_METHOD, OBJECT_METHOD -> formatClassMethod(node);
      case CLASS_METHOD_HEADER -> formatClassMethodHeader(node);
      case CLASS_METHOD_BODY -> formatClassMethodBody(node);
      case OBJECT_BODY -> formatObjectBody(node);
      case OBJECT_ELEMENT -> format(node.children.get(0)); // has a single element
      case OBJECT_ENTRY_HEADER -> formatObjectEntryHeader(node);
      case FOR_GENERATOR -> formatForGenerator(node);
      case FOR_GENERATOR_HEADER -> formatForGeneratorHeader(node);
      case FOR_GENERATOR_HEADER_DEFINITION -> formatForGeneratorHeaderDefinition(node);
      case FOR_GENERATOR_HEADER_DEFINITION_HEADER -> formatForGeneratorHeaderDefinitionHeader(node);
      case WHEN_GENERATOR -> formatWhenGenerator(node);
      case WHEN_GENERATOR_HEADER -> formatWhenGeneratorHeader(node);
      case OBJECT_SPREAD -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case MEMBER_PREDICATE -> formatMemberPredicate(node);
      case QUALIFIED_IDENTIFIER -> formatQualifiedIdentifier(node);
      case ARGUMENT_LIST -> formatArgumentList(node, false);
      case ARGUMENT_LIST_ELEMENTS -> formatArgumentListElements(node, false, false);
      case OBJECT_PARAMETER_LIST -> formatObjectParameterList(node);
      case IF_EXPR -> formatIf(node);
      case IF_HEADER -> formatIfHeader(node);
      case IF_CONDITION -> formatIfCondition(node);
      case IF_CONDITION_EXPR -> new Indent(formatGeneric(node.children, (FormatNode) null));
      case IF_THEN_EXPR -> formatIfThen(node);
      case IF_ELSE_EXPR -> formatIfElse(node);
      case NEW_EXPR, AMENDS_EXPR -> formatNewExpr(node);
      case NEW_HEADER -> formatNewHeader(node);
      case UNQUALIFIED_ACCESS_EXPR -> formatUnqualifiedAccessExpression(node);
      case QUALIFIED_ACCESS_EXPR -> formatQualifiedAccessExpression(node);
      case BINARY_OP_EXPR -> formatBinaryOpExpr(node);
      case FUNCTION_LITERAL_EXPR -> formatFunctionLiteralExpr(node);
      case FUNCTION_LITERAL_BODY -> formatFunctionLiteralBody(node);
      case SUBSCRIPT_EXPR, SUPER_SUBSCRIPT_EXPR -> formatSubscriptExpr(node);
      case TRACE_EXPR -> formatTraceThrowReadExpr(node);
      case THROW_EXPR -> formatTraceThrowReadExpr(node);
      case READ_EXPR -> formatTraceThrowReadExpr(node);
      case NON_NULL_EXPR -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case SUPER_ACCESS_EXPR -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case PARENTHESIZED_EXPR -> formatParenthesizedExpr(node);
      case PARENTHESIZED_EXPR_ELEMENTS -> formatParenthesizedExprElements(node);
      case IMPORT_EXPR -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case LET_EXPR -> formatLetExpr(node);
      case LET_PARAMETER_DEFINITION -> formatLetParameterDefinition(node);
      case LET_PARAMETER -> formatLetParameter(node);
      case UNARY_MINUS_EXPR -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case LOGICAL_NOT_EXPR -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case TYPE_ANNOTATION -> formatTypeAnnotation(node);
      case TYPE_ARGUMENT_LIST -> formatTypeParameterList(node);
      case TYPE_ARGUMENT_LIST_ELEMENTS -> formatParameterListElements(node);
      case DECLARED_TYPE -> formatDeclaredType(node);
      case CONSTRAINED_TYPE -> formatConstrainedType(node);
      case CONSTRAINED_TYPE_CONSTRAINT -> formatParameterList(node, null);
      case CONSTRAINED_TYPE_ELEMENTS -> formatParameterListElements(node);
      case NULLABLE_TYPE -> new Nodes(formatGeneric(node.children, (FormatNode) null));
      case UNION_TYPE -> formatUnionType(node);
      case FUNCTION_TYPE -> formatFunctionType(node);
      case FUNCTION_TYPE_PARAMETERS -> formatParameterList(node, null);
      case STRING_CONSTANT_TYPE -> format(node.children.get(0));
      case PARENTHESIZED_TYPE -> formatParenthesizedType(node);
      case PARENTHESIZED_TYPE_ELEMENTS -> formatParenthesizedTypeElements(node);
      default -> throw new RuntimeException("Unknown node type: " + node.type);
    };
  }

  private FormatNode formatModule(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> linesBetween(prev, next) > 1 ? TWO_NEWLINES : forceLine());
    return new Nodes(nodes);
  }

  private FormatNode formatModuleDeclaration(Node node) {
    return new Nodes(formatGeneric(node.children, TWO_NEWLINES));
  }

  private FormatNode formatModuleDefinition(Node node) {
    var split = splitPrefixes(node.children);
    var prefixes = split[0];
    var rest = split[1];
    var fnodes =
        formatGenericWithGen(
            rest,
            Space.INSTANCE,
            (n, next) ->
                n.type == NodeType.QUALIFIED_IDENTIFIER
                    ? new Nodes(formatGeneric(n.children, (FormatNode) null))
                    : format(n));
    var res = new Nodes(fnodes);
    if (prefixes.isEmpty()) {
      return res;
    }
    var sep = getSeparator(prefixes.get(prefixes.size() - 1), rest.get(0), spaceOrLine());
    var result = new ArrayList<>(formatGeneric(prefixes, spaceOrLine()));
    result.add(sep);
    result.add(res);
    return new Nodes(result);
  }

  private FormatNode formatDocComment(Node node) {
    var txt = text(node);
    if (txt.equals("///") || txt.equals("/// ")) return new Text("///");

    var comment = txt.substring(3);
    if (isStrictBlank(comment)) return new Text("///");

    if (!comment.isEmpty() && comment.charAt(0) != ' ') comment = " " + comment;
    return new Text("///" + comment);
  }

  private static boolean isStrictBlank(String s) {
    for (var i = 0; i < s.length(); i++) {
      var ch = s.charAt(i);
      if (ch != ' ' && ch != '\t') return false;
    }
    return true;
  }

  private FormatNode formatQualifiedIdentifier(Node node) {
    // short circuit
    if (node.children.size() == 1) return format(node.children.get(0));

    var first = new ArrayList<FormatNode>();
    first.add(format(node.children.get(0)));
    first.add(line());
    var rest = node.children.subList(1, node.children.size());
    var nodes = formatGeneric(rest, (n1, next) -> n1.type == NodeType.TERMINAL ? null : line());
    first.add(new Indent(nodes));
    return new Group(newId(), first);
  }

  private FormatNode formatUnqualifiedAccessExpression(Node node) {
    var children = node.children;
    if (children.size() == 1) return format(children.get(0));
    var firstNode = firstProperChild(node);
    if (firstNode != null && text(firstNode).equals("Map")) {
      var nodes =
          formatGenericWithGen(
              children,
              (FormatNode) null,
              (n, next) ->
                  n.type == NodeType.ARGUMENT_LIST ? formatArgumentList(n, true) : format(n));
      return new Nodes(nodes);
    }
    return new Nodes(formatGeneric(children, (FormatNode) null));
  }

  /**
   * Special cases when formatting qualified access:
   *
   * <p>Case 1: Dot calls followed by closing method call: wrap after the opening paren.
   *
   * <p>``` foo.bar.baz(new { qux = 1 }) ```
   *
   * <p>Case 2: Dot calls, then method calls: group the leading access together.
   *
   * <p>``` foo.bar .baz(new { qux = 1 }) .baz() ```
   *
   * <p>Case 3: If there are multiple lambdas present, always force a newline.
   *
   * <p>``` foo .map((it) -> it + 1) .filter((it) -> it.isEven) ```
   */
  private FormatNode formatQualifiedAccessExpression(Node node) {
    var lambdaCount = new int[] {0};
    var methodCallCount = new int[] {0};
    var indexBeforeFirstMethodCall = new int[] {0};
    var flat = new ArrayList<Node>();

    gatherFacts(node, flat, lambdaCount, methodCallCount, indexBeforeFirstMethodCall);

    BiFunction<Node, Node, FormatNode> leadingSeparator =
        (prev, next) -> {
          if (prev.type == NodeType.OPERATOR) return null;
          if (next.type == NodeType.OPERATOR) return line();
          return spaceOrLine();
        };

    BiFunction<Node, Node, FormatNode> trailingSeparator =
        (prev, next) -> {
          if (prev.type == NodeType.OPERATOR) return null;
          if (next.type == NodeType.OPERATOR) return lambdaCount[0] > 1 ? forceLine() : line();
          return spaceOrLine();
        };

    List<FormatNode> nodes;
    if (methodCallCount[0] == 1 && isMethodCall(lastProperNode(flat))) {
      // lift argument list into its own node
      var splitResult = splitFunctionCallNode(flat);
      var callChain = splitResult[0];
      var argsList = splitResult[1];
      var leadingNodes = indentAfterFirstNewline(formatGeneric(callChain, leadingSeparator), true);
      var trailingNodes = formatGeneric(argsList, trailingSeparator);
      var sep = getBaseSeparator(callChain.get(callChain.size() - 1), argsList.get(0));
      if (sep != null) {
        nodes = concat(leadingNodes, sep, trailingNodes);
      } else {
        nodes = concat(leadingNodes, trailingNodes);
      }
    } else if (methodCallCount[0] > 0 && indexBeforeFirstMethodCall[0] > 0) {
      var leading = flat.subList(0, indexBeforeFirstMethodCall[0]);
      var trailing = flat.subList(indexBeforeFirstMethodCall[0], flat.size());
      var leadingNodes = indentAfterFirstNewline(formatGeneric(leading, leadingSeparator), true);
      var trailingNodes = formatGeneric(trailing, trailingSeparator);
      nodes = concat(leadingNodes, line(), trailingNodes);
    } else {
      nodes = formatGeneric(flat, trailingSeparator);
    }

    var shouldGroup = node.children.size() == flat.size();
    return new Group(newId(), indentAfterFirstNewline(nodes, shouldGroup));
  }

  private void gatherFacts(
      Node current,
      List<Node> flat,
      int[] lambdaCount,
      int[] methodCallCount,
      int[] indexBeforeFirstMethodCall) {
    for (var child : current.children) {
      if (child.type == NodeType.QUALIFIED_ACCESS_EXPR) {
        gatherFacts(child, flat, lambdaCount, methodCallCount, indexBeforeFirstMethodCall);
      } else {
        flat.add(child);
        if (isMethodCall(child)) {
          methodCallCount[0]++;
          if (hasFunctionLiteral(child, 2)) {
            lambdaCount[0]++;
          }
        } else if (methodCallCount[0] == 0) {
          indexBeforeFirstMethodCall[0] = flat.size() - 1;
        }
      }
    }
  }

  /**
   * Split a function call node to extract its identifier into the leading group. For example,
   * `foo.bar(5)` becomes: leading gets `foo.bar`, rest gets `(5)`.
   */
  @SuppressWarnings("unchecked")
  private List<Node>[] splitFunctionCallNode(List<Node> nodes) {
    assert !nodes.isEmpty();
    var lastNode = nodes.get(nodes.size() - 1);
    var argListIdx = -1;
    for (var i = 0; i < lastNode.children.size(); i++) {
      if (lastNode.children.get(i).type == NodeType.ARGUMENT_LIST) {
        argListIdx = i;
        break;
      }
    }
    var leading = new ArrayList<>(nodes.subList(0, nodes.size() - 1));
    leading.addAll(lastNode.children.subList(0, argListIdx));
    var trailing = lastNode.children.subList(argListIdx, lastNode.children.size());
    return new List[] {leading, trailing};
  }

  private static boolean isMethodCall(Node node) {
    if (node == null || node.type != NodeType.UNQUALIFIED_ACCESS_EXPR) return false;
    for (var child : node.children) {
      if (child.type == NodeType.ARGUMENT_LIST) return true;
    }
    return false;
  }

  private FormatNode formatAmendsExtendsClause(Node node) {
    var prefix = formatGeneric(node.children.subList(0, node.children.size() - 1), spaceOrLine());
    // string constant
    var suffix = new Indent(List.of(format(node.children.get(node.children.size() - 1))));
    var result = new ArrayList<>(prefix);
    result.add(spaceOrLine());
    result.add(suffix);
    return new Group(newId(), result);
  }

  private FormatNode formatImport(Node node) {
    return new Group(
        newId(),
        formatGenericWithGen(
            node.children,
            spaceOrLine(),
            (n, next) -> isTerminal(n, "import") ? format(n) : indent(format(n))));
  }

  private FormatNode formatAnnotation(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatTypealias(Node node) {
    var nodes =
        groupNonPrefixes(
            node, children -> new Group(newId(), formatGeneric(children, spaceOrLine())));
    return new Nodes(nodes);
  }

  private FormatNode formatTypealiasHeader(Node node) {
    return new Group(newId(), formatGeneric(node.children, Space.INSTANCE));
  }

  private FormatNode formatTypealiasBody(Node node) {
    return new Indent(formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatClass(Node node) {
    return new Nodes(formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatClassHeader(Node node) {
    return groupOnSpace(formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatClassHeaderExtends(Node node) {
    return indent(new Group(newId(), formatGeneric(node.children, spaceOrLine())));
  }

  private FormatNode formatClassBody(Node node) {
    var children = node.children;
    if (children.size() == 2) {
      // no members
      return new Nodes(formatGeneric(children, (FormatNode) null));
    }
    return new Group(newId(), formatGeneric(children, forceLine()));
  }

  private FormatNode formatClassBodyElements(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> {
              var lineDiff = linesBetween(prev, next);
              return (lineDiff > 1 || lineDiff == 0) ? TWO_NEWLINES : forceLine();
            });
    return new Indent(nodes);
  }

  private FormatNode formatClassProperty(Node node) {
    Node lastExprOrBody = null;
    for (var i = node.children.size() - 1; i >= 0; i--) {
      var child = node.children.get(i);
      if (isExpressionOrPropertyBody(child)) {
        lastExprOrBody = child;
        break;
      }
    }
    var sameLine = false;
    if (lastExprOrBody != null) {
      sameLine =
          lastExprOrBody.type.isExpression()
              ? isSameLineExpr(lastExprOrBody)
              : isSameLineExpr(lastExprOrBody.children.get(lastExprOrBody.children.size() - 1));
    }
    var sameLineFinal = sameLine;
    var nodes =
        groupNonPrefixes(
            node,
            children ->
                groupOnSpace(
                    formatGenericWithGen(
                        children,
                        (prev, next) -> sameLineFinal ? Space.INSTANCE : spaceOrLine(),
                        (n, next) ->
                            isExpressionOrPropertyBody(n) && !sameLineFinal
                                ? indent(format(n))
                                : format(n))));
    return new Nodes(nodes);
  }

  private static boolean isExpressionOrPropertyBody(Node node) {
    return node.type.isExpression()
        || node.type == NodeType.CLASS_PROPERTY_BODY
        || node.type == NodeType.OBJECT_PROPERTY_BODY;
  }

  private FormatNode formatClassPropertyHeader(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatClassPropertyHeaderBegin(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatClassPropertyBody(Node node) {
    return new Nodes(formatGeneric(node.children, (FormatNode) null));
  }

  private FormatNode formatClassMethod(Node node) {
    var prefixes = new ArrayList<FormatNode>();
    List<Node> methodNodes;
    if (node.children.get(0).type == NodeType.CLASS_METHOD_HEADER) {
      methodNodes = node.children;
    } else {
      var idx = -1;
      for (var i = 0; i < node.children.size(); i++) {
        if (node.children.get(i).type == NodeType.CLASS_METHOD_HEADER) {
          idx = i;
          break;
        }
      }
      var prefixNodes = node.children.subList(0, idx);
      prefixes.addAll(formatGeneric(prefixNodes, (FormatNode) null));
      prefixes.add(
          getSeparator(
              prefixNodes.get(prefixNodes.size() - 1), node.children.get(idx), forceLine()));
      methodNodes = node.children.subList(idx, node.children.size());
    }

    // Separate header (before =) and body (= and after)
    var bodyIdx = -1;
    for (var i = 0; i < methodNodes.size(); i++) {
      if (methodNodes.get(i).type == NodeType.CLASS_METHOD_BODY) {
        bodyIdx = i - 1;
        break;
      }
    }
    var header = bodyIdx < 0 ? methodNodes : methodNodes.subList(0, bodyIdx);
    var headerGroupId = newId();
    var methodGroupId = newId();
    var headerNodes =
        formatGenericWithGen(
            header,
            spaceOrLine(),
            (n, next) ->
                n.type == NodeType.PARAMETER_LIST
                    ? formatParameterList(n, headerGroupId)
                    : format(n));
    if (bodyIdx < 0) {
      // body is Empty(), return header
      if (prefixes.isEmpty()) {
        return new Group(headerGroupId, headerNodes);
      }
      prefixes.add(new Group(headerGroupId, headerNodes));
      return new Nodes(prefixes);
    }

    var bodyNodes = methodNodes.subList(bodyIdx, methodNodes.size());
    var expr = bodyNodes.get(bodyNodes.size() - 1).children.get(0);
    var isSameLineBody = isSameLineExpr(expr);

    // Format body (= and expression)
    List<FormatNode> bodyFormat;
    if (isSameLineBody) {
      bodyFormat = formatGeneric(bodyNodes, Space.INSTANCE);
    } else {
      bodyFormat =
          formatGenericWithGen(
              bodyNodes, spaceOrLine(), (n, next) -> next == null ? indent(format(n)) : format(n));
    }

    var headerGroup = new Group(headerGroupId, headerNodes);
    var bodyGroup = new Group(newId(), bodyFormat);
    var separator = getSeparator(header.get(header.size() - 1), bodyNodes.get(0), Space.INSTANCE);
    var allNodes = new Group(methodGroupId, List.of(headerGroup, separator, bodyGroup));

    if (prefixes.isEmpty()) return allNodes;
    prefixes.add(allNodes);
    return new Nodes(prefixes);
  }

  private FormatNode formatClassMethodHeader(Node node) {
    return new Nodes(formatGeneric(node.children, Space.INSTANCE));
  }

  private FormatNode formatClassMethodBody(Node node) {
    return new Group(newId(), formatGeneric(node.children, (FormatNode) null));
  }

  private FormatNode formatParameter(Node node) {
    if (node.children.size() == 1) return format(node.children.get(0)); // underscore
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatParameterList(Node node, Integer id) {
    if (node.children.size() == 2) return new Text("()");
    var groupId = id != null ? id : newId();
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> {
              if (isTerminal(prev, "(") || isTerminal(next, ")")) {
                if (isTerminal(next, ")")) {
                  // trailing comma
                  if (grammarVersion == GrammarVersion.V1) {
                    return line();
                  } else {
                    return ifWrap(groupId, nodes(new Text(","), line()), line());
                  }
                }
                return line();
              }
              return spaceOrLine();
            });
    return id != null ? new Nodes(nodes) : new Group(groupId, nodes);
  }

  private FormatNode formatArgumentList(Node node, boolean twoBy2) {
    if (node.children.size() == 2) return new Text("()");
    var hasTrailingLambda = hasTrailingLambda(node);
    var groupId = newId();
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> {
              if (isTerminal(prev, "(") || isTerminal(next, ")")) {
                var lineNode = hasTrailingLambda ? Empty.INSTANCE : line();
                if (isTerminal(next, ")") && !hasTrailingLambda) {
                  // trailing comma
                  if (grammarVersion == GrammarVersion.V1) {
                    return lineNode;
                  } else {
                    return ifWrap(groupId, nodes(new Text(","), lineNode), lineNode);
                  }
                }
                return lineNode;
              }
              return spaceOrLine();
            },
            (n, next) ->
                n.type == NodeType.ARGUMENT_LIST_ELEMENTS
                    ? formatArgumentListElements(n, hasTrailingLambda, twoBy2)
                    : format(n));
    return new Group(groupId, nodes);
  }

  private FormatNode formatArgumentListElements(
      Node node, boolean hasTrailingLambda, boolean twoBy2) {
    var children = node.children;
    var shouldMultiline = shouldMultilineNodes(node, n -> isTerminal(n, ","));
    BiFunction<Node, Node, FormatNode> sep =
        (prev, next) -> shouldMultiline ? forceSpaceyLine() : spaceOrLine();
    if (twoBy2) {
      var pairs = pairArguments(children);
      var nodes =
          formatGenericWithGen(
              pairs,
              sep,
              (n, next) ->
                  n.type == NodeType.ARGUMENT_LIST_ELEMENTS
                      ? new Group(newId(), formatGeneric(n.children, spaceOrLine()))
                      : format(n));
      return new Indent(nodes);
    }
    if (hasTrailingLambda) {
      // if the args have a trailing lambda, group them differently
      var splitIndex = -1;
      for (var i = children.size() - 1; i >= 0; i--) {
        if (SAME_LINE_EXPRS.contains(children.get(i).type)) {
          splitIndex = i;
          break;
        }
      }
      var normalParams = children.subList(0, splitIndex);
      var lastParam = children.subList(splitIndex, children.size());
      var trailingNode =
          endsWithClosingCurlyBrace(lastParam.get(lastParam.size() - 1)) ? Empty.INSTANCE : line();
      var lastNodes = formatGenericWithGen(lastParam, sep, null);
      if (normalParams.isEmpty()) {
        return group(new Group(newId(), lastNodes), trailingNode);
      }
      var separator =
          getSeparator(normalParams.get(normalParams.size() - 1), lastParam.get(0), Space.INSTANCE);
      var paramNodes = formatGenericWithGen(normalParams, sep, null);
      return group(
          new Group(newId(), paramNodes), separator, new Group(newId(), lastNodes), trailingNode);
    }
    return new Indent(formatGeneric(children, sep));
  }

  private boolean shouldMultilineNodes(Node node, Predicate<Node> predicate) {
    for (var idx = 0; idx < node.children.size() - 1; idx++) {
      var prev = node.children.get(idx);
      var next = node.children.get(idx + 1);
      if ((predicate.test(prev) || predicate.test(next)) && linesBetween(prev, next) > 0) {
        return true;
      }
    }
    return false;
  }

  private boolean endsWithClosingCurlyBrace(Node node) {
    while (!node.children.isEmpty()) {
      node = node.children.get(node.children.size() - 1);
    }
    return isTerminalSingle(node, "}");
  }

  /**
   * Tells if an argument list has a trailing lambda, new expr, or amends expr.
   *
   * <p>Only considered trailing lamdba if: 1. There is only one lambda/new expr/amends expr in the
   * list. E.g. avoid formatting `toMap()` weirdly: ``` foo.toMap( (it) -> makeSomeKey(it), (it) ->
   * makeSomeValue(it), ) ``` 2. The lambda does not have leading or trailing line comment.
   */
  private boolean hasTrailingLambda(Node argList) {
    var elementsNode = firstProperChild(argList);
    if (elementsNode == null) return false;
    var children = elementsNode.children;
    var seenLambda = false;
    if (children.get(children.size() - 1).type == NodeType.LINE_COMMENT) return false;
    for (var i = children.size() - 1; i >= 0; i--) {
      var child = children.get(i);
      if (!isProper(child)) continue;
      if (!seenLambda) {
        if (!SAME_LINE_EXPRS.contains(child.type)) return false;
        // preceded by Line() comment
        if (i > 0 && children.get(i - 1).type == NodeType.LINE_COMMENT) return false;
        seenLambda = true;
      } else if (SAME_LINE_EXPRS.contains(child.type)) {
        return false;
      }
    }
    return true;
  }

  private List<Node> pairArguments(List<Node> nodes) {
    var res = new ArrayList<Node>();
    var tmp = new ArrayList<Node>();
    var commas = 0;
    for (var node : nodes) {
      if (isTerminalSingle(node, ",")) {
        commas++;
        if (commas == 2) {
          var suffixes = new ArrayList<Node>();
          while (!tmp.isEmpty() && tmp.get(tmp.size() - 1).type.isAffix()) {
            // trailing comments should not be paired
            suffixes.add(tmp.remove(tmp.size() - 1));
          }
          res.add(new Node(NodeType.ARGUMENT_LIST_ELEMENTS, tmp));
          for (var j = suffixes.size() - 1; j >= 0; j--) {
            res.add(suffixes.get(j));
          }
          res.add(node);
          commas = 0;
          tmp = new ArrayList<>();
        } else {
          tmp.add(node);
        }
      } else if (tmp.isEmpty() && node.type.isAffix()) {
        // leading comments should not be paired
        res.add(node);
      } else {
        tmp.add(node);
      }
    }
    if (!tmp.isEmpty()) {
      res.add(new Node(NodeType.ARGUMENT_LIST_ELEMENTS, tmp));
    }
    return res;
  }

  private FormatNode formatParameterListElements(Node node) {
    return new Indent(formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatTypeParameterList(Node node) {
    if (node.children.size() == 2) return new Text("<>");
    var groupId = newId();
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> {
              if (isTerminal(prev, "<") || isTerminal(next, ">")) {
                if (isTerminal(next, ">")) {
                  // trailing comma
                  if (grammarVersion == GrammarVersion.V1) {
                    return new Line();
                  } else {
                    return ifWrap(groupId, nodes(new Text(","), line()), line());
                  }
                }
                return line();
              }
              return spaceOrLine();
            });
    return new Group(groupId, nodes);
  }

  private FormatNode formatObjectParameterList(Node node) {
    // object param lists don't have trailing commas, as they have a trailing ->
    var groupId = newId();
    var nonWrappingNodes = new Nodes(formatGeneric(node.children, spaceOrLine()));
    // double indent the params if they wrap
    var wrappingNodes = indent(new Indent(concat(List.of(line()), List.of(nonWrappingNodes))));
    return new Group(
        groupId, List.of(ifWrap(groupId, wrappingNodes, nodes(Space.INSTANCE, nonWrappingNodes))));
  }

  private FormatNode formatObjectBody(Node node) {
    if (node.children.size() == 2) return new Text("{}");
    var groupId = newId();
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> {
              if (next.type == NodeType.OBJECT_PARAMETER_LIST) return Empty.INSTANCE;
              if (isTerminal(prev, "{") || isTerminal(next, "}")) {
                var lines = linesBetween(prev, next);
                return lines == 0 ? spaceOrLine() : forceSpaceyLine();
              }
              return spaceOrLine();
            },
            (n, next) ->
                n.type == NodeType.OBJECT_MEMBER_LIST
                    ? formatObjectMemberList(n, groupId)
                    : format(n));
    return new Group(groupId, nodes);
  }

  private FormatNode formatObjectMemberList(Node node, int groupId) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> {
              var lines = linesBetween(prev, next);
              if (lines == 0) return ifWrap(groupId, line(), new Text("; "));
              if (lines == 1) return forceLine();
              return TWO_NEWLINES;
            });
    return new Indent(nodes);
  }

  private FormatNode formatObjectEntryHeader(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatForGenerator(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) ->
                prev.type == NodeType.FOR_GENERATOR_HEADER
                        || next.type == NodeType.FOR_GENERATOR_HEADER
                    ? Space.INSTANCE
                    : spaceOrLine());
    return new Group(newId(), nodes);
  }

  private FormatNode formatForGeneratorHeader(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> isTerminal(prev, "(") || isTerminal(next, ")") ? line() : null);
    return new Group(newId(), nodes);
  }

  private FormatNode formatForGeneratorHeaderDefinition(Node node) {
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> SAME_LINE_EXPRS.contains(next.type) ? Space.INSTANCE : spaceOrLine(),
            (n, next) ->
                n.type.isExpression() && !SAME_LINE_EXPRS.contains(n.type)
                    ? indent(format(n))
                    : format(n));
    return indent(new Group(newId(), nodes));
  }

  private FormatNode formatForGeneratorHeaderDefinitionHeader(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatWhenGenerator(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) ->
                prev.type == NodeType.WHEN_GENERATOR_HEADER
                        || isTerminal(prev, "when", "else")
                        || isTerminal(next, "else")
                    ? Space.INSTANCE
                    : spaceOrLine());
    return new Group(newId(), nodes);
  }

  private FormatNode formatWhenGeneratorHeader(Node node) {
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> isTerminal(prev, "(") || isTerminal(next, ")") ? line() : spaceOrLine(),
            (n, next) ->
                !n.type.isAffix() && n.type != NodeType.TERMINAL ? indent(format(n)) : format(n));
    return new Group(newId(), nodes);
  }

  private FormatNode formatMemberPredicate(Node node) {
    var nodes =
        formatGenericWithGen(
            node.children,
            spaceOrLine(),
            (n, next) ->
                next == null && n.type != NodeType.OBJECT_BODY ? indent(format(n)) : format(n));
    return new Group(newId(), nodes);
  }

  private List<FormatNode> formatStringParts(List<Node> nodes) {
    var result = new ArrayList<FormatNode>();
    var isInStringInterpolation = false;
    var cursor = new PeekableIterator<>(nodes.iterator());
    Node prev = null;
    while (cursor.hasNext()) {
      if (isInStringInterpolation) {
        var prevNoNewlines = noNewlines;
        var elems = cursor.takeUntilBefore(n -> isTerminalSingle(n, ")"));
        noNewlines = !isMultilineList(elems);
        var baseSep = getBaseSeparator(prev, elems.get(0));
        if (baseSep != null) result.add(baseSep);
        result.addAll(formatGeneric(elems, (FormatNode) null));
        var endSep = getBaseSeparator(elems.get(elems.size() - 1), cursor.peek());
        if (endSep != null) result.add(endSep);
        noNewlines = prevNoNewlines;
        isInStringInterpolation = false;
        continue;
      }
      var elem = cursor.next();
      if (elem.type == NodeType.TERMINAL && text(elem).endsWith("(")) {
        isInStringInterpolation = true;
      }
      result.add(format(elem));
      prev = elem;
    }
    return result;
  }

  private FormatNode formatSingleLineString(Node node) {
    return new Group(newId(), formatStringParts(node.children));
  }

  private FormatNode formatMultilineString(Node node) {
    var nodes = formatStringParts(node.children);
    return new MultilineStringGroup(
        node.children.get(node.children.size() - 1).span.colBegin(), nodes);
  }

  private FormatNode formatIf(Node node) {
    var separator = isMultiline(node) ? forceSpaceyLine() : spaceOrLine();
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> {
              // produce `else if` in the case of nested if.
              // note: don't need to handle if `next.children[0]` is an affix because that can't be
              // emitted as `else if` anyway.
              if (next.type == NodeType.IF_ELSE_EXPR
                  && next.children.get(0).type == NodeType.IF_EXPR) {
                return Space.INSTANCE;
              }
              return separator;
            });
    return new Group(newId(), nodes);
  }

  private FormatNode formatIfHeader(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> next.type == NodeType.IF_CONDITION ? Space.INSTANCE : spaceOrLine());
    return new Group(newId(), nodes);
  }

  private FormatNode formatIfCondition(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) ->
                isTerminal(prev, "(") || isTerminal(next, ")") ? line() : spaceOrLine());
    return new Group(newId(), nodes);
  }

  private FormatNode formatIfThen(Node node) {
    return new Indent(formatGeneric(node.children, (FormatNode) null));
  }

  private FormatNode formatIfElse(Node node) {
    var children = node.children;
    if (children.size() == 1) {
      var expr = children.get(0);
      if (expr.type == NodeType.IF_EXPR) {
        // unpack the group
        var group = (Group) formatIf(expr);
        return new Nodes(group.nodes());
      }
      return indent(format(expr));
    }
    return new Indent(formatGeneric(node.children, (FormatNode) null));
  }

  private FormatNode formatNewExpr(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatNewHeader(Node node) {
    return new Group(newId(), formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatParenthesizedExpr(Node node) {
    if (node.children.size() == 2) return new Text("()");
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> isTerminal(prev, "(") || isTerminal(next, ")") ? line() : spaceOrLine(),
            (n, next) -> n.type.isExpression() ? indent(format(n)) : format(n));
    return new Group(newId(), nodes);
  }

  private FormatNode formatParenthesizedExprElements(Node node) {
    return indent(new Group(newId(), formatGeneric(node.children, (FormatNode) null)));
  }

  private FormatNode formatFunctionLiteralExpr(Node node) {
    var splitResult = splitOn(node.children, n -> isTerminalSingle(n, "->"));
    var params = splitResult[0];
    var rest = splitResult[1];
    Node bodyNode = null;
    for (var child : node.children) {
      if (child.type == NodeType.FUNCTION_LITERAL_BODY) {
        bodyNode = child;
        break;
      }
    }
    Node exprNode = null;
    assert bodyNode != null;
    for (var child : bodyNode.children) {
      if (child.type.isExpression()) {
        exprNode = child;
        break;
      }
    }
    assert exprNode != null;
    var sameLine = isSameLineExpr(exprNode);
    var sep = sameLine ? Space.INSTANCE : spaceOrLine();
    var bodySep = getSeparator(params.get(params.size() - 1), rest.get(0), sep);

    var nodes = formatGeneric(params, sep);
    var restNodes = new ArrayList<FormatNode>();
    restNodes.add(bodySep);
    restNodes.addAll(formatGeneric(rest, sep));
    var result = new ArrayList<>(nodes);
    result.add(new Group(newId(), restNodes));
    return new Group(newId(), result);
  }

  private FormatNode formatFunctionLiteralBody(Node node) {
    Node expr = null;
    for (var child : node.children) {
      if (child.type.isExpression()) {
        expr = child;
        break;
      }
    }
    var nodes = formatGeneric(node.children, (FormatNode) null);
    assert expr != null;
    return isSameLineExpr(expr) ? new Group(newId(), nodes) : new Indent(nodes);
  }

  private FormatNode formatLetExpr(Node node) {
    var separator = isMultiline(node) ? forceSpaceyLine() : spaceOrLine();
    var endsWithLet = node.children.get(node.children.size() - 1).type == NodeType.LET_EXPR;
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) ->
                next.type == NodeType.LET_PARAMETER_DEFINITION ? Space.INSTANCE : separator,
            (n, next) -> {
              if (n.type == NodeType.LET_EXPR) {
                // unpack the lets
                var group = (Group) formatLetExpr(n);
                return new Nodes(group.nodes());
              }
              if (endsWithLet) return format(n);
              if (n.type.isExpression() || n.type.isAffix()) return indent(format(n));
              return format(n);
            });
    return new Group(newId(), nodes);
  }

  private FormatNode formatLetParameterDefinition(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) ->
                isTerminal(prev, "(") || isTerminal(next, ")") ? line() : spaceOrLine());
    return new Group(newId(), nodes);
  }

  private FormatNode formatLetParameter(Node node) {
    return indent(formatClassProperty(node));
  }

  private FormatNode formatBinaryOpExpr(Node node) {
    var flat = flattenBinaryOperatorExprs(node);
    var shouldMultiline = shouldMultilineNodes(node, n -> n.type == NodeType.OPERATOR);
    var nodes =
        formatGeneric(
            flat,
            (prev, next) -> {
              var sep = shouldMultiline ? forceSpaceyLine() : spaceOrLine();
              if (prev.type == NodeType.OPERATOR) {
                return text(prev).equals("-") ? sep : Space.INSTANCE;
              }
              if (next.type == NodeType.OPERATOR) {
                return text(next).equals("-") ? Space.INSTANCE : sep;
              }
              return sep;
            });

    var shouldGroup = node.children.size() == flat.size();
    return new Group(newId(), indentAfterFirstNewline(nodes, shouldGroup));
  }

  private static boolean hasFunctionLiteral(Node node, int depth) {
    if (node.type == NodeType.FUNCTION_LITERAL_EXPR) return true;
    for (var child : node.children) {
      if (child.type == NodeType.FUNCTION_LITERAL_EXPR) return true;
      if (depth > 0 && hasFunctionLiteral(child, depth - 1)) return true;
    }
    return false;
  }

  private FormatNode formatSubscriptExpr(Node node) {
    return new Nodes(formatGeneric(node.children, (FormatNode) null));
  }

  private FormatNode formatTraceThrowReadExpr(Node node) {
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> isTerminal(prev, "(") || isTerminal(next, ")") ? line() : null,
            (n, next) -> n.type.isExpression() ? indent(format(n)) : format(n));
    return new Group(newId(), nodes);
  }

  private FormatNode formatDeclaredType(Node node) {
    return new Nodes(formatGeneric(node.children, spaceOrLine()));
  }

  private FormatNode formatConstrainedType(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) ->
                next.type == NodeType.CONSTRAINED_TYPE_CONSTRAINT ? null : spaceOrLine());
    return new Group(newId(), nodes);
  }

  private FormatNode formatUnionType(Node node) {
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) -> {
              if (isTerminal(next, "|")) return spaceOrLine();
              if (isTerminal(prev, "|")) return Space.INSTANCE;
              return null;
            });
    return new Group(newId(), indentAfterFirstNewline(nodes, false));
  }

  private FormatNode formatFunctionType(Node node) {
    var nodes =
        formatGenericWithGen(
            node.children,
            (prev, next) -> isTerminal(prev, "(") || isTerminal(next, ")") ? line() : spaceOrLine(),
            (n, next) -> next == null ? indent(format(n)) : format(n));
    return new Group(newId(), nodes);
  }

  private FormatNode formatParenthesizedType(Node node) {
    if (node.children.size() == 2) return new Text("()");
    var groupId = newId();
    var nodes =
        formatGeneric(
            node.children,
            (prev, next) ->
                isTerminal(prev, "(") || isTerminal(next, ")") ? line() : spaceOrLine());
    return new Group(groupId, nodes);
  }

  private FormatNode formatParenthesizedTypeElements(Node node) {
    return indent(new Group(newId(), formatGeneric(node.children, spaceOrLine())));
  }

  private FormatNode formatTypeAnnotation(Node node) {
    return new Group(newId(), formatGeneric(node.children, Space.INSTANCE));
  }

  private FormatNode formatModifierList(Node node) {
    var result = new ArrayList<FormatNode>();
    var affixes = new ArrayList<Node>();
    var modifiers = new ArrayList<Node>();
    for (var child : node.children) {
      if (child.type.isAffix()) {
        affixes.add(child);
      } else {
        modifiers.add(child);
      }
    }
    if (!affixes.isEmpty()) {
      result.addAll(formatGeneric(affixes, spaceOrLine()));
    }
    modifiers.sort((a, b) -> modifierPrecedence(a) - modifierPrecedence(b));
    result.addAll(formatGeneric(modifiers, Space.INSTANCE));
    return new Nodes(result);
  }

  private record ImportWithComments(
      List<Node> leadingAffixes, Node importNode, List<Node> trailingAffixes) {}

  private List<ImportWithComments> buildImportsWithComments(List<Node> children) {
    var result = new ArrayList<ImportWithComments>();
    var lastImport = (Node) null;
    var lastTrailing = new ArrayList<Node>();
    var lastLeading = new ArrayList<Node>();
    var pendingAffixes = new ArrayList<Node>();

    for (var child : children) {
      if (child.type.isAffix()) {
        if (lastImport != null && lastImport.span.lineEnd() == child.span.lineBegin()) {
          // trailing comment on the same Line as the preceding import
          lastTrailing.add(child);
        } else {
          // leading comment for the next import
          // first, flush the previous import
          if (lastImport != null) {
            result.add(new ImportWithComments(lastLeading, lastImport, lastTrailing));
            lastImport = null;
            lastTrailing = new ArrayList<>();
            lastLeading = new ArrayList<>();
          }
          pendingAffixes.add(child);
        }
      } else {
        // import node
        if (lastImport != null) {
          result.add(new ImportWithComments(lastLeading, lastImport, lastTrailing));
          lastTrailing = new ArrayList<>();
        }
        lastLeading = pendingAffixes;
        pendingAffixes = new ArrayList<>();
        lastImport = child;
      }
    }
    // flush the last import
    if (lastImport != null) {
      result.add(new ImportWithComments(lastLeading, lastImport, lastTrailing));
    }
    return result;
  }

  private FormatNode formatImportList(Node node) {
    var nodes = new ArrayList<FormatNode>();
    var allImportsWithComments = buildImportsWithComments(node.children);

    var regularImports = new ArrayList<ImportWithComments>();
    var globImports = new ArrayList<ImportWithComments>();
    for (var entry : allImportsWithComments) {
      var terminalNode = entry.importNode.findChildByType(NodeType.TERMINAL);
      var terminalText = terminalNode != null ? terminalNode.text(source) : null;
      if ("import*".equals(terminalText)) {
        globImports.add(entry);
      } else {
        regularImports.add(entry);
      }
    }

    if (!regularImports.isEmpty()) {
      formatImportListHelper(regularImports, nodes);
      if (!globImports.isEmpty()) nodes.add(TWO_NEWLINES);
    }
    if (!globImports.isEmpty()) {
      formatImportListHelper(globImports, nodes);
    }
    return new Nodes(nodes);
  }

  private void formatImportWithComments(ImportWithComments entry, List<FormatNode> nodes) {
    if (!entry.leadingAffixes.isEmpty()) {
      nodes.addAll(formatGeneric(entry.leadingAffixes, spaceOrLine()));
      nodes.add(forceLine());
    }
    nodes.add(format(entry.importNode));
    for (var affix : entry.trailingAffixes) {
      nodes.add(Space.INSTANCE);
      nodes.add(format(affix));
    }
  }

  private void formatImportListHelper(List<ImportWithComments> allImports, List<FormatNode> nodes) {
    var comparator = new ImportComparator(source);
    var absolute = new ArrayList<ImportWithComments>();
    var projects = new ArrayList<ImportWithComments>();
    var relatives = new ArrayList<ImportWithComments>();

    for (var entry : allImports) {
      var url = getImportUrl(entry.importNode);
      if (ABSOLUTE_URL_REGEX.matcher(url).matches()) {
        absolute.add(entry);
      } else if (url.startsWith("@")) {
        projects.add(entry);
      } else {
        relatives.add(entry);
      }
    }

    absolute.sort((a, b) -> comparator.compare(a.importNode, b.importNode));
    projects.sort((a, b) -> comparator.compare(a.importNode, b.importNode));
    relatives.sort((a, b) -> comparator.compare(a.importNode, b.importNode));

    var shouldNewline = false;
    if (!absolute.isEmpty()) {
      for (var i = 0; i < absolute.size(); i++) {
        if (i > 0) nodes.add(forceLine());
        formatImportWithComments(absolute.get(i), nodes);
      }
      if (!projects.isEmpty() || !relatives.isEmpty()) nodes.add(forceLine());
      shouldNewline = true;
    }
    if (!projects.isEmpty()) {
      if (shouldNewline) nodes.add(forceLine());
      for (var i = 0; i < projects.size(); i++) {
        if (i > 0) nodes.add(forceLine());
        formatImportWithComments(projects.get(i), nodes);
      }
      if (!relatives.isEmpty()) nodes.add(forceLine());
      shouldNewline = true;
    }
    if (!relatives.isEmpty()) {
      if (shouldNewline) nodes.add(forceLine());
      for (var i = 0; i < relatives.size(); i++) {
        if (i > 0) nodes.add(forceLine());
        formatImportWithComments(relatives.get(i), nodes);
      }
    }
  }

  // --- formatGeneric overloads ---

  private List<FormatNode> formatGeneric(List<Node> children, FormatNode separator) {
    return formatGeneric(children, (prev, next) -> separator);
  }

  private List<FormatNode> formatGeneric(
      List<Node> children, BiFunction<Node, Node, FormatNode> separatorFn) {
    return formatGenericWithGen(children, separatorFn, null);
  }

  private List<FormatNode> formatGenericWithGen(
      List<Node> children, FormatNode separator, BiFunction<Node, Node, FormatNode> generatorFn) {
    return formatGenericWithGen(children, (prev, next) -> separator, generatorFn);
  }

  private List<FormatNode> formatGenericWithGen(
      List<Node> children,
      BiFunction<Node, Node, FormatNode> separatorFn,
      BiFunction<Node, Node, FormatNode> generatorFn) {
    // skip semicolons
    var filtered = new ArrayList<Node>(children.size());
    for (var child : children) {
      if (!isSemicolon(child)) filtered.add(child);
    }
    children = filtered;

    // short circuit
    if (children.isEmpty()) return List.of();
    if (children.size() == 1) return List.of(format(children.get(0)));

    var nodes = new ArrayList<FormatNode>();
    var prev = children.get(0);
    for (var i = 1; i < children.size(); i++) {
      var child = children.get(i);
      nodes.add(generatorFn != null ? generatorFn.apply(prev, child) : format(prev));
      var separator = getSeparator(prev, child, separatorFn);
      if (separator != null) nodes.add(separator);
      prev = child;
    }
    nodes.add(
        generatorFn != null
            ? generatorFn.apply(children.get(children.size() - 1), null)
            : format(children.get(children.size() - 1)));
    return nodes;
  }

  private boolean isSemicolon(Node node) {
    return node.type.isAffix() && text(node).equals(";");
  }

  /** Groups all non prefixes (comments, doc comments, annotations) of this node together. */
  private List<FormatNode> groupNonPrefixes(Node node, Function<List<Node>, FormatNode> groupFn) {
    var children = node.children;
    var index = -1;
    for (var i = 0; i < children.size(); i++) {
      var child = children.get(i);
      if (!child.type.isAffix()
          && child.type != NodeType.DOC_COMMENT
          && child.type != NodeType.ANNOTATION) {
        index = i;
        break;
      }
    }
    if (index <= 0) {
      // no prefixes
      return List.of(groupFn.apply(children));
    }
    var prefixes = children.subList(0, index);
    var rest = children.subList(index, children.size());
    var res = new ArrayList<>(formatGeneric(prefixes, spaceOrLine()));
    res.add(getSeparator(prefixes.get(prefixes.size() - 1), rest.get(0), spaceOrLine()));
    res.add(groupFn.apply(rest));
    return res;
  }

  private String getImportUrl(Node node) {
    var strChars = node.findChildByType(NodeType.STRING_CHARS);
    assert strChars != null;
    var txt = strChars.text(source);
    return txt.substring(1, txt.length() - 1);
  }

  private FormatNode getSeparator(Node prev, Node next, FormatNode separator) {
    var base = getBaseSeparator(prev, next);
    return base != null ? base : separator;
  }

  private FormatNode getSeparator(
      Node prev, Node next, BiFunction<Node, Node, FormatNode> separatorFn) {
    var base = getBaseSeparator(prev, next);
    return base != null ? base : separatorFn.apply(prev, next);
  }

  private FormatNode getBaseSeparator(Node prev, Node next) {
    if (endsInLineComment(prev)) {
      return linesBetween(prev, next) > 1 ? TWO_NEWLINES : mustForceLine();
    }
    if (hasTrailingAffix(prev, next)) return Space.INSTANCE;
    if (prev.type == NodeType.DOC_COMMENT) return mustForceLine();
    if (prev.type == NodeType.ANNOTATION) return forceLine();
    if (FORCE_LINE_AFFIXES.contains(prev.type) || next.type.isAffix()) {
      return linesBetween(prev, next) > 1 ? TWO_NEWLINES : mustForceLine();
    }
    if (prev.type == NodeType.BLOCK_COMMENT) {
      return linesBetween(prev, next) > 0 ? forceSpaceyLine() : Space.INSTANCE;
    }
    if (EMPTY_SUFFIXES.contains(next.type)
        || isTerminal(prev, "[", "!", "@", "[[")
        || isTerminal(next, "]", "?", ",")) {
      return Empty.INSTANCE;
    }
    if (isTerminal(prev, "class", "function", "new")
        || isTerminal(next, "=", "{", "->", "class", "function")
        || next.type == NodeType.OBJECT_BODY
        || prev.type == NodeType.MODIFIER_LIST) {
      return Space.INSTANCE;
    }
    if (next.type == NodeType.DOC_COMMENT) return TWO_NEWLINES;
    return null;
  }

  private static boolean endsInLineComment(Node node) {
    while (true) {
      if (node.type == NodeType.LINE_COMMENT) return true;
      if (node.children.isEmpty()) return false;
      node = node.children.get(node.children.size() - 1);
    }
  }

  private FormatNode line() {
    return noNewlines ? Empty.INSTANCE : Line.INSTANCE;
  }

  private FormatNode spaceOrLine() {
    return noNewlines ? Space.INSTANCE : SpaceOrLine.INSTANCE;
  }

  private FormatNode mustForceLine() {
    if (noNewlines) {
      // should never happen; we do not set `noNewlines` for interpolation blocks that span multiple
      // lines
      throw new RuntimeException("Tried to render Pkl code as single line");
    }
    return ForceLine.INSTANCE;
  }

  private FormatNode forceLine() {
    return noNewlines ? Empty.INSTANCE : ForceLine.INSTANCE;
  }

  private FormatNode forceSpaceyLine() {
    return noNewlines ? Space.INSTANCE : ForceLine.INSTANCE;
  }

  private FormatNode ifWrap(int id, FormatNode ifWrap, FormatNode ifNotWrap) {
    return noNewlines ? ifNotWrap : new IfWrap(id, ifWrap, ifNotWrap);
  }

  private static boolean hasTrailingAffix(Node node, Node next) {
    var n = next;
    while (n != null) {
      if (n.type.isAffix() && node.span.lineEnd() == n.span.lineBegin()) return true;
      n = n.children.isEmpty() ? null : n.children.get(0);
    }
    return false;
  }

  private int modifierPrecedence(Node modifier) {
    var txt = modifier.text(source);
    return switch (txt) {
      case "abstract", "open" -> 0;
      case "external" -> 1;
      case "local", "hidden" -> 2;
      case "fixed", "const" -> 3;
      default -> throw new RuntimeException("Unknown modifier `" + txt + "`");
    };
  }

  private static boolean isSameLineExpr(Node node) {
    return SAME_LINE_EXPRS.contains(node.type);
  }

  @SuppressWarnings("unchecked")
  private static List<Node>[] splitPrefixes(List<Node> nodes) {
    var splitPoint = 0;
    for (var i = 0; i < nodes.size(); i++) {
      if (!nodes.get(i).type.isAffix() && nodes.get(i).type != NodeType.DOC_COMMENT) {
        splitPoint = i;
        break;
      }
    }
    return new List[] {nodes.subList(0, splitPoint), nodes.subList(splitPoint, nodes.size())};
  }

  private List<FormatNode> indentAfterFirstNewline(List<FormatNode> nodes, boolean group) {
    var index = -1;
    for (var i = 0; i < nodes.size(); i++) {
      var n = nodes.get(i);
      if (n instanceof SpaceOrLine || n instanceof ForceLine || n instanceof Line) {
        index = i;
        break;
      }
    }
    if (index <= 0) return nodes;
    FormatNode indented;
    if (group) {
      indented = group(new Indent(nodes.subList(index, nodes.size())));
    } else {
      indented = new Indent(nodes.subList(index, nodes.size()));
    }
    var result = new ArrayList<>(nodes.subList(0, index));
    result.add(indented);
    return result;
  }

  private FormatNode groupOnSpace(List<FormatNode> fnodes) {
    var res = new ArrayList<FormatNode>();
    for (var i = 0; i < fnodes.size(); i++) {
      var node = fnodes.get(i);
      if (i > 0 && (node instanceof SpaceOrLine || node instanceof Space)) {
        res.add(groupOnSpace(fnodes.subList(i, fnodes.size())));
        break;
      } else {
        res.add(node);
      }
    }
    return new Group(newId(), res);
  }

  /** Flatten binary operators by precedence */
  private List<Node> flattenBinaryOperatorExprs(Node node) {
    Node opNode = null;
    for (var child : node.children) {
      if (child.type == NodeType.OPERATOR) {
        opNode = child;
        break;
      }
    }
    assert opNode != null;
    var op = text(opNode);
    return flattenBinaryOperatorExprs(node, Operator.byName(op).getPrec());
  }

  private List<Node> flattenBinaryOperatorExprs(Node node, int prec) {
    Node opNode = null;
    for (var child : node.children) {
      if (child.type == NodeType.OPERATOR) {
        opNode = child;
        break;
      }
    }
    assert opNode != null;
    if (prec != Operator.byName(text(opNode)).getPrec()) return List.of(node);
    var result = new ArrayList<Node>();
    for (var child : node.children) {
      if (child.type == NodeType.BINARY_OP_EXPR) {
        result.addAll(flattenBinaryOperatorExprs(child, prec));
      } else {
        result.add(child);
      }
    }
    return result;
  }

  private static int linesBetween(Node prev, Node next) {
    return next.span.lineBegin() - prev.span.lineEnd();
  }

  private String text(Node node) {
    return node.text(source);
  }

  private boolean isTerminal(Node node, String... texts) {
    if (node.type != NodeType.TERMINAL) return false;
    var t = node.text(source);
    for (var text : texts) {
      if (t.equals(text)) return true;
    }
    return false;
  }

  private boolean isTerminalSingle(Node node, String text) {
    return node.type == NodeType.TERMINAL && node.text(source).equals(text);
  }

  private int newId() {
    return id++;
  }

  private static Nodes nodes(FormatNode... nodes) {
    return new Nodes(List.of(nodes));
  }

  private Group group(FormatNode... nodes) {
    return new Group(newId(), List.of(nodes));
  }

  private static Indent indent(FormatNode... nodes) {
    return new Indent(List.of(nodes));
  }

  private static Node firstProperChild(Node node) {
    for (var child : node.children) {
      if (isProper(child)) return child;
    }
    return null;
  }

  private static Node lastProperNode(List<Node> nodes) {
    for (var i = nodes.size() - 1; i >= 0; i--) {
      if (isProper(nodes.get(i))) return nodes.get(i);
    }
    return null;
  }

  // returns true if this node is not an affix or terminal
  private static boolean isProper(Node node) {
    return !node.type.isAffix() && node.type != NodeType.TERMINAL;
  }

  private static boolean isMultiline(Node node) {
    return node.span.lineBegin() < node.span.lineEnd();
  }

  private static boolean isMultilineList(List<Node> nodes) {
    if (nodes.isEmpty()) return false;
    return nodes.get(0).span.lineBegin() < nodes.get(nodes.size() - 1).span.lineEnd();
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T>[] splitOn(List<T> list, Predicate<T> pred) {
    var index = -1;
    for (var i = 0; i < list.size(); i++) {
      if (pred.test(list.get(i))) {
        index = i;
        break;
      }
    }
    if (index == -1) {
      return new List[] {list, List.of()};
    }
    return new List[] {list.subList(0, index), list.subList(index, list.size())};
  }

  @SafeVarargs
  private static <T> List<T> concat(List<T>... lists) {
    var result = new ArrayList<T>();
    for (var list : lists) {
      result.addAll(list);
    }
    return result;
  }

  private static <T> List<T> concat(List<T> a, T elem, List<T> b) {
    var result = new ArrayList<T>(a.size() + 1 + b.size());
    result.addAll(a);
    result.add(elem);
    result.addAll(b);
    return result;
  }

  static final class PeekableIterator<T> implements Iterator<T> {
    private final Iterator<T> iterator;
    private T peeked;
    private boolean hasPeeked = false;

    PeekableIterator(Iterator<T> iterator) {
      this.iterator = iterator;
    }

    @Override
    public T next() {
      if (hasPeeked) {
        hasPeeked = false;
        return peeked;
      }
      return iterator.next();
    }

    @Override
    public boolean hasNext() {
      return hasPeeked || iterator.hasNext();
    }

    T peek() {
      if (!hasNext()) throw new NoSuchElementException();
      if (hasPeeked) return peeked;
      peeked = iterator.next();
      hasPeeked = true;
      return peeked;
    }

    List<T> takeUntilBefore(Predicate<T> predicate) {
      var result = new ArrayList<T>();
      while (true) {
        if (!hasNext() || predicate.test(peek())) {
          return result;
        }
        result.add(next());
      }
    }
  }

  private static final Pattern ABSOLUTE_URL_REGEX = Pattern.compile("\\w+:.*");

  private static final Nodes TWO_NEWLINES =
      new Nodes(List.of(ForceLine.INSTANCE, ForceLine.INSTANCE));

  private static final EnumSet<NodeType> FORCE_LINE_AFFIXES =
      EnumSet.of(
          NodeType.DOC_COMMENT_LINE, NodeType.LINE_COMMENT, NodeType.SEMICOLON, NodeType.SHEBANG);

  private static final EnumSet<NodeType> EMPTY_SUFFIXES =
      EnumSet.of(
          NodeType.TYPE_ARGUMENT_LIST,
          NodeType.TYPE_ANNOTATION,
          NodeType.TYPE_PARAMETER_LIST,
          NodeType.PARAMETER_LIST);

  private static final EnumSet<NodeType> SAME_LINE_EXPRS =
      EnumSet.of(NodeType.NEW_EXPR, NodeType.AMENDS_EXPR, NodeType.FUNCTION_LITERAL_EXPR);

  private static final class ImportComparator implements java.util.Comparator<Node> {
    private final char[] source;

    ImportComparator(char[] source) {
      this.source = source;
    }

    @Override
    public int compare(Node o1, Node o2) {
      var import1 = o1.findChildByType(NodeType.STRING_CHARS);
      var import2 = o2.findChildByType(NodeType.STRING_CHARS);
      if (import1 == null || import2 == null) {
        // should never happen
        throw new RuntimeException("ImportComparator: not an import");
      }
      return new NaturalOrderComparator(true).compare(import1.text(source), import2.text(source));
    }
  }
}
