package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.DataSizeUnit.*

class DataSizeTest {
  private val size1 = DataSize(0.3, KILOBYTES)
  private val size2 = DataSize(300.0, BYTES)
  private val size3 = DataSize(300.1, BYTES)
  private val size4 = DataSize(0.0, PEBIBYTES)

  @Test
  fun `of()`() {
    assertThat(DataSize.ofBytes(33.0)).isEqualTo(DataSize(33.0, BYTES))
    assertThat(DataSize.ofKilobytes(33.0)).isEqualTo(DataSize(33.0, KILOBYTES))
    assertThat(DataSize.ofKibibytes(33.0)).isEqualTo(DataSize(33.0, KIBIBYTES))
    assertThat(DataSize.ofMegabytes(33.0)).isEqualTo(DataSize(33.0, MEGABYTES))
    assertThat(DataSize.ofMebibytes(33.0)).isEqualTo(DataSize(33.0, MEBIBYTES))
    assertThat(DataSize.ofGigabytes(33.0)).isEqualTo(DataSize(33.0, GIGABYTES))
    assertThat(DataSize.ofGibibytes(33.0)).isEqualTo(DataSize(33.0, GIBIBYTES))
    assertThat(DataSize.ofTerabytes(33.0)).isEqualTo(DataSize(33.0, TERABYTES))
    assertThat(DataSize.ofTebibytes(33.0)).isEqualTo(DataSize(33.0, TEBIBYTES))
    assertThat(DataSize.ofPetabytes(33.0)).isEqualTo(DataSize(33.0, PETABYTES))
    assertThat(DataSize.ofPebibytes(33.0)).isEqualTo(DataSize(33.0, PEBIBYTES))
  }

  @Test
  fun `in()`() {
    assertThat(size1.inBytes()).isEqualTo(300.0)
    assertThat(size1.inKilobytes()).isEqualTo(0.3)
    assertThat(size1.inMegabytes()).isEqualTo(0.0003)
    assertThat(size1.inGigabytes()).isEqualTo(0.0000003)
    assertThat(size1.inTerabytes()).isEqualTo(0.0000000003)
    assertThat(size1.inPetabytes()).isEqualTo(0.0000000000003)

    assertThat(DataSize.ofBytes(1024.0).inKibibytes()).isEqualTo(1.0)
    assertThat(DataSize.ofKibibytes(1024.0).inMebibytes()).isEqualTo(1.0)
    assertThat(DataSize.ofMebibytes(1024.0).inGibibytes()).isEqualTo(1.0)
    assertThat(DataSize.ofGibibytes(1024.0).inTebibytes()).isEqualTo(1.0)
    assertThat(DataSize.ofTebibytes(1024.0).inPebibytes()).isEqualTo(1.0)
  }

  @Test
  fun `inWhole()`() {
    assertThat(DataSize.ofBytes(123.4).inWholeBytes()).isEqualTo(123)
    assertThat(DataSize.ofBytes(1000.0).inWholeKilobytes()).isEqualTo(1)
    assertThat(DataSize.ofKilobytes(999.0).inWholeMegabytes()).isEqualTo(1)
    assertThat(DataSize.ofMegabytes(1001.0).inWholeGigabytes()).isEqualTo(1)
    assertThat(DataSize.ofGigabytes(2000.0).inWholeTerabytes()).isEqualTo(2)
    assertThat(DataSize.ofTerabytes(1600.0).inWholePetabytes()).isEqualTo(2)

    assertThat(DataSize.ofBytes(1023.0).inWholeKibibytes()).isEqualTo(1)
    assertThat(DataSize.ofKibibytes(1024.0).inWholeMebibytes()).isEqualTo(1)
    assertThat(DataSize.ofMebibytes(1025.0).inWholeGibibytes()).isEqualTo(1)
    assertThat(DataSize.ofGibibytes(2000.0).inWholeTebibytes()).isEqualTo(2)
    assertThat(DataSize.ofTebibytes(1600.0).inWholePebibytes()).isEqualTo(2)
  }

  @Test
  fun `destructure()`() {
    assertThat(size1.value).isEqualTo(0.3)
    assertThat(size1.unit).isEqualTo(KILOBYTES)

    assertThat(size2.value).isEqualTo(300.0)
    assertThat(size2.unit).isEqualTo(BYTES)

    assertThat(size3.value).isEqualTo(300.1)
    assertThat(size3.unit).isEqualTo(BYTES)

    assertThat(size4.value).isEqualTo(0.0)
    assertThat(size4.unit).isEqualTo(PEBIBYTES)
  }

  @Test
  fun `convertTo()`() {
    assertThat(size1.convertTo(KILOBYTES)).isEqualTo(size1)
    assertThat(size1.convertTo(BYTES)).isEqualTo(size2)
    assertThat(size2.convertTo(KILOBYTES)).isEqualTo(size1)
    assertThat(size4.convertTo(PETABYTES)).isEqualTo(DataSize(0.0, KIBIBYTES))
  }

  @Test
  fun `convertValueTo()`() {
    assertThat(size1.convertValueTo(KILOBYTES)).isEqualTo(size1.value)
    assertThat(size1.convertValueTo(BYTES)).isEqualTo(size2.value)
    assertThat(size2.convertValueTo(KILOBYTES)).isEqualTo(size1.value)
    assertThat(size4.convertValueTo(PETABYTES)).isEqualTo(0.0)
  }

  @Test
  fun `equals()`() {
    assertThat(size1).isEqualTo(size1)
    assertThat(size1).isEqualTo(size2)
    assertThat(size2).isEqualTo(size1)

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
