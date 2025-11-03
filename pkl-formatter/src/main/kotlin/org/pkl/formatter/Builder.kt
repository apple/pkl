/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.formatter

import java.util.EnumSet
import kotlin.collections.withIndex
import org.pkl.formatter.ast.Empty
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.FormatNode
import org.pkl.formatter.ast.Group
import org.pkl.formatter.ast.IfWrap
import org.pkl.formatter.ast.Indent
import org.pkl.formatter.ast.Line
import org.pkl.formatter.ast.MultilineStringGroup
import org.pkl.formatter.ast.Nodes
import org.pkl.formatter.ast.Space
import org.pkl.formatter.ast.SpaceOrLine
import org.pkl.formatter.ast.Text
import org.pkl.parser.syntax.Operator
import org.pkl.parser.syntax.generic.Node
import org.pkl.parser.syntax.generic.NodeType

internal class Builder(sourceText: String, private val grammarVersion: GrammarVersion) {
  private var id: Int = 0
  private val source: CharArray = sourceText.toCharArray()
  private var prevNode: Node? = null
  private var noNewlines = false

  fun format(node: Node): FormatNode {
    prevNode = node
    return when (node.type) {
      NodeType.MODULE -> formatModule(node)
      NodeType.DOC_COMMENT -> Nodes(formatGeneric(node.children, null))
      NodeType.DOC_COMMENT_LINE -> formatDocComment(node)
      NodeType.LINE_COMMENT,
      NodeType.BLOCK_COMMENT,
      NodeType.TERMINAL,
      NodeType.MODIFIER,
      NodeType.IDENTIFIER,
      NodeType.STRING_CHARS,
      NodeType.STRING_ESCAPE,
      NodeType.INT_LITERAL_EXPR,
      NodeType.FLOAT_LITERAL_EXPR,
      NodeType.BOOL_LITERAL_EXPR,
      NodeType.THIS_EXPR,
      NodeType.OUTER_EXPR,
      NodeType.MODULE_EXPR,
      NodeType.NULL_EXPR,
      NodeType.MODULE_TYPE,
      NodeType.UNKNOWN_TYPE,
      NodeType.NOTHING_TYPE,
      NodeType.SHEBANG,
      NodeType.OPERATOR -> Text(node.text(source))
      NodeType.STRING_NEWLINE -> mustForceLine()
      NodeType.MODULE_DECLARATION -> formatModuleDeclaration(node)
      NodeType.MODULE_DEFINITION -> formatModuleDefinition(node)
      NodeType.SINGLE_LINE_STRING_LITERAL_EXPR -> formatSingleLineString(node)
      NodeType.MULTI_LINE_STRING_LITERAL_EXPR -> formatMultilineString(node)
      NodeType.ANNOTATION -> formatAnnotation(node)
      NodeType.TYPEALIAS -> formatTypealias(node)
      NodeType.TYPEALIAS_HEADER -> formatTypealiasHeader(node)
      NodeType.TYPEALIAS_BODY -> formatTypealiasBody(node)
      NodeType.MODIFIER_LIST -> formatModifierList(node)
      NodeType.PARAMETER_LIST -> formatParameterList(node)
      NodeType.PARAMETER_LIST_ELEMENTS -> formatParameterListElements(node)
      NodeType.TYPE_PARAMETER_LIST -> formatTypeParameterList(node)
      NodeType.TYPE_PARAMETER_LIST_ELEMENTS -> formatParameterListElements(node)
      NodeType.TYPE_PARAMETER -> Group(newId(), formatGeneric(node.children, spaceOrLine()))
      NodeType.PARAMETER -> formatParameter(node)
      NodeType.EXTENDS_CLAUSE,
      NodeType.AMENDS_CLAUSE -> formatAmendsExtendsClause(node)
      NodeType.IMPORT_LIST -> formatImportList(node)
      NodeType.IMPORT -> formatImport(node)
      NodeType.IMPORT_ALIAS -> Group(newId(), formatGeneric(node.children, spaceOrLine()))
      NodeType.CLASS -> formatClass(node)
      NodeType.CLASS_HEADER -> formatClassHeader(node)
      NodeType.CLASS_HEADER_EXTENDS -> formatClassHeaderExtends(node)
      NodeType.CLASS_BODY -> formatClassBody(node)
      NodeType.CLASS_BODY_ELEMENTS -> formatClassBodyElements(node)
      NodeType.CLASS_PROPERTY,
      NodeType.OBJECT_PROPERTY,
      NodeType.OBJECT_ENTRY -> formatClassProperty(node)
      NodeType.CLASS_PROPERTY_HEADER,
      NodeType.OBJECT_PROPERTY_HEADER -> formatClassPropertyHeader(node)
      NodeType.CLASS_PROPERTY_HEADER_BEGIN,
      NodeType.OBJECT_PROPERTY_HEADER_BEGIN -> formatClassPropertyHeaderBegin(node)
      NodeType.CLASS_PROPERTY_BODY,
      NodeType.OBJECT_PROPERTY_BODY -> formatClassPropertyBody(node)
      NodeType.CLASS_METHOD,
      NodeType.OBJECT_METHOD -> formatClassMethod(node)
      NodeType.CLASS_METHOD_HEADER -> formatClassMethodHeader(node)
      NodeType.CLASS_METHOD_BODY -> formatClassMethodBody(node)
      NodeType.OBJECT_BODY -> formatObjectBody(node)
      NodeType.OBJECT_ELEMENT -> format(node.children[0]) // has a single element
      NodeType.OBJECT_ENTRY_HEADER -> formatObjectEntryHeader(node)
      NodeType.FOR_GENERATOR -> formatForGenerator(node)
      NodeType.FOR_GENERATOR_HEADER -> formatForGeneratorHeader(node)
      NodeType.FOR_GENERATOR_HEADER_DEFINITION -> formatForGeneratorHeaderDefinition(node)
      NodeType.FOR_GENERATOR_HEADER_DEFINITION_HEADER ->
        formatForGeneratorHeaderDefinitionHeader(node)
      NodeType.WHEN_GENERATOR -> formatWhenGenerator(node)
      NodeType.WHEN_GENERATOR_HEADER -> formatWhenGeneratorHeader(node)
      NodeType.OBJECT_SPREAD -> Nodes(formatGeneric(node.children, null))
      NodeType.MEMBER_PREDICATE -> formatMemberPredicate(node)
      NodeType.QUALIFIED_IDENTIFIER -> formatQualifiedIdentifier(node)
      NodeType.ARGUMENT_LIST -> formatArgumentList(node)
      NodeType.ARGUMENT_LIST_ELEMENTS -> formatArgumentListElements(node)
      NodeType.OBJECT_PARAMETER_LIST -> formatObjectParameterList(node)
      NodeType.IF_EXPR -> formatIf(node)
      NodeType.IF_HEADER -> formatIfHeader(node)
      NodeType.IF_CONDITION -> formatIfCondition(node)
      NodeType.IF_CONDITION_EXPR -> Indent(formatGeneric(node.children, null))
      NodeType.IF_THEN_EXPR -> formatIfThen(node)
      NodeType.IF_ELSE_EXPR -> formatIfElse(node)
      NodeType.NEW_EXPR,
      NodeType.AMENDS_EXPR -> formatNewExpr(node)
      NodeType.NEW_HEADER -> formatNewHeader(node)
      NodeType.UNQUALIFIED_ACCESS_EXPR -> formatUnqualifiedAccessExpression(node)
      NodeType.QUALIFIED_ACCESS_EXPR -> formatQualifiedAccessExpression(node)
      NodeType.BINARY_OP_EXPR -> formatBinaryOpExpr(node)
      NodeType.FUNCTION_LITERAL_EXPR -> formatFunctionLiteralExpr(node)
      NodeType.FUNCTION_LITERAL_BODY -> formatFunctionLiteralBody(node)
      NodeType.SUBSCRIPT_EXPR,
      NodeType.SUPER_SUBSCRIPT_EXPR -> formatSubscriptExpr(node)
      NodeType.TRACE_EXPR -> formatTraceThrowReadExpr(node)
      NodeType.THROW_EXPR -> formatTraceThrowReadExpr(node)
      NodeType.READ_EXPR -> formatTraceThrowReadExpr(node)
      NodeType.NON_NULL_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.SUPER_ACCESS_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.PARENTHESIZED_EXPR -> formatParenthesizedExpr(node)
      NodeType.PARENTHESIZED_EXPR_ELEMENTS -> formatParenthesizedExprElements(node)
      NodeType.IMPORT_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.LET_EXPR -> formatLetExpr(node)
      NodeType.LET_PARAMETER_DEFINITION -> formatLetParameterDefinition(node)
      NodeType.LET_PARAMETER -> formatLetParameter(node)
      NodeType.UNARY_MINUS_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.LOGICAL_NOT_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.TYPE_ANNOTATION -> formatTypeAnnotation(node)
      NodeType.TYPE_ARGUMENT_LIST -> formatTypeParameterList(node)
      NodeType.TYPE_ARGUMENT_LIST_ELEMENTS -> formatParameterListElements(node)
      NodeType.DECLARED_TYPE -> formatDeclaredType(node)
      NodeType.CONSTRAINED_TYPE -> formatConstrainedType(node)
      NodeType.CONSTRAINED_TYPE_CONSTRAINT -> formatParameterList(node)
      NodeType.CONSTRAINED_TYPE_ELEMENTS -> formatParameterListElements(node)
      NodeType.NULLABLE_TYPE -> Nodes(formatGeneric(node.children, null))
      NodeType.UNION_TYPE -> formatUnionType(node)
      NodeType.FUNCTION_TYPE -> formatFunctionType(node)
      NodeType.FUNCTION_TYPE_PARAMETERS -> formatParameterList(node)
      NodeType.STRING_CONSTANT_TYPE -> format(node.children[0])
      NodeType.PARENTHESIZED_TYPE -> formatParenthesizedType(node)
      NodeType.PARENTHESIZED_TYPE_ELEMENTS -> formatParenthesizedTypeElements(node)
      else -> throw RuntimeException("Unknown node type: ${node.type}")
    }
  }

