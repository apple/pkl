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
import org.pkl.formatter.ast.ForceWrap
import org.pkl.formatter.ast.FormatNode
import org.pkl.formatter.ast.Group
import org.pkl.formatter.ast.Indent
import org.pkl.formatter.ast.Line
import org.pkl.formatter.ast.Nodes
import org.pkl.formatter.ast.SemicolonOrLine
import org.pkl.formatter.ast.SpaceOrLine
import org.pkl.formatter.ast.Text
import org.pkl.parser.syntax.generic.FullSpan
import org.pkl.parser.syntax.generic.GenNode
import org.pkl.parser.syntax.generic.NodeType

class Builder(sourceText: String) {
  private var id: Int = 0
  private val source: CharArray = sourceText.toCharArray()

  fun format(node: GenNode): FormatNode =
    when (node.type) {
      NodeType.MODULE -> formatModule(node)
      NodeType.LINE_COMMENT,
      NodeType.BLOCK_COMMENT,
      NodeType.TERMINAL,
      NodeType.MODIFIER,
      NodeType.IDENTIFIER,
      NodeType.STRING_CONSTANT,
      NodeType.INT_LITERAL_EXPR,
      NodeType.FLOAT_LITERAL_EXPR,
      NodeType.BOOL_LITERAL_EXPR,
      NodeType.DOC_COMMENT,
      NodeType.DOC_COMMENT_LINE -> Text(node.text())
      NodeType.MODULE_DECLARATION -> formatModuleDeclaration(node)
      NodeType.MODULE_DEFINITION -> formatModuleDefinition(node)
      NodeType.TYPEALIAS -> formatTypealias(node)
      NodeType.TYPEALIAS_HEADER -> formatTypealiasHeader(node)
      NodeType.MODIFIER_LIST -> formatModifierList(node)
      NodeType.EXTENDS_CLAUSE,
      NodeType.AMENDS_CLAUSE -> formatAmendsExtendsClause(node)
      NodeType.IMPORT_LIST -> formatImportList(node)
      NodeType.IMPORT -> formatImport(node)
      NodeType.CLASS -> formatClass(node)
      NodeType.CLASS_PROPERTY -> formatClassProperty(node)
      NodeType.CLASS_PROPERTY_HEADER -> formatClassPropertyHeader(node)
      NodeType.OBJECT_BODY -> formatObjectBody(node)
      NodeType.OBJECT_MEMBER_LIST -> formatObjectMemberList(node)
      NodeType.IF_EXPR -> formatIf(node)
      NodeType.IF_HEADER -> formatIfHeader(node)
      NodeType.IF_CONDITION -> formatIfCondition(node)
      NodeType.IF_THEN_EXPR,
      NodeType.IF_ELSE_EXPR -> formatIfThenElse(node)
      NodeType.NEW_EXPR -> formatNewExpr(node)
      NodeType.QUALIFIED_IDENTIFIER -> formatQualifiedIdentifier(node)
      else -> Text(node.text()) // throw RuntimeException("Unknown node type: ${node.type}")
    }

