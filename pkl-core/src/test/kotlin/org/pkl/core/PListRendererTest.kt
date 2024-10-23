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
package org.pkl.core

import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.util.IoUtils
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

class PListRendererTest {
  @Test
  fun `render document`() {
    val evaluator = Evaluator.preconfigured()
    val module = evaluator.evaluate(ModuleSource.modulePath("org/pkl/core/rendererTest.pkl"))
    val writer = StringWriter()
    val renderer = ValueRenderers.plist(writer, "  ")

    renderer.renderDocument(module)
    val output = writer.toString()
    val expected = IoUtils.readClassPathResourceAsString(javaClass, "rendererTest.plist")

    assertThat(output.trim()).isEqualTo(expected.trim())

    parseAndValidateRenderedDocument(output)
  }

  @Test
  fun `rendered document ends in newline`() {
    val module = Evaluator.preconfigured().evaluate(ModuleSource.text("foo { bar = 0 }"))

    val writer = StringWriter()
    ValueRenderers.plist(writer, "  ").renderDocument(module)
    assertThat(writer.toString()).endsWith("\n")
  }

  private fun parseAndValidateRenderedDocument(output: String) {
    val builderFactory = DocumentBuilderFactory.newInstance().apply { isValidating = true }

    val builder =
      builderFactory.newDocumentBuilder().apply {
        setEntityResolver { _, _ ->
          InputSource(PListRendererTest::class.java.getResourceAsStream("PropertyList-1.0.dtd"))
        }
        setErrorHandler(
          object : ErrorHandler {
            override fun warning(exception: SAXParseException) {
              throw exception
            }

            override fun error(exception: SAXParseException) {
              throw exception
            }

            override fun fatalError(exception: SAXParseException) {
              throw exception
            }
          }
        )
      }

    builder.parse(output.byteInputStream())
  }
}
