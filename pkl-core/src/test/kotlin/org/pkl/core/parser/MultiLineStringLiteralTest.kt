package org.pkl.core.parser

import org.pkl.core.Evaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.ModuleSource

// tests whitespace handling in multi-line string literals that cannot be reliably tested via snippets
// (e.g. due to editors not displaying and/or automatically removing whitespace)
class MultiLineStringLiteralTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `multi-line strings have unix newlines`() {
    val module = evaluator.evaluate(
      ModuleSource.text(
        "x = \"\"\"\none\rtwo\nthree\r\nfour\n\"\"\""
      )
    )
    assertThat(module.properties["x"]).isEqualTo("one\ntwo\nthree\nfour")
  }

  @Test
  fun `raw multi-line strings have unix newlines`() {
    val module = evaluator.evaluate(
      ModuleSource.text("x = #\"\"\"\none\rtwo\nthree\r\nfour\n\"\"\"#")
    )
    assertThat(module.properties["x"]).isEqualTo("one\ntwo\nthree\nfour")
  }
}
