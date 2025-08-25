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
import org.pkl.formatter.ast.Empty
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.ForceWrap
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
import org.pkl.parser.syntax.generic.GenNode
import org.pkl.parser.syntax.generic.NodeType

class Builder(sourceText: String) {
  private var id: Int = 0
  private val source: CharArray = sourceText.toCharArray()
  private var prevNode: GenNode? = null

  fun format(node: GenNode): FormatNode {
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
      NodeType.STRING_CONSTANT,
      NodeType.STRING_ESCAPE,
      NodeType.SINGLE_LINE_STRING_LITERAL_EXPR,
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
      NodeType.STRING_NEWLINE -> ForceLine
      NodeType.MODULE_DECLARATION -> formatModuleDeclaration(node)
      NodeType.MODULE_DEFINITION -> formatModuleDefinition(node)
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
      NodeType.TYPE_PARAMETER -> Group(newId(), formatGeneric(node.children, SpaceOrLine))
      NodeType.PARAMETER -> formatParameter(node)
      NodeType.EXTENDS_CLAUSE,
      NodeType.AMENDS_CLAUSE -> formatAmendsExtendsClause(node)
      NodeType.IMPORT_LIST -> formatImportList(node)
      NodeType.IMPORT -> formatImport(node)
      NodeType.IMPORT_ALIAS -> Group(newId(), formatGeneric(node.children, SpaceOrLine))
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

  private fun formatModule(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.linesBetween(next) > 1) TWO_NEWLINES else ForceLine
      }
    return Nodes(nodes)
  }

  private fun formatModuleDeclaration(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, TWO_NEWLINES))
  }

  private fun formatModuleDefinition(node: GenNode): FormatNode {
    val (prefixes, nodes) = splitPrefixes(node.children)
    val fnodes =
      formatGenericWithGen(nodes, SpaceOrLine) { node, next ->
        if (next == null) {
          indent(format(node))
        } else {
          format(node)
        }
      }
    val res = Group(newId(), fnodes)
    return if (prefixes.isEmpty()) {
      res
    } else {
      val sep = getSeparator(prefixes.last(), nodes.first())
      Nodes(formatGeneric(prefixes, SpaceOrLine) + listOf(sep, res))
    }
  }

  private fun formatDocComment(node: GenNode): FormatNode {
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

  private fun formatQualifiedIdentifier(node: GenNode): FormatNode {
    // short circuit
    if (node.children.size == 1) return format(node.children[0])

    val first = listOf(format(node.children[0]), Line)
    val nodes =
      formatGeneric(node.children.drop(1)) { n1, n2 ->
        if (n1.type == NodeType.TERMINAL) null else Line
      }
    return Group(newId(), first + listOf(Indent(nodes)))
  }

  private fun formatUnqualifiedAccessExpression(node: GenNode): FormatNode {
    val children = node.children
    if (children.size == 1) return format(children[0])
    val firstNode = children[0]
    return if (firstNode.text() == "Map") {
      val nodes = mutableListOf<FormatNode>()
      nodes += format(firstNode)
      nodes += formatArgumentList(children[1], twoBy2 = true)
      Nodes(nodes)
    } else {
      Nodes(formatGeneric(children, null))
    }
  }

  private fun formatAmendsExtendsClause(node: GenNode): FormatNode {
    val prefix = formatGeneric(node.children.dropLast(1), SpaceOrLine)
    // string constant
    val suffix = Indent(listOf(format(node.children.last())))
    return Group(newId(), prefix + listOf(SpaceOrLine) + suffix)
  }

  private fun formatImport(node: GenNode): FormatNode {
    return Group(
      newId(),
      formatGenericWithGen(node.children, SpaceOrLine) { node, next ->
        if (node.isTerminal("import")) format(node) else indent(format(node))
      },
    )
  }

  private fun formatAnnotation(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatTypealias(node: GenNode): FormatNode {
    val nodes =
      groupNonPrefixes(node) { children -> Group(newId(), formatGeneric(children, SpaceOrLine)) }
    return Nodes(nodes)
  }

  private fun formatTypealiasHeader(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, Space))
  }

  private fun formatTypealiasBody(node: GenNode): FormatNode {
    return Indent(formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClass(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClassHeader(node: GenNode): FormatNode {
    return groupOnSpace(formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClassHeaderExtends(node: GenNode): FormatNode {
    return indent(Group(newId(), formatGeneric(node.children, SpaceOrLine)))
  }

  private fun formatClassBody(node: GenNode): FormatNode {
    val children = node.children
    if (children.size == 2) {
      // no members
      return Nodes(formatGeneric(children, null))
    }
    return Group(newId(), formatGeneric(children, ForceLine))
  }

  private fun formatClassBodyElements(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        val lineDiff = prev.linesBetween(next)
        if (lineDiff > 1 || lineDiff == 0) TWO_NEWLINES else ForceLine
      }
    return Indent(nodes)
  }

  private fun formatClassProperty(node: GenNode): FormatNode {
    val sameLine =
      node.children
        .lastOrNull { it.isExpressionOrPropertyBody() }
        ?.let {
          if (it.type.isExpression) isSameLineExpr(it) else isSameLineExpr(it.children.last())
        } ?: false
    val nodes =
      groupNonPrefixes(node) { children ->
        val nodes =
          formatGenericWithGen(children, { prev, next -> if (sameLine) Space else SpaceOrLine }) {
            node,
            next ->
            if ((node.isExpressionOrPropertyBody()) && !sameLine) {
              indent(format(node))
            } else format(node)
          }
        groupOnSpace(nodes)
      }
    return Nodes(nodes)
  }

  private fun GenNode.isExpressionOrPropertyBody(): Boolean =
    type.isExpression ||
      type == NodeType.CLASS_PROPERTY_BODY ||
      type == NodeType.OBJECT_PROPERTY_BODY

  private fun formatClassPropertyHeader(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClassPropertyHeaderBegin(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClassPropertyBody(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, null))
  }

  private fun formatClassMethod(node: GenNode): FormatNode {
    val prefixes = mutableListOf<FormatNode>()
    val nodes =
      if (node.children[0].type == NodeType.CLASS_METHOD_HEADER) node.children
      else {
        val idx = node.children.indexOfFirst { it.type == NodeType.CLASS_METHOD_HEADER }
        val prefixNodes = node.children.subList(0, idx)
        prefixes += formatGeneric(prefixNodes, null)
        prefixes += getSeparator(prefixNodes.last(), node.children[idx], ForceLine)
        node.children.subList(idx, node.children.size)
      }
    val expr = node.children.last().children[0]
    val noWrapNodes = formatGeneric(nodes, Space)
    val shouldIndent = !isSameLineExpr(expr)
    val wrapNodes =
      formatGenericWithGen(
          nodes,
          { prev, next -> if (shouldIndent) SpaceOrLine else Space },
          { node, next ->
            if (node.type == NodeType.PARAMETER_LIST) {
              formatParameterList(node, force = true)
            } else if (next == null && shouldIndent) {
              indent(format(node))
            } else {
              format(node)
            }
          },
        )
        .groupAfter { it is Text && it.text == "=" }
    val groupId = newId()
    val group = Group(groupId, listOf(IfWrap(groupId, Nodes(wrapNodes), Nodes(noWrapNodes))))
    return if (prefixes.isEmpty()) group else Nodes(prefixes + group)
  }

  private fun formatClassMethodHeader(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, Space)
    return Group(newId(), nodes)
  }

  private fun formatClassMethodBody(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, null))
  }

  private fun formatParameter(node: GenNode): FormatNode {
    if (node.children.size == 1) return format(node.children[0]) // underscore
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatParameterList(node: GenNode, force: Boolean = false): FormatNode {
    if (node.children.size == 2) return Text("()")
    val groupId = newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) {
          if (next.isTerminal(")")) {
            // trailing comma
            IfWrap(groupId, nodes(Text(","), Line), Line)
          } else Line
        } else SpaceOrLine
      }
    return if (force) ForceWrap(groupId, nodes) else Group(groupId, nodes)
  }

  private fun formatArgumentList(node: GenNode, twoBy2: Boolean = false): FormatNode {
    if (node.children.size == 2) return Text("()")
    val arg0 = node.children.find { it.type == NodeType.ARGUMENT_LIST_ELEMENTS }?.properChildren()
    val isSingleFunctionArg =
      arg0 != null && arg0.size == 1 && arg0[0].type == NodeType.FUNCTION_LITERAL_EXPR
    val groupId = newId()
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (prev.isTerminal("(") || next.isTerminal(")")) {
            val node = if (isSingleFunctionArg) Empty else Line
            if (next.isTerminal(")") && !isSingleFunctionArg) {
              // trailing comma
              IfWrap(groupId, nodes(Text(","), node), node)
            } else node
          } else SpaceOrLine
        },
      ) { node, next ->
        if (node.type == NodeType.ARGUMENT_LIST_ELEMENTS) {
          formatArgumentListElements(node, shouldIndent = !isSingleFunctionArg, twoBy2 = twoBy2)
        } else format(node)
      }
    return Group(groupId, nodes)
  }

  private fun formatArgumentListElements(
    node: GenNode,
    shouldIndent: Boolean = true,
    twoBy2: Boolean = false,
  ): FormatNode {
    val children = node.children
    val nodes =
      if (twoBy2) {
        val pairs = pairArguments(children)
        formatGenericWithGen(pairs, SpaceOrLine) { node, next ->
          if (node.type == NodeType.ARGUMENT_LIST_ELEMENTS) {
            Group(newId(), formatGeneric(node.children, SpaceOrLine))
          } else {
            format(node)
          }
        }
      } else {
        formatGeneric(node.children, SpaceOrLine)
      }
    return if (shouldIndent) Indent(nodes) else Nodes(nodes)
  }

  private fun pairArguments(nodes: List<GenNode>): List<GenNode> {
    val res = mutableListOf<GenNode>()
    var tmp = mutableListOf<GenNode>()
    var commas = 0
    for (node in nodes) {
      if (node.isTerminal(",")) {
        commas++
        if (commas == 2) {
          res += GenNode(NodeType.ARGUMENT_LIST_ELEMENTS, tmp)
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
      res += GenNode(NodeType.ARGUMENT_LIST_ELEMENTS, tmp)
    }
    return res
  }

  private fun formatParameterListElements(node: GenNode): FormatNode {
    return Indent(formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatTypeParameterList(node: GenNode): FormatNode {
    if (node.children.size == 2) return Text("<>")
    val id = newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("<") || next.isTerminal(">")) {
          if (next.isTerminal(">")) {
            // trailing comma
            IfWrap(id, nodes(Text(","), Line), Line)
          } else Line
        } else SpaceOrLine
      }
    return Group(id, nodes)
  }

  private fun formatObjectParameterList(node: GenNode): FormatNode {
    // object param lists don't have trailing commas, as they have a trailing ->
    val groupId = newId()
    val nonWrappingNodes = Nodes(formatGeneric(node.children, SpaceOrLine))
    // double indent the params if they wrap
    val wrappingNodes = indent(Indent(listOf(Line) + nonWrappingNodes))
    return Group(groupId, listOf(IfWrap(groupId, wrappingNodes, nodes(Space, nonWrappingNodes))))
  }

  private fun formatObjectBody(node: GenNode): FormatNode {
    if (node.children.size == 2) return Text("{}")
    val groupId = newId()
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (next.type == NodeType.OBJECT_PARAMETER_LIST) Empty
          else if (prev.isTerminal("{") || next.isTerminal("}")) {
            val lines = prev.linesBetween(next)
            if (lines == 0) SpaceOrLine else ForceLine
          } else SpaceOrLine
        },
      ) { node, next ->
        if (node.type == NodeType.OBJECT_MEMBER_LIST) {
          formatObjectMemberList(node, groupId)
        } else format(node)
      }
    return Group(groupId, nodes)
  }

  private fun formatObjectMemberList(node: GenNode, groupId: Int): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        val lines = prev.linesBetween(next)
        when (lines) {
          0 -> IfWrap(groupId, Line, Text("; "))
          1 -> ForceLine
          else -> TWO_NEWLINES
        }
      }
    return Indent(nodes)
  }

  private fun formatObjectEntryHeader(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatForGenerator(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (
          prev.type == NodeType.FOR_GENERATOR_HEADER || next.type == NodeType.FOR_GENERATOR_HEADER
        ) {
          Space
        } else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatForGeneratorHeader(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) Line else null
      }
    return Group(newId(), nodes)
  }

  private fun formatForGeneratorHeaderDefinition(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (next.type in SAME_LINE_EXPRS) Space else SpaceOrLine },
      ) { node, next ->
        if (node.type.isExpression && node.type !in SAME_LINE_EXPRS) indent(format(node))
        else format(node)
      }
    return indent(Group(newId(), nodes))
  }

  private fun formatForGeneratorHeaderDefinitionHeader(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, SpaceOrLine)
    return Group(newId(), nodes)
  }

  private fun formatWhenGenerator(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (
          prev.type == NodeType.WHEN_GENERATOR_HEADER ||
            prev.isTerminal("when", "else") ||
            next.isTerminal("else")
        ) {
          Space
        } else {
          SpaceOrLine
        }
      }
    return Group(newId(), nodes)
  }

  private fun formatWhenGeneratorHeader(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine },
      ) { node, next ->
        if (!node.type.isAffix && node.type != NodeType.TERMINAL) {
          indent(format(node))
        } else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatMemberPredicate(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(node.children, SpaceOrLine) { node, next ->
        if (next == null && node.type != NodeType.OBJECT_BODY) {
          indent(format(node))
        } else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatMultilineString(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, null)
    return MultilineStringGroup(node.children.last().span.colBegin, nodes)
  }

  private fun formatIf(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (next.type == NodeType.IF_ELSE_EXPR && next.children[0].type == NodeType.IF_EXPR) {
          Space
        } else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatIfHeader(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (next.type == NodeType.IF_CONDITION) Space else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatIfCondition(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatIfThen(node: GenNode): FormatNode {
    return Indent(formatGeneric(node.children, null))
  }

  private fun formatIfElse(node: GenNode): FormatNode {
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

  private fun formatNewExpr(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, SpaceOrLine)
    return Group(newId(), nodes)
  }

  private fun formatNewHeader(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, SpaceOrLine)
    return Group(newId(), nodes)
  }

  private fun formatParenthesizedExpr(node: GenNode): FormatNode {
    if (node.children.size == 2) return Text("()")
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine },
      ) { node, next ->
        if (node.type.isExpression) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatParenthesizedExprElements(node: GenNode): FormatNode {
    return indent(Group(newId(), formatGeneric(node.children, null)))
  }

  private fun formatFunctionLiteralExpr(node: GenNode): FormatNode {
    val (params, rest) = node.children.splitOn { it.isTerminal("->") }
    val sameLine =
      node.children
        .last { it.type == NodeType.FUNCTION_LITERAL_BODY }
        .let { body ->
          val expr = body.children.find { it.type.isExpression }!!
          isSameLineExpr(expr)
        }
    val sep = if (sameLine) Space else SpaceOrLine
    val bodySep = getSeparator(params.last(), rest.first(), sep)

    val nodes = formatGeneric(params, sep)
    val restNodes = listOf(bodySep) + formatGeneric(rest, sep)
    return Group(newId(), nodes + listOf(Group(newId(), restNodes)))
  }

  private fun formatFunctionLiteralBody(node: GenNode): FormatNode {
    val expr = node.children.find { it.type.isExpression }!!
    val nodes = formatGeneric(node.children, null)
    return if (isSameLineExpr(expr)) Group(newId(), nodes) else Indent(nodes)
  }

  private fun formatLetExpr(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (next.type == NodeType.LET_PARAMETER_DEFINITION) Space else SpaceOrLine },
      ) { node, next ->
        if (next == null) {
          if (node.type == NodeType.LET_EXPR) {
            // unpack the lets
            val group = formatLetExpr(node) as Group
            Nodes(group.nodes)
          } else indent(format(node))
        } else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatBinaryOpExpr(node: GenNode): FormatNode {
    val flat = flattenBinaryOperatorExprs(node)
    val callChainSize = flat.count { it.isOperator(".", "?.") }
    val hasLambda = callChainSize > 1 && flat.any { hasFunctionLiteral(it, 2) }
    val nodes =
      formatGeneric(flat) { prev, next ->
        if (prev.type == NodeType.OPERATOR) {
          when (prev.text()) {
            ".",
            "?." -> null
            "-" -> SpaceOrLine
            else -> Space
          }
        } else if (next.type == NodeType.OPERATOR) {
          when (next.text()) {
            ".",
            "?." -> if (hasLambda) ForceLine else Line
            "-" -> Space
            else -> SpaceOrLine
          }
        } else SpaceOrLine
      }
    val shouldGroup = node.children.size == flat.size
    return Group(newId(), indentAfterFirstNewline(nodes, shouldGroup))
  }

  private fun hasFunctionLiteral(node: GenNode, depth: Int): Boolean {
    if (node.type == NodeType.FUNCTION_LITERAL_EXPR) return true
    for (child in node.children) {
      if (child.type == NodeType.FUNCTION_LITERAL_EXPR) return true
      if (depth > 0 && hasFunctionLiteral(child, depth - 1)) return true
    }
    return false
  }

  private fun formatLetParameterDefinition(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatLetParameter(node: GenNode): FormatNode {
    return indent(formatClassProperty(node))
  }

  private fun formatSubscriptExpr(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, null))
  }

  private fun formatTraceThrowReadExpr(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (prev.isTerminal("(") || next.isTerminal(")")) Line else null },
      ) { node, next ->
        if (node.type.isExpression) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatDeclaredType(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatConstrainedType(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (next.type == NodeType.CONSTRAINED_TYPE_CONSTRAINT) null else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatUnionType(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        when {
          next.isTerminal("|") -> SpaceOrLine
          prev.isTerminal("|") -> Space
          else -> null
        }
      }
    return Group(newId(), indentAfterFirstNewline(nodes))
  }

  private fun formatFunctionType(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine },
      ) { node, next ->
        if (next == null) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatParenthesizedType(node: GenNode): FormatNode {
    if (node.children.size == 2) return Text("()")
    val groupId = newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine
      }
    return Group(groupId, nodes)
  }

  private fun formatParenthesizedTypeElements(node: GenNode): FormatNode {
    return indent(Group(newId(), formatGeneric(node.children, SpaceOrLine)))
  }

  private fun formatTypeAnnotation(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, Space))
  }

  private fun formatModifierList(node: GenNode): FormatNode {
    val nodes = mutableListOf<FormatNode>()
    val children = node.children.groupBy { it.type.isAffix }
    if (children[true] != null) {
      nodes += formatGeneric(children[true]!!, SpaceOrLine)
    }
    val modifiers = children[false]!!.sortedBy(::modifierPrecedence)
    nodes += formatGeneric(modifiers, Space)
    return Nodes(nodes)
  }

  private fun formatImportList(node: GenNode): FormatNode {
    val nodes = mutableListOf<FormatNode>()
    val children = node.children.groupBy { it.type.isAffix }
    if (children[true] != null) {
      nodes += formatGeneric(children[true]!!, SpaceOrLine)
      nodes += ForceLine
    }
    val imports =
      children[false]!!.groupBy { imp ->
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
        if (i > 0) nodes += ForceLine
        nodes += format(imp)
      }
      if (projects != null || relatives != null) nodes += ForceLine
      shouldNewline = true
    }

    if (projects != null) {
      if (shouldNewline) nodes += ForceLine
      for ((i, imp) in projects.withIndex()) {
        if (i > 0) nodes += ForceLine
        nodes += format(imp)
      }
      if (relatives != null) nodes += ForceLine
      shouldNewline = true
    }

    if (relatives != null) {
      if (shouldNewline) nodes += ForceLine
      for ((i, imp) in relatives.withIndex()) {
        if (i > 0) nodes += ForceLine
        nodes += format(imp)
      }
    }
    return Nodes(nodes)
  }

  private fun formatGeneric(children: List<GenNode>, separator: FormatNode?): List<FormatNode> {
    return formatGeneric(children) { n1, n2 -> separator }
  }

  private fun formatGeneric(
    children: List<GenNode>,
    separatorFn: (GenNode, GenNode) -> FormatNode?,
  ): List<FormatNode> {
    return formatGenericWithGen(children, separatorFn, null)
  }

  private fun formatGenericWithGen(
    children: List<GenNode>,
    separator: FormatNode?,
    generatorFn: ((GenNode, GenNode?) -> FormatNode)?,
  ): List<FormatNode> {
    return formatGenericWithGen(children, { n1, n2 -> separator }, generatorFn)
  }

  private fun formatGenericWithGen(
    children: List<GenNode>,
    separatorFn: (GenNode, GenNode) -> FormatNode?,
    generatorFn: ((GenNode, GenNode?) -> FormatNode)?,
  ): List<FormatNode> {
    // skip semicolons
    val children = children.filter { !it.isSemicolon() }
    // short circuit
    if (children.isEmpty()) return listOf(SpaceOrLine)
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

  private fun GenNode.isSemicolon(): Boolean = type.isAffix && text() == ";"

  /** Groups all non prefixes (comments, doc comments, annotations) of this node together. */
  private fun groupNonPrefixes(
    node: GenNode,
    groupFn: (List<GenNode>) -> FormatNode,
  ): List<FormatNode> {
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
    res += formatGeneric(prefixes, SpaceOrLine)
    res += getSeparator(prefixes.last(), nodes.first())
    res += groupFn(nodes)
    return res
  }

  private fun getImportUrl(node: GenNode): String =
    node.findChildByType(NodeType.STRING_CONSTANT)!!.text().drop(1).dropLast(1)

  private fun getSeparator(
    prev: GenNode,
    next: GenNode,
    separator: FormatNode = SpaceOrLine,
  ): FormatNode {
    return getSeparator(prev, next) { x, y -> separator }!!
  }

  private fun getSeparator(
    prev: GenNode,
    next: GenNode,
    separatorFn: (GenNode, GenNode) -> FormatNode?,
  ): FormatNode? {
    return when {
      prevNode?.type == NodeType.LINE_COMMENT -> {
        if (prev.linesBetween(next) > 1) {
          TWO_NEWLINES
        } else {
          ForceLine
        }
      }
      hasTrailingAffix(prev, next) -> Space
      prev.type == NodeType.DOC_COMMENT || prev.type == NodeType.ANNOTATION -> ForceLine
      prev.type in FORCE_LINE_AFFIXES || next.type.isAffix -> {
        if (prev.linesBetween(next) > 1) {
          TWO_NEWLINES
        } else {
          ForceLine
        }
      }
      prev.type == NodeType.BLOCK_COMMENT -> if (prev.linesBetween(next) > 0) ForceLine else Space
      next.type in EMPTY_SUFFIXES ||
        prev.isTerminal("[", "!", "@", "[[") ||
        next.isTerminal("]", "?", ",") -> null
      prev.isTerminal("class", "function", "new") ||
        next.isTerminal("=", "{", "->", "class", "function") ||
        next.type == NodeType.OBJECT_BODY ||
        prev.type == NodeType.MODIFIER_LIST -> Space
      next.type == NodeType.DOC_COMMENT -> TWO_NEWLINES
      else -> separatorFn(prev, next)
    }
  }

  private fun hasTrailingAffix(node: GenNode, next: GenNode): Boolean {
    if (node.span.lineEnd < next.span.lineBegin) return false
    var n: GenNode? = next
    while (n != null) {
      if (n.type.isAffix && node.span.lineEnd == n.span.lineBegin) return true
      n = n.children.getOrNull(0)
    }
    return false
  }

  private fun modifierPrecedence(modifier: GenNode): Int {
    val text = modifier.text()
    return when (text) {
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

  private fun isSameLineExpr(node: GenNode): Boolean {
    return node.type in SAME_LINE_EXPRS
  }

  private fun splitPrefixes(nodes: List<GenNode>): Pair<List<GenNode>, List<GenNode>> {
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
  private fun flattenBinaryOperatorExprs(node: GenNode): List<GenNode> {
    val op = node.children.first { it.type == NodeType.OPERATOR }.text()
    return flattenBinaryOperatorExprs(node, Operator.byName(op).prec)
  }

  private fun flattenBinaryOperatorExprs(node: GenNode, prec: Int): List<GenNode> {
    val actualOp = node.children.first { it.type == NodeType.OPERATOR }.text()
    if (prec != Operator.byName(actualOp).prec) return listOf(node)
    val res = mutableListOf<GenNode>()
    for (child in node.children) {
      if (child.type == NodeType.BINARY_OP_EXPR) {
        res += flattenBinaryOperatorExprs(child, prec)
      } else {
        res += child
      }
    }
    return res
  }

  private fun List<FormatNode>.groupAfter(pred: (FormatNode) -> Boolean): List<FormatNode> {
    val res = mutableListOf<FormatNode>()
    val grouped = mutableListOf<FormatNode>()
    var group = false
    for (node in this) {
      if (pred(node)) group = true
      if (group) grouped += node else res += node
    }
    if (grouped.isNotEmpty()) res += Group(newId(), grouped)
    return res
  }

  private fun GenNode.linesBetween(next: GenNode): Int = next.span.lineBegin - span.lineEnd

  private fun GenNode.text() = text(source)

  private fun GenNode.isTerminal(vararg texts: String): Boolean =
    type == NodeType.TERMINAL && text(source) in texts

  private fun GenNode.isOperator(vararg texts: String): Boolean =
    type == NodeType.OPERATOR && text(source) in texts

  private fun newId(): Int {
    return id++
  }

  private fun nodes(vararg nodes: FormatNode) = Nodes(nodes.toList())

  private fun group(vararg nodes: FormatNode) = Group(newId(), nodes.toList())

  private fun indent(vararg nodes: FormatNode) = Indent(nodes.toList())

  private class ImportComparator(private val source: CharArray) : Comparator<GenNode> {
    override fun compare(o1: GenNode, o2: GenNode): Int {
      val import1 = o1.findChildByType(NodeType.STRING_CONSTANT)?.text(source)
      val import2 = o2.findChildByType(NodeType.STRING_CONSTANT)?.text(source)
      if (import1 == null || import2 == null) {
        // should never happen
        throw RuntimeException("ImportComparator: not an import")
      }

      return NaturalOrderComparator(ignoreCase = true).compare(import1, import2)
    }
  }

  // returns children that are not comments or punctuation
  private fun GenNode.properChildren(): List<GenNode> =
    children.filter { !it.type.isAffix && it.type != NodeType.TERMINAL }

  private fun <T> List<T>.splitOn(pred: (T) -> Boolean): Pair<List<T>, List<T>> {
    val index = indexOfFirst { pred(it) }
    return if (index == -1) {
      Pair(this, emptyList())
    } else {
      Pair(take(index), drop(index))
    }
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
