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
import org.pkl.core.util.json.JsonHandler
import org.pkl.core.util.json.JsonParser

class JsonRendererTest {
  @Test
  fun `render document`() {
    val evaluator = Evaluator.preconfigured()
    val module = evaluator.evaluate(ModuleSource.modulePath("org/pkl/core/rendererTest.pkl"))
    val writer = StringWriter()
    val renderer = ValueRenderers.json(writer, "  ", true)

    renderer.renderDocument(module)
    val output = writer.toString()
    val expected = IoUtils.readClassPathResourceAsString(javaClass, "rendererTest.json")

    assertThat(output).isEqualTo(expected)
    assertThatCode { JsonParser(object : JsonHandler<Any, Any>() {}).parse(output) }
      .doesNotThrowAnyException()
  }

  @Test
  fun `rendered document ends in newline`() {
    val module: PModule = Evaluator.preconfigured().evaluate(ModuleSource.text("foo { bar = 0 }"))

    for (omitNullProperties in listOf(false, true)) {
      val writer = StringWriter()
      ValueRenderers.json(writer, "  ", omitNullProperties).renderDocument(module)
      assertThat(writer.toString()).endsWith("\n")
    }
  }
}
