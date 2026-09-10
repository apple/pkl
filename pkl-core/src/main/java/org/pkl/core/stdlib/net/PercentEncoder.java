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
package org.pkl.core.stdlib.net;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

/**
 * Percent-encoding, and the character classes of <a
 * href="https://www.rfc-editor.org/rfc/rfc3986#appendix-A">RFC 3986's ABNF</a>.
 *
 * <p>Each {@code IntPredicate} here holds for the characters that may appear literally in a
 * component. Anything else has to be percent-encoded to appear there at all.
 */
final class PercentEncoder {
  private PercentEncoder() {}

  /** unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~" */
  static final IntPredicate UNRESERVED = PercentEncoder::isUnreserved;

  /** userinfo = *( unreserved / pct-encoded / sub-delims / ":" ) */
  static final IntPredicate USERINFO = c -> isUnreserved(c) || isSubDelim(c) || c == ':';

  /** reg-name = *( unreserved / pct-encoded / sub-delims ) */
  static final IntPredicate REG_NAME = c -> isUnreserved(c) || isSubDelim(c);

  /** {@code *( pchar / "/" )}, which covers every shape of path. */
  static final IntPredicate PATH = c -> isPchar(c) || c == '/';

  /** {@code query} and {@code fragment} share a set: {@code *( pchar / "/" / "?" )}. */
  static final IntPredicate QUERY_OR_FRAGMENT = c -> isPchar(c) || c == '/' || c == '?';

  private static boolean isUnreserved(int c) {
    return isAlpha(c) || isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
  }

  /** sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "=" */
  private static boolean isSubDelim(int c) {
    return switch (c) {
      case '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=' -> true;
      default -> false;
    };
  }

  /** pchar = unreserved / pct-encoded / sub-delims / ":" / "@" */
  private static boolean isPchar(int c) {
    return isUnreserved(c) || isSubDelim(c) || c == ':' || c == '@';
  }

  static boolean isAlpha(int c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  static boolean isHexDigit(int c) {
    return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  /**
   * Appends {@code value} to {@code out}, percent-encoding every character that is not {@code
   * allowed}.
   *
   * <p>A {@code %} is passed through rather than encoded, so this leaves an already percent-encoded
   * value alone. Callers are expected to have checked that every {@code %} begins a percent-encoded
   * octet; see {@link UrlParser#hasValidPercentEncoding}.
   */
  static void encode(StringBuilder out, String value, IntPredicate allowed) {
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (codePoint == '%' || allowed.test(codePoint)) {
                out.appendCodePoint(codePoint);
              } else {
                encodeUtf8(codePoint, out);
              }
            });
  }

  /** UTF-8 percent-encodes {@code codePoint} and appends the result to {@code out}. */
  static void encodeUtf8(int codePoint, StringBuilder out) {
    var bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
    for (var b : bytes) {
      out.append('%');
      out.append(toUpperHexDigit((b >> 4) & 0xF));
      out.append(toUpperHexDigit(b & 0xF));
    }
  }

  /**
   * Appends {@code value} to {@code out}, percent-encoding everything that is not {@link
   * #UNRESERVED}, {@code %} included.
   *
   * <p>Unlike {@link #encode}, this treats its input as entirely undecoded, which is what a value
   * that is about to be interpolated into a URL component is.
   */
  static void encodeComponent(StringBuilder out, String value) {
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (isUnreserved(codePoint)) {
                out.appendCodePoint(codePoint);
              } else {
                encodeUtf8(codePoint, out);
              }
            });
  }

  private static char toUpperHexDigit(int value) {
    return Character.toUpperCase(Character.forDigit(value, 16));
  }

  /**
   * Percent-decodes {@code input} and interprets the decoded bytes as UTF-8.
   *
   * <p>A {@code %} that does not begin a percent-encoded octet is kept as-is.
   */
  static String decode(String input) {
    var in = input.getBytes(StandardCharsets.UTF_8);
    var bytes = new ByteArrayOutputStream(in.length);
    for (var i = 0; i < in.length; i++) {
      if (in[i] != '%' || i + 2 >= in.length || !isHexDigit(in[i + 1]) || !isHexDigit(in[i + 2])) {
        bytes.write(in[i]);
      } else {
        bytes.write((Character.digit(in[i + 1], 16) << 4) | Character.digit(in[i + 2], 16));
        i += 2;
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  /** Percent-decodes {@code input} as an {@code application/x-www-form-urlencoded}. */
  static String decodeForm(String input) {
    return decode(input.replace('+', ' '));
  }

  /**
   * Appends {@code value} to {@code out} as an {@code application/x-www-form-urlencoded}.
   *
   * <p>The inverse of {@link #decodeForm}.
   */
  static void encodeForm(StringBuilder out, String value) {
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (isUnreserved(codePoint)) {
                out.appendCodePoint(codePoint);
              } else if (codePoint == ' ') {
                out.append('+');
              } else {
                encodeUtf8(codePoint, out);
              }
            });
  }
}
