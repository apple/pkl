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

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min
import org.pkl.parser.GenericParser
import org.pkl.parser.syntax.generic.GenNode
import org.pkl.parser.syntax.generic.NodeType

class Formatter {
  private lateinit var buf: StringBuilder
  private lateinit var source: CharArray
  private var indent: String = ""
  private var lineLength: Int = 0

  fun format(path: Path): String {
    try {
      return format(Files.readString(path))
    } catch (e: IOException) {
      throw RuntimeException("TODO", e)
    }
  }

  fun format(text: String): String {
    buf = StringBuilder()
    source = text.toCharArray()
    val parser = GenericParser()
    val mod = parser.parseModule(text)
    format(mod)
    if (!buf.endsWith('\n')) buf.append('\n')
    return buf.toString()
  }

  private fun format(node: GenNode) =
    when (node.type) {
      NodeType.LINE_COMMENT,
      NodeType.BLOCK_COMMENT,
      NodeType.TERMINAL,
      NodeType.MODIFIER,
      NodeType.IDENTIFIER,
      NodeType.STRING_CONSTANT,
      NodeType.DOC_COMMENT_LINE -> append(node.text())
      NodeType.IMPORT_LIST -> formatImportList(node)
      NodeType.MODULE -> formatChildren(node, separator = "\n\n$indent")
      NodeType.DOC_COMMENT,
      NodeType.MODULE_DECLARATION -> formatChildren(node, separator = "\n$indent")
      NodeType.MODULE_DEFINITION,
      NodeType.AMENDS_CLAUSE,
      NodeType.EXTENDS_CLAUSE,
      NodeType.IMPORT -> formatChildren(node, separator = " ")
      NodeType.QUALIFIED_IDENTIFIER -> formatChildren(node, separator = "")
      else -> throw RuntimeException("Unknown node type: ${node.type}")
    }

  private fun formatImportList(node: GenNode) {
    val imports = toImports(node.children)
    val groups =
      imports.groupBy { imp ->
        when {
          ABSOLUTE_URL_REGEX.matches(imp.url) -> "absolute"
          imp.url.startsWith('@') -> "project"
          else -> "relative"
        }
      }
    val absolutes =
      groups["absolute"]?.sortedBy { it.url }?.map { it.children }?.flatten() ?: listOf()
    val projects =
      groups["project"]?.sortedBy { it.url }?.map { it.children }?.flatten() ?: listOf()
    val relatives =
      groups["relative"]?.sortedBy { it.url }?.map { it.children }?.flatten() ?: listOf()
    var shouldNewline = false

    if (absolutes.isNotEmpty()) {
      formatImportChildren(absolutes)
      appendLine()
      shouldNewline = true
    }

    if (projects.isNotEmpty()) {
      if (shouldNewline) appendLine()
      formatImportChildren(projects)
      appendLine()
      shouldNewline = true
    }

    if (relatives.isNotEmpty()) {
      if (shouldNewline) appendLine()
      formatImportChildren(relatives)
      appendLine()
    }
  }

  private fun formatImportChildren(children: List<GenNode>) {
    var first = true
    var prev: GenNode? = null
    for (child in children) {
      if (first) first = false
      else {
        val diff = child.lineDiff(prev!!)
        if (diff == 0 && child.type != NodeType.IMPORT && prev.type == NodeType.IMPORT) {
          append(' ')
        } else {
          appendLine()
        }
      }
      format(child)
      prev = child
    }
  }

  private fun formatChildren(node: GenNode, separator: String = " ") {
    formatChildren(node.children, separator)
  }

  private fun formatChildren(children: List<GenNode>, separator: String = " ") {
    var first = true
    var prev: GenNode? = null
    for (child in children) {
      if (first) first = false else append(spaceBetween(prev, child, separator))
      format(child)
      prev = child
    }
  }

  private fun toImports(imports: List<GenNode>): List<Import> {
    if (imports.isEmpty()) return listOf()
    var current = mutableListOf<GenNode>()
    var url: String? = null
    val res = mutableListOf<Import>()
    var prev = imports[0]
    for (child in imports) {
      if (url == null) {
        if (child.type == NodeType.IMPORT) {
          child.findChildByType(NodeType.STRING_CONSTANT)!!.let { url = stringConstantText(it) }
        }
      } else {
        if (child.type == NodeType.IMPORT) {
          res += Import(url, current)
          current = mutableListOf()
          child.findChildByType(NodeType.STRING_CONSTANT)!!.let { url = stringConstantText(it) }
        } else if (child.lineDiff(prev) > 0) {
          res += Import(url, current)
          current = mutableListOf()
          url = null
        }
      }
      current += child
      prev = child
    }
    res += Import(url!!, current)
    return res
  }

  private fun stringConstantText(node: GenNode): String {
    val txt = node.text()
    return txt.substring(1, txt.length - 1)
  }

  private fun spaceBetween(prev: GenNode?, node: GenNode, separator: String): String =
    when (prev?.type) {
      NodeType.LINE_COMMENT,
      NodeType.DOC_COMMENT_LINE -> {
        val diff = node.lineDiff(prev)
        if (diff <= 1) "\n" else "\n\n"
      }
      NodeType.DOC_COMMENT -> "\n"
      NodeType.BLOCK_COMMENT -> {
        val diff = node.lineDiff(prev)
        if (diff < 1) " " else "\n".repeat(min(2, diff))
      }
      else -> {
        if (node.type.isAffix && prev != null && node.lineDiff(prev) == 0) {
          " "
        } else separator
      }
    }

  private fun GenNode.text(): String = text(source)

  private fun GenNode.lineDiff(other: GenNode) = span.lineBegin - other.span.lineEnd

  private fun appendLine() {
    buf.append("\n$indent")
    lineLength = 0
  }

  private fun append(char: Char) {
    assert(char != '\n' && char != '\r')
    buf.append(char)
    lineLength++
  }

  private fun append(string: String) {
    buf.append(string)
    val newlineIdx = string.lastIndexOf('\n')
    lineLength +=
      if (newlineIdx < 0) {
        string.length
      } else {
        string.length - (newlineIdx + 1)
      }
  }

  companion object {
    private const val MAX_LINE_LENGTH = 100
    private const val INDENT = "  "
    private val ABSOLUTE_URL_REGEX = Regex("""\w+://.*""")

    private data class Import(val url: String, val children: List<GenNode>)
  }
}
