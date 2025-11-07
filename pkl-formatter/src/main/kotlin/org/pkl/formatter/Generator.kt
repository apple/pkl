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
import org.pkl.formatter.ast.Wrap

internal class Generator {
  private val buf: StringBuilder = StringBuilder()
  private var indent: Int = 0
  private var size: Int = 0
  private val wrapped: MutableSet<Int> = mutableSetOf()
  private var shouldAddIndent = false

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
      is IfWrap -> {
        if (wrapped.contains(node.id)) {
          node(node.ifWrap, Wrap.ENABLED)
        } else {
          node(node.ifNotWrap, wrap)
        }
      }
      is Text -> text(node.text)
      is Line -> {
        if (wrap.isEnabled()) {
          newline()
        }
      }
      is ForceLine -> newline()
      is SpaceOrLine -> {
        if (wrap.isEnabled()) {
          newline()
        } else {
          text(" ")
        }
      }
      is Space -> text(" ")
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
        val oldIndent = indentFor(node)
        var previousNewline = false
        for ((i, child) in node.nodes.withIndex()) {
          when {
            child is ForceLine -> newline(shouldIndent = false) // don't indent
            child is Text &&
              previousNewline &&
              child.text.isBlank() &&
              child.text.length == oldIndent &&
              node.nodes[i + 1] is ForceLine -> {}
            child is Text && previousNewline ->
              text(reposition(child.text, node.endQuoteCol - 1, indentLength))
            else -> node(child, Wrap.DETECT) // always detect wrapping
          }
          previousNewline = child is ForceLine
        }
      }
    }
  }

  private fun text(value: String) {
    if (shouldAddIndent) {
      repeat(times = indent) { buf.append(INDENT) }
      shouldAddIndent = false
    }
    size += value.length
    buf.append(value)
  }

  private fun newline(shouldIndent: Boolean = true) {
    size = INDENT.length * indent
    buf.append('\n')
    shouldAddIndent = shouldIndent
  }

  // accept text indented by originalOffset characters (tabs or spaces)
  // and return it indented by newOffset characters (spaces only)
  private fun reposition(text: String, originalOffset: Int, newOffset: Int): String =
    " ".repeat(newOffset) + text.drop(originalOffset)

  // Returns the indent of this multiline string, which is the size of the last node before the
  // closing quotes, or 0 if the closing quotes have no indentation
  private fun indentFor(multi: MultilineStringGroup): Int {
    val nodes = multi.nodes
    if (nodes.size < 2) return 0
    val beforeLast = nodes[nodes.lastIndex - 1]
    return if (beforeLast is Text) beforeLast.text.length else 0
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
