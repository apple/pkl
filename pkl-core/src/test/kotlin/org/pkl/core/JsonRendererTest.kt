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
    val module = evaluator.evaluate(
      ModuleSource.modulePath("org/pkl/core/rendererTest.pkl")
    )
    val writer = StringWriter()
    val renderer = ValueRenderers.json(writer, "  ", true)

    renderer.renderDocument(module)
    val output = writer.toString()
    val expected = IoUtils.readClassPathResourceAsString(javaClass, "rendererTest.json")

    assertThat(output).isEqualTo(expected)
    assertThatCode { JsonParser(object : JsonHandler<Any, Any>() {}).parse(output) }.doesNotThrowAnyException()
  }

  @Test
  fun `rendered document ends in newline`() {
    val module: PModule = Evaluator
      .preconfigured()
      .evaluate(ModuleSource.text("foo { bar = 0 }"))

    for (omitNullProperties in listOf(false, true)) {
      val writer = StringWriter()
      ValueRenderers.json(writer, "  ", omitNullProperties).renderDocument(module)
      assertThat(writer.toString()).endsWith("\n")
    }
  }
}
