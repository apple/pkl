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

// Useful links:
// https://yaml-online-parser.appspot.com
// https://github.com/FasterXML/jackson-dataformats-text/pull/201
// https://perlpunk.github.io/slides.tpcia2017/the-state-of-the-yaml/index.html
// http://blogs.perl.org/users/tinita/2018/03/strings-in-yaml---to-quote-or-not-to-quote.html
public abstract class YamlEmitter {
  private static final YamlEscaper escaper = new YamlEscaper();

  protected final StringBuilder builder;
  protected final String indent;

  protected YamlEmitter(StringBuilder builder, String indent) {
    this.builder = builder;
    this.indent = indent;
  }

  public static YamlEmitter create(StringBuilder builder, String mode, String indent) {
    return switch (mode) {
      case "compat" -> new YamlCompatEmitter(builder, indent);
      case "1.1" -> new Yaml11Emitter(builder, indent);
      case "1.2" -> new Yaml12Emitter(builder, indent);
      default -> throw new IllegalArgumentException(mode);
    };
  }

  public void emit(String str, StringBuilder currIndent, boolean isKey) {
    if (isReservedWord(str)) {
      emitSingleQuotedString(str, -1);
      return;
    }

    var length = str.length();
    var needsEscaping = false;
    var needsQuoting = false;
    var hasNonNumberChar = false;
    var newlineIndex = -1;
    var colonIndex = -1;
    var singleQuoteIndex = -1;

    var first = str.charAt(0);
    switch (first) {
      case '\n':
        newlineIndex = 0;
        break;
      case '\'':
        needsQuoting = true;
        singleQuoteIndex = 0;
        break;
      case '!':
      case '%':
      case '&':
      case '*':
      case '{':
      case '}':
      case '[':
      case ']':
      case ',':
      case '#':
      case '|':
      case '>':
      case '@':
      case '`':
      case '"':
      case ' ':
        needsQuoting = true;
        break;
      case '-':
      case ':':
      case '?':
        needsQuoting = length == 1 || str.charAt(1) == ' ';
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
      case '8':
      case '9':
      case '+':
      case '.':
      case 'o':
        break;
      default:
        needsEscaping = first < 0x20;
        hasNonNumberChar = true;
    }

    for (int i = 1; i < length; i++) {
      var ch = str.charAt(i);
      switch (ch) {
        case '\n' -> {
          if (newlineIndex == -1) {
            newlineIndex = i;
          }
        }
        case '\'' -> {
          hasNonNumberChar = true;
          if (singleQuoteIndex == -1) {
            singleQuoteIndex = i;
          }
        }
        case ' ' -> {
          needsQuoting = needsQuoting || i == length - 1;
          hasNonNumberChar = true;
        }
        case '[', ']', '{', '}', ',' -> {
          needsQuoting = needsQuoting || isKey;
          hasNonNumberChar = true;
        }
        case '#' -> {
          needsQuoting = needsQuoting || str.charAt(i - 1) == ' ';
          hasNonNumberChar = true;
        }
        case ':' -> {
          if (colonIndex == -1) {
            colonIndex = i;
          }
          needsQuoting =
              needsQuoting || i == (length - 1) || (i + 1 < length) && str.charAt(i + 1) == ' ';
        }
          // number chars
        case '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'A',
            'B',
            'C',
            'D',
            'E',
            'F',
            'a',
            'b',
            'c',
            'd',
            'e',
            'f',
            '+',
            '-',
            '_',
            '.',
            'o',
            'x' -> {}
        default -> {
          needsEscaping = needsEscaping || ch < 0x20;
          hasNonNumberChar = true;
        }
      }
    }

    var pos = builder.length();

    if (needsEscaping) {
      emitDoubleQuotedString(str);
    } else if (newlineIndex != -1) {
      emitMultilineString(str, newlineIndex, currIndent);
    } else if (needsQuoting || !hasNonNumberChar && isNumber(str, colonIndex)) {
      emitSingleQuotedString(str, singleQuoteIndex);
    } else {
      builder.append(str);
    }

    // Make key explicit if it's more than 1024 characters long or a multiline string.
    // (YAML spec mandates explicit key for keys longer than 1024 Unicode characters,
    // for which 1024 characters is a safe approximation.)
    if (isKey && (builder.length() - pos > 1024 || !needsEscaping && newlineIndex != -1)) {
      builder.insert(pos, "? ").append('\n').append(currIndent);
    }
  }

  public final void emit(long value) {
    builder.append(value);
  }

  public final void emit(double value) {
    builder.append(
        Double.isNaN(value)
            ? ".NaN"
            : value == Double.POSITIVE_INFINITY
                ? ".Inf"
                : value == Double.NEGATIVE_INFINITY ? "-.Inf" : value);
  }

  public final void emit(boolean value) {
    builder.append(value);
  }

  public final void emitNull() {
    builder.append("null");
  }

  /** `Inf` and `NaN` are already taken care of by {@link #isReservedWord(String)}. */
  protected abstract boolean isNumber(String str, int colonIndex);

  public final String getResult() {
    return builder.toString();
  }

  protected abstract boolean isReservedWord(String str);

  protected static boolean isReserved11Word(String str) {
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
              "FALSE",
              "on",
              "On",
              "ON",
              "off",
              "Off",
              "OFF",
              "y",
              "Y",
              "yes",
              "Yes",
              "YES",
              "n",
              "N",
              "no",
              "No",
              "NO" ->
          true;
      default -> false;
    };
  }

  private void emitMultilineString(String str, int newlineIndex, StringBuilder currIndent) {
    currIndent.append(indent);

    builder.append('|');
    if (str.charAt(0) == ' ') {
      builder.append(indent.length());
    }

    var length = str.length();
    if (str.charAt(length - 1) == '\n') {
      if (length == 1 || str.charAt(length - 2) == '\n') {
        builder.append('+');
      }
    } else {
      builder.append('-');
    }

    builder.append('\n');

    var start = 0;
    for (int i = newlineIndex; i < length; i++) {
      var ch = str.charAt(i);
      if (ch != '\n') continue;

      if (i == start) {
        // don't add leading indent before newline
        builder.append('\n');
      } else {
        builder.append(currIndent).append(str, start, i + 1);
      }
      start = i + 1;
    }
    if (start < length) {
      builder.append(currIndent).append(str, start, length);
    }

    currIndent.setLength(currIndent.length() - indent.length());
  }

  private void emitDoubleQuotedString(String str) {
    builder.append('"');
    escaper.escape(str, builder);
    builder.append('"');
  }

  private void emitSingleQuotedString(String str, int singleQuoteIndex) {
    builder.append('\'');

    if (singleQuoteIndex == -1) {
      builder.append(str);
    } else {
      var start = 0;
      var length = str.length();
      for (var i = singleQuoteIndex; i < length; i++) {
        if (str.charAt(i) == '\'') {
          builder.append(str, start, i).append("''");
          start = i + 1;
        }
      }
      if (start < length) {
        builder.append(str, start, length);
      }
    }

    builder.append('\'');
  }
}
