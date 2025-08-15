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
package org.pkl.formatter.ast

import org.pkl.formatter.Generator

enum class Wrap {
  ENABLED,
  DETECT,
  DISABLED;

  fun isEnabled(): Boolean = this == ENABLED
}

sealed interface FormatNode {
  fun width(wrapped: Set<Int>): Int =
    when (this) {
      is Nodes -> nodes.sumOf { it.width(wrapped) }
      is Group -> nodes.sumOf { it.width(wrapped) }
      is Indent -> nodes.sumOf { it.width(wrapped) }
      is NoWrap -> nodes.sumOf { it.width(wrapped) }
      is IfWrap -> if (id in wrapped) ifWrap.width(wrapped) else ifNotWrap.width(wrapped)
      is Text -> text.length
      is SpaceOrLine,
      is Space -> 1
      is ForceLine,
      is CommentLine,
      is MultilineStringGroup -> Generator.MAX
      else -> 0
    }
}

object Empty : FormatNode

data class Text(val text: String) : FormatNode

object Line : FormatNode

object ForceLine : FormatNode

object CommentLine : FormatNode

object SpaceOrLine : FormatNode

object Space : FormatNode

data class Indent(val nodes: List<FormatNode>) : FormatNode

data class Nodes(val nodes: List<FormatNode>) : FormatNode

data class Group(val id: Int, val nodes: List<FormatNode>) : FormatNode

data class NoWrap(val nodes: List<FormatNode>) : FormatNode

data class MultilineStringGroup(
  val indentSize: Int,
  val start: String,
  val end: String,
  val nodes: List<FormatNode>,
) : FormatNode

data class IfWrap(val id: Int, val ifWrap: FormatNode, val ifNotWrap: FormatNode) : FormatNode

val twoNewlines = Nodes(listOf(ForceLine, ForceLine))

fun nodes(vararg nodes: FormatNode): FormatNode =
  when (nodes.size) {
    0 -> Empty
    1 -> nodes[0]
    else -> Nodes(nodes.toList())
  }

fun nodes(nodes: List<FormatNode>): FormatNode =
  when (nodes.size) {
    0 -> Empty
    1 -> nodes[0]
    else -> Nodes(nodes)
  }

fun group(id: Int, vararg nodes: FormatNode): FormatNode =
  when (nodes.size) {
    0 -> Empty
    1 -> nodes[0]
    else -> Group(id, nodes.toList())
  }

fun group(id: Int, nodes: List<FormatNode>): FormatNode =
  when (nodes.size) {
    0 -> Empty
    1 -> nodes[0]
    else -> Group(id, nodes)
  }

fun indent(vararg node: FormatNode) = Indent(node.toList())

fun noWrap(vararg nodes: FormatNode) = NoWrap(nodes.toList())
