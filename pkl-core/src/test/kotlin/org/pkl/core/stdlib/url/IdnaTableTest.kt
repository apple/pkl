/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.url

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.pkl.core.stdlib.url.IdnaTable.BidiClass
import org.pkl.core.stdlib.url.IdnaTable.Status

class IdnaTableTest {
  @Test
  fun `looks up statuses`() {
    assertStatuses(
      // valid
      0x002D to Status.VALID,
      0x002E to Status.VALID,
      0x0030 to Status.VALID,
      0x0061 to Status.VALID,
      0x00E9 to Status.VALID,
      0x2603 to Status.VALID,
      // `disallowed_STD3_valid`
      0x002F to Status.VALID,
      0x0040 to Status.VALID,
      0x005F to Status.VALID,
      // `deviation`
      0x00DF to Status.VALID,
      0x03C2 to Status.VALID,
      0x200C to Status.VALID,
      0x200D to Status.VALID,
      // ignored
      0x00AD to Status.IGNORED,
      0x200B to Status.IGNORED,
      // mapped
      0x0041 to Status.MAPPED,
      0x3002 to Status.MAPPED,
      0xFF10 to Status.MAPPED,
      0x1D400 to Status.MAPPED,
      // `disallowed_STD3_mapped`
      0x2000 to Status.MAPPED,
      // disallowed
      0x0378 to Status.DISALLOWED,
      0xD800 to Status.DISALLOWED,
      0x10FFFF to Status.DISALLOWED,
    )
  }

  @Test
  fun `looks up mappings`() {
    assertMappings(
      0x0041 to "a",
      0x3002 to ".",
      0xFF10 to "0",
      0x1D400 to "a",
      0x3260 to "ᄀ",
      0x1E9E to "ß",
      0xFB00 to "ff",
      // a mapping applies to every code point of its range, not just the first
      0x0132 to "ij",
      0x0133 to "ij",
      0x2000 to " ",
      0x200A to " ",
    )
  }

  @Test
  fun `looks up bidi classes`() {
    assertBidiClasses(
      0x0061 to BidiClass.L,
      0x05D0 to BidiClass.R,
      0x0627 to BidiClass.AL,
      0x0660 to BidiClass.AN,
      0x0030 to BidiClass.EN,
      0x002D to BidiClass.ES,
      0x002E to BidiClass.CS,
      0x0024 to BidiClass.ET,
      0x00A9 to BidiClass.ON,
      0x200B to BidiClass.BN,
      0x0300 to BidiClass.NSM,
      0x0020 to BidiClass.OTHER,
      0x000A to BidiClass.OTHER,
      0x2066 to BidiClass.OTHER,
    )
  }

  @Test
  fun `looks up combining marks`() {
    assertMarks(
      0x0300 to true,
      0x0903 to true,
      0x0488 to true,
      0x0061 to false,
      0x1D400 to false,
      0x10FFFF to false,
    )
  }

  @Test
  fun `covers every code point`() {
    for (codePoint in 0..0x10FFFF) {
      val status = IdnaTable.status(codePoint)
      val hasMapping = IdnaTable.mapping(codePoint) != null
      if (hasMapping != (status == Status.MAPPED)) {
        fail<Unit>(
          "${name(codePoint)} has status $status but ${if (hasMapping) "has" else "has no"} mapping"
        )
      }
      // an unordered or short table would throw here rather than at some later boundary
      IdnaTable.isMark(codePoint)
      IdnaTable.bidiClass(codePoint)
    }
  }

  @Test
  fun `agrees with the ASCII fast path`() {
    for (codePoint in 0..0x7F) {
      val lowercase = codePoint.toChar().lowercaseChar()
      if (lowercase == codePoint.toChar()) {
        assertStatuses(codePoint to Status.VALID)
      } else {
        assertStatuses(codePoint to Status.MAPPED)
        assertMappings(codePoint to lowercase.toString())
      }
      assertMarks(codePoint to false)
    }
  }

  @Test
  fun `defaults an unassigned code point to its block's bidi class`() {
    assertBidiClasses(
      0x05EB to BidiClass.R,
      0x086B to BidiClass.AL,
      0xFBC3 to BidiClass.AL,
      // outside such a block the global default still applies
      0x0378 to BidiClass.L,
    )
  }

  @Test
  fun `is pinned to one Unicode version`() {
    assertThat(IdnaTable.unicodeVersion()).isEqualTo("15.1.0")
  }

  private fun assertStatuses(vararg cases: Pair<Int, Status>) =
    assertLookups(cases) { IdnaTable.status(it) }

  private fun assertMappings(vararg cases: Pair<Int, String>) =
    assertLookups(cases) { IdnaTable.mapping(it) }

  private fun assertBidiClasses(vararg cases: Pair<Int, BidiClass>) =
    assertLookups(cases) { IdnaTable.bidiClass(it) }

  private fun assertMarks(vararg cases: Pair<Int, Boolean>) =
    assertLookups(cases) { IdnaTable.isMark(it) }

  private fun <T> assertLookups(cases: Array<out Pair<Int, T>>, lookUp: (Int) -> T?) {
    assertThat(cases.map { (codePoint, _) -> name(codePoint) to lookUp(codePoint) })
      .isEqualTo(cases.map { (codePoint, expected) -> name(codePoint) to expected })
  }

  private fun name(codePoint: Int) = "U+%04X".format(codePoint)
}
