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
package org.pkl.core.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LexerTest {

  @Test
  fun isRegularIdentifier() {
    assertThat(Lexer.isRegularIdentifier("pigeon")).isTrue

    assertThat(Lexer.isRegularIdentifier("_pigeon")).isTrue
    assertThat(Lexer.isRegularIdentifier("f_red")).isTrue

    assertThat(Lexer.isRegularIdentifier("\$pigeon")).isTrue
    assertThat(Lexer.isRegularIdentifier("f\$red")).isTrue

    assertThat(Lexer.isRegularIdentifier("जावास्क्रिप्ट")).isTrue

    assertThat(Lexer.isRegularIdentifier("this")).isFalse
    assertThat(Lexer.isRegularIdentifier("😀")).isFalse
  }

  @Test
  fun maybeQuoteIdentifier() {
    assertThat(Lexer.maybeQuoteIdentifier("pigeon")).isEqualTo("pigeon")
    assertThat(Lexer.maybeQuoteIdentifier("_pigeon")).isEqualTo("_pigeon")
    assertThat(Lexer.maybeQuoteIdentifier("\$pigeon")).isEqualTo("\$pigeon")
    assertThat(Lexer.maybeQuoteIdentifier("जावास्क्रिप्ट")).isEqualTo("जावास्क्रिप्ट")

    assertThat(Lexer.maybeQuoteIdentifier("this")).isEqualTo("`this`")
    assertThat(Lexer.maybeQuoteIdentifier("😀")).isEqualTo("`😀`")
  }

  @Test
  fun `lexer keywords are sorted`() {
    assertThat(Lexer.KEYWORDS).isSortedAccordingTo { a, b -> a.compareTo(b.name) }
  }
}
