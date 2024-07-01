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

import java.io.StringWriter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.pkl.core.util.IoUtils
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class YamlRendererTest {
  @Test
  fun `render document`() {
    val evaluator = Evaluator.preconfigured()
    val module = evaluator.evaluate(ModuleSource.modulePath("org/pkl/core/rendererTest.pkl"))
    val writer = StringWriter()
    val renderer = ValueRenderers.yaml(writer, 2, true, false)

    renderer.renderDocument(module)
    val output = writer.toString()
    val expected = IoUtils.readClassPathResourceAsString(javaClass, "rendererTest.yaml")

    assertThat(output.trim()).isEqualTo(expected.trim())
    assertThatCode { Load(LoadSettings.builder().build()).loadFromString(output) }
      .doesNotThrowAnyException()
  }

  @Test
  fun `render YAML stream`() {
    val evaluator = Evaluator.preconfigured()
    val module =
      evaluator.evaluate(
        ModuleSource.text(
          """
        stream = new Listing {
          new Dynamic {
            name = "Pigeon"
            age = 42
          }
          new Listing {
            "one"
            "two"
            "three"
          }
          new Mapping {
            ["one"] = 1
            ["two"] = 2
            ["three"] = 3
          }
          "Blue Rock Ltd."
          12345
        }
        """
            .trimIndent()
        )
      )

    val writer = StringWriter()
    val renderer = ValueRenderers.yaml(writer, 2, true, true)

    renderer.renderDocument(module.getProperty("stream"))
    val output = writer.toString()

    assertThat(output.trim())
      .isEqualTo(
        """
      name: Pigeon
      age: 42
      ---
      - one
      - two
      - three
      ---
      one: 1
      two: 2
      three: 3
      --- Blue Rock Ltd.
      --- 12345
    """
          .trimIndent()
      )
  }

  @Test
  fun `rendered document ends in newline`() {
    val module = Evaluator.preconfigured().evaluate(ModuleSource.text("foo { bar = 0 }"))

    for (omitNullProperties in listOf(false, true)) {
      for (isStream in listOf(false, true)) {
        val writer = StringWriter()
        ValueRenderers.yaml(writer, 2, omitNullProperties, isStream).renderDocument(listOf(module))
        assertThat(writer.toString()).endsWith("\n")
      }
    }
  }

  @Test
  fun `render truthy strings, octals and number-like strings`() {
    val evaluator = Evaluator.preconfigured()
    val module =
      evaluator.evaluate(
        ModuleSource.text(
          """
        num1 = "50"
        num2 = "50.123"
        `60.123` = "60.123"
        yes = "yes"
        truth = "true"
        octalNumber = "0777"
        """
            .trimIndent()
        )
      )

    val writer = StringWriter()
    val renderer = ValueRenderers.yaml(writer, 2, true, false)

    renderer.renderDocument(module)
    val output = writer.toString()

    assertThat(output.trim())
      .isEqualTo(
        """
      num1: '50'
      num2: '50.123'
      '60.123': '60.123'
      'yes': 'yes'
      truth: 'true'
      octalNumber: '0777'
    """
          .trimIndent()
      )
  }
}
