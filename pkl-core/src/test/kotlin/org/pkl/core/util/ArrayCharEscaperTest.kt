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
package org.pkl.core.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArrayCharEscaperTest {
  @Test
  fun `basic usage`() {
    val escaper =
      ArrayCharEscaper.builder()
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
      ArrayCharEscaper.builder().withEscape('a', "aa").withEscape('Ɇ', "ee").build()
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
    val escaper = ArrayCharEscaper.builder().withEscape('ä', "ae").build()

    val fox = "The quick brown fox jumps over the lazy dog."
    assertThat(escaper.escape(fox)).isSameAs(fox)
  }
}
