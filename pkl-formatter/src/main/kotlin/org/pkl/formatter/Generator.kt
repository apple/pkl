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
import org.pkl.formatter.ast.FormatNode
import org.pkl.formatter.ast.Group
import org.pkl.formatter.ast.IfWrap
import org.pkl.formatter.ast.Indent
import org.pkl.formatter.ast.Line
import org.pkl.formatter.ast.MultilineStringGroup
import org.pkl.formatter.ast.NoWrap
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
          if (wrap == Wrap.DISABLED) Wrap.DISABLED
          else if (size + width > MAX) {
            wrapped += node.id
            Wrap.ENABLED
          } else Wrap.DETECT
        node.nodes.forEach { node(it, wrap) }
      }
      is NoWrap -> node.nodes.forEach { node(it, Wrap.DISABLED) }
      is IfWrap -> {
        if (wrapped.contains(node.id) && wrap != Wrap.DISABLED) {
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
        val offset = indentLength - node.indentSize
        text(node.start)
        processMultilineString(node, offset)
        newline()
        text(node.end)
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

  private fun processMultilineString(multilineString: MultilineStringGroup, offset: Int) {
    val newIndentSize = multilineString.indentSize + offset
    val indentString = " ".repeat(newIndentSize)
    val originalIndent = " ".repeat(multilineString.indentSize)
    val toRemove = "\n$indentString\n"

    for ((index, node) in multilineString.nodes.withIndex()) {
      when (node) {
        is Text -> {
          val nodeText = if (index == 0) "\n${node.text}" else node.text
          val txt =
            if (offset == 0) {
              nodeText
            } else {
              nodeText.replace("\n$originalIndent", "\n$indentString")
            }
          // remove useless indentation
          text(txt.replace(toRemove, "\n\n"))
        }
        else -> {
          if (index == 0) text("\n$indentString")
          node(node, Wrap.DETECT)
        }
      }
    }
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
