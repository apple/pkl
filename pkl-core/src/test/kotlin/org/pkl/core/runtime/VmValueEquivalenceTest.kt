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
package org.pkl.core.runtime

import java.math.BigDecimal
import java.util.Random
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VmValueEquivalenceTest {
  private val equivalence = VmValueEquivalence.INSTANCE

  @Test
  fun `numeric equivalence is reflexive symmetric and transitive`() {
    val values =
      listOf(
        0L,
        0.0,
        -0.0,
        1L,
        1.0,
        1.5,
        9007199254740992L,
        9007199254740992.0,
        9007199254740993L,
        Long.MIN_VALUE,
        Long.MIN_VALUE.toDouble(),
        Long.MAX_VALUE,
        Long.MAX_VALUE.toDouble(),
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.NaN,
        Double.fromBits(0x7ff0000000000001L),
      )
    for (a in values) {
      assertThat(equivalence.eq(a, a)).isTrue()
      for (b in values) {
        assertThat(equivalence.eq(a, b)).isEqualTo(equivalence.eq(b, a))
        if (!equivalence.eq(a, b)) continue
        assertThat(equivalence.hash(a)).isEqualTo(equivalence.hash(b))
        for (c in values) {
          if (equivalence.eq(b, c)) assertThat(equivalence.eq(a, c)).isTrue()
        }
      }
    }
  }

  @Test
  fun `mixed numeric comparison agrees with exact arithmetic`() {
    val random = Random(0)
    repeat(10000) {
      val integer = random.nextLong()
      val floating = integer.toDouble()
      for (value in listOf(floating, Math.nextDown(floating), Math.nextUp(floating))) {
        val expected = BigDecimal.valueOf(integer).compareTo(BigDecimal(value)) == 0
        assertThat(equivalence.eq(integer, value)).isEqualTo(expected)
        assertThat(equivalence.eq(value, integer)).isEqualTo(expected)
        if (expected) assertThat(equivalence.hash(integer)).isEqualTo(equivalence.hash(value))
      }
    }
  }
}
