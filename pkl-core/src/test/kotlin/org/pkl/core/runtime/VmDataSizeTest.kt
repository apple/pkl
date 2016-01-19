package org.pkl.core.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.DataSizeUnit

class VmDataSizeTest {
  private val size1 = VmDataSize(0.3, DataSizeUnit.KILOBYTES)
  private val size2 = VmDataSize(300.0, DataSizeUnit.BYTES)
  private val size3 = VmDataSize(300.1, DataSizeUnit.BYTES)

  @Test
  fun `equals()`() {
    assertThat(size1).isEqualTo(size1)
    assertThat(size2).isEqualTo(size1)
    assertThat(size1).isEqualTo(size2)

    assertThat(size3).isNotEqualTo(size1)
    assertThat(size2).isNotEqualTo(size3)
  }

  @Test
  fun `hashCode()`() {
    assertThat(size1.hashCode()).isEqualTo(size1.hashCode())
    assertThat(size2.hashCode()).isEqualTo(size1.hashCode())
    assertThat(size1.hashCode()).isEqualTo(size2.hashCode())

    assertThat(size3.hashCode()).isNotEqualTo(size1.hashCode())
    assertThat(size2.hashCode()).isNotEqualTo(size3.hashCode())
  }
}
