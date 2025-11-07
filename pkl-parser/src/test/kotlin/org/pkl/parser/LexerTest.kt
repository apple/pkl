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
package org.pkl.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
  fun lexSingleBacktick() {
    val thrown = assertThrows<ParserError> { Lexer("`").next() }
    assertThat(thrown).hasMessageContaining("Unexpected character `EOF`")
  }

  @Test
  fun rejectsSentinelBetweenTokens() {
    val lexerFFFF = Lexer("// Comment with \uFFFF character\nclass \uFFFF Bar")
    assertThat(lexerFFFF.next()).isEqualTo(Token.LINE_COMMENT)
    assertThat(lexerFFFF.next()).isEqualTo(Token.CLASS)
    val thrown = assertThrows<ParserError> { lexerFFFF.next() }
    assertThat(thrown).hasMessageContaining("Invalid identifier")
  }

  @Test
  fun acceptsAllUnicodeCodepointsInComments() {
    // Test valid Unicode codepoints can appear literally
    // without being misinterpreted as EOF.

    // Test the previously problematic U+7FFF (Short.MAX_VALUE)
    val lexer7FFF = Lexer("// Comment with \u7FFF character\nclass Foo")
    assertThat(lexer7FFF.next()).isEqualTo(Token.LINE_COMMENT)
    assertThat(lexer7FFF.next()).isEqualTo(Token.CLASS)
    assertThat(lexer7FFF.next()).isEqualTo(Token.IDENTIFIER)
    assertThat(lexer7FFF.next()).isEqualTo(Token.EOF)

    // Test U+FFFF (Character.MAX_VALUE)
    val lexerFFFF = Lexer("// Comment with \uFFFF character\nclass Bar")
    assertThat(lexerFFFF.next()).isEqualTo(Token.LINE_COMMENT)
    assertThat(lexerFFFF.next()).isEqualTo(Token.CLASS)
    assertThat(lexerFFFF.next()).isEqualTo(Token.IDENTIFIER)
    assertThat(lexerFFFF.next()).isEqualTo(Token.EOF)

    // Test a range of codepoints including edge cases
    val testCodepoints =
      listOf(
        0x0000, // NULL
        0x0001, // Start of heading
        0x007F, // DELETE
        0x0080, // First non-ASCII
        0x7FFE, // One before the old problematic value
        0x7FFF, // Old EOF sentinel (Short.MAX_VALUE)
        0x8000, // One after the old problematic value
        0xFFFE, // One before Character.MAX_VALUE
        0xFFFF, // Character.MAX_VALUE (noncharacter)
      )

    for (codepoint in testCodepoints) {
      val char = codepoint.toChar()
      // Put the test character in a comment, followed by actual code tokens
      val input = "// Test $char\nmodule Test"
      val lexer = Lexer(input)
      assertThat(lexer.next())
        .withFailMessage("Codepoint U+%04X should be accepted in comment", codepoint)
        .isEqualTo(Token.LINE_COMMENT)
      assertThat(lexer.next())
        .withFailMessage(
          "Codepoint U+%04X should not terminate input early (expecting MODULE)",
          codepoint,
        )
        .isEqualTo(Token.MODULE)
      assertThat(lexer.next())
        .withFailMessage(
          "Codepoint U+%04X should not terminate input early (expecting IDENTIFIER)",
          codepoint,
        )
        .isEqualTo(Token.IDENTIFIER)
      assertThat(lexer.next()).isEqualTo(Token.EOF)
    }
  }
}
