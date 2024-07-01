/**
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
package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.fusesource.jansi.Ansi
import org.junit.jupiter.api.*

class ErrorColoringTest {
  companion object {
    val evaluator by lazy { Evaluator.preconfigured() }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      evaluator.close()
    }
  }

  private fun evaluate(program: String, expression: String): Any {
    return evaluator.evaluateExpression(ModuleSource.text(program), expression)
  }

  @BeforeEach
  fun setup() {
    // Enable colouring before each test
    Ansi.setEnabled(true)
  }

  @AfterEach
  fun teardown() {
    // Disable colouring after each test
    Ansi.setEnabled(false)
  }

  @Test
  fun `simple error`() {
    val error = assertThrows<PklException> { evaluate("bar = 2", "bar = 15") }

    assertThat(error)
      .message()
      .contains("\u001B[31m–– Pkl Error ––\u001B[m")
      .contains("\u001B[94m1 | \u001B[m")
      .contains("\u001B[0;31m^")
  }

  @Test
  fun `repeated error`() {
    val error =
      assertThrows<PklException> {
        evaluate(
          """self: String = "Strings; if they were lazy, you could tie the knot on \(self.take(7))"""",
          "self"
        )
      }
    assertThat(error)
      .message()
      .contains("[91mA stack overflow occurred.[m")
      .contains("[93m┌─ [0;1;35m")
      .contains("[m repetitions of:")
      .contains("[93m│ [94m1 | [m")
      .contains("[0;31m^^^^[m")
  }
}
