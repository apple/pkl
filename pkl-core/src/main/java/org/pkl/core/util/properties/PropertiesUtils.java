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
package org.pkl.core.util.properties;

import java.io.BufferedWriter;

public class PropertiesUtils {

  // Bitmap of characters that need escaping ('\t', '\n', '\f', '\r', ' ', '!', '#', ':', '=', '\\')
  // in four 32-bit segments.
  private static final int[] bitmapEscapeSpace = new int[] {13824, 603979787, 268435456, 0};

  // Bitmap of characters that need escaping ('\t', '\n', '\f', '\r', ' ', '!', '#', ':', '=', '\\')
  // in four 32-bit segments.
  private static final int[] bitmapNoEscapeSpace = new int[] {13824, 603979786, 268435456, 0};

  private static final char[] hexDigitTable =
      new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  // Hashtable for substituting characters that need escaping, using perfect hash function
  // `character % 32`.
  // The compacted array literal is equivalent to the code below:
  //
  // char[] hashtable = new char[32];
  // hashtable['\t' % 32 /*  9 */] =  't';
  // hashtable['\n' % 32 /* 10 */] =  'n';
  // hashtable['\f' % 32 /* 12 */] =  'f';
  // hashtable['\r' % 32 /* 13 */] =  'r';
  // hashtable[ ' ' % 32 /*  0 */] =  ' ';
  // hashtable[ '!' % 32 /*  1 */] =  '!';
  // hashtable[ '#' % 32 /*  3 */] =  '#';
  // hashtable[ ':' % 32 /* 26 */] =  ':';
  // hashtable[ '=' % 32 /* 29 */] =  '=';
  // hashtable['\\' % 32 /* 28 */] = '\\';
  private static final char[] hashtable =
      new char[] {
        ' ', '!', 0, '#', 0, 0, 0, 0, 0, 't', 'n', 0, 'f', 'r', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ':', 0, '\\', '=', 0, 0
      };

  private PropertiesUtils() {}

  /**
   * Write a property key or value according to {@link java.util.Properties#store0(BufferedWriter,
   * String, boolean)}.
   */
  @SuppressWarnings("JavadocReference")
  public static String renderPropertiesKeyOrValue(
      String value, boolean escapeSpace, boolean restrictCharset) {
    if (value.isEmpty()) return "";
    var builder = new StringBuilder();

    if (!escapeSpace && value.charAt(0) == ' ') {
      builder.append('\\'); // ensure escaping of first leading space
    }

    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      var bitmap = escapeSpace ? bitmapEscapeSpace : bitmapNoEscapeSpace;
      var isEscapeChar = c < 128 && (bitmap[c >> 5] & (1 << c)) != 0;

      if (isEscapeChar) {
        builder.append('\\').append(hashtable[c % 32]);
      } else if (restrictCharset && (c < 32 || c > 126)) {
        builder
            .append('\\')
            .append('u')
            .append(hexDigitTable[c >> 12 & 0xF])
            .append(hexDigitTable[c >> 8 & 0xF])
            .append(hexDigitTable[c >> 4 & 0xF])
            .append(hexDigitTable[c & 0xF]);
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }
}
