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
  DETECT;

  fun isEnabled(): Boolean = this == ENABLED
}

sealed interface FormatNode {
  fun width(wrapped: Set<Int>): Int =
    when (this) {
      is Nodes -> nodes.sumOf { it.width(wrapped) }
      is Group -> nodes.sumOf { it.width(wrapped) }
      is Indent -> nodes.sumOf { it.width(wrapped) }
      is IfWrap -> if (id in wrapped) ifWrap.width(wrapped) else ifNotWrap.width(wrapped)
      is Text -> text.length
      SpaceOrLine,
      Space -> 1
      ForceLine,
      is MultilineStringGroup -> Generator.MAX
      Empty -> 0
      Line -> 0
    }
}

data class Text(val text: String) : FormatNode

object Empty : FormatNode

object Line : FormatNode

object ForceLine : FormatNode

object SpaceOrLine : FormatNode

object Space : FormatNode

data class Indent(val nodes: List<FormatNode>) : FormatNode

data class Nodes(val nodes: List<FormatNode>) : FormatNode

data class Group(val id: Int, val nodes: List<FormatNode>) : FormatNode

data class MultilineStringGroup(val endQuoteCol: Int, val nodes: List<FormatNode>) : FormatNode

data class IfWrap(val id: Int, val ifWrap: FormatNode, val ifNotWrap: FormatNode) : FormatNode
