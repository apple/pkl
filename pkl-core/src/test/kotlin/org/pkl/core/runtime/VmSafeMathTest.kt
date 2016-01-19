package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VmSafeMathTest {
  @Test
  fun `negate long`() {
    assertThat(VmSafeMath.negate(0)).isEqualTo(0)
    assertThat(VmSafeMath.negate(1)).isEqualTo(-1)
    assertThat(VmSafeMath.negate(-1)).isEqualTo(1)
    assertThat(VmSafeMath.negate(java.lang.Long.MAX_VALUE))
      .isEqualTo(-java.lang.Long.MAX_VALUE)
  }

  @Test
  fun `negate long - overflow`() {
    assertThrows<VmEvalException> {
      VmSafeMath.negate(java.lang.Long.MIN_VALUE)
    }
  }

  @Test
  fun `negate double`() {
    assertThat(VmSafeMath.negate(0.0)).isEqualTo(-0.0)
    assertThat(VmSafeMath.negate(-1.0)).isEqualTo(1.0)
    assertThat(VmSafeMath.negate(1.0)).isEqualTo(-1.0)
    assertThat(VmSafeMath.negate(123.456)).isEqualTo(-123.456)
    assertThat(VmSafeMath.negate(-java.lang.Double.MAX_VALUE))
      .isEqualTo(java.lang.Double.MAX_VALUE)
    assertThat(VmSafeMath.negate(-java.lang.Double.MIN_VALUE))
      .isEqualTo(java.lang.Double.MIN_VALUE)
  }

  @Test
  fun `add long`() {
    assertThat(VmSafeMath.add(0, 0)).isEqualTo(0)
    assertThat(VmSafeMath.add(1, 2)).isEqualTo(3)
    assertThat(VmSafeMath.add(1, -2)).isEqualTo(-1)
    assertThat(VmSafeMath.add(java.lang.Long.MAX_VALUE - 1, 1))
      .isEqualTo(java.lang.Long.MAX_VALUE)
  }

  @Test
  fun `add long - overflow #1`() {
    assertThrows<VmEvalException> {
      VmSafeMath.add(java.lang.Long.MAX_VALUE, 1)
    }
  }

  @Test
  fun `add long - overflow #2`() {
    assertThrows<VmEvalException> {
      VmSafeMath.add(java.lang.Long.MIN_VALUE, -1)
    }
  }

  @Test
  fun `add long - overflow #3`() {
    assertThrows<VmEvalException> {
      VmSafeMath.add(
        java.lang.Long.MAX_VALUE / 2,
        java.lang.Long.MAX_VALUE / 3 * 2
      )
    }
  }
}
