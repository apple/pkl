package org.pkl.core

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DurationUnitTest {
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
    assertThat(DurationUnit.parse("min")).isEqualTo(DurationUnit.MINUTES)
    assertThat(DurationUnit.parse("other")).isNull()
  }

  @Test
  fun toChronoUnit() {
    assertThat(DurationUnit.NANOS.toChronoUnit()).isEqualTo(ChronoUnit.NANOS)
    assertThat(DurationUnit.MICROS.toChronoUnit()).isEqualTo(ChronoUnit.MICROS)
    assertThat(DurationUnit.MILLIS.toChronoUnit()).isEqualTo(ChronoUnit.MILLIS)
    assertThat(DurationUnit.SECONDS.toChronoUnit()).isEqualTo(ChronoUnit.SECONDS)
    assertThat(DurationUnit.MINUTES.toChronoUnit()).isEqualTo(ChronoUnit.MINUTES)
    assertThat(DurationUnit.HOURS.toChronoUnit()).isEqualTo(ChronoUnit.HOURS)
    assertThat(DurationUnit.DAYS.toChronoUnit()).isEqualTo(ChronoUnit.DAYS)
  }

  @Test
  fun toTimeUnit() {
    assertThat(DurationUnit.NANOS.toTimeUnit()).isEqualTo(TimeUnit.NANOSECONDS)
    assertThat(DurationUnit.MICROS.toTimeUnit()).isEqualTo(TimeUnit.MICROSECONDS)
    assertThat(DurationUnit.MILLIS.toTimeUnit()).isEqualTo(TimeUnit.MILLISECONDS)
    assertThat(DurationUnit.SECONDS.toTimeUnit()).isEqualTo(TimeUnit.SECONDS)
    assertThat(DurationUnit.MINUTES.toTimeUnit()).isEqualTo(TimeUnit.MINUTES)
    assertThat(DurationUnit.HOURS.toTimeUnit()).isEqualTo(TimeUnit.HOURS)
    assertThat(DurationUnit.DAYS.toTimeUnit()).isEqualTo(TimeUnit.DAYS)
  }
}
