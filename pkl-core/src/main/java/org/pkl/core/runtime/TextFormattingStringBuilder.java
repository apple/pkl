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
package org.pkl.core.runtime;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import org.pkl.core.util.StringBuilderWriter;

// TODO: Replace `newInstance()` with an alternative that doesn't require instance management,
//   i.e. better composition (currently only used for pre-rendering `hint`s).
@SuppressWarnings("DuplicatedCode")
public final class TextFormattingStringBuilder {
  private final StringBuilder builder = new StringBuilder();
  private final boolean usingColor;

  /** The set of ansi codes currently applied. */
  private Set<AnsiCode> currentCodes = Collections.emptySet();

  /** The set of ansi codes intended to be applied the next time text is written. */
  private Set<AnsiCode> declaredCodes = Collections.emptySet();

  public TextFormattingStringBuilder(boolean usingColor) {
    this.usingColor = usingColor;
  }

  public TextFormattingStringBuilder appendLine() {
    builder.append("\n");
    return this;
  }

  public TextFormattingStringBuilder appendLine(int count) {
    builder.append("\n".repeat(count));
    return this;
  }

  public TextFormattingStringBuilder append(Set<AnsiCode> codes, String value) {
    if (!usingColor) {
      builder.append(value);
      return this;
    }
    var prevDeclaredCodes = declaredCodes;
    declaredCodes = codes;
    append(value);
    declaredCodes = prevDeclaredCodes;
    return this;
  }

  public TextFormattingStringBuilder append(AnsiCode code, int value) {
    if (!usingColor) {
      builder.append(value);
      return this;
    }
    var prevDeclaredCodes = declaredCodes;
    declaredCodes = EnumSet.of(code);
    append(value);
    declaredCodes = prevDeclaredCodes;
    return this;
  }

  public TextFormattingStringBuilder append(AnsiCode code, String value) {
    if (!usingColor) {
      builder.append(value);
      return this;
    }
    var prevDeclaredCodes = declaredCodes;
    declaredCodes = EnumSet.of(code);
    append(value);
    declaredCodes = prevDeclaredCodes;
    return this;
  }

  public TextFormattingStringBuilder append(AnsiCode code, Runnable runnable) {
    if (!usingColor) {
      runnable.run();
      return this;
    }
    var prevDeclaredCodes = declaredCodes;
    declaredCodes = EnumSet.of(code);
    runnable.run();
    declaredCodes = prevDeclaredCodes;
    return this;
  }

  /**
   * Append a string whose contents are unknown, and might contain ANSI color codes.
   *
   * <p>Always add a reset and re-apply all colors after appending the string.
   */
  public TextFormattingStringBuilder appendUntrusted(String value) {
    appendCodes();
    builder.append(value);
    if (usingColor) {
      doReset();
      doAppendCodes(currentCodes);
    }
    return this;
  }

  public TextFormattingStringBuilder append(String value) {
    appendCodes();
    builder.append(value);
    return this;
  }

  public TextFormattingStringBuilder append(char value) {
    appendCodes();
    builder.append(value);
    return this;
  }

  public TextFormattingStringBuilder append(int value) {
    appendCodes();
    builder.append(value);
    return this;
  }

  public TextFormattingStringBuilder append(Object value) {
    appendCodes();
    builder.append(value);
    return this;
  }

  public <T> TextFormattingStringBuilder join(
      Iterable<T> coll, String delimiter, Consumer<T> eachFn) {
    var i = 0;
    for (var v : coll) {
      if (i != 0) {
        builder.append(delimiter);
      }
      eachFn.accept(v);
      i++;
    }
    return this;
  }

  public TextFormattingStringBuilder newInstance() {
    return new TextFormattingStringBuilder(usingColor);
  }

  public PrintWriter toPrintWriter() {
    return new PrintWriter(new StringBuilderWriter(builder));
  }

  public String toString() {
    // be a good citizen and unset any ansi escape codes currently set.
    reset();
    return builder.toString();
  }

  private void doAppendCodes(Set<AnsiCode> codes) {
    if (codes.isEmpty()) return;
    builder.append("\033[");
    var isFirst = true;
    for (var code : codes) {
      if (isFirst) {
        isFirst = false;
      } else {
        builder.append(';');
      }
      builder.append(code.value);
    }
    builder.append('m');
  }

  private void appendCodes() {
    if (!usingColor || currentCodes.equals(declaredCodes)) return;
    if (declaredCodes.containsAll(currentCodes)) {
      var newCodes = EnumSet.copyOf(declaredCodes);
      newCodes.removeAll(currentCodes);
      doAppendCodes(newCodes);
    } else {
      reset();
      doAppendCodes(declaredCodes);
    }
    currentCodes = declaredCodes;
  }

  private void reset() {
    if (!usingColor || currentCodes.isEmpty()) return;
    doReset();
  }

  private void doReset() {
    builder.append("\033[0m");
  }

  public enum Element {
    PLAIN,
    MARGIN,
    HINT,
    STACK_OVERFLOW_LOOP_COUNT,
    LINE_NUMBER,
    TEXT,
    ERROR_HEADER,
    ERROR,
    RESET,
    FAILING_TEST_MARK,
    PASSING_TEST_MARK,
    TEST_NAME,
  }

  public enum AnsiCode {
    RESET(0),
    BOLD(1),
    FAINT(2),

    BLACK(30),
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    MAGENTA(35),
    CYAN(36),
    WHITE(37),

    BG_BLACK(40),
    BG_RED(41),
    BG_GREEN(42),
    BG_YELLOW(43),
    BG_BLUE(44),
    BG_MAGENTA(45),
    BG_CYAN(46),
    BG_WHITE(47),

    BRIGHT_BLACK(90),
    BRIGHT_RED(91),
    BRIGHT_GREEN(92),
    BRIGHT_YELLOW(93),
    BRIGHT_BLUE(94),
    BRIGHT_MAGENTA(95),
    BRIGHT_CYAN(96),
    BRIGHT_WHITE(97),

    BG_BRIGHT_BLACK(100),
    BG_BRIGHT_RED(101),
    BG_BRIGHT_GREEN(102),
    BG_BRIGHT_YELLOW(103),
    BG_BRIGHT_BLUE(104),
    BG_BRIGHT_MAGENTA(105),
    BG_BRIGHT_CYAN(106),
    BG_BRIGHT_WHITE(107);

    private final int value;

    AnsiCode(int value) {
      this.value = value;
    }
  }
}
