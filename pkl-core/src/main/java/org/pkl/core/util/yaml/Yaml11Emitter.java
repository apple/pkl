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
package org.pkl.core.util.yaml;

import org.pkl.core.util.IoUtils;

@SuppressWarnings("DuplicatedCode")
public class Yaml11Emitter extends YamlEmitter {
  public Yaml11Emitter(StringBuilder builder, String indent) {
    super(builder, indent);
  }

  @Override
  protected boolean isReservedWord(String str) {
    return isReserved11Word(str);
  }

  protected boolean isNumber(String str, int colonIndex) {
    var length = str.length();
    assert length > 0;

    if (length == 1) {
      var ch = str.charAt(0);
      return IoUtils.isDecimalDigit(ch) || ch == '.';
    }

    var offset =
        switch (str.charAt(0)) {
          case '+', '-' -> 1;
          default -> 0;
        };

    if (colonIndex != -1) {
      return Yaml11Emitter.isSexagesimalNumber(str, offset, length, colonIndex);
    }

    switch (str.charAt(offset)) {
      case 'o':
        return Yaml11Emitter.isOctalNumber(str, offset + 1, length);
      case '0':
        if (offset == length - 1) return true;
        switch (str.charAt(offset + 1)) {
          case 'b':
            return Yaml11Emitter.isBinaryNumber(str, offset + 2, length);
          case 'x':
            return Yaml11Emitter.isHexadecimalNumber(str, offset + 2, length);
        }
    }

    return isDecimalNumber(str, offset, length);
  }

  static boolean isBinaryNumber(String str, int start, int length) {
    if (start == length) return false;
    for (var i = start; i < length; i++) {
      var ch = str.charAt(i);
      if (!IoUtils.isBinaryDigitOrUnderscore(ch)) return false;
    }
    return true;
  }

  static boolean isOctalNumber(String str, int start, int length) {
    if (start == length) return false;
    for (var i = start; i < length; i++) {
      var ch = str.charAt(i);
      if (!IoUtils.isOctalDigitOrUnderscore(ch)) return false;
    }
    return true;
  }

  static boolean isDecimalNumber(String str, int start, int length) {
    var ch = '\0';
    int index;

    for (index = start; index < length; index++) {
      ch = str.charAt(index);
      if (!IoUtils.isDecimalDigitOrUnderscore(ch)) break;
    }
    if (index == length) return true;

    if (ch != '.') return false;

    for (index = index + 1; index < length; index++) {
      ch = str.charAt(index);
      if (!IoUtils.isDecimalDigitOrUnderscore(ch)) break;
    }
    if (index == length) return true;

    if (!(ch == 'e' || ch == 'E')) return false;

    index++;
    if (index + 1 >= length) return false;
    ch = str.charAt(index);
    if (!(ch == '-' || ch == '+')) return false;
    for (index = index + 1; index < length; index++) {
      ch = str.charAt(index);
      if (!IoUtils.isDecimalDigit(ch)) break;
    }

    return index == length;
  }

  static boolean isHexadecimalNumber(String str, int start, int length) {
    if (start == length) return false;
    for (var i = start; i < length; i++) {
      var ch = str.charAt(i);
      if (!IoUtils.isHexDigitOrUnderscore(ch)) return false;
    }
    return true;
  }

  static boolean isSexagesimalNumber(String str, int start, int length, int colonIndex) {
    if (!IoUtils.isNonZeroDecimalDigit(str.charAt(start))) return false;
    // SonarQube: overflow is ok, will just throw IOOBE
    for (var i = start + 1; i < colonIndex; i++) {
      if (!IoUtils.isDecimalDigitOrUnderscore(str.charAt(i))) return false;
    }

    var state = 1;
    // SonarQube: overflow is ok, will just throw IOOBE
    for (var i = colonIndex + 1; i < length; i++) {
      switch (state) {
        case 0 -> {
          switch (str.charAt(i)) {
            case ':' -> state = 1;
            case '.' -> state = 3;
            default -> {
              return false;
            }
          }
        }
        case 1 -> {
          switch (str.charAt(i)) {
            case '0', '1', '2', '3', '4', '5' -> state = 2;
            case '6', '7', '8', '9' -> state = 0;
            default -> {
              return false;
            }
          }
        }
        case 2 -> {
          switch (str.charAt(i)) {
            case ':' -> state = 1;
            case '.' -> state = 3;
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> state = 0;
            default -> {
              return false;
            }
          }
        }
        case 3 -> {
          for (var j = i; j < length; j++) {
            if (!IoUtils.isDecimalDigitOrUnderscore(str.charAt(j))) return false;
          }
          return true;
        }
      }
    }

    return state != 1;
  }
}
