package org.pkl.core

import kotlin.math.nextDown
import kotlin.math.nextUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.core.DurationUnit.*

class DurationTest {
  private val duration1 = Duration(0.3, SECONDS)
  private val duration2 = Duration(300.0, MILLIS)
  private val duration3 = Duration(300.1, MILLIS)
  private val duration4 = Duration(0.0, DAYS)

  @Test
  fun `of()`() {
    assertThat(Duration.ofNanos(33.0)).isEqualTo(Duration(33.0, NANOS))
    assertThat(Duration.ofMicros(33.0)).isEqualTo(Duration(33.0, MICROS))
    assertThat(Duration.ofMillis(33.0)).isEqualTo(Duration(33.0, MILLIS))
    assertThat(Duration.ofSeconds(33.0)).isEqualTo(Duration(33.0, SECONDS))
    assertThat(Duration.ofMinutes(33.0)).isEqualTo(Duration(33.0, MINUTES))
    assertThat(Duration.ofHours(33.0)).isEqualTo(Duration(33.0, HOURS))
    assertThat(Duration.ofDays(33.0)).isEqualTo(Duration(33.0, DAYS))
  }

  @Test
  fun `in()`() {
    val d = Duration.ofNanos(123456789.0)
    assertThat(d.inNanos()).isEqualTo(123456789.0)
    assertThat(d.inMicros()).isEqualTo(123456.789)
    assertThat(d.inMillis()).isEqualTo(123.456789)
    assertThat(d.inSeconds()).isEqualTo(0.123456789)
    assertThat(d.inMinutes()).isEqualTo(0.00205761315)
    assertThat(d.inHours()).isEqualTo(3.42935525E-5)
    assertThat(d.inDays()).isEqualTo(1.4288980208333333E-6)
  }

  @Test
  fun `inWhole()`() {
    assertThat(Duration.ofNanos(1.23).inWholeNanos()).isEqualTo(1)
    assertThat(Duration.ofMicros(1.87).inWholeMicros()).isEqualTo(2)
    assertThat(Duration.ofMillis(1923.4).inWholeMillis()).isEqualTo(1923)
    assertThat(Duration.ofSeconds(1234.5).inWholeSeconds()).isEqualTo(1235)
    assertThat(Duration.ofMinutes(987.6).inWholeMinutes()).isEqualTo(988)
    assertThat(Duration.ofHours(456.7).inWholeHours()).isEqualTo(457)
    assertThat(Duration.ofDays(543.2).inWholeDays()).isEqualTo(543)
  }

  @Test
  fun `destructure()`() {
    assertThat(duration1.value).isEqualTo(0.3)
    assertThat(duration1.unit).isEqualTo(SECONDS)

    assertThat(duration2.value).isEqualTo(300.0)
    assertThat(duration2.unit).isEqualTo(MILLIS)

    assertThat(duration3.value).isEqualTo(300.1)
    assertThat(duration3.unit).isEqualTo(MILLIS)

    assertThat(duration4.value).isEqualTo(0.0)
    assertThat(duration4.unit).isEqualTo(DAYS)
  }

  @Test
  fun `convertTo()`() {
    assertThat(duration1.convertTo(SECONDS)).isEqualTo(duration1)
    assertThat(duration1.convertTo(MILLIS)).isEqualTo(duration2)
    assertThat(duration2.convertTo(SECONDS)).isEqualTo(duration1)

    assertThat(duration4.convertTo(NANOS))
      .isEqualTo(Duration(0.0, NANOS))
  }
  
  @Test
  fun toIsoString() {
    assertThat(duration1.toIsoString()).isEqualTo("PT0.3S")
    assertThat(duration2.toIsoString()).isEqualTo("PT0.3S")
    assertThat(duration3.toIsoString()).isEqualTo("PT0.3001S")
    assertThat(duration4.toIsoString()).isEqualTo("PT0S")
    assertThat(Duration(1.0, NANOS).toIsoString()).isEqualTo("PT0.000000001S")
    // Although ISO8601 allows for durations (P) denoted in days, months and years, it is not recommended.
    // The day notation can express an hour more or less, depending on whether it crosses a daylight savings transition,
    // when added to "now" (at the time of evaluation).
    assertThat(Duration(100.0, DAYS).toIsoString()).isEqualTo("PT2400H")
  }

