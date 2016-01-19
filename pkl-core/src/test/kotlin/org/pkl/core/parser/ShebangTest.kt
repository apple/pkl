package org.pkl.core.parser

import org.pkl.core.Evaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.ModuleSource

class ShebangTest {
  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `shebang is ignored`() {
    val module = evaluator.evaluate(
      ModuleSource.text(
        """
          #!/usr/local/bin/pkl
          x = 1
        """.trimIndent()
      )
    )

    assertThat (module.properties["x"]).isEqualTo(1L)
  }
}
