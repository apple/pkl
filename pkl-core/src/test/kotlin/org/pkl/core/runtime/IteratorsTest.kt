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
