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
package org.pkl.core

import java.io.StringWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.util.IoUtils

class PropertiesRendererTest {
  @Test
  fun `render document`() {
    val evaluator = Evaluator.preconfigured()
    val module =
      evaluator.evaluate(ModuleSource.modulePath("org/pkl/core/propertiesRendererTest.pkl"))
    val writer = StringWriter()
    val renderer = ValueRenderers.properties(writer, true, false)

    renderer.renderDocument(module)
    val output = writer.toString()
    val expected =
      IoUtils.readClassPathResourceAsString(javaClass, "propertiesRendererTest.properties")

    assertThat(output).isEqualTo(expected)
  }

  @Test
  fun `render unsupported document values`() {
    val unsupportedValues =
      listOf(
        "List()",
        "new Listing {}",
        "Map()",
        "new Mapping {}",
        "Set()",
        "new PropertiesRenderer {}",
        "new Dynamic {}",
      )

    unsupportedValues.forEach {
      val evaluator = Evaluator.preconfigured()
      val renderer = ValueRenderers.properties(StringWriter(), true, false)

      val module = evaluator.evaluate(ModuleSource.text("value = $it"))
      assertThrows<RendererException> { renderer.renderValue(module) }
    }
  }

  @Test
  fun `rendered document ends in newline`() {
    val module = Evaluator.preconfigured().evaluate(ModuleSource.text("foo { bar = 0 }"))

    for (omitNullProperties in listOf(false, true)) {
      for (restrictCharSet in listOf(false, true)) {
        val writer = StringWriter()
        ValueRenderers.properties(writer, omitNullProperties, restrictCharSet)
          .renderDocument(module)
        assertThat(writer.toString()).endsWith("\n")
      }
    }
  }
}
