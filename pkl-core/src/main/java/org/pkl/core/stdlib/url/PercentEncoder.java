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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

/**
 * Percent-encoding and the WHATWG <a href="https://url.spec.whatwg.org/#percent-encoded-bytes">
 * percent-encode sets</a>.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
final class PercentEncoder {
  private PercentEncoder() {}

  static final IntPredicate C0_CONTROL = c -> c <= 0x1F || c > 0x7E;

  static final IntPredicate FRAGMENT =
      c -> C0_CONTROL.test(c) || c == ' ' || c == '"' || c == '<' || c == '>' || c == '`';

  static final IntPredicate QUERY =
      c -> C0_CONTROL.test(c) || c == ' ' || c == '"' || c == '#' || c == '<' || c == '>';

  static final IntPredicate SPECIAL_QUERY = c -> QUERY.test(c) || c == '\'';

  static final IntPredicate PATH =
      c -> QUERY.test(c) || c == '?' || c == '^' || c == '`' || c == '{' || c == '}';

  static final IntPredicate USERINFO =
      c ->
          PATH.test(c)
              || c == '/'
              || c == ':'
              || c == ';'
              || c == '='
              || c == '@'
              || c == '['
              || c == '\\'
              || c == ']'
              || c == '^'
              || c == '|';

  static final IntPredicate COMPONENT =
      c -> USERINFO.test(c) || c == '$' || c == '%' || c == '&' || c == '+' || c == ',';

  static final IntPredicate FORM_URLENCODED =
      c -> COMPONENT.test(c) || c == '!' || c == '\'' || c == '(' || c == ')' || c == '~';

  /**
   * UTF-8 percent-encodes {@code codePoint} using {@code set} and appends the result to {@code
   * out}.
   */
  static void encode(int codePoint, IntPredicate set, StringBuilder out) {
    if (!set.test(codePoint)) {
      out.appendCodePoint(codePoint);
      return;
    }
    var bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
    for (var b : bytes) {
      out.append('%');
      out.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
      out.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
    }
  }

  /**
   * UTF-8 percent-encodes {@code codePoint} for an {@code application/x-www-form-urlencoded} string
   * and appends the result to {@code out}.
   */
  static void encodeForm(int codePoint, StringBuilder out) {
    if (codePoint == ' ') {
      out.append('+');
    } else {
      encode(codePoint, FORM_URLENCODED, out);
    }
  }

  /** decodes percent-encoded bytes and interprets the result as UTF-8. */
  static String percentDecode(String input) {
    var in = input.getBytes(StandardCharsets.UTF_8);
    var bytes = new ByteArrayOutputStream(in.length);
    for (var i = 0; i < in.length; i++) {
      if (in[i] != '%' || i + 2 >= in.length || !isHexDigit(in[i + 1]) || !isHexDigit(in[i + 2])) {
        bytes.write(in[i]);
      } else {
        bytes.write((hexValue(in[i + 1]) << 4) | hexValue(in[i + 2]));
        i += 2;
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  static boolean isHexDigit(int c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static int hexValue(int c) {
    return Character.digit(c, 16);
  }
}
