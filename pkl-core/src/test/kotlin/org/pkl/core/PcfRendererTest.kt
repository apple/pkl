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
import org.junit.jupiter.api.Test
import org.pkl.core.util.IoUtils

class PcfRendererTest {
  private val writer = StringWriter()
  private val renderer = ValueRenderers.pcf(writer, "  ", false, false)

  @Test
  fun `render document`() {
    val evaluator = Evaluator.preconfigured()
    val module = evaluator.evaluate(ModuleSource.modulePath("org/pkl/core/rendererTest.pkl"))

    renderer.renderDocument(module)
    val output = writer.toString()
    val expected = IoUtils.readClassPathResourceAsString(javaClass, "rendererTest.pcf")

    assertThat(output.trim()).isEqualTo(expected.trim())

    // TODO: make pcf a pkl subset again
    // assertThatCode { evaluator.evaluateText(output) }.doesNotThrowAnyException()
  }

  @Test
  fun `rendered document ends in newline`() {
    val module =
      EvaluatorBuilder.preconfigured().build().evaluate(ModuleSource.text("foo { bar = 0 }"))

    val writer = StringWriter()
    ValueRenderers.pcf(writer, "  ", false, false).renderDocument(module)
    assertThat(writer.toString()).endsWith("\n")
  }

  @Test
  fun `rendering with and without null properties`() {
    val cases =
      listOf(
        true to
          """
          baz {
            qux = 42
            corge = List(null, 1337, null, "Hello World")
            grault = Map("garply", null, "waldo", 42, "pigeon", null)
          }
        """
            .trimIndent(),
        false to
          """
          foo = null
          bar = null
          baz {
            qux = 42
            quux = null
            corge = List(null, 1337, null, "Hello World")
            grault = Map("garply", null, "waldo", 42, "pigeon", null)
          }
        """
            .trimIndent()
      )

    val module =
      Evaluator.preconfigured()
        .evaluate(
          ModuleSource.text(
            """
        foo = null
        bar = null
        baz {
          qux = 42
          quux = null
          corge = new Listing {
            null
            1337
            null
            "Hello World"
          }
          grault = new Mapping {
            ["garply"] = null
            ["waldo"] = 42
            ["pigeon"] = null
          }
        }
      """
              .trimIndent()
          )
        )
    for ((omitNullProperties, expected) in cases) {
      val writer = StringWriter()
      ValueRenderers.pcf(writer, "  ", omitNullProperties, false).renderDocument(module)
      assertThat(writer.toString().trim()).isEqualTo(expected)
    }
  }

  // TODO: ada
  // can happen in REPL or when rendering manually constructed container
  /*  @Test
  fun `render container with unevaluated element`() {
    renderer.renderValue(PObject(PClassInfo.Mapping, mapOf("one" to 1L, "two" to null)))

    assertThat(writer.toString().trim()).isEqualTo("""
      {
        one = 1
        two = ?
      }
    """.trimIndent())
  }*/
}
