package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PNullTest {
  @Test
  fun basics() {
    assertThat(PNull.getInstance()).isEqualTo(PNull.getInstance())
    assertThat(PNull.getInstance().hashCode()).isEqualTo(PNull.getInstance().hashCode())
    assertThat(PNull.getInstance().classInfo).isEqualTo(PClassInfo.Null)
    assertThat(PNull.getInstance().toString()).isEqualTo("null")
  }
}