  @Test
  fun `convertValueTo()`() {
    assertThat(duration1.convertValueTo(SECONDS)).isEqualTo(duration1.value)
    assertThat(duration1.convertValueTo(MILLIS)).isEqualTo(duration2.value)
    assertThat(duration2.convertValueTo(SECONDS)).isEqualTo(duration1.value)
    assertThat(duration4.convertValueTo(NANOS)).isEqualTo(0.0)
  }

  @Test
  fun `toJavaDuration() - positive`() {
    assertThat(Duration(999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(999))
    assertThat(Duration(999999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(999999))
    assertThat(Duration(999999999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(999999999))
    assertThat(Duration(999999999999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(999999999999))
    assertThat(
      Duration(
        999999999999999.0,
        NANOS
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofNanos(999999999999999))
    assertThat(Duration(9999999999999999.0, NANOS).toJavaDuration()).isNotEqualTo(
      java.time.Duration.ofNanos(
        9999999999999999
      )
    )

    assertThat(Duration(999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(999))
    assertThat(Duration(999999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(999999))
    assertThat(Duration(999999999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(999999999))
    assertThat(Duration(999999999999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(999999999999))
    assertThat(Duration(999999999999999.0, SECONDS).toJavaDuration()).isEqualTo(
      java.time.Duration.ofSeconds(
        999999999999999
      )
    )
    assertThat(Duration(9999999999999999.0, SECONDS).toJavaDuration()).isNotEqualTo(
      java.time.Duration.ofSeconds(
        9999999999999999
      )
    )

    assertThat(Duration(999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(999))
    assertThat(Duration(999999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(999999))
    assertThat(Duration(999999999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(999999999))
    assertThat(Duration(999999999999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(999999999999))
    assertThat(Duration(999999999999999.0, MINUTES).toJavaDuration()).isEqualTo(
      java.time.Duration.ofMinutes(
        999999999999999
      )
    )
    assertThat(Duration(9999999999999999.0, MINUTES).toJavaDuration()).isNotEqualTo(
      java.time.Duration.ofMinutes(
        9999999999999999
      )
    )

    assertThat(Duration(999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(999))
    assertThat(Duration(999999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(999999))
    assertThat(Duration(999999999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(999999999))
    assertThat(Duration(999999999999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(999999999999))
    assertThat(
      Duration(
        999999999999999.0,
        HOURS
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofHours(999999999999999))
    assertThrows<ArithmeticException> { Duration(9999999999999999.0, HOURS).toJavaDuration() }

    assertThat(Duration(999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(999))
    assertThat(Duration(999999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(999999))
    assertThat(Duration(999999999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(999999999))
    assertThat(Duration(999999999999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(999999999999))
    assertThrows<ArithmeticException> { Duration(999999999999999.0, DAYS).toJavaDuration() }
  }

  @Test
  fun `toJavaDuration() - negative`() {
    assertThat(Duration(-999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(-999))
    assertThat(Duration(-999999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(-999999))
    assertThat(Duration(-999999999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(-999999999))
    assertThat(Duration(-999999999999.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(-999999999999))
    assertThat(
      Duration(
        -999999999999999.0,
        NANOS
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofNanos(-999999999999999))
    assertThat(
      Duration(
        -9999999999999999.0,
        NANOS
      ).toJavaDuration()
    ).isNotEqualTo(java.time.Duration.ofNanos(-9999999999999999))

    assertThat(Duration(-999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(-999))
    assertThat(Duration(-999999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(-999999))
    assertThat(Duration(-999999999.0, SECONDS).toJavaDuration()).isEqualTo(java.time.Duration.ofSeconds(-999999999))
    assertThat(
      Duration(
        -999999999999.0,
        SECONDS
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofSeconds(-999999999999))
    assertThat(
      Duration(
        -999999999999999.0,
        SECONDS
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofSeconds(-999999999999999))
    assertThat(
      Duration(
        -9999999999999999.0,
        SECONDS
      ).toJavaDuration()
    ).isNotEqualTo(java.time.Duration.ofSeconds(-9999999999999999))

    assertThat(Duration(-999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(-999))
    assertThat(Duration(-999999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(-999999))
    assertThat(Duration(-999999999.0, MINUTES).toJavaDuration()).isEqualTo(java.time.Duration.ofMinutes(-999999999))
    assertThat(
      Duration(
        -999999999999.0,
        MINUTES
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofMinutes(-999999999999))
    assertThat(
      Duration(
        -999999999999999.0,
        MINUTES
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofMinutes(-999999999999999))
    assertThat(
      Duration(
        -9999999999999999.0,
        MINUTES
      ).toJavaDuration()
    ).isNotEqualTo(java.time.Duration.ofMinutes(-9999999999999999))

    assertThat(Duration(-999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(-999))
    assertThat(Duration(-999999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(-999999))
    assertThat(Duration(-999999999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(-999999999))
    assertThat(Duration(-999999999999.0, HOURS).toJavaDuration()).isEqualTo(java.time.Duration.ofHours(-999999999999))
    assertThat(
      Duration(
        -999999999999999.0,
        HOURS
      ).toJavaDuration()
    ).isEqualTo(java.time.Duration.ofHours(-999999999999999))
    assertThrows<ArithmeticException> { Duration(-9999999999999999.0, HOURS).toJavaDuration() }

    assertThat(Duration(-999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(-999))
    assertThat(Duration(-999999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(-999999))
    assertThat(Duration(-999999999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(-999999999))
    assertThat(Duration(-999999999999.0, DAYS).toJavaDuration()).isEqualTo(java.time.Duration.ofDays(-999999999999))
    assertThrows<ArithmeticException> { Duration(-999999999999999.0, DAYS).toJavaDuration() }
  }

  @Test
  fun `toJavaDuration() - edge cases`() {
    assertThat(Duration(0.0, NANOS).toJavaDuration()).isEqualTo(java.time.Duration.ofNanos(0))
    assertThat(Duration(Long.MAX_VALUE.toDouble(), SECONDS).toJavaDuration()).isEqualTo(
      java.time.Duration.ofSeconds(
        Long.MAX_VALUE
      )
    )
    assertThat(Duration(Long.MIN_VALUE.toDouble(), SECONDS).toJavaDuration()).isEqualTo(
      java.time.Duration.ofSeconds(
        Long.MIN_VALUE
      )
    )

    val justTooLarge = Duration(Long.MAX_VALUE.toDouble().nextUp(), SECONDS)
    assertThrows<ArithmeticException> { justTooLarge.toJavaDuration() }

    val negJustTooLarge = Duration(Long.MIN_VALUE.toDouble().nextDown(), SECONDS)
    assertThrows<ArithmeticException> { negJustTooLarge.toJavaDuration() }

    val nan = Duration(Double.NaN, SECONDS)
    assertThrows<ArithmeticException> { nan.toJavaDuration() }

    val inf = Duration(Double.POSITIVE_INFINITY, SECONDS)
    assertThrows<ArithmeticException> { inf.toJavaDuration() }

    val negInf = Duration(Double.NEGATIVE_INFINITY, SECONDS)
    assertThrows<ArithmeticException> { negInf.toJavaDuration() }
  }

  @Test
  fun `equals()`() {
    assertThat(duration1).isEqualTo(duration1)
    assertThat(duration1).isEqualTo(duration2)
    assertThat(duration2).isEqualTo(duration1)

    assertThat(duration3).isNotEqualTo(duration1)
    assertThat(duration2).isNotEqualTo(duration3)
  }

  @Test
  fun `hashCode()`() {
    assertThat(duration1.hashCode()).isEqualTo(duration1.hashCode())
    assertThat(duration2.hashCode()).isEqualTo(duration1.hashCode())
    assertThat(duration1.hashCode()).isEqualTo(duration2.hashCode())

    assertThat(duration3.hashCode()).isNotEqualTo(duration1.hashCode())
    assertThat(duration2.hashCode()).isNotEqualTo(duration3.hashCode())
  }
}
