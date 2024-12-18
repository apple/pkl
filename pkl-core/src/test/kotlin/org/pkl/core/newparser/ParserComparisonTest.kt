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
@file:Suppress("DEPRECATION")

package org.pkl.core.newparser

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.commons.walk
import org.pkl.core.parser.antlr.PklLexer
import org.pkl.core.parser.antlr.PklParser

class ParserComparisonTest {

  @Test
  fun testAsPrecedence() {
    compare("prop = 3 + bar as Int * 5")
  }

  @Test
  fun testStringInterpolation() {
    compare(
      """
      prop = "\(bar)"
      prop2 = "foo \(bar)"
      prop3 = "\(bar) foo"
      prop4 = "foo \(bar + baz) foo"
      """
        .trimIndent()
    )

    compare(
      """
      prop = ${"\"\"\""}\(bar)${"\"\"\""}
      prop2 = ${"\"\"\""}foo \(bar)${"\"\"\""}
      prop3 = ${"\"\"\""}\(bar) foo${"\"\"\""}
      prop4 = ${"\"\"\""}foo \(bar + baz) foo${"\"\"\""}
      """
        .trimIndent()
    )
  }

  @Test
  fun compareSnippetTests() {
    getSnippets().forEach { snippet ->
      val text = snippet.readText()
      compare(text)
    }
  }

  companion object {
    private fun compare(code: String) {
      val sexp = renderCode(code)
      val antlrExp = renderANTLRCode(code)
      assertThat(sexp).isEqualTo(antlrExp)
    }

    private fun renderCode(code: String): String {
      val lexer = Lexer(code)
      val parser = Parser(lexer)
      val mod = parser.parseModule() ?: return "(module)"
      val renderer = SexpRenderer()
      return renderer.render(mod)
    }

    private fun renderANTLRCode(code: String): String {
      val lexer = PklLexer(ANTLRInputStream(code))
      val parser = PklParser(CommonTokenStream(lexer))
      val mod = parser.module()
      val renderer = ANTLRSexpRenderer()
      return renderer.render(mod)
    }

    // tests that are not syntactically valid Pkl
    private val exceptions =
      setOf(
        "stringError1.pkl",
        "annotationIsNotExpression2.pkl",
        "amendsRequiresParens.pkl",
        "errors/parser18.pkl",
        "errors/nested1.pkl",
        "errors/invalidCharacterEscape.pkl",
        "errors/invalidUnicodeEscape.pkl",
        "errors/unterminatedUnicodeEscape.pkl",
        "errors/keywordNotAllowedHere1.pkl",
        "errors/keywordNotAllowedHere2.pkl",
        "errors/keywordNotAllowedHere3.pkl",
        "errors/keywordNotAllowedHere4.pkl",
        "errors/moduleWithHighMinPklVersionAndParseErrors.pkl",
        "errors/underscore.pkl",
      )

    private val regexExceptions =
      setOf(Regex(".*/errors/delimiters/.*"), Regex(".*/errors/parser\\d+\\.pkl"))

    private fun getSnippets(): List<Path> {
      return Path("src/test/files/LanguageSnippetTests/input")
        .walk()
        .filter { path ->
          val pathStr = path.toString()
          path.extension == "pkl" &&
            !exceptions.any { pathStr.endsWith(it) } &&
            !regexExceptions.any { it.matches(pathStr) }
        }
        .toList()
    }
  }
}
