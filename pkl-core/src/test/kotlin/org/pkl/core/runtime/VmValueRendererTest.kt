package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VmValueRendererTest {
  private val renderer = VmValueRenderer.singleLine(80)

  @Test
  fun `render null without default`() {
    val none = VmNull.withoutDefault()
    assertThat(renderer.render(none)).isEqualTo("null")
  }

  @Test
  fun `render null with default`() {
    val none = VmNull.withDefault("default")
    assertThat(renderer.render(none)).isEqualTo("null")
  }
}
