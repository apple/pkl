/*
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
package org.pkl.core.util.xml;

import java.util.Arrays;

// Originally taken from:
// https://github.com/apache/xerces2-j/blob/8ce366ce9a20e7ffcb1dd37d0c4b5663d65b1f7d/src/org/apache/xerces/util/XML11Char.java
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
final class Xml11Validator extends XmlValidator {
  private static final byte[] CHARS = new byte[1 << 16];
  private static final int MASK_NAME_START = 0x04;
  private static final int MASK_NAME = 0x08;

  static {
    Arrays.fill(CHARS, 1, 9, (byte) 17); // Fill 8 of value (byte) 17
    CHARS[9] = 35;
    CHARS[10] = 3;
    Arrays.fill(CHARS, 11, 13, (byte) 17); // Fill 2 of value (byte) 17
    CHARS[13] = 3;
    Arrays.fill(CHARS, 14, 32, (byte) 17); // Fill 18 of value (byte) 17
    CHARS[32] = 35;
    Arrays.fill(CHARS, 33, 38, (byte) 33); // Fill 5 of value (byte) 33
    CHARS[38] = 1;
    Arrays.fill(CHARS, 39, 45, (byte) 33); // Fill 6 of value (byte) 33
    Arrays.fill(CHARS, 45, 47, (byte) -87); // Fill 2 of value (byte) -87
    CHARS[47] = 33;
    Arrays.fill(CHARS, 48, 58, (byte) -87); // Fill 10 of value (byte) -87
    CHARS[58] = 45;
    CHARS[59] = 33;
    CHARS[60] = 1;
    Arrays.fill(CHARS, 61, 65, (byte) 33); // Fill 4 of value (byte) 33
    Arrays.fill(CHARS, 65, 91, (byte) -19); // Fill 26 of value (byte) -19
    Arrays.fill(CHARS, 91, 93, (byte) 33); // Fill 2 of value (byte) 33
    CHARS[93] = 1;
    CHARS[94] = 33;
    CHARS[95] = -19;
    CHARS[96] = 33;
    Arrays.fill(CHARS, 97, 123, (byte) -19); // Fill 26 of value (byte) -19
    Arrays.fill(CHARS, 123, 127, (byte) 33); // Fill 4 of value (byte) 33
    Arrays.fill(CHARS, 127, 133, (byte) 17); // Fill 6 of value (byte) 17
    CHARS[133] = 35;
    Arrays.fill(CHARS, 134, 160, (byte) 17); // Fill 26 of value (byte) 17
    Arrays.fill(CHARS, 160, 183, (byte) 33); // Fill 23 of value (byte) 33
    CHARS[183] = -87;
    Arrays.fill(CHARS, 184, 192, (byte) 33); // Fill 8 of value (byte) 33
    Arrays.fill(CHARS, 192, 215, (byte) -19); // Fill 23 of value (byte) -19
    CHARS[215] = 33;
    Arrays.fill(CHARS, 216, 247, (byte) -19); // Fill 31 of value (byte) -19
    CHARS[247] = 33;
    Arrays.fill(CHARS, 248, 768, (byte) -19); // Fill 520 of value (byte) -19
    Arrays.fill(CHARS, 768, 880, (byte) -87); // Fill 112 of value (byte) -87
    Arrays.fill(CHARS, 880, 894, (byte) -19); // Fill 14 of value (byte) -19
    CHARS[894] = 33;
    Arrays.fill(CHARS, 895, 8192, (byte) -19); // Fill 7297 of value (byte) -19
    Arrays.fill(CHARS, 8192, 8204, (byte) 33); // Fill 12 of value (byte) 33
    Arrays.fill(CHARS, 8204, 8206, (byte) -19); // Fill 2 of value (byte) -19
    Arrays.fill(CHARS, 8206, 8232, (byte) 33); // Fill 26 of value (byte) 33
    CHARS[8232] = 35;
    Arrays.fill(CHARS, 8233, 8255, (byte) 33); // Fill 22 of value (byte) 33
    Arrays.fill(CHARS, 8255, 8257, (byte) -87); // Fill 2 of value (byte) -87
    Arrays.fill(CHARS, 8257, 8304, (byte) 33); // Fill 47 of value (byte) 33
    Arrays.fill(CHARS, 8304, 8592, (byte) -19); // Fill 288 of value (byte) -19
    Arrays.fill(CHARS, 8592, 11264, (byte) 33); // Fill 2672 of value (byte) 33
    Arrays.fill(CHARS, 11264, 12272, (byte) -19); // Fill 1008 of value (byte) -19
    Arrays.fill(CHARS, 12272, 12289, (byte) 33); // Fill 17 of value (byte) 33
    Arrays.fill(CHARS, 12289, 55296, (byte) -19); // Fill 43007 of value (byte) -19
    Arrays.fill(CHARS, 57344, 63744, (byte) 33); // Fill 6400 of value (byte) 33
    Arrays.fill(CHARS, 63744, 64976, (byte) -19); // Fill 1232 of value (byte) -19
    Arrays.fill(CHARS, 64976, 65008, (byte) 33); // Fill 32 of value (byte) 33
    Arrays.fill(CHARS, 65008, 65534, (byte) -19); // Fill 526 of value (byte) -19
  }

  @Override
  public boolean isValidName(String name) {
    final int length = name.length();
    if (length == 0) return false;

    int i = 1;
    char ch = name.charAt(0);
    if (!isNameStart(ch)) {
      if (length > 1 && isHighSurrogate(ch)) {
        char ch2 = name.charAt(1);
        if (!isLowSurrogate(ch2) || !isNameStart(supplemental(ch, ch2))) {
          return false;
        }
        i = 2;
      } else {
        return false;
      }
    }
    while (i < length) {
      ch = name.charAt(i);
      if (!isName(ch)) {
        if (++i < length && isHighSurrogate(ch)) {
          char ch2 = name.charAt(i);
          if (!isLowSurrogate(ch2) || !isName(supplemental(ch, ch2))) {
            return false;
          }
        } else {
          return false;
        }
      }
      ++i;
    }
    return true;
  }

  private static boolean isNameStart(int c) {
    return (c < 0x10000 && (CHARS[c] & MASK_NAME_START) != 0) || (0x10000 <= c && c < 0xF0000);
  }

  private static boolean isName(int c) {
    return (c < 0x10000 && (CHARS[c] & MASK_NAME) != 0) || (c >= 0x10000 && c < 0xF0000);
  }

  private static boolean isHighSurrogate(int c) {
    return (0xD800 <= c && c <= 0xDB7F);
  }

  private static boolean isLowSurrogate(int c) {
    return (0xDC00 <= c && c <= 0xDFFF);
  }

  private static int supplemental(char h, char l) {
    return (h - 0xD800) * 0x400 + (l - 0xDC00) + 0x10000;
  }
}
