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
package org.pkl.core.util.ini;

public class IniUtils {
  // bitmap 32 bit need to escape ' ', ';'
  private static final int[] bitmapEscapeSpace = new int[] {9728, 738197900, 268435456, 0};

  private static final int[] bitmapNoEscapeSpace = new int[] {9728, 738197644, 268435456, 0};

  private static final char[] hexDigitTable =
      new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  private static final char[] hashTable =
      new char[] {
        ' ', 0, '"', '#', 0, 0, 0, '\'', 0, 't', 'n', 0, 0, 'r', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ':', ';', '\\', '=', 0, 0
      };

  public static String renderPropertiesKeyOrValue(
      String value, boolean escapeSpace, boolean restrictCharset) {
    if (value.isEmpty()) {
      return "";
    }
    var builder = new StringBuilder();

    if (!escapeSpace && value.charAt(0) == ' ') {
      builder.append('\\');
    }

    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      var bitmap = escapeSpace ? bitmapEscapeSpace : bitmapNoEscapeSpace;
      var isEscapeChar = c < 128 && (bitmap[c >> 5] & (1 << c)) != 0;

      if (isEscapeChar) {
        builder.append('\\').append(hashTable[c % 32]);
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
