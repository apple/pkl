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

/*
   TODO:
     * Manage "active state" to reduce "escape code spew" in output
*/
public class AnsiFormatter extends TextFormatter<AnsiFormatter> {
  private static final Color frameColor = Color.YELLOW;
  private static final Color lineNumColor = Color.BLUE;
  private static final Color repetitionColor = Color.MAGENTA;

  @Override
  public AnsiFormatter newInstance() {
    return new AnsiFormatter();
  }

  @Override
  public AnsiFormatter margin(String marginMatter) {
    return fgBright(frameColor).a(marginMatter).reset();
  }

  @Override
  public AnsiFormatter hint(String hint) {
    return fgBright(frameColor).bold().a(hint).reset();
  }

  @Override
  public AnsiFormatter stackOverflowLoopCount(int counter) {
    return bold().fg(repetitionColor).a(Integer.toString(counter)).reset();
  }

  @Override
  public AnsiFormatter text(String text) {
    return a(text);
  }

  @Override
  public AnsiFormatter errorHeader(String header) {
    return fg(Color.RED).a(header).reset();
  }

  @Override
  public AnsiFormatter error(String message) {
    return fgBright(Color.RED).a(message).reset();
  }

  @Override
  public AnsiFormatter lineNumber(String line) {
    return fgBright(lineNumColor).a(line).reset();
  }

  @Override
  public AnsiFormatter repeatError(int width, char ch) {
    return fg(Color.RED).repeat(width, ch).reset();
  }

  @Override
  public AnsiFormatter newline() {
    return a('\n');
  }

  @Override
  public AnsiFormatter newlines(int count) {
    return repeat(count, '\n');
  }

  @Override
  public AnsiFormatter object(Object obj) {
    return a(obj);
  }

  @Override
  protected AnsiFormatter self() {
    return this;
  }

  private AnsiFormatter fgBright(Color color) {
    return escape(color.fgBrightCode());
  }

  private AnsiFormatter fg(Color color) {
    return escape(color.fgCode());
  }

  private AnsiFormatter bold() {
    return escape(22);
  }

  private AnsiFormatter reset() {
    return escape(0);
  }

  private AnsiFormatter escape(int i) {
    return a('\033').a('[').a(i).a('m');
  }

  enum Color {
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
