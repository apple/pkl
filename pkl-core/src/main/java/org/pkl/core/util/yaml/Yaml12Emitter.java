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
package org.pkl.core.util.yaml;

import org.pkl.core.util.IoUtils;

@SuppressWarnings("DuplicatedCode")
public final class Yaml12Emitter extends YamlEmitter {
  public Yaml12Emitter(StringBuilder builder, String indent) {
    super(builder, indent);
  }

  @Override
  protected boolean isReservedWord(String str) {
    if (str.length() > 5) return false;

    return switch (str) {
      case "",
              "~",
              "null",
              "Null",
              "NULL",
              ".nan",
              ".NaN",
              ".NAN",
              ".inf",
              ".Inf",
              ".INF",
              "+.inf",
              "+.Inf",
              "+.INF",
              "-.inf",
              "-.Inf",
              "-.INF",
              "true",
              "True",
              "TRUE",
              "false",
              "False",
              "FALSE" ->
          true;
      default -> false;
    };
  }

  @Override
  protected boolean isNumber(String str, int colonIndex) {
    var length = str.length();
    assert length > 0;

    if (length == 1) return IoUtils.isDecimalDigit(str.charAt(0));

    switch (str.charAt(0)) {
      case '0':
        switch (str.charAt(1)) {
          case 'o':
            return isOctalNumber(str, 2, length);
          case 'x':
            return isHexadecimalNumber(str, 2, length);
        }
      case '-':
      case '+':
        return isDecimalNumber(str, 1, length);
    }

    return isDecimalNumber(str, 0, length);
  }

  static boolean isOctalNumber(String str, int start, int length) {
    if (start == length) return false;
    for (var i = start; i < length; i++) {
      var ch = str.charAt(i);
      if (!IoUtils.isOctalDigit(ch)) return false;
    }
    return true;
  }

  static boolean isHexadecimalNumber(String str, int start, int length) {
    if (start == length) return false;
    for (var i = start; i < length; i++) {
      var ch = str.charAt(i);
      if (!IoUtils.isHexDigit(ch)) return false;
    }
    return true;
  }

  static boolean isDecimalNumber(String str, int start, int length) {
    var ch = '\0';
    int index;

    for (index = start; index < length; index++) {
      ch = str.charAt(index);
      if (!IoUtils.isDecimalDigit(ch)) break;
    }
    if (index == length) return true;

    if (ch == '.') {
      for (index = index + 1; index < length; index++) {
        ch = str.charAt(index);
        if (!IoUtils.isDecimalDigit(ch)) break;
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
