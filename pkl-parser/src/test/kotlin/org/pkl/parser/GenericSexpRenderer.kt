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
package org.pkl.parser

import java.util.EnumSet
import org.pkl.parser.syntax.generic.GenNode
import org.pkl.parser.syntax.generic.NodeType

class GenericSexpRenderer(code: String) {
  private var tab = ""
  private var buf = StringBuilder()
  private val source = code.toCharArray()

  fun render(node: GenNode): String {
    innerRender(node)
    return buf.toString()
  }

  private fun innerRender(node: GenNode) {
    if (node.type == NodeType.UNION_TYPE) {
      renderUnionType(node)
      return
    }
    if (node.type == NodeType.BINARY_OP_EXPR && binopName(node).endsWith("ualifiedAccessExpr")) {
      renderQualifiedAccess(node)
      return
    }
    doRender(name(node), collectChildren(node))
  }

  private fun doRender(name: String, children: List<GenNode>) {
    buf.append(tab)
    buf.append("(")
    buf.append(name)
    val oldTab = increaseTab()
    for (child in children) {
      buf.append('\n')
      innerRender(child)
    }
    tab = oldTab
    buf.append(')')
  }

  private fun renderUnionType(node: GenNode) {
    buf.append(tab)
    buf.append("(")
    buf.append(name(node))
    val oldTab = increaseTab()
    var previousTerminal: GenNode? = null
    for (child in node.children) {
      if (child.type == NodeType.TERMINAL) previousTerminal = child
      if (child.type in IGNORED_CHILDREN) continue
      buf.append('\n')
      if (previousTerminal != null && previousTerminal.text(source) == "*") {
        previousTerminal = null
        renderDefaultUnionType(child)
      } else {
        innerRender(child)
      }
    }
    tab = oldTab
    buf.append(')')
  }

  private fun renderQualifiedAccess(node: GenNode) {
    var children = node.children
    if (children.last().type == NodeType.UNQUALIFIED_ACCESS_EXPR) {
      children = children.dropLast(1) + collectChildren(children.last())
    }
    val toRender = mutableListOf<GenNode>()
    for (child in children) {
      if (child.type in IGNORED_CHILDREN || child.type == NodeType.OPERATOR) continue
      toRender += child
    }
    doRender(name(node), toRender)
  }

  private fun renderDefaultUnionType(node: GenNode) {
    buf.append(tab)
    buf.append("(defaultUnionType\n")
    val oldTab = increaseTab()
    innerRender(node)
    tab = oldTab
    buf.append(')')
  }

  private fun collectChildren(node: GenNode): List<GenNode> =
    when (node.type) {
      NodeType.MULTI_LINE_STRING_LITERAL_EXPR ->
        node.children.filter { it.type !in IGNORED_CHILDREN && !it.type.isStringData() }
      NodeType.SINGLE_LINE_STRING_LITERAL_EXPR -> {
        val children = node.children.filter { it.type !in IGNORED_CHILDREN }
        val res = mutableListOf<GenNode>()
        var prev: GenNode? = null
        for (child in children) {
          val inARow = child.type.isStringData() && (prev != null && prev.type.isStringData())
          if (!inARow) {
            res += child
          }
          prev = child
        }
        res
      }
      NodeType.DOC_COMMENT -> listOf()
      else -> {
        val nodes = mutableListOf<GenNode>()
        for (child in node.children) {
          if (child.type in IGNORED_CHILDREN) continue
          if (child.type in UNPACK_CHILDREN) {
            nodes += collectChildren(child)
          } else {
            nodes += child
          }
        }
        nodes
      }
    }

  private fun NodeType.isStringData(): Boolean =
    this == NodeType.STRING_CONSTANT || this == NodeType.STRING_ESCAPE

  private fun name(node: GenNode): String =
    when (node.type) {
      NodeType.MODULE_DECLARATION -> "moduleHeader"
      NodeType.IMPORT -> importName(node, isExpr = false)
      NodeType.IMPORT_EXPR -> importName(node, isExpr = true)
      NodeType.BINARY_OP_EXPR -> binopName(node)
      NodeType.CLASS -> "clazz"
      NodeType.EXTENDS_CLAUSE,
      NodeType.AMENDS_CLAUSE -> "extendsOrAmendsClause"
      NodeType.TYPEALIAS -> "typeAlias"
      NodeType.STRING_ESCAPE -> "stringConstant"
      NodeType.READ_EXPR -> {
        val terminal = node.children.find { it.type == NodeType.TERMINAL }!!.text(source)
        when (terminal) {
          "read*" -> "readGlobExpr"
          "read?" -> "readNullExpr"
          else -> "readExpr"
        }
      }
      else -> {
        val names = node.type.name.split('_').map { it.lowercase() }
        if (names.size > 1) {
          val capitalized = names.drop(1).map { n -> n.replaceFirstChar { it.titlecase() } }
          (listOf(names[0]) + capitalized).joinToString("")
        } else names[0]
      }
    }

