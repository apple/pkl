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
package org.pkl.core.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource

// tests whitespace handling in multi-line string literals that cannot be reliably tested via
// snippets
// (e.g. due to editors not displaying and/or automatically removing whitespace)
class MultiLineStringLiteralTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `multi-line strings have unix newlines`() {
    val module =
      evaluator.evaluate(ModuleSource.text("x = \"\"\"\none\rtwo\nthree\r\nfour\n\"\"\""))
    assertThat(module.properties["x"]).isEqualTo("one\ntwo\nthree\nfour")
  }

  @Test
  fun `raw multi-line strings have unix newlines`() {
    val module =
      evaluator.evaluate(ModuleSource.text("x = #\"\"\"\none\rtwo\nthree\r\nfour\n\"\"\"#"))
    assertThat(module.properties["x"]).isEqualTo("one\ntwo\nthree\nfour")
  }
}
