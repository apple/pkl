package org.pkl.core

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class ReleaseTest {
  @Test
  fun `get current release`() {
    assertThatCode { Release.current() }.doesNotThrowAnyException()
  }
}
