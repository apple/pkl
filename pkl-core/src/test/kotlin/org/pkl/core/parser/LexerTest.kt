package org.pkl.core.parser

import org.antlr.v4.runtime.CommonToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.parser.antlr.PklLexer

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
  fun isKeyword() {
    assertThat(Lexer.isKeyword(CommonToken(PklLexer.THIS))).isTrue
    assertThat(Lexer.isKeyword(CommonToken(PklLexer.MINUS))).isFalse
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
}
