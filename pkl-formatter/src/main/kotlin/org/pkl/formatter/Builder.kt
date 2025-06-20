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
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.FormatNode
import org.pkl.formatter.ast.Group
import org.pkl.formatter.ast.IfWrap
import org.pkl.formatter.ast.Indent
import org.pkl.formatter.ast.IndentFreeLine
import org.pkl.formatter.ast.Line
import org.pkl.formatter.ast.Nodes
import org.pkl.formatter.ast.Space
import org.pkl.formatter.ast.SpaceOrLine
import org.pkl.formatter.ast.Text
import org.pkl.parser.syntax.generic.GenNode
import org.pkl.parser.syntax.generic.NodeType

class Builder(sourceText: String) {
  private var id: Int = 0
  private val source: CharArray = sourceText.toCharArray()

  fun format(node: GenNode): FormatNode =
    when (node.type) {
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
      NodeType.OPERATOR -> Text(node.text())
      NodeType.STRING_NEWLINE -> IndentFreeLine
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
      NodeType.CLASS_BODY -> formatClassBody(node)
      NodeType.CLASS_BODY_ELEMENTS -> formatClassBodyElements(node)
      NodeType.CLASS_PROPERTY,
      NodeType.OBJECT_PROPERTY,
      NodeType.OBJECT_ENTRY -> formatClassProperty(node)
      NodeType.CLASS_PROPERTY_HEADER,
      NodeType.OBJECT_PROPERTY_HEADER -> formatClassPropertyHeader(node)
      NodeType.CLASS_METHOD,
      NodeType.OBJECT_METHOD -> formatClassMethod(node)
      NodeType.CLASS_METHOD_HEADER -> formatClassMethodHeader(node)
      NodeType.CLASS_METHOD_BODY -> formatClassMethodBody(node)
      NodeType.OBJECT_BODY -> formatObjectBody(node)
      NodeType.OBJECT_ELEMENT -> format(node.children[0]) // has a single element
      NodeType.OBJECT_ENTRY_HEADER -> formatObjectEntryHeader(node)
      NodeType.FOR_GENERATOR -> formatForGenerator(node)
      NodeType.FOR_GENERATOR_HEADER -> formatParameterList(node)
      NodeType.WHEN_GENERATOR -> formatWhenGenerator(node)
      NodeType.WHEN_GENERATOR_HEADER -> formatWhenGeneratorHeader(node)
      NodeType.OBJECT_SPREAD -> Nodes(formatGeneric(node.children, null))
      NodeType.MEMBER_PREDICATE -> formatMemberPredicate(node)
      NodeType.QUALIFIED_IDENTIFIER -> formatQualifiedIdentifier(node)
      NodeType.ARGUMENT_LIST -> formatParameterList(node)
      NodeType.ARGUMENT_LIST_ELEMENTS -> formatParameterListElements(node)
      NodeType.OBJECT_PARAMETER_LIST -> formatObjectParameterList(node)
      NodeType.IF_EXPR -> formatIf(node)
      NodeType.IF_HEADER -> formatIfHeader(node)
      NodeType.IF_CONDITION -> formatParameterList(node)
      NodeType.IF_THEN_EXPR -> formatIfThen(node)
      NodeType.IF_ELSE_EXPR -> formatIfElse(node)
      NodeType.NEW_EXPR -> formatNewExpr(node)
      NodeType.NEW_HEADER -> Group(newId(), formatGeneric(node.children, SpaceOrLine))
      NodeType.UNQUALIFIED_ACCESS_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.BINARY_OP_EXPR -> formatBinaryOpExpr(node)
      NodeType.FUNCTION_LITERAL_EXPR -> formatFunctionLiteralExpr(node)
      NodeType.SUBSCRIPT_EXPR,
      NodeType.SUPER_SUBSCRIPT_EXPR -> formatSubscriptExpr(node)
      NodeType.TRACE_EXPR -> formatTraceThrowReadExpr(node)
      NodeType.THROW_EXPR -> formatTraceThrowReadExpr(node)
      NodeType.READ_EXPR -> formatTraceThrowReadExpr(node)
      NodeType.NON_NULL_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.SUPER_ACCESS_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.PARENTHESIZED_EXPR -> Group(newId(), formatGeneric(node.children, null))
      NodeType.IMPORT_EXPR -> Nodes(formatGeneric(node.children, null))
      NodeType.LET_EXPR -> formatLetExpr(node)
      NodeType.LET_PARAMETER_DEFINITION -> formatLetParameterDefinition(node)
      NodeType.LET_PARAMETER -> formatLetParameter(node)
      NodeType.AMENDS_EXPR -> Group(newId(), formatGeneric(node.children, SpaceOrLine))
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
      NodeType.STRING_CONSTANT_TYPE -> format(node.children[0])
      NodeType.PARENTHESIZED_TYPE -> Group(newId(), formatGeneric(node.children, null))
      else -> throw RuntimeException("Unknown node type: ${node.type}")
    }

