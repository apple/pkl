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

internal class NaturalOrderComparator(private val ignoreCase: Boolean = false) :
  Comparator<String> {

  override fun compare(s1: String, s2: String): Int {
    var i = 0
    var j = 0

    while (i < s1.length && j < s2.length) {
      val c1 = if (ignoreCase) s1[i].lowercaseChar() else s1[i]
      val c2 = if (ignoreCase) s2[j].lowercaseChar() else s2[j]

      if (c1.isDigit() && c2.isDigit()) {
        val (num1, nextI) = getNumber(s1, i)
        val (num2, nextJ) = getNumber(s2, j)

        val numComparison = num1.compareTo(num2)
        if (numComparison != 0) {
          return numComparison
        }
        i = nextI
        j = nextJ
      } else {
        val charComparison = c1.compareTo(c2)
        if (charComparison != 0) {
          return charComparison
        }
        i++
        j++
      }
    }

    return s1.length.compareTo(s2.length)
  }

  private fun getNumber(s: String, startIndex: Int): LongAndInt {
    var i = startIndex
    val start = i

    while (i < s.length && s[i].isDigit()) {
      i++
    }
    val numStr = s.substring(start, i)
    val number = numStr.toLongOrNull() ?: 0L
    return LongAndInt(number, i)
  }

  // use this instead of Pair to avoid boxing
  private data class LongAndInt(val l: Long, var i: Int)
}