  private fun importName(node: GenNode, isExpr: Boolean): String {
    val terminal = node.children.find { it.type == NodeType.TERMINAL }!!.text(source)
    val suffix = if (isExpr) "Expr" else "Clause"
    return if (terminal == "import*") "importGlob$suffix" else "import$suffix"
  }

  private fun binopName(node: GenNode): String {
    val op = node.children.find { it.type == NodeType.OPERATOR }!!.text(source)
    return when (op) {
      "**" -> "exponentiationExpr"
      "*",
      "/",
      "~/",
      "%" -> "multiplicativeExpr"
      "+",
      "-" -> "additiveExpr"
      ">",
      ">=",
      "<",
      "<=" -> "comparisonExpr"
      "is" -> "typeCheckExpr"
      "as" -> "typeCastExpr"
      "==",
      "!=" -> "equalityExpr"
      "&&" -> "logicalAndExpr"
      "||" -> "logicalOrExpr"
      "|>" -> "pipeExpr"
      "??" -> "nullCoalesceExpr"
      "." -> "qualifiedAccessExpr"
      "?." -> "nullableQualifiedAccessExpr"
      else -> throw RuntimeException("Unknown operator: $op")
    }
  }

  private fun increaseTab(): String {
    val old = tab
    tab += "  "
    return old
  }

  companion object {
    private val IGNORED_CHILDREN =
      EnumSet.of(
        NodeType.LINE_COMMENT,
        NodeType.BLOCK_COMMENT,
        NodeType.SHEBANG,
        NodeType.SEMICOLON,
        NodeType.TERMINAL,
        NodeType.OPERATOR,
        NodeType.STRING_NEWLINE,
      )

    private val UNPACK_CHILDREN =
      EnumSet.of(
        NodeType.MODULE_DEFINITION,
        NodeType.IMPORT_LIST,
        NodeType.IMPORT_ALIAS,
        NodeType.TYPEALIAS_HEADER,
        NodeType.TYPEALIAS_BODY,
        NodeType.CLASS_PROPERTY_HEADER,
        NodeType.CLASS_PROPERTY_HEADER_BEGIN,
        NodeType.CLASS_METHOD_HEADER,
        NodeType.CLASS_METHOD_BODY,
        NodeType.CLASS_HEADER,
        NodeType.CLASS_BODY_ELEMENTS,
        NodeType.MODIFIER_LIST,
        NodeType.NEW_HEADER,
        NodeType.OBJECT_MEMBER_LIST,
        NodeType.OBJECT_ENTRY_HEADER,
        NodeType.OBJECT_PROPERTY_HEADER,
        NodeType.OBJECT_PROPERTY_HEADER_BEGIN,
        NodeType.OBJECT_PARAMETER_LIST,
        NodeType.FOR_GENERATOR_HEADER,
        NodeType.FOR_GENERATOR_HEADER_DEFINITION,
        NodeType.FOR_GENERATOR_HEADER_DEFINITION_HEADER,
        NodeType.WHEN_GENERATOR_HEADER,
        NodeType.IF_HEADER,
        NodeType.IF_CONDITION,
        NodeType.IF_CONDITION_EXPR,
        NodeType.IF_THEN_EXPR,
        NodeType.IF_ELSE_EXPR,
        NodeType.FUNCTION_LITERAL_BODY,
        NodeType.ARGUMENT_LIST_ELEMENTS,
        NodeType.PARAMETER_LIST_ELEMENTS,
        NodeType.CONSTRAINED_TYPE_CONSTRAINT,
        NodeType.CONSTRAINED_TYPE_ELEMENTS,
        NodeType.TYPE_PARAMETER_LIST_ELEMENTS,
        NodeType.TYPE_ARGUMENT_LIST_ELEMENTS,
        NodeType.LET_PARAMETER_DEFINITION,
        NodeType.LET_PARAMETER,
      )
  }
}
