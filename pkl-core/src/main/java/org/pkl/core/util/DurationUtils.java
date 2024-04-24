/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.pkl.core.DurationUnit;

public final class DurationUtils {
  private DurationUtils() {}

  public static String toPklString(double value, DurationUnit unit) {
    return MathUtils.isMathematicalInteger(value) ? (long) value + "." + unit : value + "." + unit;
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
    var hours = (long) (absoluteSeconds / 3600);
    var minutes = (long) (absoluteSeconds / 60) % 60;
    var seconds = (long) (absoluteSeconds % 60);
    var nanos =
        (long) (absoluteSeconds * 1_000_000_000 - Math.floor(absoluteSeconds) * 1_000_000_000);

    var builder = new StringBuilder();

    if (totalSeconds < 0.0) {
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

    if (seconds != 0 || nanos != 0 || totalSeconds == 0) {
      builder.append(seconds);
      if (nanos != 0) {
        builder.append('.');
        var nanosString = String.valueOf(nanos);
        builder.append("0".repeat(9 - nanosString.length()));
        for (int i = nanosString.length(); i >= 0; i--) {
          if (nanosString.charAt(i - 1) != '0') {
            nanosString = nanosString.substring(0, i);
            break;
          }
        }
        builder.append(nanosString);
      }
      builder.append('S');
    }

    return builder.toString();
  }
}
