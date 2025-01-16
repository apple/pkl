/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.newparser

import java.nio.file.Path
import kotlin.io.path.readText
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.pkl.core.parser.antlr.PklLexer
import org.pkl.core.parser.antlr.PklParser

@TestInstance(Lifecycle.PER_CLASS)
interface ParserComparisonTestInterface {
  @Test
  fun compareSnippetTests() {
    getSnippets().forEach { snippet ->
      assertThatCode { compare(snippet) }.`as`("path: $snippet").doesNotThrowAnyException()
    }
  }

  fun getSnippets(): List<Path>

  fun compare(path: Path) {
    val code = path.readText()
    val (sexp, antlrExp) = renderBoth(code)
    assertThat(sexp).`as`("path: $path").isEqualTo(antlrExp)
  }

  fun compare(code: String) {
    val (sexp, antlrExp) = renderBoth(code)
    assertThat(sexp).isEqualTo(antlrExp)
  }

  fun renderBoth(code: String): Pair<String, String> = Pair(renderCode(code), renderANTLRCode(code))

  companion object {
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
  }
}