  private fun formatModule(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.linesBetween(next) > 1) TWO_NEWLINES else forceLine()
      }
    return Nodes(nodes)
  }

  private fun formatModuleDeclaration(node: Node): FormatNode {
    return Nodes(formatGeneric(node.children, TWO_NEWLINES))
  }

  private fun formatModuleDefinition(node: Node): FormatNode {
    val (prefixes, nodes) = splitPrefixes(node.children)
    val fnodes =
      formatGenericWithGen(nodes, Space) { node, _ ->
        if (node.type == NodeType.QUALIFIED_IDENTIFIER) {
          Nodes(formatGeneric(node.children, null))
        } else {
          format(node)
        }
      }
    val res = Nodes(fnodes)
    return if (prefixes.isEmpty()) {
      res
    } else {
      val sep = getSeparator(prefixes.last(), nodes.first(), spaceOrLine())
      Nodes(formatGeneric(prefixes, spaceOrLine()) + listOf(sep, res))
    }
  }

  private fun formatDocComment(node: Node): FormatNode {
    val txt = node.text()
    if (txt == "///" || txt == "/// ") return Text("///")

    var comment = txt.substring(3)
    if (comment.isStrictBlank()) return Text("///")

    if (comment.isNotEmpty() && comment[0] != ' ') comment = " $comment"
    return Text("///$comment")
  }

  private fun String.isStrictBlank(): Boolean {
    for (ch in this) {
      if (ch != ' ' && ch != '\t') return false
    }
    return true
  }

  private fun formatQualifiedIdentifier(node: Node): FormatNode {
    // short circuit
    if (node.children.size == 1) return format(node.children[0])

    val first = listOf(format(node.children[0]), line())
    val nodes =
      formatGeneric(node.children.drop(1)) { n1, _ ->
        if (n1.type == NodeType.TERMINAL) null else line()
      }
    return Group(newId(), first + listOf(Indent(nodes)))
  }

  private fun formatUnqualifiedAccessExpression(node: Node): FormatNode {
    val children = node.children
    if (children.size == 1) return format(children[0])
    val firstNode = node.firstProperChild()!!
    return if (firstNode.text() == "Map") {
      val nodes = mutableListOf<FormatNode>()
      nodes += format(firstNode)
      nodes += formatArgumentList(children[1], twoBy2 = true)
      Nodes(nodes)
    } else {
      Nodes(formatGeneric(children, null))
    }
  }

  /**
   * Special cases when formatting qualified access:
   *
   * Case 1: Dot calls followed by closing method call: wrap after the opening paren.
   *
   * ```
   * foo.bar.baz(new {
   *   qux = 1
   * })
   * ```
   *
   * Case 2: Dot calls, then method calls: group the leading access together.
   *
   * ```
   * foo.bar
   *   .baz(new {
   *     qux = 1
   *   })
   *   .baz()
   * ```
   *
   * Case 3: If there are multiple lambdas present, always force a newline.
   *
   * ```
   * foo
   *   .map((it) -> it + 1)
   *   .filter((it) -> it.isEven)
   * ```
   */
  private fun formatQualifiedAccessExpression(node: Node): FormatNode {
    var lambdaCount = 0
    var methodCallCount = 0
    var indexBeforeFirstMethodCall = 0
    val flat = mutableListOf<Node>()

    fun gatherFacts(current: Node) {
      for (child in current.children) {
        if (child.type == NodeType.QUALIFIED_ACCESS_EXPR) {
          gatherFacts(child)
        } else {
          flat.add(child)
          when {
            isMethodCall(child) -> {
              methodCallCount++
              if (hasFunctionLiteral(child, 2)) {
                lambdaCount++
              }
            }
            methodCallCount == 0 -> {
              indexBeforeFirstMethodCall = flat.lastIndex
            }
          }
        }
      }
    }

    gatherFacts(node)

    val leadingSeparator: (Node, Node) -> FormatNode? = { prev, next ->
      when {
        prev.type == NodeType.OPERATOR -> null
        next.type == NodeType.OPERATOR -> line()
        else -> spaceOrLine()
      }
    }

    val trailingSeparator: (Node, Node) -> FormatNode? = { prev, next ->
      when {
        prev.type == NodeType.OPERATOR -> null
        next.type == NodeType.OPERATOR -> if (lambdaCount > 1) forceLine() else line()
        else -> spaceOrLine()
      }
    }

    val nodes =
      when {
        methodCallCount == 1 && isMethodCall(flat.lastProperNode()!!) -> {
          // lift argument list into its own node
          val (callChain, argsList) = splitFunctionCallNode(flat)
          val leadingNodes =
            indentAfterFirstNewline(formatGeneric(callChain, leadingSeparator), true)
          val trailingNodes = formatGeneric(argsList, trailingSeparator)
          val sep = getBaseSeparator(callChain.last(), argsList.first())
          if (sep != null) {
            leadingNodes + sep + trailingNodes
          } else {
            leadingNodes + trailingNodes
          }
        }
        methodCallCount > 0 && indexBeforeFirstMethodCall > 0 -> {
          val leading = flat.subList(0, indexBeforeFirstMethodCall)
          val trailing = flat.subList(indexBeforeFirstMethodCall, flat.size)
          val leadingNodes = indentAfterFirstNewline(formatGeneric(leading, leadingSeparator), true)
          val trailingNodes = formatGeneric(trailing, trailingSeparator)
          leadingNodes + line() + trailingNodes
        }
        else -> formatGeneric(flat, trailingSeparator)
      }

    val shouldGroup = node.children.size == flat.size
    return Group(newId(), indentAfterFirstNewline(nodes, shouldGroup))
  }

  /**
   * Split a function call node to extract its identifier into the leading group. For example,
   * `foo.bar(5)` becomes: leading gets `foo.bar`, rest gets `(5)`.
   */
  private fun splitFunctionCallNode(nodes: List<Node>): Pair<List<Node>, List<Node>> {
    assert(nodes.isNotEmpty())
    val lastNode = nodes.last()
    val argListIdx = lastNode.children.indexOfFirst { it.type == NodeType.ARGUMENT_LIST }
    val leading = nodes.subList(0, nodes.lastIndex) + lastNode.children.subList(0, argListIdx)
    val trailing = lastNode.children.subList(argListIdx, lastNode.children.size)
    return leading to trailing
  }

  private fun isMethodCall(node: Node): Boolean {
    if (node.type != NodeType.UNQUALIFIED_ACCESS_EXPR) return false
    for (child in node.children) {
      if (child.type == NodeType.ARGUMENT_LIST) {
        return true
      }
    }
    return false
  }

  private fun formatAmendsExtendsClause(node: Node): FormatNode {
    val prefix = formatGeneric(node.children.dropLast(1), spaceOrLine())
    // string constant
    val suffix = Indent(listOf(format(node.children.last())))
    return Group(newId(), prefix + listOf(spaceOrLine()) + suffix)
  }

  private fun formatImport(node: Node): FormatNode {
    return Group(
      newId(),
      formatGenericWithGen(node.children, spaceOrLine()) { node, _ ->
        if (node.isTerminal("import")) format(node) else indent(format(node))
      },
    )
  }

  private fun formatAnnotation(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatTypealias(node: Node): FormatNode {
    val nodes =
      groupNonPrefixes(node) { children -> Group(newId(), formatGeneric(children, spaceOrLine())) }
    return Nodes(nodes)
  }

  private fun formatTypealiasHeader(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, Space))
  }

  private fun formatTypealiasBody(node: Node): FormatNode {
    return Indent(formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatClass(node: Node): FormatNode {
    return Nodes(formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatClassHeader(node: Node): FormatNode {
    return groupOnSpace(formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatClassHeaderExtends(node: Node): FormatNode {
    return indent(Group(newId(), formatGeneric(node.children, spaceOrLine())))
  }

  private fun formatClassBody(node: Node): FormatNode {
    val children = node.children
    if (children.size == 2) {
      // no members
      return Nodes(formatGeneric(children, null))
    }
    return Group(newId(), formatGeneric(children, forceLine()))
  }

  private fun formatClassBodyElements(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        val lineDiff = prev.linesBetween(next)
        if (lineDiff > 1 || lineDiff == 0) TWO_NEWLINES else forceLine()
      }
    return Indent(nodes)
  }

  private fun formatClassProperty(node: Node): FormatNode {
    val sameLine =
      node.children
        .lastOrNull { it.isExpressionOrPropertyBody() }
        ?.let {
          if (it.type.isExpression) isSameLineExpr(it) else isSameLineExpr(it.children.last())
        } ?: false
    val nodes =
      groupNonPrefixes(node) { children ->
        val nodes =
          formatGenericWithGen(children, { _, _ -> if (sameLine) Space else spaceOrLine() }) {
            node,
            _ ->
            if ((node.isExpressionOrPropertyBody()) && !sameLine) {
              indent(format(node))
            } else format(node)
          }
        groupOnSpace(nodes)
      }
    return Nodes(nodes)
  }

  private fun Node.isExpressionOrPropertyBody(): Boolean =
    type.isExpression ||
      type == NodeType.CLASS_PROPERTY_BODY ||
      type == NodeType.OBJECT_PROPERTY_BODY

  private fun formatClassPropertyHeader(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatClassPropertyHeaderBegin(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatClassPropertyBody(node: Node): FormatNode {
    return Nodes(formatGeneric(node.children, null))
  }

  private fun formatClassMethod(node: Node): FormatNode {
    val prefixes = mutableListOf<FormatNode>()
    val nodes =
      if (node.children[0].type == NodeType.CLASS_METHOD_HEADER) node.children
      else {
        val idx = node.children.indexOfFirst { it.type == NodeType.CLASS_METHOD_HEADER }
        val prefixNodes = node.children.subList(0, idx)
        prefixes += formatGeneric(prefixNodes, null)
        prefixes += getSeparator(prefixNodes.last(), node.children[idx], forceLine())
        node.children.subList(idx, node.children.size)
      }

    // Separate header (before =) and body (= and after)
    val bodyIdx = nodes.indexOfFirst { it.type == NodeType.CLASS_METHOD_BODY } - 1
    val header = if (bodyIdx < 0) nodes else nodes.subList(0, bodyIdx)
    val headerGroupId = newId()
    val methodGroupId = newId()
    val headerNodes =
      formatGenericWithGen(header, spaceOrLine()) { node, _ ->
        if (node.type == NodeType.PARAMETER_LIST) {
          formatParameterList(node, id = headerGroupId)
        } else {
          format(node)
        }
      }
    if (bodyIdx < 0) {
      // body is empty, return header
      return if (prefixes.isEmpty()) {
        Group(headerGroupId, headerNodes)
      } else {
        Nodes(prefixes + Group(headerGroupId, headerNodes))
      }
    }

    val bodyNodes = nodes.subList(bodyIdx, nodes.size)

    val expr = bodyNodes.last().children[0]
    val isSameLineBody = isSameLineExpr(expr)

    // Format body (= and expression)
    val bodyFormat =
      if (isSameLineBody) {
        formatGeneric(bodyNodes, Space)
      } else {
        formatGenericWithGen(bodyNodes, spaceOrLine()) { node, next ->
          if (next == null) indent(format(node)) else format(node)
        }
      }

    val headerGroup = Group(headerGroupId, headerNodes)
    val bodyGroup = Group(newId(), bodyFormat)
    val separator = getSeparator(header.last(), bodyNodes.first(), Space)
    val allNodes = Group(methodGroupId, listOf(headerGroup, separator, bodyGroup))

    return if (prefixes.isEmpty()) allNodes else Nodes(prefixes + allNodes)
  }

  private fun formatClassMethodHeader(node: Node): FormatNode {
    val nodes = formatGeneric(node.children, Space)
    return Nodes(nodes)
  }

  private fun formatClassMethodBody(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, null))
  }

  private fun formatParameter(node: Node): FormatNode {
    if (node.children.size == 1) return format(node.children[0]) // underscore
    return Group(newId(), formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatParameterList(node: Node, id: Int? = null): FormatNode {
    if (node.children.size == 2) return Text("()")
    val groupId = id ?: newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) {
          if (next.isTerminal(")")) {
            // trailing comma
            if (grammarVersion == GrammarVersion.V1) {
              line()
            } else {
              ifWrap(groupId, nodes(Text(","), line()), line())
            }
          } else line()
        } else spaceOrLine()
      }
    return if (id != null) Nodes(nodes) else Group(groupId, nodes)
  }

  private fun formatArgumentList(node: Node, twoBy2: Boolean = false): FormatNode {
    if (node.children.size == 2) return Text("()")
    val hasTrailingLambda = hasTrailingLambda(node)
    val groupId = newId()
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (prev.isTerminal("(") || next.isTerminal(")")) {
            val node = if (hasTrailingLambda) Empty else line()
            if (next.isTerminal(")") && !hasTrailingLambda) {
              // trailing comma
              if (grammarVersion == GrammarVersion.V1) {
                node
              } else {
                ifWrap(groupId, nodes(Text(","), node), node)
              }
            } else node
          } else spaceOrLine()
        },
      ) { node, _ ->
        if (node.type == NodeType.ARGUMENT_LIST_ELEMENTS) {
          formatArgumentListElements(node, hasTrailingLambda, twoBy2 = twoBy2)
        } else format(node)
      }
    return Group(groupId, nodes)
  }

  private fun formatArgumentListElements(
    node: Node,
    hasTrailingLambda: Boolean = false,
    twoBy2: Boolean = false,
  ): FormatNode {
    val children = node.children
    val shouldMultiline = shouldMultlineNodes(node) { it.isTerminal(",") }
    val sep: (Node, Node) -> FormatNode = { _, _ ->
      if (shouldMultiline) forceSpaceyLine() else spaceOrLine()
    }
    return if (twoBy2) {
      val pairs = pairArguments(children)
      val nodes =
        formatGenericWithGen(pairs, sep) { node, _ ->
          if (node.type == NodeType.ARGUMENT_LIST_ELEMENTS) {
            Group(newId(), formatGeneric(node.children, spaceOrLine()))
          } else {
            format(node)
          }
        }
      Indent(nodes)
    } else if (hasTrailingLambda) {
      // if the args have a trailing lambda, group them differently
      val splitIndex = children.indexOfLast { it.type in SAME_LINE_EXPRS }
      val normalParams = children.subList(0, splitIndex)
      val lastParam = children.subList(splitIndex, children.size)
      val trailingNode = if (endsWithClosingBracket(lastParam.last())) Empty else line()
      val lastNodes = formatGenericWithGen(lastParam, sep, null)
      if (normalParams.isEmpty()) {
        group(Group(newId(), lastNodes), trailingNode)
      } else {
        val separator = getSeparator(normalParams.last(), lastParam[0], Space)
        val paramNodes = formatGenericWithGen(normalParams, sep, null)
        group(Group(newId(), paramNodes), separator, Group(newId(), lastNodes), trailingNode)
      }
    } else {
      Indent(formatGeneric(children, sep))
    }
  }

  private fun shouldMultlineNodes(node: Node, predicate: (Node) -> Boolean): Boolean {
    for (idx in 0..<node.children.lastIndex) {
      val prev = node.children[idx]
      val next = node.children[idx + 1]
      if ((predicate(prev) || predicate(next)) && prev.linesBetween(next) > 0) {
        return true
      }
    }
    return false
  }

  private tailrec fun endsWithClosingBracket(node: Node): Boolean {
    return if (node.children.isNotEmpty()) {
      endsWithClosingBracket(node.children.last())
    } else {
      node.isTerminal("}") || node.type.isAffix
    }
  }

  /**
   * Only considered trailing lamdba if there is only one lambda/new expr/amends expr in the list.
   *
   * E.g. avoid formatting `toMap()` weirdly:
   * ```
   * foo.toMap(
   *   (it) -> makeSomeKey(it),
   *   (it) -> makeSomeValue(it),
   * )
   * ```
   */
  private fun hasTrailingLambda(argList: Node): Boolean {
    val children = argList.firstProperChild()?.children ?: return false
    var seenArg = false
    var ret = false
    for (i in children.lastIndex downTo 0) {
      val child = children[i]
      if (!child.isProper()) continue
      if (child.type in SAME_LINE_EXPRS) {
        if (seenArg) {
          return false
        } else {
          seenArg = true
          ret = true
        }
      }
    }
    return ret
  }

  private fun pairArguments(nodes: List<Node>): List<Node> {
    val res = mutableListOf<Node>()
    var tmp = mutableListOf<Node>()
    var commas = 0
    for (node in nodes) {
      if (node.isTerminal(",")) {
        commas++
        if (commas == 2) {
          res += Node(NodeType.ARGUMENT_LIST_ELEMENTS, tmp)
          res += node
          commas = 0
          tmp = mutableListOf()
        } else {
          tmp += node
        }
      } else {
        tmp += node
      }
    }
    if (tmp.isNotEmpty()) {
      res += Node(NodeType.ARGUMENT_LIST_ELEMENTS, tmp)
    }
    return res
  }

  private fun formatParameterListElements(node: Node): FormatNode {
    return Indent(formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatTypeParameterList(node: Node): FormatNode {
    if (node.children.size == 2) return Text("<>")
    val id = newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("<") || next.isTerminal(">")) {
          if (next.isTerminal(">")) {
            // trailing comma
            if (grammarVersion == GrammarVersion.V1) {
              Line
            } else {
              ifWrap(id, nodes(Text(","), line()), line())
            }
          } else line()
        } else spaceOrLine()
      }
    return Group(id, nodes)
  }

  private fun formatObjectParameterList(node: Node): FormatNode {
    // object param lists don't have trailing commas, as they have a trailing ->
    val groupId = newId()
    val nonWrappingNodes = Nodes(formatGeneric(node.children, spaceOrLine()))
    // double indent the params if they wrap
    val wrappingNodes = indent(Indent(listOf(line()) + nonWrappingNodes))
    return Group(groupId, listOf(ifWrap(groupId, wrappingNodes, nodes(Space, nonWrappingNodes))))
  }

  private fun formatObjectBody(node: Node): FormatNode {
    if (node.children.size == 2) return Text("{}")
    val groupId = newId()
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (next.type == NodeType.OBJECT_PARAMETER_LIST) Empty
          else if (prev.isTerminal("{") || next.isTerminal("}")) {
            val lines = prev.linesBetween(next)
            if (lines == 0) spaceOrLine() else forceSpaceyLine()
          } else spaceOrLine()
        },
      ) { node, _ ->
        if (node.type == NodeType.OBJECT_MEMBER_LIST) {
          formatObjectMemberList(node, groupId)
        } else format(node)
      }
    return Group(groupId, nodes)
  }

  private fun formatObjectMemberList(node: Node, groupId: Int): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        val lines = prev.linesBetween(next)
        when (lines) {
          0 -> ifWrap(groupId, line(), Text("; "))
          1 -> forceLine()
          else -> TWO_NEWLINES
        }
      }
    return Indent(nodes)
  }

  private fun formatObjectEntryHeader(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatForGenerator(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (
          prev.type == NodeType.FOR_GENERATOR_HEADER || next.type == NodeType.FOR_GENERATOR_HEADER
        ) {
          Space
        } else spaceOrLine()
      }
    return Group(newId(), nodes)
  }

  private fun formatForGeneratorHeader(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) line() else null
      }
    return Group(newId(), nodes)
  }

  private fun formatForGeneratorHeaderDefinition(node: Node): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { _, next -> if (next.type in SAME_LINE_EXPRS) Space else spaceOrLine() },
      ) { node, _ ->
        if (node.type.isExpression && node.type !in SAME_LINE_EXPRS) indent(format(node))
        else format(node)
      }
    return indent(Group(newId(), nodes))
  }

  private fun formatForGeneratorHeaderDefinitionHeader(node: Node): FormatNode {
    val nodes = formatGeneric(node.children, spaceOrLine())
    return Group(newId(), nodes)
  }

  private fun formatWhenGenerator(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (
          prev.type == NodeType.WHEN_GENERATOR_HEADER ||
            prev.isTerminal("when", "else") ||
            next.isTerminal("else")
        ) {
          Space
        } else {
          spaceOrLine()
        }
      }
    return Group(newId(), nodes)
  }

  private fun formatWhenGeneratorHeader(node: Node): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (prev.isTerminal("(") || next.isTerminal(")")) line() else spaceOrLine()
        },
      ) { node, _ ->
        if (!node.type.isAffix && node.type != NodeType.TERMINAL) {
          indent(format(node))
        } else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatMemberPredicate(node: Node): FormatNode {
    val nodes =
      formatGenericWithGen(node.children, spaceOrLine()) { node, next ->
        if (next == null && node.type != NodeType.OBJECT_BODY) {
          indent(format(node))
        } else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatStringParts(nodes: List<Node>): List<FormatNode> {
    return buildList {
      var isInStringInterpolation = false
      val cursor = nodes.iterator().peekable()
      var prev: Node? = null
      while (cursor.hasNext()) {
        if (isInStringInterpolation) {
          val prevNoNewlines = noNewlines
          val elems = cursor.takeUntilBefore { it.isTerminal(")") }
          noNewlines = !elems.isMultiline()
          getBaseSeparator(prev!!, elems.first())?.let { add(it) }
          val formatted = formatGeneric(elems, null)
          addAll(formatted)
          getBaseSeparator(elems.last(), cursor.peek())?.let { add(it) }
          noNewlines = prevNoNewlines
          isInStringInterpolation = false
          continue
        }
        val elem = cursor.next()
        if (elem.type == NodeType.TERMINAL && elem.text().endsWith("(")) {
          isInStringInterpolation = true
        }
        add(format(elem))
        prev = elem
      }
    }
  }

  private fun formatSingleLineString(node: Node): FormatNode {
    return Group(newId(), formatStringParts(node.children))
  }

  private fun formatMultilineString(node: Node): FormatNode {
    val nodes = formatStringParts(node.children)
    return MultilineStringGroup(node.children.last().span.colBegin, nodes)
  }

  private fun formatIf(node: Node): FormatNode {
    val separator = if (node.isMultiline()) forceSpaceyLine() else spaceOrLine()
    val nodes =
      formatGeneric(node.children) { _, next ->
        // produce `else if` in the case of nested if.
        // note: don't need to handle if `next.children[0]` is an affix because that can't be
        // emitted as `else if` anyway.
        if (next.type == NodeType.IF_ELSE_EXPR && next.children[0].type == NodeType.IF_EXPR) Space
        else separator
      }
    return Group(newId(), nodes)
  }

  private fun formatIfHeader(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { _, next ->
        if (next.type == NodeType.IF_CONDITION) Space else spaceOrLine()
      }
    return Group(newId(), nodes)
  }

  private fun formatIfCondition(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) line() else spaceOrLine()
      }
    return Group(newId(), nodes)
  }

  private fun formatIfThen(node: Node): FormatNode {
    return Indent(formatGeneric(node.children, null))
  }

  private fun formatIfElse(node: Node): FormatNode {
    val children = node.children
    if (children.size == 1) {
      val expr = children[0]
      return if (expr.type == NodeType.IF_EXPR) {
        // unpack the group
        val group = formatIf(expr) as Group
        Nodes(group.nodes)
      } else {
        indent(format(expr))
      }
    }
    return Indent(formatGeneric(node.children, null))
  }

  private fun formatNewExpr(node: Node): FormatNode {
    val nodes = formatGeneric(node.children, spaceOrLine())
    return Group(newId(), nodes)
  }

  private fun formatNewHeader(node: Node): FormatNode {
    val nodes = formatGeneric(node.children, spaceOrLine())
    return Group(newId(), nodes)
  }

  private fun formatParenthesizedExpr(node: Node): FormatNode {
    if (node.children.size == 2) return Text("()")
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (prev.isTerminal("(") || next.isTerminal(")")) line() else spaceOrLine()
        },
      ) { node, _ ->
        if (node.type.isExpression) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatParenthesizedExprElements(node: Node): FormatNode {
    return indent(Group(newId(), formatGeneric(node.children, null)))
  }

  private fun formatFunctionLiteralExpr(node: Node): FormatNode {
    val (params, rest) = node.children.splitOn { it.isTerminal("->") }
    val sameLine =
      node.children
        .last { it.type == NodeType.FUNCTION_LITERAL_BODY }
        .let { body ->
          val expr = body.children.find { it.type.isExpression }!!
          isSameLineExpr(expr)
        }
    val sep = if (sameLine) Space else spaceOrLine()
    val bodySep = getSeparator(params.last(), rest.first(), sep)

    val nodes = formatGeneric(params, sep)
    val restNodes = listOf(bodySep) + formatGeneric(rest, sep)
    return Group(newId(), nodes + listOf(Group(newId(), restNodes)))
  }

  private fun formatFunctionLiteralBody(node: Node): FormatNode {
    val expr = node.children.find { it.type.isExpression }!!
    val nodes = formatGeneric(node.children, null)
    return if (isSameLineExpr(expr)) Group(newId(), nodes) else Indent(nodes)
  }

  private fun formatLetExpr(node: Node): FormatNode {
    val separator = if (node.isMultiline()) forceSpaceyLine() else spaceOrLine()
    val endsWithLet = node.children.last().type == NodeType.LET_EXPR
    val nodes =
      formatGenericWithGen(
        node.children,
        { _, next -> if (next.type == NodeType.LET_PARAMETER_DEFINITION) Space else separator },
      ) { node, _ ->
        when {
          node.type == NodeType.LET_EXPR -> {
            // unpack the lets
            val group = formatLetExpr(node) as Group
            Nodes(group.nodes)
          }
          endsWithLet -> format(node)
          node.type.isExpression || node.type.isAffix -> indent(format(node))
          else -> format(node)
        }
      }
    return Group(newId(), nodes)
  }

  private fun formatLetParameterDefinition(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) line() else spaceOrLine()
      }
    return Group(newId(), nodes)
  }

  private fun formatLetParameter(node: Node): FormatNode {
    return indent(formatClassProperty(node))
  }

  private fun formatBinaryOpExpr(node: Node): FormatNode {
    val flat = flattenBinaryOperatorExprs(node)
    val shouldMultiline = shouldMultlineNodes(node) { it.type == NodeType.OPERATOR }
    val nodes =
      formatGeneric(flat) { prev, next ->
        val sep = if (shouldMultiline) forceSpaceyLine() else spaceOrLine()
        if (prev.type == NodeType.OPERATOR) {
          when (prev.text()) {
            "-" -> sep
            else -> Space
          }
        } else if (next.type == NodeType.OPERATOR) {
          when (next.text()) {
            "-" -> Space
            else -> sep
          }
        } else sep
      }

    val shouldGroup = node.children.size == flat.size
    return Group(newId(), indentAfterFirstNewline(nodes, shouldGroup))
  }

  private fun hasFunctionLiteral(node: Node, depth: Int): Boolean {
    if (node.type == NodeType.FUNCTION_LITERAL_EXPR) return true
    for (child in node.children) {
      if (child.type == NodeType.FUNCTION_LITERAL_EXPR) return true
      if (depth > 0 && hasFunctionLiteral(child, depth - 1)) return true
    }
    return false
  }

  private fun formatSubscriptExpr(node: Node): FormatNode {
    return Nodes(formatGeneric(node.children, null))
  }

  private fun formatTraceThrowReadExpr(node: Node): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (prev.isTerminal("(") || next.isTerminal(")")) line() else null },
      ) { node, _ ->
        if (node.type.isExpression) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatDeclaredType(node: Node): FormatNode {
    return Nodes(formatGeneric(node.children, spaceOrLine()))
  }

  private fun formatConstrainedType(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { _, next ->
        if (next.type == NodeType.CONSTRAINED_TYPE_CONSTRAINT) null else spaceOrLine()
      }
    return Group(newId(), nodes)
  }

  private fun formatUnionType(node: Node): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        when {
          next.isTerminal("|") -> spaceOrLine()
          prev.isTerminal("|") -> Space
          else -> null
        }
      }
    return Group(newId(), indentAfterFirstNewline(nodes))
  }

  private fun formatFunctionType(node: Node): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (prev.isTerminal("(") || next.isTerminal(")")) line() else spaceOrLine()
        },
      ) { node, next ->
        if (next == null) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatParenthesizedType(node: Node): FormatNode {
    if (node.children.size == 2) return Text("()")
    val groupId = newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) line() else spaceOrLine()
      }
    return Group(groupId, nodes)
  }

  private fun formatParenthesizedTypeElements(node: Node): FormatNode {
    return indent(Group(newId(), formatGeneric(node.children, spaceOrLine())))
  }

  private fun formatTypeAnnotation(node: Node): FormatNode {
    return Group(newId(), formatGeneric(node.children, Space))
  }

  private fun formatModifierList(node: Node): FormatNode {
    val nodes = mutableListOf<FormatNode>()
    val children = node.children.groupBy { it.type.isAffix }
    if (children[true] != null) {
      nodes += formatGeneric(children[true]!!, spaceOrLine())
    }
    val modifiers = children[false]!!.sortedBy(::modifierPrecedence)
    nodes += formatGeneric(modifiers, Space)
    return Nodes(nodes)
  }

  private fun formatImportList(node: Node): FormatNode {
    val nodes = mutableListOf<FormatNode>()
    val children = node.children.groupBy { it.type.isAffix }
    if (children[true] != null) {
      nodes += formatGeneric(children[true]!!, spaceOrLine())
      nodes += forceLine()
    }

    val allImports = children[false]!!
    val imports = allImports.groupBy { it.findChildByType(NodeType.TERMINAL)?.text(source) }
    if (imports["import"] != null) {
      formatImportListHelper(imports["import"]!!, nodes)
      if (imports["import*"] != null) nodes += TWO_NEWLINES
    }
    if (imports["import*"] != null) {
      formatImportListHelper(imports["import*"]!!, nodes)
    }

    return Nodes(nodes)
  }

  private fun formatImportListHelper(allImports: List<Node>, nodes: MutableList<FormatNode>) {
    val imports =
      allImports.groupBy { imp ->
        val url = getImportUrl(imp)
        when {
          ABSOLUTE_URL_REGEX.matches(url) -> 0
          url.startsWith('@') -> 1
          else -> 2
        }
      }
    val absolute = imports[0]?.sortedWith(ImportComparator(source))
    val projects = imports[1]?.sortedWith(ImportComparator(source))
    val relatives = imports[2]?.sortedWith(ImportComparator(source))
    var shouldNewline = false

    if (absolute != null) {
      for ((i, imp) in absolute.withIndex()) {
        if (i > 0) nodes += forceLine()
        nodes += format(imp)
      }
      if (projects != null || relatives != null) nodes += forceLine()
      shouldNewline = true
    }

    if (projects != null) {
      if (shouldNewline) nodes += forceLine()
      for ((i, imp) in projects.withIndex()) {
        if (i > 0) nodes += forceLine()
        nodes += format(imp)
      }
      if (relatives != null) nodes += forceLine()
      shouldNewline = true
    }

    if (relatives != null) {
      if (shouldNewline) nodes += forceLine()
      for ((i, imp) in relatives.withIndex()) {
        if (i > 0) nodes += forceLine()
        nodes += format(imp)
      }
    }
  }

  private fun formatGeneric(children: List<Node>, separator: FormatNode?): List<FormatNode> {
    return formatGeneric(children) { _, _ -> separator }
  }

  private fun formatGeneric(
    children: List<Node>,
    separatorFn: (Node, Node) -> FormatNode?,
  ): List<FormatNode> {
    return formatGenericWithGen(children, separatorFn, null)
  }

  private fun formatGenericWithGen(
    children: List<Node>,
    separator: FormatNode?,
    generatorFn: ((Node, Node?) -> FormatNode)?,
  ): List<FormatNode> {
    return formatGenericWithGen(children, { _, _ -> separator }, generatorFn)
  }

  private fun formatGenericWithGen(
    children: List<Node>,
    separatorFn: (Node, Node) -> FormatNode?,
    generatorFn: ((Node, Node?) -> FormatNode)?,
  ): List<FormatNode> {
    // skip semicolons
    val children = children.filter { !it.isSemicolon() }
    // short circuit
    if (children.isEmpty()) return listOf(spaceOrLine())
    if (children.size == 1) return listOf(format(children[0]))

    val nodes = mutableListOf<FormatNode>()
    var prev = children[0]
    for (child in children.drop(1)) {
      nodes +=
        if (generatorFn != null) {
          generatorFn(prev, child)
        } else {
          format(prev)
        }
      val separator = getSeparator(prev, child, separatorFn)
      if (separator != null) nodes += separator
      prev = child
    }
    nodes +=
      if (generatorFn != null) {
        generatorFn(children.last(), null)
      } else {
        format(children.last())
      }
    return nodes
  }

  private fun Node.isSemicolon(): Boolean = type.isAffix && text() == ";"

  /** Groups all non prefixes (comments, doc comments, annotations) of this node together. */
  private fun groupNonPrefixes(node: Node, groupFn: (List<Node>) -> FormatNode): List<FormatNode> {
    val children = node.children
    val index =
      children.indexOfFirst {
        !it.type.isAffix && it.type != NodeType.DOC_COMMENT && it.type != NodeType.ANNOTATION
      }
    if (index <= 0) {
      // no prefixes
      return listOf(groupFn(children))
    }
    val prefixes = children.subList(0, index)
    val nodes = children.subList(index, children.size)
    val res = mutableListOf<FormatNode>()
    res += formatGeneric(prefixes, spaceOrLine())
    res += getSeparator(prefixes.last(), nodes.first(), spaceOrLine())
    res += groupFn(nodes)
    return res
  }

  private fun getImportUrl(node: Node): String =
    node.findChildByType(NodeType.STRING_CHARS)!!.text().drop(1).dropLast(1)

  private fun getSeparator(prev: Node, next: Node, separator: FormatNode): FormatNode {
    return getBaseSeparator(prev, next) ?: separator
  }

  private fun getSeparator(
    prev: Node,
    next: Node,
    separatorFn: (Node, Node) -> FormatNode?,
  ): FormatNode? {
    return getBaseSeparator(prev, next) ?: separatorFn(prev, next)
  }

  private fun getBaseSeparator(prev: Node, next: Node): FormatNode? {
    return when {
      prevNode?.type == NodeType.LINE_COMMENT -> {
        if (prev.linesBetween(next) > 1) {
          TWO_NEWLINES
        } else {
          mustForceLine()
        }
      }

      hasTrailingAffix(prev, next) -> Space
      prev.type == NodeType.DOC_COMMENT -> mustForceLine()
      prev.type == NodeType.ANNOTATION -> forceLine()
      prev.type in FORCE_LINE_AFFIXES || next.type.isAffix -> {
        if (prev.linesBetween(next) > 1) {
          TWO_NEWLINES
        } else {
          mustForceLine()
        }
      }

      prev.type == NodeType.BLOCK_COMMENT ->
        if (prev.linesBetween(next) > 0) forceSpaceyLine() else Space

      next.type in EMPTY_SUFFIXES ||
        prev.isTerminal("[", "!", "@", "[[") ||
        next.isTerminal("]", "?", ",") -> Empty

      prev.isTerminal("class", "function", "new") ||
        next.isTerminal("=", "{", "->", "class", "function") ||
        next.type == NodeType.OBJECT_BODY ||
        prev.type == NodeType.MODIFIER_LIST -> Space

      next.type == NodeType.DOC_COMMENT -> TWO_NEWLINES
      else -> null
    }
  }

  private fun line(): FormatNode {
    return if (noNewlines) Empty else Line
  }

  private fun spaceOrLine(): FormatNode {
    return if (noNewlines) Space else SpaceOrLine
  }

  private fun mustForceLine(): FormatNode {
    if (noNewlines) {
      // should never happen; we do not set `noNewlines` for interpolation blocks that span multiple
      // lines
      throw RuntimeException("Tried to render Pkl code as single line")
    }
    return ForceLine
  }

  private fun forceLine(): FormatNode {
    return if (noNewlines) Empty else ForceLine
  }

  private fun forceSpaceyLine(): FormatNode {
    return if (noNewlines) Space else ForceLine
  }

  private fun ifWrap(id: Int, ifWrap: FormatNode, ifNotWrap: FormatNode): FormatNode {
    return if (noNewlines) ifNotWrap else IfWrap(id, ifWrap, ifNotWrap)
  }

  private fun hasTrailingAffix(node: Node, next: Node): Boolean {
    var n: Node? = next
    while (n != null) {
      if (n.type.isAffix && node.span.lineEnd == n.span.lineBegin) return true
      n = n.children.getOrNull(0)
    }
    return false
  }

  private fun modifierPrecedence(modifier: Node): Int {
    return when (val text = modifier.text()) {
      "abstract",
      "open" -> 0
      "external" -> 1
      "local",
      "hidden" -> 2
      "fixed",
      "const" -> 3
      else -> throw RuntimeException("Unknown modifier `$text`")
    }
  }

  private fun isSameLineExpr(node: Node): Boolean {
    return node.type in SAME_LINE_EXPRS
  }

  private fun splitPrefixes(nodes: List<Node>): Pair<List<Node>, List<Node>> {
    val splitPoint = nodes.indexOfFirst { !it.type.isAffix && it.type != NodeType.DOC_COMMENT }
    return nodes.subList(0, splitPoint) to nodes.subList(splitPoint, nodes.size)
  }

  private fun indentAfterFirstNewline(
    nodes: List<FormatNode>,
    group: Boolean = false,
  ): List<FormatNode> {
    val index = nodes.indexOfFirst { it is SpaceOrLine || it is ForceLine || it is Line }
    if (index <= 0) return nodes
    val indented =
      if (group) {
        group(Indent(nodes.subList(index, nodes.size)))
      } else {
        Indent(nodes.subList(index, nodes.size))
      }

    return nodes.subList(0, index) + listOf(indented)
  }

  private fun groupOnSpace(fnodes: List<FormatNode>): FormatNode {
    val res = mutableListOf<FormatNode>()
    for ((i, node) in fnodes.withIndex()) {
      if (i > 0 && (node is SpaceOrLine || node is Space)) {
        res += groupOnSpace(fnodes.subList(i, fnodes.size))
        break
      } else {
        res += node
      }
    }
    return Group(newId(), res)
  }

  /** Flatten binary operators by precedence */
  private fun flattenBinaryOperatorExprs(node: Node): List<Node> {
    val op = node.children.first { it.type == NodeType.OPERATOR }.text()
    return flattenBinaryOperatorExprs(node, Operator.byName(op).prec)
  }

  private fun flattenBinaryOperatorExprs(node: Node, prec: Int): List<Node> {
    val actualOp = node.children.first { it.type == NodeType.OPERATOR }.text()
    if (prec != Operator.byName(actualOp).prec) return listOf(node)
    return buildList {
      for (child in node.children) {
        if (child.type == NodeType.BINARY_OP_EXPR) {
          addAll(flattenBinaryOperatorExprs(child, prec))
        } else {
          add(child)
        }
      }
    }
  }

  private fun Node.linesBetween(next: Node): Int = next.span.lineBegin - span.lineEnd

  private fun Node.text() = text(source)

  private fun Node.isTerminal(vararg texts: String): Boolean =
    type == NodeType.TERMINAL && text(source) in texts

  private fun newId(): Int {
    return id++
  }

  private fun nodes(vararg nodes: FormatNode) = Nodes(nodes.toList())

  private fun group(vararg nodes: FormatNode) = Group(newId(), nodes.toList())

  private fun indent(vararg nodes: FormatNode) = Indent(nodes.toList())

  private class ImportComparator(private val source: CharArray) : Comparator<Node> {
    override fun compare(o1: Node, o2: Node): Int {
      val import1 = o1.findChildByType(NodeType.STRING_CHARS)?.text(source)
      val import2 = o2.findChildByType(NodeType.STRING_CHARS)?.text(source)
      if (import1 == null || import2 == null) {
        // should never happen
        throw RuntimeException("ImportComparator: not an import")
      }

      return NaturalOrderComparator(ignoreCase = true).compare(import1, import2)
    }
  }

  private fun Node.firstProperChild(): Node? {
    for (child in children) {
      if (child.isProper()) return child
    }
    return null
  }

  private fun List<Node>.lastProperNode(): Node? {
    for (i in lastIndex downTo 0) {
      if (this[i].isProper()) return this[i]
    }
    return null
  }

  // returns true if this node is not an affix or terminal
  private fun Node.isProper(): Boolean = !type.isAffix && type != NodeType.TERMINAL

  private fun Node.isMultiline(): Boolean = span.lineBegin < span.lineEnd

  private fun List<Node>.isMultiline(): Boolean =
    if (isEmpty()) false else first().span.lineBegin < last().span.lineEnd

  private inline fun <T> List<T>.splitOn(pred: (T) -> Boolean): Pair<List<T>, List<T>> {
    val index = indexOfFirst { pred(it) }
    return if (index == -1) {
      Pair(this, emptyList())
    } else {
      Pair(take(index), drop(index))
    }
  }

  class PeekableIterator<T>(private val iterator: Iterator<T>) : Iterator<T> {
    private var peek: T? = null

    private var hasPeek = false

    override fun next(): T {
      return if (hasPeek) {
        hasPeek = false
        peek!!
      } else {
        iterator.next()
      }
    }

    override fun hasNext(): Boolean {
      return if (hasPeek) true else iterator.hasNext()
    }

    fun peek(): T {
      if (!hasNext()) {
        throw NoSuchElementException()
      }
      if (hasPeek) {
        return peek!!
      }
      peek = iterator.next()
      hasPeek = true
      return peek!!
    }

    inline fun takeUntilBefore(predicate: (T) -> Boolean): List<T> {
      return buildList {
        while (true) {
          if (!hasNext() || predicate(peek())) {
            return@buildList
          }
          add(next())
        }
      }
    }
  }

  private fun <T> Iterator<T>.peekable(): PeekableIterator<T> {
    return PeekableIterator(this)
  }

  companion object {
    private val ABSOLUTE_URL_REGEX = Regex("""\w+:.*""")

    private val TWO_NEWLINES = Nodes(listOf(ForceLine, ForceLine))

    private val FORCE_LINE_AFFIXES =
      EnumSet.of(
        NodeType.DOC_COMMENT_LINE,
        NodeType.LINE_COMMENT,
        NodeType.SEMICOLON,
        NodeType.SHEBANG,
      )

    private val EMPTY_SUFFIXES =
      EnumSet.of(
        NodeType.TYPE_ARGUMENT_LIST,
        NodeType.TYPE_ANNOTATION,
        NodeType.TYPE_PARAMETER_LIST,
        NodeType.PARAMETER_LIST,
      )

    private val SAME_LINE_EXPRS =
      EnumSet.of(NodeType.NEW_EXPR, NodeType.AMENDS_EXPR, NodeType.FUNCTION_LITERAL_EXPR)
  }
}
