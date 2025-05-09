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
      NodeType.DOC_COMMENT_LINE -> buf.append(node.text())
      NodeType.MODULE,
      NodeType.MODULE_DECLARATION -> formatChildren(node, separator = "\n\n$indent")
      NodeType.DOC_COMMENT -> formatChildren(node, separator = "\n$indent")
      NodeType.MODULE_DEFINITION,
      NodeType.AMENDS_CLAUSE,
      NodeType.EXTENDS_CLAUSE -> formatChildren(node, separator = " ")
      NodeType.QUALIFIED_IDENTIFIER -> formatChildren(node, separator = "")
      else -> throw RuntimeException("Unknown node type: ${node.type}")
    }

  private fun formatChildren(node: GenNode, separator: String = " ") {
    var first = true
    var prev: GenNode? = null
    for (child in node.children) {
      if (first) first = false else buf.append(spaceBetween(prev, child, separator))
      format(child)
      prev = child
    }
  }

  private fun spaceBetween(prev: GenNode?, node: GenNode, separator: String): String =
    when (prev?.type) {
      NodeType.LINE_COMMENT,
      NodeType.DOC_COMMENT_LINE -> {
        val diff = node.lineDiff(prev)
        if (diff <= 1) "\n" else "\n\n"
      }
      NodeType.BLOCK_COMMENT -> {
        val diff = node.lineDiff(prev)
        if (diff < 1) " " else "\n".repeat(min(2, diff))
      }
      else -> separator
    }

  private fun GenNode.text(): String = text(source)

  private fun GenNode.lineDiff(other: GenNode) = span.lineBegin - other.span.lineEnd

  companion object {
    private const val MAX_LINE_LENGTH = 100
    private const val INDENT = "  "
    private val ABSOLUTE_URL_REGEX = Regex("""\w+://.*""")
  }
}
