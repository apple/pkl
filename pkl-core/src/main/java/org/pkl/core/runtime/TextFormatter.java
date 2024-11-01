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
import java.util.HashMap;
import java.util.Map;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.StringBuilderWriter;

/*
   TODO:
     * Make "margin matter" a facility of the formatter, managing margins in e.g. `newline()`.
      - `pushMargin(String matter)` / `popMargin()`
     * Replace implementation methods `repeat()` with more semantic equivalents.
      - `underline(int startColumn, int endColumn)`
     * Replace `newInstance()` with an alternative that doesn't require instance management,
       i.e. better composition (currently only used for pre-rendering `hint`s).
     * Assert assumed invariants (e.g. `append(String text)` checking there are no newlines).
     * Replace `THEME_ANSI` with one read from `pkl:settings`.
*/
public final class TextFormatter {
  public static final Map<Element, @Nullable Styling> THEME_PLAIN = new HashMap<>();
  public static final Map<Element, @Nullable Styling> THEME_ANSI;

  static {
    THEME_ANSI =
        Map.of(
            Element.MARGIN, new Styling(Color.YELLOW, true, false),
            Element.HINT, new Styling(Color.YELLOW, true, true),
            Element.STACK_OVERFLOW_LOOP_COUNT, new Styling(Color.MAGENTA, false, false),
            Element.LINE_NUMBER, new Styling(Color.BLUE, false, false),
            Element.ERROR_HEADER, new Styling(Color.RED, false, false),
            Element.ERROR, new Styling(Color.RED, false, true));
  }

  private final Map<Element, @Nullable Styling> theme;
  private final StringBuilder builder = new StringBuilder();

  private @Nullable Styling currentStyle;

  private TextFormatter(Map<Element, Styling> theme) {
    this.theme = theme;
    this.currentStyle = theme.getOrDefault(Element.PLAIN, null);
  }

  public static TextFormatter create(boolean usingColor) {
    return new TextFormatter(usingColor ? THEME_ANSI : THEME_PLAIN);
  }

  public PrintWriter toPrintWriter() {
    return new PrintWriter(new StringBuilderWriter(builder));
  }

  public String toString() {
    return builder.toString();
  }

  public TextFormatter newline() {
    return newlines(1);
  }

  public TextFormatter newInstance() {
    return new TextFormatter(theme);
  }

  public TextFormatter newlines(int count) {
    return repeat(count, '\n');
  }

  public TextFormatter margin(String marginMatter) {
    return style(Element.MARGIN).append(marginMatter);
  }

  public TextFormatter style(Element element) {
    var style = theme.getOrDefault(element, null);
    if (currentStyle == style) {
      return this;
    }
    if (style == null) {
      append("\033[0m");
      currentStyle = style;
      return this;
    }
    var colorCode =
        style.bright() ? style.foreground().fgBrightCode() : style.foreground().fgCode();
    append('\033');
    append('[');
    append(colorCode);
    if (style.bold() && (currentStyle == null || !currentStyle.bold())) {
      append(";1");
    } else if (!style.bold() && currentStyle != null && currentStyle.bold()) {
      append(";22");
    }
    append('m');
    currentStyle = style;
    return this;
  }

  public TextFormatter repeat(int width, char ch) {
    for (var i = 0; i < width; i++) {
      append(ch);
    }
    return this;
  }

  public TextFormatter append(String s) {
    builder.append(s);
    return this;
  }

  public TextFormatter append(char ch) {
    builder.append(ch);
    return this;
  }

  public TextFormatter append(int i) {
    builder.append(i);
    return this;
  }

  public TextFormatter append(Object obj) {
    builder.append(obj);
    return this;
  }

  public enum Element {
    PLAIN,
    MARGIN,
    HINT,
    STACK_OVERFLOW_LOOP_COUNT,
    LINE_NUMBER,
    TEXT,
    ERROR_HEADER,
    ERROR
  }

  public record Styling(Color foreground, boolean bold, boolean bright) {}

  public enum Color {
    BLACK(30),
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    MAGENTA(35),
    CYAN(36),
    WHITE(37);

    private final int code;

    Color(int code) {
      this.code = code;
    }

    public int fgCode() {
      return code;
    }

    public int bgCode() {
      return code + 10;
    }

    public int fgBrightCode() {
      return code + 60;
    }

    public int bgBrightCode() {
      return code + 70;
    }
  }
}
