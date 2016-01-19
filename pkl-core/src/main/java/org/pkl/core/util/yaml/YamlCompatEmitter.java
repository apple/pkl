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
public final class YamlCompatEmitter extends YamlEmitter {
  public YamlCompatEmitter(StringBuilder builder, String indent) {
    super(builder, indent);
  }

  @Override
  protected boolean isReservedWord(String str) {
    return isReserved11Word(str);
  }

  @Override
  protected boolean isNumber(String str, int colonIndex) {
    var length = str.length();
    assert length > 0;

    if (length == 1) {
      var ch = str.charAt(0);
      return IoUtils.isDecimalDigit(ch) || ch == '.';
    }

    int offset;
    switch (str.charAt(0)) {
      case '+':
      case '-':
        offset = 1;
        break;
      default:
        offset = 0;
    }

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
          case 'o':
            return Yaml11Emitter.isOctalNumber(str, offset + 2, length);
          case 'x':
            return Yaml11Emitter.isHexadecimalNumber(str, offset + 2, length);
        }
    }

    return isDecimalNumber(str, offset, length);
  }

  // same as Yaml12Emitter.isDecimalNumber except for underscores
  static boolean isDecimalNumber(String str, int start, int length) {
    var ch = '\0';
    int index;

    for (index = start; index < length; index++) {
      ch = str.charAt(index);
      if (!IoUtils.isDecimalDigitOrUnderscore(ch)) break;
    }
    if (index == length) return true;

    if (ch == '.') {
      for (index = index + 1; index < length; index++) {
        ch = str.charAt(index);
        if (!IoUtils.isDecimalDigitOrUnderscore(ch)) break;
      }
      if (index == length) return true;
    }

    if (ch == 'e' || ch == 'E') {
      index++;
      if (index == length) return false;
      ch = str.charAt(index);
      if (ch == '-' || ch == '+') {
        index++;
        if (index == length) return false;
      }
      for (; index < length; index++) {
        ch = str.charAt(index);
        if (!IoUtils.isDecimalDigit(ch)) break;
      }
    }

    return index == length;
  }
}
