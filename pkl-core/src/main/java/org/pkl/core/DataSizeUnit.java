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
package org.pkl.core;

import org.pkl.core.util.Nullable;

/**
 * The unit of a {@link DataSize}. In Pkl, data size units are represented as String {@link
 * #getSymbol() symbols}.
 */
public enum DataSizeUnit {
  BYTES(1, "b"),
  KILOBYTES(1000, "kb"),
  KIBIBYTES(1024, "kib"),
  MEGABYTES(1000 * 1000, "mb"),
  MEBIBYTES(1024 * 1024, "mib"),
  GIGABYTES(1000 * 1000 * 1000, "gb"),
  GIBIBYTES(1024 * 1024 * 1024, "gib"),
  TERABYTES(1000L * 1000 * 1000 * 1000, "tb"),
  TEBIBYTES(1024L * 1024 * 1024 * 1024, "tib"),
  PETABYTES(1000L * 1000 * 1000 * 1000 * 1000, "pb"),
  PEBIBYTES(1024L * 1024 * 1024 * 1024 * 1024, "pib");

  private final long bytes;

  private final String symbol;

  DataSizeUnit(long bytes, String symbol) {
    this.bytes = bytes;
    this.symbol = symbol;
  }

  /**
   * Returns the unit with the given symbol, or {@code null} if no unit with the given symbol
   * exists.
   */
  public static @Nullable DataSizeUnit parse(String symbol) {
    switch (symbol) {
      case "b":
        return BYTES;
      case "kb":
        return KILOBYTES;
      case "kib":
        return KIBIBYTES;
      case "mb":
        return MEGABYTES;
      case "mib":
        return MEBIBYTES;
      case "gb":
        return GIGABYTES;
      case "gib":
        return GIBIBYTES;
      case "tb":
        return TERABYTES;
      case "tib":
        return TEBIBYTES;
      case "pb":
        return PETABYTES;
      case "pib":
        return PEBIBYTES;
      default:
        return null;
    }
  }

  /** Returns the String symbol of this unit. */
  public String getSymbol() {
    return symbol;
  }

  /** Returns the conversion factor from this unit to bytes. */
  public long getBytes() {
    return bytes;
  }

  public String toString() {
    return symbol;
  }
}
