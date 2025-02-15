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
package org.pkl.core.parser

import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.readText
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.pkl.core.parser.antlr.PklLexer
import org.pkl.core.parser.antlr.PklParser

@Execution(ExecutionMode.CONCURRENT)
interface ParserComparisonTestInterface {
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun compareSnippetTests() {
    SoftAssertions.assertSoftly { softly ->
      getSnippets()
        .parallelStream()
        .map { Pair(it.pathString, it.readText()) }
        .forEach { (path, snippet) ->
          try {
            compare(snippet, path, softly)
          } catch (e: ParserError) {
            softly.fail("path: $path. Message: ${e.message}", e)
          }
        }
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun compareSnippetTestsSpans() {
    SoftAssertions.assertSoftly { softly ->
      getSnippets()
        .parallelStream()
        .map { Pair(it.pathString, it.readText()) }
        .forEach { (path, snippet) ->
          try {
            compareSpans(snippet, path, softly)
          } catch (e: ParserError) {
            softly.fail("path: $path. Message: ${e.message}", e)
          }
        }
    }
  }

  fun getSnippets(): List<Path>

  fun compare(code: String, path: String? = null, softly: SoftAssertions? = null) {
    val (sexp, antlrExp) = renderBoth(code)
    when {
      (path != null && softly != null) ->
        softly.assertThat(sexp).`as`("path: $path").isEqualTo(antlrExp)
      else -> assertThat(sexp).isEqualTo(antlrExp)
    }
  }

  fun compareSpans(code: String, path: String, softly: SoftAssertions) {
    // Our ANTLR grammar always start doc comment spans in the beginning of the line,
    // even though they may have leading spaces.
    // This is a regression, but it's a bugfix
    if (path.endsWith("annotation1.pkl")) return

    val parser = Parser()
    val mod = parser.parseModule(code)
    val lexer = PklLexer(ANTLRInputStream(code))
    val antlr = PklParser(CommonTokenStream(lexer))
    val antlrMod = antlr.module()

    val comparer = SpanComparison(path, softly)
    comparer.compare(mod, antlrMod)
  }

  fun renderBoth(code: String): Pair<String, String> = Pair(renderCode(code), renderANTLRCode(code))

  companion object {
    private fun renderCode(code: String): String {
      val parser = Parser()
      val mod = parser.parseModule(code)
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
