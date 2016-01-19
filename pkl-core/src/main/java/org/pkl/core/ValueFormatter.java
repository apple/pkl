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
package org.pkl.core;

import java.io.IOException;
import org.pkl.core.util.ArrayCharEscaper;

public final class ValueFormatter {
  private static final ArrayCharEscaper charEscaper =
      ArrayCharEscaper.builder()
          .withEscape('\n', "\\n")
          .withEscape('\r', "\\r")
          .withEscape('\t', "\\t")
          .withEscape('"', "\\\"")
          .withEscape('\\', "\\\\")
          .build();

  private static final ValueFormatter BASIC = new ValueFormatter(false, false);

  private static final ValueFormatter WITH_CUSTOM_DELIMITERS = new ValueFormatter(false, true);

  private final boolean useMultilineStrings;
  private final boolean useCustomStringDelimiters;

  /** Equivalent to {@code new ValueFormatter(false, false)}. */
  public static ValueFormatter basic() {
    return BASIC;
  }

  /** Equivalent to {@code new ValueFormatter(false, true)}. */
  public static ValueFormatter withCustomStringDelimiters() {
    return WITH_CUSTOM_DELIMITERS;
  }

  /**
   * Constructs an instance of a {@link ValueFormatter}.
   *
   * <p>If {@code useMultilineStrings} is {@code true}, string values containing newline characters
   * are formatted as multiline string literals.
   *
   * <p>If {@code useCustomStringDelimiters} is {@code true}, custom string delimiters (such as
   * {@code #"..."#}) are preferred over escaping quotes and backslashes.
   */
  public ValueFormatter(boolean useMultilineStrings, boolean useCustomStringDelimiters) {
    this.useMultilineStrings = useMultilineStrings;
    this.useCustomStringDelimiters = useCustomStringDelimiters;
  }

  /**
   * Formats {@code value} as a Pkl/Pcf string literal (including quotes).
   *
   * <p>If {@code value} contains a {@code \n} character, a multiline string literal is returned,
   * and subsequent lines are indented by {@code lineIndent}. Otherwise, a single line string
   * literal is returned.
   */
  @SuppressWarnings("unused")
  public String formatStringValue(String value, CharSequence lineIndent) {
    StringBuilder builder = new StringBuilder(value.length() * 2);
    formatStringValue(value, lineIndent, builder);
    return builder.toString();
  }

  /**
   * Same as {@link #formatStringValue(String, CharSequence)}, except that output goes to {@code
   * builder}.
   */
  public void formatStringValue(String value, CharSequence lineIndent, StringBuilder builder) {
    try {
      formatStringValue(value, lineIndent, (Appendable) builder);
    } catch (IOException e) {
      throw new AssertionError("unreachable");
    }
  }

  /**
   * Same as {@link #formatStringValue(String, CharSequence)}, except that output goes to {@code
   * appendable} (which may cause {@link IOException}).
   */
  public void formatStringValue(String value, CharSequence lineIndent, Appendable appendable)
      throws IOException {
    // Optimization: if we are rendering single line strings and not rendering custom string
    // delimiters, there is no need to gather string facts.
    if (!useMultilineStrings && !useCustomStringDelimiters) {
      formatSingleLineString(value, appendable, "");
      return;
    }
    var stringFacts = StringFacts.gather(value);
    var isMultiline = useMultilineStrings && stringFacts.isMultiline;
    if (isMultiline) {
      var poundChars =
          useCustomStringDelimiters ? "#".repeat(stringFacts.poundCharCountMultiline) : "";
      formatMultilineString(value, lineIndent, appendable, poundChars);
    } else {
      var poundChars =
          useCustomStringDelimiters ? "#".repeat(stringFacts.poundCharCountSingleLine) : "";
      formatSingleLineString(value, appendable, poundChars);
    }
  }

  private void formatSingleLineString(String value, Appendable appendable, String poundChars)
      throws IOException {
    appendable.append(poundChars).append('"');

    var i = 0;
    var escapeSequence = "\\" + poundChars;

    if (useCustomStringDelimiters) {
      if (value.equals("\"")) {
        // Edge case 1: If the string consists of a single quote, we must escape it. Otherwise the
        // output is `#"""#`.
        appendable.append(escapeSequence).append(value);
        i = 1;
      } else if (value.startsWith("\"\"")) {
        // Edge case 2: If the string starts with two quotes, we must escape the second one.
        // Otherwise, it will
        // be interpreted as a multiline string start (e.g. `"""`).
        appendable.append('"').append(escapeSequence).append('"');
        i = 2;
      }
    }

    for (; i < value.length(); i++) {
      var ch = value.charAt(i);
      switch (ch) {
        case '\n':
          appendable.append(escapeSequence).append('n');
          break;
        case '\r':
          appendable.append(escapeSequence).append('r');
          break;
        case '\t':
          appendable.append(escapeSequence).append('t');
          break;
        case '\\':
          if (useCustomStringDelimiters) {
            appendable.append(ch);
          } else {
            appendable.append("\\\\");
          }
          break;
        case '"':
          if (useCustomStringDelimiters) {
            appendable.append(ch);
          } else {
            appendable.append("\\\"");
          }
          break;
        default:
          appendable.append(ch);
      }
    }

    appendable.append('"').append(poundChars);
  }

