/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Malicious input must produce a regular parse error instead of a [StackOverflowError], even on a
 * small thread stack.
 */
class ParserDepthTest {
  companion object {
    private const val STACK_SIZE = 512L * 1024
    private const val HUGE = 100_000

    private val nesting: Map<String, (Int) -> String> =
      mapOf(
        "parens" to { n -> "x = " + "(".repeat(n) + "1" + ")".repeat(n) },
        "unary minus" to { n -> "x = " + "-".repeat(n) + "1" },
        "types" to { n -> "x: " + "List<".repeat(n) + "Int" + ">".repeat(n) },
        "parenthesized types" to { n -> "x: " + "(".repeat(n) + "Int" + ")".repeat(n) },
        "objects" to { n -> "x " + "{ y ".repeat(n) + "{}" + " }".repeat(n) },
        "string interpolation" to { n -> "x = " + "\"\\(".repeat(n) + "1" + ")\"".repeat(n) },
        "if" to { n -> "x = " + "if (true) 1 else ".repeat(n) + "2" },
        "let" to { n -> "x = " + "let (a = 1) ".repeat(n) + "2" },
        "function literal" to { n -> "x = " + "(a) -> ".repeat(n) + "2" },
        "right associative operator" to { n -> "x = " + "a ?? ".repeat(n) + "b" },
        "pow" to { n -> "x = " + "a ** ".repeat(n) + "b" },
      )

    // shapes that build deep, left-nested trees without recursing in the parser
    private val chains: Map<String, (Int) -> String> =
      mapOf(
        "binary operator" to { n -> "x = " + "1 + ".repeat(n) + "1" },
        "qualified access" to { n -> "x = a" + ".b".repeat(n) },
        "subscript" to { n -> "x = a" + "[1]".repeat(n) },
        "non-null" to { n -> "x = a" + "!!".repeat(n) },
        "constrained type" to { n -> "x: Int" + "(isPositive)".repeat(n) },
        "amends" to { n -> "x = (y) " + "{} ".repeat(n) },
      )

    private fun parsers(): Map<String, (String) -> Unit> =
      mapOf(
        "Parser" to { src -> Parser().parseModule(src) },
        "GenericParser" to { src -> GenericParser().parseModule(src) },
      )

    /** Runs [action] on a thread with a small stack, and returns what it threw (if anything). */
    private fun runOnSmallStack(action: () -> Unit): Throwable? {
      var thrown: Throwable? = null
      val thread =
        Thread(null, { thrown = runCatching(action).exceptionOrNull() }, "test", STACK_SIZE)
      thread.start()
      thread.join()
      return thrown
    }
  }

  @TestFactory
  fun `deep nesting fails with a parse error`(): List<DynamicTest> =
    nesting.flatMap { (shape, gen) ->
      parsers().map { (parser, parse) ->
        dynamicTest("$parser: $shape x $HUGE") {
          val thrown = runOnSmallStack { parse(gen(HUGE)) }
          assertThat(thrown).isNotNull
          assertThat(thrown).isNotInstanceOf(StackOverflowError::class.java)
          assertThat(thrown).hasMessageContaining("nested too deeply")
        }
      }
    }

  @TestFactory
  fun `long chains are parsed iteratively`(): List<DynamicTest> = chains.flatMap { (shape, gen) ->
    parsers().map { (parser, parse) ->
      dynamicTest("$parser: $shape x $HUGE") {
        assertThat(runOnSmallStack { parse(gen(HUGE)) }).isNull()
      }
    }
  }

  @TestFactory
  fun `flat repetition is unaffected`(): List<DynamicTest> =
    mapOf(
        "union type" to "x: " + "Int|".repeat(HUGE) + "Int",
        "listing elements" to "x = new Listing { " + "1\n".repeat(HUGE) + "}",
        "properties" to (1..10_000).joinToString("\n") { "p$it = 1" },
      )
      .flatMap { (shape, src) ->
        parsers().map { (parser, parse) ->
          dynamicTest("$parser: $shape") { assertThat(runOnSmallStack { parse(src) }).isNull() }
        }
      }
}
