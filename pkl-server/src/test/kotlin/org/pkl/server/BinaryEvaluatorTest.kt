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
package org.pkl.server

import java.nio.file.Path
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.test.msgpackDebugRendering
import org.pkl.core.*
import org.pkl.core.evaluatorSettings.TraceMode
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.resource.ResourceReaders

class BinaryEvaluatorTest {
  private val evaluator =
    BinaryEvaluator(
      StackFrameTransformers.defaultTransformer,
      SecurityManagers.standard(
        listOf(Pattern.compile(".*")),
        listOf(Pattern.compile(".*")),
        SecurityManagers.defaultTrustLevels,
        Path.of(""),
      ),
      HttpClient.dummyClient(),
      Loggers.noop(),
      listOf(ModuleKeyFactories.standardLibrary),
      listOf(ResourceReaders.environmentVariable(), ResourceReaders.externalProperty()),
      mapOf(),
      mapOf(),
      null,
      null,
      null,
      null,
      TraceMode.COMPACT,
    )

  private fun evaluate(text: String, expression: String?) =
    evaluator.evaluate(ModuleSource.text(text), expression)

  @Test
  fun `evaluate whole module`() {
    val bytes = evaluate("foo = 1", null)
    assertThat(bytes.msgpackDebugRendering)
      .isEqualTo(
        """
      - 1
      - text
      - repl:text
      - 
        - 
          - 16
          - foo
          - 1
    """
          .trimIndent()
      )
  }

  @Test
  fun `evaluate subpath`() {
    val bytes =
      evaluate(
        """
      foo {
        bar = 2
      }
    """
          .trimIndent(),
        "foo.bar",
      )

    assertThat(bytes.asInt()).isEqualTo(2)
  }

  @Test
  fun `evaluate output text`() {
    val bytes =
      evaluate(
        """
      foo {
        bar = 2
      }
      
      output {
        renderer = new YamlRenderer {}
      }
    """
          .trimIndent(),
        "output.text",
      )

    assertThat(bytes.asString())
      .isEqualTo(
        """
      foo:
        bar: 2

    """
          .trimIndent()
      )
  }

  @Test
  fun `evaluate let expression`() {
    val bytes = evaluate("foo = 1", "let (bar = 2) foo + bar")

    assertThat(bytes.asInt()).isEqualTo(3)
  }

  @Test
  fun `evaluate import expression`() {
    val bytes = evaluate("", """import("pkl:release").current.documentation.homepage""")

    assertThat(bytes.asString()).startsWith("https://pkl-lang.org/")
  }

  @Test
  fun `evaluate expression with invalid syntax`() {
    val error = assertThrows<PklException> { evaluate("foo = 1", "<>!!!") }

    assertThat(error).hasMessageContaining("Unexpected token")
    assertThat(error).hasMessageContaining("<>!!!")
  }

  @Test
  fun `evaluate non-expression`() {
    val error = assertThrows<PklException> { evaluate("bar = 2", "bar = 15") }

    assertThat(error).hasMessageContaining("Unexpected token")
    assertThat(error).hasMessageContaining("bar = 15")
  }

  @Test
  fun `evaluate semantically invalid expression`() {
    val error = assertThrows<PklException> { evaluate("foo = 1", "foo as String") }

    assertThat(error).hasMessageContaining("Expected value of type `String`, but got type `Int`")
  }
}
