package org.pkl.core

import org.pkl.core.util.IoUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// tests language-internal renderers
// uses same input/output files as Pcf/Json/Yaml/PListRendererTest
class EvaluateOutputTextTest {
  @Test
  fun `render Pcf`() {
    checkRenderedOutput(OutputFormat.PCF)
  }

  @Test
  fun `render JSON`() {
    checkRenderedOutput(OutputFormat.JSON)
  }

  @Test
  fun `render YAML`() {
    checkRenderedOutput(OutputFormat.YAML)
  }

  @Test
  fun `render plist`() {
    checkRenderedOutput(OutputFormat.PLIST)
  }

  private fun checkRenderedOutput(format: OutputFormat) {
    val evaluator = EvaluatorBuilder.preconfigured()
      .setOutputFormat(format)
      .build()

    val output = evaluator.evaluateOutputText(
      ModuleSource.modulePath("org/pkl/core/rendererTest.pkl")
    )
    val expected = IoUtils.readClassPathResourceAsString(javaClass, "rendererTest.$format")

    assertThat(output.trim()).isEqualTo(expected.trim())
  }
}
