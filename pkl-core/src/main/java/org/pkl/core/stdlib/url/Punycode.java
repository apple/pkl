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
 * <a href="https://www.rfc-editor.org/rfc/rfc3492">RFC 3492</a> Punycode, the Bootstring encoding
 * IDNA uses for the {@code xn--} ACE form.
 *
 * <p>Both directions operate on a single label, without the {@code xn--} prefix, and return {@code
 * null} rather than throwing on any failure the RFC calls out.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
final class Punycode {
  private Punycode() {}

  // bootstring parameters (https://www.rfc-editor.org/rfc/rfc3492#section-5).
  private static final int BASE = 36;
  private static final int TMIN = 1;
  private static final int TMAX = 26;
  private static final int SKEW = 38;
  private static final int DAMP = 700;
  private static final int INITIAL_BIAS = 72;
  private static final int INITIAL_N = 0x80;
  private static final char DELIMITER = '-';

  /** The overflow threshold: any accumulator above this fails. */
  private static final long MAX = Integer.MAX_VALUE;

  /**
   * Decodes a Punycode label. Returns {@code null} on a digit that is not a Punycode digit, a
   * truncated final delta, arithmetic overflow, a decoded code point that is not a Unicode scalar
   * value, or empty output.
   *
   * <p>Surrogate code points are rejected since UTS-46 gives every surrogate the status {@code
   * disallowed} and so would reject the label regardless.
   */
  static @Nullable String decode(String input) {
    var output = new int[16];
    var outputLength = 0;

    // everything before the last delimiter is literal
    var basicEnd = input.lastIndexOf(DELIMITER);
    for (var j = 0; j < basicEnd; j++) {
      var c = input.charAt(j);
      if (c >= INITIAL_N) {
        return null;
      }
      output = insert(output, outputLength, outputLength, c);
      outputLength++;
    }

    var n = INITIAL_N;
    var bias = INITIAL_BIAS;
    var i = 0L;
    var pointer = basicEnd + 1;
    while (pointer < input.length()) {
      var oldi = i;
      var w = 1L;
      for (var k = BASE; ; k += BASE) {
        if (pointer >= input.length()) {
          return null;
        }
        var digit = digitValue(input.charAt(pointer++));
        if (digit < 0) {
          return null;
        }
        i += digit * w;
        if (i > MAX) {
          return null;
        }
        var t = threshold(k, bias);
        if (digit < t) {
          break;
        }
        w *= BASE - t;
        if (w > MAX) {
          return null;
        }
      }

      var numPoints = outputLength + 1;
      bias = adapt(i - oldi, numPoints, oldi == 0);
      var next = n + i / numPoints;
      if (next > Character.MAX_CODE_POINT
          || (next >= Character.MIN_SURROGATE && next <= Character.MAX_SURROGATE)) {
        return null;
      }
      n = (int) next;
      var index = (int) (i % numPoints);
      output = insert(output, outputLength, index, n);
      outputLength++;
      i = index + 1;
    }

    if (outputLength == 0) {
      return null;
    }
    var result = new StringBuilder(outputLength);
    for (var j = 0; j < outputLength; j++) {
      result.appendCodePoint(output[j]);
    }
    return result.toString();
  }

  /**
   * Encodes a label as Punycode. Returns {@code null} on arithmetic overflow, which the RFC's "fail
   * on overflow" steps require but which no input of a realistic length can reach.
   */
  static @Nullable String encode(String input) {
    var codePoints = input.codePoints().toArray();
    var out = new StringBuilder(codePoints.length);
    var basicCount = 0;
    for (var codePoint : codePoints) {
      if (codePoint < INITIAL_N) {
        out.appendCodePoint(codePoint);
        basicCount++;
      }
    }
    if (basicCount > 0) {
      out.append(DELIMITER);
    }

    var n = INITIAL_N;
    var bias = INITIAL_BIAS;
    var delta = 0L;
    var handled = basicCount;
    while (handled < codePoints.length) {
      var m = Integer.MAX_VALUE;
      for (var codePoint : codePoints) {
        if (codePoint >= n && codePoint < m) {
          m = codePoint;
        }
      }
      delta += (long) (m - n) * (handled + 1);
      if (delta > MAX) {
        return null;
      }
      n = m;

      for (var codePoint : codePoints) {
        if (codePoint < n) {
          if (++delta > MAX) {
            return null;
          }
        } else if (codePoint == n) {
          var q = delta;
          for (var k = BASE; ; k += BASE) {
            var t = threshold(k, bias);
            if (q < t) {
              break;
            }
            out.append(digitChar((int) (t + (q - t) % (BASE - t))));
            q = (q - t) / (BASE - t);
          }
          out.append(digitChar((int) q));
          bias = adapt(delta, handled + 1, handled == basicCount);
          delta = 0;
          handled++;
        }
      }
      delta++;
      n++;
    }
    return out.toString();
  }

  /** https://www.rfc-editor.org/rfc/rfc3492#section-6.1 */
  private static int adapt(long delta, int numPoints, boolean firstTime) {
    delta = firstTime ? delta / DAMP : delta / 2;
    delta += delta / numPoints;
    var k = 0;
    while (delta > ((BASE - TMIN) * TMAX) / 2) {
      delta /= BASE - TMIN;
      k += BASE;
    }
    return (int) (k + (BASE - TMIN + 1) * delta / (delta + SKEW));
  }

  private static int threshold(int k, int bias) {
    return Math.min(Math.max(k - bias, TMIN), TMAX);
  }

  /** The value of a Punycode digit, or a negative number if {@code c} is not one. */
  private static int digitValue(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0' + 26;
    }
    if (c >= 'a' && c <= 'z') {
      return c - 'a';
    }
    if (c >= 'A' && c <= 'Z') {
      return c - 'A';
    }
    return -1;
  }

  private static char digitChar(int digit) {
    return (char) (digit < 26 ? 'a' + digit : '0' + digit - 26);
  }

  /** Inserts {@code codePoint} at code-point position {@code index}, growing {@code output}. */
  private static int[] insert(int[] output, int length, int index, int codePoint) {
    var result = length == output.length ? Arrays.copyOf(output, length * 2) : output;
    System.arraycopy(result, index, result, index + 1, length - index);
    result[index] = codePoint;
    return result;
  }
}
