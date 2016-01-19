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

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.pkl.core.util.Nullable;

/**
 * The unit of a {@link Duration}. In Pkl, duration units are represented as String {@link
 * #getSymbol() symbols}.
 */
public enum DurationUnit {
  NANOS(1, "ns"),
  MICROS(1000, "us"),
  MILLIS(1000 * 1000, "ms"),
  SECONDS(1000 * 1000 * 1000, "s"),
  MINUTES(1000L * 1000 * 1000 * 60, "min"),
  HOURS(1000L * 1000 * 1000 * 60 * 60, "h"),
  DAYS(1000L * 1000 * 1000 * 60 * 60 * 24, "d");

  private final long nanos;

  private final String symbol;

  DurationUnit(long nanos, String symbol) {
    this.nanos = nanos;
    this.symbol = symbol;
  }

  /**
   * Returns the unit with the given symbol, or {@code null} if no unit with the given symbol
   * exists.
   */
  public static @Nullable DurationUnit parse(String symbol) {
    switch (symbol) {
      case "ns":
        return NANOS;
      case "us":
        return MICROS;
      case "ms":
        return MILLIS;
      case "s":
        return SECONDS;
      case "min":
        return MINUTES;
      case "h":
        return HOURS;
      case "d":
        return DAYS;
      default:
        return null;
    }
  }

  /** Returns the string symbol of this unit. */
  public String getSymbol() {
    return symbol;
  }

  /** Returns the conversion factor from this unit to nanoseconds. */
  public long getNanos() {
    return nanos;
  }

  /** Converts this unit to a {@link java.time.temporal.ChronoUnit}. */
  public ChronoUnit toChronoUnit() {
    switch (this) {
      case NANOS:
        return ChronoUnit.NANOS;
      case MICROS:
        return ChronoUnit.MICROS;
      case MILLIS:
        return ChronoUnit.MILLIS;
      case SECONDS:
        return ChronoUnit.SECONDS;
      case MINUTES:
        return ChronoUnit.MINUTES;
      case HOURS:
        return ChronoUnit.HOURS;
      case DAYS:
        return ChronoUnit.DAYS;
      default:
        throw new AssertionError("Unknown duration unit: " + this);
    }
  }

  /** Converts this unit to a {@link java.util.concurrent.TimeUnit}. */
  public TimeUnit toTimeUnit() {
    switch (this) {
      case NANOS:
        return TimeUnit.NANOSECONDS;
      case MICROS:
        return TimeUnit.MICROSECONDS;
      case MILLIS:
        return TimeUnit.MILLISECONDS;
      case SECONDS:
        return TimeUnit.SECONDS;
      case MINUTES:
        return TimeUnit.MINUTES;
      case HOURS:
        return TimeUnit.HOURS;
      case DAYS:
        return TimeUnit.DAYS;
      default:
        throw new AssertionError("Unknown duration unit: " + this);
    }
  }

  @Override
  public String toString() {
    return symbol;
  }
}