  private fun formatModule(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, nodes(ForceLine, ForceLine)))
  }

  private fun formatModuleDeclaration(node: GenNode): FormatNode {
    return Nodes(formatGeneric(node.children, ForceLine))
  }

  private fun formatModuleDefinition(node: GenNode): FormatNode {
    val prefix = formatGeneric(node.children.dropLast(1), SpaceOrLine)
    // qualified identifier
    val suffix = Indent(listOf(format(node.children.last())))
    return Group(newId(), prefix + listOf(SpaceOrLine) + suffix)
  }

  private fun formatQualifiedIdentifier(node: GenNode): FormatNode {
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
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatTypealias(node: GenNode): FormatNode {
    val children = node.children
    val header = format(children[0])
    val rest = group(SpaceOrLine, *formatGeneric(children.drop(1), SpaceOrLine).toTypedArray())
    return Group(newId(), listOf(header, indent(rest)))
  }

  private fun formatTypealiasHeader(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClass(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatClassProperty(node: GenNode): FormatNode {
    val children = node.children
    val nodes = mutableListOf<FormatNode>()
    nodes += formatGeneric(children.dropLast(1), SpaceOrLine)
    val beforeLast = children[children.size - 2]
    val exprOrBody = children.last()
    val sep = getSeparator(beforeLast, exprOrBody, SpaceOrLine)
    when (exprOrBody.type) {
      NodeType.OBJECT_BODY -> {
        // special case for `foo { ...` the { should be in the same line
        nodes += sep
        val open = exprOrBody.children[0]
        nodes += formatObjectBodyRest(format(open), exprOrBody.children.drop(1))
      }
      NodeType.NEW_EXPR -> {
        // special case `foo = new <type> { ...` the new should be in the same line
        nodes += sep
        val header = exprOrBody.children[0]
        val rest = exprOrBody.children.drop(1)
        nodes += format(header)
        nodes += SpaceOrLine
        nodes += Group(newId(), formatGeneric(rest, SpaceOrLine))
      }
      else -> {
        nodes += group(sep, indent(format(exprOrBody)))
      }
    }
    return Nodes(nodes)
  }

  private fun formatClassPropertyHeader(node: GenNode): FormatNode {
    val nodes =
      formatGenericWithGen(
        node.children,
        separatorFn = { n1, n2 ->
          if (n2.type == NodeType.TYPE_ANNOTATION) EMPTY_NODE else SpaceOrLine
        },
      ) { node, next ->
        if (node.type == NodeType.TERMINAL) {
          // ends with `=`
          Indent(listOf(format(node)))
        } else {
          format(node)
        }
      }
    return Group(newId(), nodes)
  }

  private fun formatObjectBody(node: GenNode): FormatNode {
    val begin = format(node.children[0]) // opening {
    return formatObjectBodyRest(begin, node.children.drop(1))
  }

  private fun formatObjectBodyRest(begin: FormatNode, rest: List<GenNode>): FormatNode {
    val nodes = formatGeneric(rest, SpaceOrLine)
    val memberList = rest.find { it.type == NodeType.OBJECT_MEMBER_LIST }
    val hasNewLine = memberList?.let { hasNewlineElements(it.children) } ?: false
    return if (hasNewLine) {
      group(begin, SpaceOrLine, ForceWrap(newId(), nodes))
    } else {
      group(begin, SpaceOrLine, *nodes.toTypedArray())
    }
  }

  private fun formatObjectMemberList(node: GenNode): FormatNode {
    return Indent(formatGeneric(node.children, SemicolonOrLine))
  }

  private fun formatIf(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatIfHeader(node: GenNode): FormatNode {
    return Group(newId(), formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatIfCondition(node: GenNode): FormatNode {
    val children = node.children
    val nodes = mutableListOf<FormatNode>()
    nodes += format(children[0]) // opening (
    nodes += Line
    nodes += Indent(formatGeneric(children.drop(1).dropLast(1), SpaceOrLine))
    nodes += Line
    nodes += format(children.last()) // closing )
    return Nodes(nodes)
  }

  private fun formatIfThenElse(node: GenNode): FormatNode {
    return Indent(formatGeneric(node.children, SpaceOrLine))
  }

  private fun formatNewExpr(node: GenNode): FormatNode {
    val header =
      Group(newId(), formatGeneric(node.children.dropLast(1), SpaceOrLine) + listOf(SpaceOrLine))
    val body = format(node.children.last())
    return group(header, body)
  }

  private fun formatModifierList(node: GenNode): FormatNode {
    val nodes = mutableListOf<FormatNode>()
    val children = node.children.groupBy { it.type.isAffix }
    if (children[true] != null) {
      nodes += formatGeneric(children[true]!!, SpaceOrLine)
    }
    val modifiers = children[false]!!.sortedBy(::modifierPrecedence)
    nodes += formatGeneric(modifiers, SpaceOrLine)
    return Nodes(nodes)
  }

  private fun formatImportList(node: GenNode): FormatNode {
    val nodes = mutableListOf<FormatNode>()
    val children = node.children.groupBy { it.type.isAffix }
    if (children[true] != null) {
      nodes += formatGeneric(children[true]!!, SpaceOrLine)
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
    separator: FormatNode,
    generatorFn: ((GenNode, GenNode?) -> FormatNode)?,
  ): List<FormatNode> {
    return formatGenericWithGen(children, { n1, n2 -> separator }, generatorFn)
  }

  private fun formatGenericWithGen(
    children: List<GenNode>,
    separatorFn: (GenNode, GenNode) -> FormatNode?,
    generatorFn: ((GenNode, GenNode?) -> FormatNode)?,
  ): List<FormatNode> {
    // short circuit
    if (children.isEmpty()) return listOf(SpaceOrLine)
    if (children.size == 1) return listOf(format(children[0]))

    val nodes = mutableListOf<FormatNode>()
    var prev = children[0]
    for (child in children.drop(1)) {
      // skip semicolons
      if (child.type.isAffix && child.text() == ";") continue

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

  private fun getImportUrl(node: GenNode): String =
    node.findChildByType(NodeType.STRING_CONSTANT)!!.text().drop(1).dropLast(1)

  private fun getSeparator(prev: GenNode, next: GenNode, separator: FormatNode): FormatNode {
    return getSeparator(prev, next) { x, y -> separator }!!
  }

  private fun getSeparator(
    prev: GenNode,
    next: GenNode,
    separatorFn: (GenNode, GenNode) -> FormatNode?,
  ): FormatNode? {
    return if (hasTraillingAffix(prev, next)) {
      SpaceOrLine
    } else if (prev.type in FORCE_LINE_AFFIXES) {
      if (prev.type != NodeType.DOC_COMMENT && prev.linesBetween(next) > 1) {
        nodes(ForceLine, ForceLine)
      } else {
        ForceLine
      }
    } else separatorFn(prev, next)
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

  private fun hasNewlineElements(nodes: List<GenNode>): Boolean {
    var prev: FullSpan? = null
    for (child in nodes) {
      if (child.type.isAffix) continue
      if (prev == null) {
        prev = child.span
      } else if (prev.lineBegin != child.span.lineEnd) {
        return true
      }
    }
    return false
  }

  private fun GenNode.linesBetween(next: GenNode): Int = next.span.lineBegin - span.lineEnd

  private fun newId(): Int {
    return id++
  }

  private fun nodes(vararg nodes: FormatNode) = Nodes(nodes.toList())

  private fun group(vararg nodes: FormatNode) = Group(newId(), nodes.toList())

  private fun indent(vararg nodes: FormatNode) = Indent(nodes.toList())

  private fun GenNode.text() = text(source)

  companion object {
    private val ABSOLUTE_URL_REGEX = Regex("""\w+://.*""")

    private val EMPTY_NODE = Nodes(listOf())

    private val FORCE_LINE_AFFIXES =
      EnumSet.of(
        NodeType.LINE_COMMENT,
        NodeType.BLOCK_COMMENT,
        NodeType.SEMICOLON,
        NodeType.SHEBANG,
        NodeType.DOC_COMMENT,
      )
  }
}
