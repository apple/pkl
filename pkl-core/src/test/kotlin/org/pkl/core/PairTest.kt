/*
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
package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PairTest {
  @Test
  fun basics() {
    val pair = Pair(3, "five")
    assertThat(pair.first).isEqualTo(3)
    assertThat(pair.second).isEqualTo("five")
    assertThat(pair.toString()).isEqualTo("Pair(3, five)")
    assertThat(pair.classInfo).isEqualTo(PClassInfo.Pair)
  }

  @Test
  fun iterator() {
    val pair = Pair(3, "five")
    val iterator = pair.iterator()
    assertThat(iterator.next()).isEqualTo(3)
    assertThat(iterator.next()).isEqualTo("five")
    assertThrows<NoSuchElementException> { iterator.next() }
  }

  @Test
  fun equals() {
    val pair1 = Pair(3, "five")
    val pair2 = Pair(3, "five")
    val pair3 = Pair("five", 3)

    assertThat(pair1).isEqualTo(pair1)
    assertThat(pair1).isEqualTo(pair2)
    assertThat(pair2).isEqualTo(pair1)

    assertThat(pair1).isNotEqualTo(pair3)
    assertThat(pair3).isNotEqualTo(pair2)
  }

  @Test
  fun hash() {
    val pair1 = Pair(3, "five")
    val pair2 = Pair("five", 3)

    assertThat(pair1.hashCode()).isEqualTo(pair1.hashCode())

    assertThat(pair1.hashCode()).isNotEqualTo(pair2.hashCode())
  }
}
