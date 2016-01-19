/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.core;

import static org.pkl.core.DurationUnit.*;

import java.util.Objects;
import org.pkl.core.util.DurationUtils;
import org.pkl.core.util.Nullable;

/** Java representation of a {@code pkl.base#Duration} value. */
public final strictfp class Duration implements Value {
  private static final long serialVersionUID = 0L;

  private final double value;
  private final DurationUnit unit;

  /** Constructs a new duration with the given value and unit. */
  public Duration(double value, DurationUnit unit) {
    this.value = value;
    this.unit = Objects.requireNonNull(unit, "unit");
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#NANOS}. */
  public static Duration ofNanos(double value) {
    return new Duration(value, NANOS);
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#MICROS}. */
  public static Duration ofMicros(double value) {
    return new Duration(value, MICROS);
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#MILLIS}. */
  public static Duration ofMillis(double value) {
    return new Duration(value, MILLIS);
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#SECONDS}. */
  public static Duration ofSeconds(double value) {
    return new Duration(value, SECONDS);
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#MINUTES}. */
  public static Duration ofMinutes(double value) {
    return new Duration(value, MINUTES);
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#HOURS}. */
  public static Duration ofHours(double value) {
    return new Duration(value, HOURS);
  }

  /** Constructs a new duration with the given value and unit {@link DurationUnit#DAYS}. */
  public static Duration ofDays(double value) {
    return new Duration(value, DAYS);
  }

  /** Returns the value of this duration. The value is relative to the unit and may be negative. */
  public double getValue() {
    return value;
  }

  /** Returns an ISO 8601 representation of this duration. */
  public String toIsoString() {
    return DurationUtils.toIsoString(value, unit);
  }

  /** Returns the unit of this duration. */
  public DurationUnit getUnit() {
    return unit;
  }

  /** Returns the value of this duration measured in {@link DurationUnit#NANOS}. */
  public double inNanos() {
    return convertValueTo(NANOS);
  }

  /** Returns the value of this duration measured in {@link DurationUnit#MICROS}. */
  public double inMicros() {
    return convertValueTo(MICROS);
  }

  /** Returns the value of this duration measured in {@link DurationUnit#MILLIS}. */
  public double inMillis() {
    return convertValueTo(MILLIS);
  }

  /** Returns the value of this duration measured in {@link DurationUnit#SECONDS}. */
  public double inSeconds() {
    return convertValueTo(SECONDS);
  }

  /** Returns the value of this duration measured in {@link DurationUnit#MINUTES}. */
  public double inMinutes() {
    return convertValueTo(MINUTES);
  }

  /** Returns the value of this duration measured in {@link DurationUnit#HOURS}. */
  public double inHours() {
    return convertValueTo(HOURS);
  }

  /** Returns the value of this duration measured in {@link DurationUnit#DAYS}. */
  public double inDays() {
    return convertValueTo(DAYS);
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#NANOS}. */
  public long inWholeNanos() {
    return Math.round(inNanos());
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#MICROS}. */
  public long inWholeMicros() {
    return Math.round(inMicros());
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#MILLIS}. */
  public long inWholeMillis() {
    return Math.round(inMillis());
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#SECONDS}. */
  public long inWholeSeconds() {
    return Math.round(inSeconds());
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#MINUTES}. */
  public long inWholeMinutes() {
    return Math.round(inMinutes());
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#HOURS}. */
  public long inWholeHours() {
    return Math.round(inHours());
  }

  /** Returns the value of this duration measured in whole {@link DurationUnit#DAYS}. */
  public long inWholeDays() {
    return Math.round(inDays());
  }

  /**
   * Converts this duration to a {@link java.time.Duration}. If {@link #getValue()} is NaN,
   * +/-Infinity or too large to fit into {@link java.time.Duration}, {@link ArithmeticException} is
   * thrown.
   */
  public java.time.Duration toJavaDuration() {
    if (!Double.isFinite(value)) {
      throw new ArithmeticException(
          "Cannot convert Pkl duration `" + this + "` to `java.time.Duration`.");
    }

    var l = (long) value;
    if (l == value) {
      // `value` is a mathematical integer that fits into a long.
      // Hence this duration is easy to convert without risk of rounding errors.
      // Throws ArithmeticException if this duration doesn't fit into java.time.Duration (e.g.,
      // Long.MAX_VALUE days).
      return java.time.Duration.of(l, unit.toChronoUnit());
    }

    var seconds = convertValueTo(DurationUnit.SECONDS);
    var secondsPart = (long) seconds;
    var nanosPart = (long) ((seconds - secondsPart) * 1_000_000_000);
    // If `seconds` is infinite or too large to fit into java.time.Duration,
    // this throws ArithmeticException because one the following holds:
    //   secondsPart == Long.MAX_VALUE && nanosPart >= 1_000_000_000
    //   secondsPart == Long.MIN_VALUE && nanosPart <= -1_000_000_000.
    return java.time.Duration.ofSeconds(secondsPart, nanosPart);
  }

  /** Returns a new duration with the given unit and this value converted to the given unit. */
  public Duration convertTo(DurationUnit other) {
    return new Duration(convertValueTo(other), other);
  }

  /** Returns the value of this duration converted to the given unit. */
  public double convertValueTo(DurationUnit other) {
    return value * unit.getNanos() / other.getNanos();
  }

  /** {@inheritDoc} */
  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitDuration(this);
  }

  /** {@inheritDoc} */
  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertDuration(this);
  }

  /** {@inheritDoc} */
  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.Duration;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Duration)) return false;

    var other = (Duration) obj;
    return convertValueTo(DurationUnit.NANOS) == other.convertValueTo(DurationUnit.NANOS);
  }

  @Override
  public int hashCode() {
    return Double.hashCode(convertValueTo(DurationUnit.NANOS));
  }

  @Override
  public String toString() {
    return DurationUtils.toPklString(value, unit);
  }
}