  private void formatMultilineString(
      String value, CharSequence lineIndent, Appendable appendable, String poundChars)
      throws IOException {
    var consecutiveQuotes = 0;
    var escapeSequence = "\\" + poundChars;

    appendable.append(poundChars).append("\"\"\"\n").append(lineIndent);

    for (var i = 0; i < value.length(); i++) {
      var ch = value.charAt(i);
      switch (ch) {
        case '\n':
          appendable.append('\n').append(lineIndent);
          consecutiveQuotes = 0;
          break;
        case '\r':
          appendable.append(escapeSequence).append('r');
          consecutiveQuotes = 0;
          break;
        case '\t':
          appendable.append(escapeSequence).append('t');
          consecutiveQuotes = 0;
          break;
        case '\\':
          if (useCustomStringDelimiters) {
            appendable.append(ch);
          } else {
            appendable.append("\\\\");
          }
          consecutiveQuotes = 0;
          break;
        case '"':
          if (consecutiveQuotes == 2 && !useCustomStringDelimiters) {
            appendable.append("\\\"");
            consecutiveQuotes = 0;
          } else {
            appendable.append('"');
            consecutiveQuotes += 1;
          }
          break;
        default:
          appendable.append(ch);
          consecutiveQuotes = 0;
      }
    }

    appendable.append("\n").append(lineIndent).append("\"\"\"").append(poundChars);
  }

  /**
   * Stores basic facts about a string. This is used to assist with pretty-formatting Pcf strings;
   * e.g. determining whether to render as a multiline string, and whether to wrap the string with
   * {@code #} delimiters.
   */
  private static final class StringFacts {
    private final boolean isMultiline;

    /** The number of pound characters that should wrap a string if rendering as single line. */
    private final int poundCharCountSingleLine;

    /** The number of pound characters that should wrap a string if rendering as multiline. */
    private final int poundCharCountMultiline;

    private StringFacts(
        boolean isMultiline, int poundCharCountSingleLine, int poundCharCountMultiline) {
      this.isMultiline = isMultiline;
      this.poundCharCountSingleLine = poundCharCountSingleLine;
      this.poundCharCountMultiline = poundCharCountMultiline;
    }

    /**
     * Gathers the following pieces of information about a string:
     *
     * <ul>
     *   <li>What is the maximum number of consecutive pound characters that follow a multiline
     *       quote ({@code """})?
     *   <li>What is the maximum number of consecutive pound characters that follow a single line
     *       quote ({@code "})?
     *   <li>Are there newline characters in the string?
     * </ul>
     *
     * This is used to assist with rendering custom delimited strings (e.g. {@code #"..."#}).
     *
     * <p>Algorithm:
     *
     * <ol>
     *   <li>Determine the current token context (backlash, single line quote, multiline quote,
     *       other).
     *   <li>If there is a current token context, count the number of pound characters succeeding
     *       that token.
     *   <li>Keep track of the maximum number of pound characters for each token type.
     * </ol>
     */
    static StringFacts gather(final String value) {
      var isMultiline = false;
      var consecutiveQuoteCount = 0;
      var currentPoundContext = PoundContext.OTHER;
      var currentPoundCountSingleQuote = 0;
      var currentPoundCountMultilineQuote = 0;
      var currentPoundCountBackslash = 0;
      var poundCountSingleQuote = 0;
      var poundCountMultilineQuote = 0;
      var poundCountBackslash = 0;
      for (var i = 0; i < value.length(); i++) {
        var ch = value.charAt(i);
        switch (ch) {
          case '\\':
            currentPoundContext = PoundContext.BACKSLASH;
            currentPoundCountBackslash = 1;
            poundCountBackslash = Math.max(poundCountBackslash, currentPoundCountBackslash);
            break;
          case '"':
            consecutiveQuoteCount += 1;
            if (consecutiveQuoteCount < 3) {
              currentPoundContext = PoundContext.SINGLELINE_QUOTE;
              currentPoundCountSingleQuote = 1;
              poundCountSingleQuote = Math.max(poundCountSingleQuote, currentPoundCountSingleQuote);
            } else {
              currentPoundContext = PoundContext.MULTILINE_QUOTE;
              currentPoundCountMultilineQuote = 1;
              poundCountMultilineQuote =
                  Math.max(poundCountMultilineQuote, currentPoundCountMultilineQuote);
            }
            break;
          case '#':
            consecutiveQuoteCount = 0;
            switch (currentPoundContext) {
              case SINGLELINE_QUOTE:
                currentPoundCountSingleQuote += 1;
                poundCountSingleQuote =
                    Math.max(poundCountSingleQuote, currentPoundCountSingleQuote);
                break;
              case MULTILINE_QUOTE:
                currentPoundCountMultilineQuote += 1;
                poundCountMultilineQuote =
                    Math.max(poundCountMultilineQuote, currentPoundCountMultilineQuote);
                break;
              case BACKSLASH:
                currentPoundCountBackslash += 1;
                poundCountBackslash = Math.max(poundCountBackslash, currentPoundCountBackslash);
                break;
              default:
                break;
            }
            break;
          case '\n':
            isMultiline = true;
          default:
            consecutiveQuoteCount = 0;
            currentPoundContext = PoundContext.OTHER;
            break;
        }
      }
      return new StringFacts(
          isMultiline,
          Math.max(poundCountBackslash, poundCountSingleQuote),
          Math.max(poundCountBackslash, poundCountMultilineQuote));
    }

    /** Represents the context in which the pound character ({@code #}) succeeds. */
    private enum PoundContext {
      OTHER,
      SINGLELINE_QUOTE,
      MULTILINE_QUOTE,
      BACKSLASH,
    }
  }
}
