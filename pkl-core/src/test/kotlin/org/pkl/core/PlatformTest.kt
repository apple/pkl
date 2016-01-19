package org.pkl.core

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class PlatformTest {
  @Test
  fun `get current platform`() {
    assertThatCode { Platform.current() }.doesNotThrowAnyException()
  }
}
