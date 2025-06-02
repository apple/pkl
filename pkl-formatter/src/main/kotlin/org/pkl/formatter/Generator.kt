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

import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.FormatNode
import org.pkl.formatter.ast.Group
import org.pkl.formatter.ast.Indent
import org.pkl.formatter.ast.Line
import org.pkl.formatter.ast.Nodes
import org.pkl.formatter.ast.SemicolonOrLine
import org.pkl.formatter.ast.Space
import org.pkl.formatter.ast.SpaceOrLine
import org.pkl.formatter.ast.Text
import org.pkl.formatter.ast.Wrap

class Generator {
  private val buf: StringBuilder = StringBuilder()
  private var indent: Int = 0
  private var size: Int = 0
  private val wrapped: MutableSet<Int> = mutableSetOf()

  fun generate(node: FormatNode) {
    node(node, Wrap.DETECT)
  }

  private fun node(node: FormatNode, wrap: Wrap) {
    when (node) {
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
        if (wrap.isEnabled()) {
          size += INDENT.length
          indent++
          buf.append(INDENT)
          node.nodes.forEach { node(it, wrap) }
          indent--
        } else {
          node.nodes.forEach { node(it, wrap) }
        }
      }
      is SemicolonOrLine -> {
        if (wrap.isEnabled()) {
          newline()
        } else {
          text("; ")
        }
      }
    }
  }

  private fun text(value: String) {
    size += value.length
    buf.append(value)
  }

  private fun newline() {
    size = INDENT.length * indent
    buf.append('\n')
    repeat(times = indent) { buf.append(INDENT) }
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
