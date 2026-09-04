/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.url;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * The Unicode properties IDNA needs, decoded from {@link IdnaTableData}.
 *
 * <p>Decoding is lazy, behind {@link Tables}, so a URL that never parses a non-ASCII domain never
 * pays for it.
 */
final class IdnaTable {
  private IdnaTable() {}

  /** A UTS-46 status, as configured. */
  enum Status {
    /** Left as is. */
    VALID,
    /** Removed. */
    IGNORED,
    /** Replaced by {@link #mapping}. */
    MAPPED,
    /** Fatal to {@code ToASCII}. */
    DISALLOWED
  }

  /** The Bidi_Class values RFC 5893 names. */
  enum BidiClass {
    L,
    R,
    AL,
    AN,
    EN,
    ES,
    CS,
    ET,
    ON,
    BN,
    NSM,
    OTHER
  }

  private static final Status[] STATUS_BY_DIGIT = {
    Status.VALID,
    Status.IGNORED,
    Status.MAPPED,
    Status.VALID,
    Status.DISALLOWED,
    Status.VALID,
    Status.MAPPED,
  };

  private static final BidiClass[] BIDI_CLASS_BY_DIGIT = {
    BidiClass.L,
    BidiClass.R,
    BidiClass.AL,
    BidiClass.AN,
    BidiClass.EN,
    BidiClass.ES,
    BidiClass.CS,
    BidiClass.ET,
    BidiClass.ON,
    BidiClass.BN,
    BidiClass.NSM,
    BidiClass.OTHER,
    BidiClass.OTHER,
    BidiClass.OTHER,
    BidiClass.OTHER,
    BidiClass.OTHER,
  };

  /** The UTS-46 status of {@code codePoint}. */
  static Status status(int codePoint) {
    return STATUS_BY_DIGIT[Tables.STATUS_VALUES[rangeIndex(Tables.STATUS_STARTS, codePoint)]];
  }

  static @Nullable String mapping(int codePoint) {
    return Tables.STATUS_MAPPINGS[rangeIndex(Tables.STATUS_STARTS, codePoint)];
  }

  static boolean isMark(int codePoint) {
    return Tables.MARK_VALUES[rangeIndex(Tables.MARK_STARTS, codePoint)] != 0;
  }

  static BidiClass bidiClass(int codePoint) {
    return BIDI_CLASS_BY_DIGIT[Tables.BIDI_VALUES[rangeIndex(Tables.BIDI_STARTS, codePoint)]];
  }

  /** The Unicode version the tables were generated from. */
  static String unicodeVersion() {
    return IdnaTableData.UNICODE_VERSION;
  }

  private static int rangeIndex(int[] starts, int codePoint) {
    var index = Arrays.binarySearch(starts, codePoint);
    return index >= 0 ? index : -index - 2;
  }

  private static final class Tables {
    static final int[] STATUS_STARTS = decodeStarts(IdnaTableData.STATUS_STARTS);
    static final byte[] STATUS_VALUES =
        decodeValues(IdnaTableData.STATUS_VALUES, STATUS_STARTS.length);
    static final @Nullable String[] STATUS_MAPPINGS =
        decodeMappings(IdnaTableData.STATUS_MAPPINGS, STATUS_VALUES);

    static final int[] MARK_STARTS = decodeStarts(IdnaTableData.MARK_STARTS);
    static final byte[] MARK_VALUES = decodeValues(IdnaTableData.MARK_VALUES, MARK_STARTS.length);

    static final int[] BIDI_STARTS = decodeStarts(IdnaTableData.BIDI_STARTS);
    static final byte[] BIDI_VALUES = decodeValues(IdnaTableData.BIDI_VALUES, BIDI_STARTS.length);

    private Tables() {}
  }

  private static int[] decodeStarts(String[] chunks) {
    var fields = String.join("", chunks).split(",");
    var starts = new int[fields.length];
    var start = 0;
    for (var i = 0; i < fields.length; i++) {
      start += Integer.parseInt(fields[i], 16);
      starts[i] = start;
    }
    return starts;
  }

  private static byte[] decodeValues(String[] chunks, int rangeCount) {
    var encoded = String.join("", chunks);
    if (encoded.length() != rangeCount) {
      throw new AssertionError(
          "`IdnaTableData` states "
              + rangeCount
              + " ranges but "
              + encoded.length()
              + " values for them.");
    }
    var values = new byte[encoded.length()];
    for (var i = 0; i < values.length; i++) {
      var digit = Character.digit(encoded.charAt(i), 16);
      if (digit < 0) {
        throw new AssertionError("`IdnaTableData` has `" + encoded.charAt(i) + "` as a value.");
      }
      values[i] = (byte) digit;
    }
    return values;
  }

  private static @Nullable String[] decodeMappings(String[] chunks, byte[] values) {
    var pool = String.join("", chunks).split(",");
    var mappings = new @Nullable String[values.length];
    var next = 0;
    for (var i = 0; i < values.length; i++) {
      if (STATUS_BY_DIGIT[values[i]] != Status.MAPPED) {
        continue;
      }
      if (next == pool.length) {
        throw new AssertionError("`IdnaTableData` has fewer mappings than mapped ranges.");
      }
      mappings[i] = decodeCodePoints(pool[next++]);
    }
    if (next != pool.length) {
      throw new AssertionError(
          "`IdnaTableData` has " + (pool.length - next) + " mappings too many.");
    }
    return mappings;
  }

  private static String decodeCodePoints(String encoded) {
    var out = new StringBuilder(encoded.length() / 4);
    var start = 0;
    while (true) {
      var end = encoded.indexOf('.', start);
      out.appendCodePoint(Integer.parseInt(encoded, start, end < 0 ? encoded.length() : end, 16));
      if (end < 0) {
        return out.toString();
      }
      start = end + 1;
    }
  }
}