  private fun formatModule(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, nodes(ForceLine, ForceLine)))
  }

  private fun formatModuleDeclaration(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, ForceLine))
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
    val index = txt.indexOfFirst { it != '/' }
    if (index <= 0) return Text("///")
    var comment = txt.substring(index)
    if (!comment.startsWith(" ")) comment = " $comment"
    return Text("///$comment")
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
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
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

  private fun formatClassBody(node: GenNode): FormatNode {
    val children = node.children
    if (children.size == 2) {
      // no members
      return Nodes(formatGeneric(children, EMPTY_NODE))
    }
    return Group(newId(), formatGeneric(children, ForceLine))
  }

  private fun formatClassBodyElements(node: GenNode): FormatNode {
    return Indent(formatGeneric(node.children, Nodes(listOf(ForceLine, ForceLine))))
  }

  private fun formatClassProperty(node: GenNode): FormatNode {
    val shouldIndent =
      node.children.last().type !in
        EnumSet.of(NodeType.OBJECT_BODY, NodeType.NEW_EXPR, NodeType.CLASS_PROPERTY_HEADER)
    val nodes =
      groupNonPrefixes(node) { children ->
        val nodes =
          formatGenericWithGen(
            children,
            { prev, next -> if (next.type == NodeType.NEW_EXPR) Space else SpaceOrLine },
          ) { node, next ->
            if (next == null && shouldIndent) indent(format(node)) else format(node)
          }
        groupOnSpace(nodes)
      }
    return Nodes(nodes)
  }

  private fun formatClassPropertyHeader(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, SpaceOrLine)
    return Group(newId(), nodes)
  }

  private fun formatClassMethod(node: GenNode): FormatNode {
    val expr = node.children.last().children[0]
    val shouldIndent = expr.type != NodeType.NEW_EXPR
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (next.type == NodeType.CLASS_METHOD_BODY && !shouldIndent) Space else SpaceOrLine
        },
      ) { node, next ->
        if (next == null && shouldIndent) indent(format(node)) else format(node)
      }
    return group(groupOnSpace(nodes))
  }

  private fun formatClassMethodHeader(node: GenNode): FormatNode {
    val nodes = formatGeneric(node.children, SpaceOrLine)
    return Group(newId(), nodes)
  }

  private fun formatClassMethodBody(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, null))
  }

  private fun formatParameter(node: GenNode): FormatNode {
    if (node.children.size == 1) return format(node.children[0]) // underscore
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatParameterList(node: GenNode): FormatNode {
    if (node.children.size == 2) return Text("()")
    val id = newId()
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine
      }
    return Group(id, nodes)
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
          Line
        } else SpaceOrLine
      }
    return Group(id, nodes)
  }

  private fun formatObjectParameterList(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatObjectBody(node: GenNode): FormatNode {
    if (node.children.size == 2) return Text("{}")
    val groupId = newId()
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next ->
          if (prev.isTerminal("{") || next.isTerminal("}")) {
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
          else -> Nodes(listOf(ForceLine, ForceLine))
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
        if (prev.type == NodeType.FOR_GENERATOR_HEADER) Space else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatWhenGenerator(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.type == NodeType.WHEN_GENERATOR_HEADER || prev.isTerminal("when", "else")) {
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
    val endWithNewline = endWithNewline(node.children)
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (
          (prev.isTerminal("\"\"\"") && next.type != NodeType.STRING_NEWLINE) ||
            (next.isTerminal("\"\"\"") && !endWithNewline)
        ) {
          ForceLine
        } else null
      }
    return Group(newId(), nodes)
  }

  private fun endWithNewline(nodes: List<GenNode>): Boolean {
    val rev = nodes.reversed().drop(1)
    for (child in rev) {
      if (child.type == NodeType.STRING_NEWLINE) return true
      if (child.type != NodeType.STRING_CONSTANT) return false
      if (!child.text().isBlank()) return false
    }
    return false
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
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
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
    val header =
      Group(newId(), formatGeneric(node.children.dropLast(1), SpaceOrLine) + listOf(SpaceOrLine))
    val body = format(node.children.last())
    return group(header, body)
  }

  private fun formatFunctionLiteralExpr(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(node.children, SpaceOrLine) { node, next ->
        if (next == null) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatLetExpr(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (next.type == NodeType.LET_PARAMETER_DEFINITION) Space else SpaceOrLine },
      ) { node, next ->
        if (next == null && node.type == NodeType.LET_EXPR) {
          // unpack the lets
          val group = formatLetExpr(node) as Group
          Nodes(group.nodes)
        } else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatBinaryOpExpr(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(flattenBinaryOperatorExprs(node)) { prev, next ->
        when {
          prev.isOperator(".", "?.") -> null
          next.isOperator(".", "?.") -> Line
          (prev.type == NodeType.OPERATOR && prev.text() != "-") || next.isOperator("-") -> Space
          else -> SpaceOrLine
        }
      }
    return Group(newId(), indentAfterFirstNewline(nodes))
  }

  private fun formatLetParameterDefinition(node: GenNode): FormatNode {
    val nodes =
      formatGeneric(node.children) { prev, next ->
        if (prev.isTerminal("(") || next.isTerminal(")")) Line else SpaceOrLine
      }
    return Group(newId(), nodes)
  }

  private fun formatLetParameter(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(node.children, SpaceOrLine) { node, next ->
        if (next == null) indent(format(node)) else format(node)
      }
    return indent(Group(newId(), nodes))
  }

  private fun formatSubscriptExpr(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, EMPTY_NODE))
  }
  
  private fun formatTraceThrowReadExpr(node: GenNode): FormatNode {
    val nodes = formatGenericWithGen(node.children, { prev, next ->
      if (prev.isTerminal("(") || next.isTerminal(")")) Line else null
    }) { node, next ->
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
      formatGeneric(node.children) { prev, next -> if (next.isTerminal("|")) Line else EMPTY_NODE }
    return Group(newId(), nodes)
  }

  private fun formatFunctionType(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        { prev, next -> if (prev.isTerminal("(") || next.isTerminal(")")) null else SpaceOrLine },
      ) { node, next ->
        if (next == null) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
  }

  private fun formatTypeAnnotation(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(node.children, SpaceOrLine) { node, next ->
        if (next == null) indent(format(node)) else format(node)
      }
    return Group(newId(), nodes)
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
    val absolute = imports[0]?.sortedBy { it.findChildByType(NodeType.STRING_CONSTANT)!!.text() }
    val projects = imports[1]?.sortedBy { it.findChildByType(NodeType.STRING_CONSTANT)!!.text() }
    val relatives = imports[2]?.sortedBy { it.findChildByType(NodeType.STRING_CONSTANT)!!.text() }
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

  private fun groupNonPrefixes(
    node: GenNode,
    groupFn: (List<GenNode>) -> FormatNode,
  ): List<FormatNode> {
    val children = node.children
    val index = children.indexOfFirst { !it.type.isAffix && it.type != NodeType.DOC_COMMENT }
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
      hasTraillingAffix(prev, next) -> Space
      prev.type == NodeType.DOC_COMMENT || prev.type == NodeType.ANNOTATION -> ForceLine
      prev.type in FORCE_LINE_AFFIXES || next.type.isAffix -> {
        if (prev.linesBetween(next) > 1) {
          nodes(ForceLine, ForceLine)
        } else {
          ForceLine
        }
      }
      prev.type == NodeType.BLOCK_COMMENT -> if (prev.linesBetween(next) > 0) ForceLine else Space
      next.type in EMPTY_SUFFIXES ||
        prev.isTerminal("[", "!", "@", "[[") ||
        next.isTerminal("]", "?", ",") -> null
      next.isTerminal("=", "{", "->") || next.type == NodeType.OBJECT_BODY -> Space
      next.type == NodeType.DOC_COMMENT -> nodes(ForceLine, ForceLine)
      else -> separatorFn(prev, next)
    }
  }

  private fun hasTraillingAffix(node: GenNode, next: GenNode): Boolean {
    return next.type.isAffix && node.span.lineEnd == next.span.lineBegin
  }

  private fun modifierPrecedence(modifier: GenNode): Int {
    val text = modifier.text()
    return when (text) {
      "external" -> 0
      "fixed",
      "const" -> 1
      "local",
      "hidden" -> 2
      "abstract",
      "open" -> 3
      else -> throw RuntimeException("Unknown modifier `$text`")
    }
  }

  private fun splitPrefixes(nodes: List<GenNode>): Pair<List<GenNode>, List<GenNode>> {
    val splitPoint = nodes.indexOfFirst { !it.type.isAffix && it.type != NodeType.DOC_COMMENT }
    return nodes.subList(0, splitPoint) to nodes.subList(splitPoint, nodes.size)
  }

  private fun indentAfterFirstNewline(nodes: List<FormatNode>): List<FormatNode> {
    val index = nodes.indexOfFirst { it is SpaceOrLine || it is ForceLine || it is Line }
    if (index <= 0) return nodes
    return nodes.subList(0, index) + listOf(Indent(nodes.subList(index, nodes.size)))
  }

  private fun groupOnSpace(fnodes: List<FormatNode>): FormatNode {
    val res = mutableListOf<FormatNode>()
    for ((i, node) in fnodes.withIndex()) {
      if (i > 0 && node is SpaceOrLine) {
        res += groupOnSpace(fnodes.subList(i, fnodes.size))
        break
      } else {
        res += node
      }
    }
    return Group(newId(), res)
  }

  private fun flattenBinaryOperatorExprs(node: GenNode): List<GenNode> {
    val res = mutableListOf<GenNode>()
    for (child in node.children) {
      if (child.type == NodeType.BINARY_OP_EXPR) {
        res += flattenBinaryOperatorExprs(child)
      } else {
        res += child
      }
    }
    return res
  }

  private fun GenNode.linesBetween(next: GenNode): Int = next.span.lineBegin - span.lineEnd

  private fun GenNode.text() = text(source)

  private fun GenNode.isTerminal(vararg texts: String): Boolean =
    type == NodeType.TERMINAL && text(source) in texts

  private fun GenNode.isOperator(vararg names: String): Boolean =
    type == NodeType.OPERATOR && text(source) in names

  private fun newId(): Int {
    return id++
  }

  private fun nodes(vararg nodes: FormatNode) = Nodes(nodes.toList())

  private fun group(vararg nodes: FormatNode) = Group(newId(), nodes.toList())

  private fun indent(vararg nodes: FormatNode) = Indent(nodes.toList())

  companion object {
    private val ABSOLUTE_URL_REGEX = Regex("""\w+://.*""")

    private val EMPTY_NODE = Text("")

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
  }
}
