package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArrayCharEscaperTest {
  @Test
  fun `basic usage`() {
    val escaper = ArrayCharEscaper.builder()
      .withEscape('ä', "ae")
      .withEscape('ö', "oe")
      .withEscape('ü', "ue")
      .build()

    assertThat(escaper.escape("")).isEqualTo("")
    assertThat(escaper.escape("äää")).isEqualTo("aeaeae")
    assertThat(escaper.escape("äxöyüz")).isEqualTo("aexoeyuez")

    val fox = "The quick brown fox jumps over the lazy dog."
    assertThat(escaper.escape(fox)).isEqualTo(fox)

    assertThat(escaper.escape("ä😀😈😍öö😎😡🤢üüü🤣")).isEqualTo("ae😀😈😍oeoe😎😡🤢ueueue🤣")
  }

  @Test
  fun `enforces size limit`() {
    assertThrows<IllegalStateException> {
      ArrayCharEscaper.builder()
        .withEscape('a', "aa")
        .withEscape('Ɇ', "ee")
        .build()
    }
  }

  @Test
  fun `works if no escapes defined`() {
    val escaper = ArrayCharEscaper.builder().build()

    assertThat(escaper.escape("")).isEqualTo("")
    assertThat(escaper.escape("äää")).isEqualTo("äää")
    assertThat(escaper.escape("äxöyüz")).isEqualTo("äxöyüz")
  }

  @Test
  fun `returns original string if no escaping required`() {
    val escaper = ArrayCharEscaper.builder()
      .withEscape('ä', "ae")
      .build()

    val fox = "The quick brown fox jumps over the lazy dog."
    assertThat(escaper.escape(fox)).isSameAs(fox)
  }
}
