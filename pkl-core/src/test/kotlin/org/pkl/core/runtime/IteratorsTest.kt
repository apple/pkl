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
package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.runtime.Iterators.*

class IteratorsTest {
  @Test
  fun `forward iterator`() {
    val iter = TruffleIterator(listOf(1, 2, 3))

    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(1)
    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(2)
    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(3)
    assertThat(iter.hasNext()).isFalse
  }

  @Test
  fun `empty forward iterator`() {
    val iter = TruffleIterator(listOf<Any>())

    assertThat(iter.hasNext()).isFalse
  }

  @Test
  fun `reverse iterator`() {
    val iter = ReverseTruffleIterator(listOf(1, 2, 3))

    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(3)
    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(2)
    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(1)
    assertThat(iter.hasNext()).isFalse
  }

  @Test
  fun `empty reverse iterator`() {
    val iter = ReverseTruffleIterator(listOf<Any>())

    assertThat(iter.hasNext()).isFalse
  }

  @Test
  fun `reverse array iterator`() {
    val iter = ReverseArrayIterator(arrayOf(1, 2, 3))

    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(3)
    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(2)
    assertThat(iter.hasNext()).isTrue
    assertThat(iter.next()).isEqualTo(1)
    assertThat(iter.hasNext()).isFalse
  }

  @Test
  fun `empty reverse array iterator`() {
    val iter = ReverseArrayIterator(arrayOf())

    assertThat(iter.hasNext()).isFalse
  }
}
