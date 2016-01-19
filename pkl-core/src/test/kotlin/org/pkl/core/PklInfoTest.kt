package org.pkl.core

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class PklInfoTest {
  @Test
  fun `get current info`() {
    assertThatCode { PklInfo.current() }.doesNotThrowAnyException()
  }
}
