/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.lsp.ast

data class Span(val beginLine: Int, val beginCol: Int, val endLine: Int, val endCol: Int) {
  override fun toString(): String {
    return "($beginLine:$beginCol - $endLine:$endCol)"
  }

  /** True if the given line and column are inside this span. */
  fun matches(line: Int, col: Int): Boolean =
    when {
      line < beginLine || line > endLine -> false
      beginLine == endLine -> col in beginCol..endCol
      line == beginLine -> col >= beginCol
      line == endLine -> col <= endCol
      else -> true
    }

  companion object {
    fun from(s1: Span, s2: Span): Span = Span(s1.beginLine, s1.beginCol, s2.endLine, s2.endCol)
  }
}
