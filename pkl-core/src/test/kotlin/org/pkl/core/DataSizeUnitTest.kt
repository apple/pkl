package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataSizeUnitTest {
  @Test
  fun destructure() {
    val bytes = DataSizeUnit.BYTES
    assertThat(bytes.bytes).isEqualTo(1)
    assertThat(bytes.symbol).isEqualTo("b")

    val mebibytes = DataSizeUnit.MEBIBYTES
    assertThat(mebibytes.bytes).isEqualTo(1024L * 1024)
    assertThat(mebibytes.symbol).isEqualTo("mib")
  }

  @Test
  fun `toString()`() {
    assertThat(DataSizeUnit.BYTES.toString()).isEqualTo("b")
    assertThat(DataSizeUnit.MEBIBYTES.toString()).isEqualTo("mib")
  }

  @Test
  fun parse() {
    assertThat(DataSizeUnit.parse("gb")).isEqualTo(DataSizeUnit.GIGABYTES)
    assertThat(DataSizeUnit.parse("other")).isNull()
  }
}
