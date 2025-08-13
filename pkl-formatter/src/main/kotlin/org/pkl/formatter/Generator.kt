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

import org.pkl.formatter.ast.CommentLine
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
import org.pkl.formatter.ast.Wrap

class Generator {
  private val buf: StringBuilder = StringBuilder()
  private var indent: Int = 0
  private var size: Int = 0
  private val wrapped: MutableSet<Int> = mutableSetOf()
  private var shouldAddIndent = false
  private var commentLine = false

  fun generate(node: FormatNode) {
    node(node, Wrap.DETECT)
  }

  private fun node(node: FormatNode, wrap: Wrap) {
    when (node) {
      is Empty -> {}
      is Nodes -> node.nodes.forEach { node(it, wrap) }
      is Group -> {
        val width = node.nodes.sumOf { it.width(wrapped) }
        val wrap =
          if (size + width > MAX) {
            wrapped += node.id
            Wrap.ENABLED
          } else {
            Wrap.DETECT
          }
        node.nodes.forEach { node(it, wrap) }
      }
      is ForceWrap -> {
        wrapped += node.id
        val wrap = Wrap.ENABLED
        node.nodes.forEach { node(it, wrap) }
      }
      is IfWrap -> {
        if (wrapped.contains(node.id)) {
          node(node.ifWrap, Wrap.ENABLED)
        } else {
          node(node.ifNotWrap, wrap)
        }
      }
      is Text -> {
        commentLine = false
        text(node.text)
      }
      is Line -> {
        if (commentLine) commentLine = false
        else if (wrap.isEnabled()) {
          newline()
        }
      }
      is ForceLine -> {
        if (commentLine) commentLine = false else newline()
      }
      is CommentLine -> {
        newline()
        commentLine = true
      }
      is SpaceOrLine -> {
        if (commentLine) commentLine = false
        else if (wrap.isEnabled()) {
          newline()
        } else {
          text(" ")
        }
      }
      is Space -> if (commentLine) commentLine = false else text(" ")
      is Indent -> {
        if (wrap.isEnabled() && node.nodes.isNotEmpty()) {
          size += INDENT.length
          indent++
          node.nodes.forEach { node(it, wrap) }
          indent--
        } else {
          node.nodes.forEach { node(it, wrap) }
        }
      }
      is MultilineStringGroup -> {
        val indentLength = indent * INDENT.length
        val offset = (indentLength + 1) - node.endQuoteCol
        val nodes = processMultilineText(node.nodes, indentLength, offset)
        text("\"\"\"")
        newline(shouldIndent = false)
        for (child in nodes) {
          node(child, Wrap.DETECT)
        }
        newline()
        text("\"\"\"")
      }
    }
  }

  private fun text(value: String) {
    if (shouldAddIndent) {
      repeat(times = indent) { buf.append(INDENT) }
      shouldAddIndent = false
    }
    val len = value.substringAfterLast('\n').length
    size += len
    buf.append(value)
  }

  private fun newline(shouldIndent: Boolean = true) {
    size = INDENT.length * indent
    buf.append('\n')
    shouldAddIndent = shouldIndent
  }

  private fun processMultilineText(
    nodes: List<FormatNode>,
    indentLength: Int,
    offset: Int,
  ): List<FormatNode> {
    val res = mutableListOf<FormatNode>()
    var prev: FormatNode? = null
    for (node in nodes) {
      res +=
        if (node is Text) {
          Text(processIndent(node.text, offset, indentLength, prev is Nodes))
        } else node
      prev = node
    }
    return res
  }

  fun processIndent(original: String, offset: Int, indentLength: Int, ignoreStart: Boolean): String {
    val toRemove = "\n${" ".repeat(indentLength)}\n"
    return original
      .split('\n')
      .mapIndexed { index, line ->
        if (ignoreStart && index == 0 || offset == 0) line
        else {
          val leadingSpaces = line.takeWhile { it == ' ' }.length
          val newIndentation = maxOf(0, leadingSpaces + offset)
          " ".repeat(newIndentation) + line.drop(leadingSpaces)
        }
      }
      .joinToString("\n")
      // remove useless indentation
      .replace(toRemove, "\n\n")
  }

  override fun toString(): String {
    return buf.toString()
  }

  companion object {
    // max line length
    const val MAX = 100
    private const val INDENT = "  "
  }
}
