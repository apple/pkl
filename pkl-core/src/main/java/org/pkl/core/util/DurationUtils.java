/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.pkl.core.DurationUnit;

public final class DurationUtils {
  private DurationUtils() {}

  public static String toPklString(double value, DurationUnit unit) {
    return MathUtils.isMathematicalIntegerFittingLong(value)
        ? (long) value + "." + unit
        : value + "." + unit;
  }

  // see: https://standards.calconnect.org/csd/cc-18011.html#toc32
  public static String toIsoString(double value, DurationUnit unit) {
    // different rounding behavior from `VmDuration.convertValueTo()`
    var totalSeconds = value * (unit.getNanos() / 1e9);

    if (!Double.isFinite(totalSeconds)) {
      throw new ArithmeticException(
          "Cannot convert Pkl duration `"
              + DurationUtils.toPklString(value, unit)
              + "` to ISO 8601 duration.");
    }

    var absoluteSeconds = Math.abs(totalSeconds);
    // computed in decimal, as ISO 8601 puts no limit on the precision of the seconds
    var exactSeconds =
        BigDecimal.valueOf(Math.abs(value))
            .multiply(BigDecimal.valueOf(unit.getNanos()))
            .movePointLeft(9);
    var wholeSeconds = exactSeconds.setScale(0, RoundingMode.DOWN);
    var fraction = exactSeconds.subtract(wholeSeconds);
    long hours;
    long minutes;
    long seconds;
    if (wholeSeconds.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
      var longSeconds = wholeSeconds.longValueExact();
      hours = longSeconds / 3600;
      minutes = longSeconds / 60 % 60;
      seconds = longSeconds % 60;
    } else {
      hours = (long) (absoluteSeconds / 3600);
      minutes = (long) (absoluteSeconds / 60) % 60;
      seconds = (long) (absoluteSeconds % 60);
    }

    var isZero = hours == 0 && minutes == 0 && seconds == 0 && fraction.signum() == 0;
    var builder = new StringBuilder();

    if (value < 0.0) {
      builder.append('-');
    }

    builder.append("PT");

    if (hours != 0) {
      builder.append(hours);
      builder.append('H');
    }

    if (minutes != 0) {
      builder.append(minutes);
      builder.append('M');
    }

    if (seconds != 0 || fraction.signum() != 0 || isZero) {
      builder.append(seconds);
      if (fraction.signum() != 0) {
        // drop the leading "0" of "0.<digits>"
        builder.append(fraction.stripTrailingZeros().toPlainString().substring(1));
      }
      builder.append('S');
    }

    return builder.toString();
  }
}
