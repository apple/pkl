package org.pkl.core.module

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class ModulePathResolverTest {
  @Test
  fun `close without having been used`() {
    val resolver = ModulePathResolver(listOf())
    assertThatCode { resolver.close() }.doesNotThrowAnyException()
  }
}
