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
package org.pkl.formatter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.pkl.parser.GenericParserError

/** The deepest input accepted by the parser must be formattable on a small thread stack. */
class FormatterDepthTest {
  companion object {
    private const val STACK_SIZE = 512L * 1024
    private const val NESTING_MAX = 48
    private const val CHAIN_MAX = 115

    private val shapes: Map<String, Pair<Int, (Int) -> String>> =
      mapOf(
        "parens" to (NESTING_MAX to { n -> "x = " + "(".repeat(n) + "1" + ")".repeat(n) }),
        "types" to (NESTING_MAX to { n -> "x: " + "List<".repeat(n) + "Int" + ">".repeat(n) }),
        "objects" to (NESTING_MAX to { n -> "x " + "{ y ".repeat(n) + "{}" + " }".repeat(n) }),
        "binary operator" to (CHAIN_MAX to { n -> "x = " + "1 + ".repeat(n) + "1" }),
        "qualified access" to (CHAIN_MAX to { n -> "x = a" + ".b".repeat(n) }),
        "subscript" to (CHAIN_MAX to { n -> "x = a" + "[1]".repeat(n) }),
        "constrained type" to (CHAIN_MAX to { n -> "x: Int" + "(isPositive)".repeat(n) }),
      )

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
  fun `deepest accepted input formats`(): List<DynamicTest> = shapes.map { (shape, spec) ->
    dynamicTest(shape) {
      val (n, gen) = spec
      assertThat(runOnSmallStack { Formatter().format(gen(n)) }).isNull()
    }
  }

  @TestFactory
  fun `input beyond limits fails with a parse error`(): List<DynamicTest> =
    shapes.map { (shape, spec) ->
      dynamicTest(shape) {
        val (_, gen) = spec
        val thrown = runOnSmallStack { Formatter().format(gen(100_000)) }
        assertThat(thrown).isInstanceOf(GenericParserError::class.java)
        assertThat(thrown).hasMessageContaining("nested too deeply")
      }
    }
}
