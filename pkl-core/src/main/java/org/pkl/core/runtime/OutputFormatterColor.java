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
import org.pkl.core.OutputFormatter;
import org.pkl.core.util.StringBuilderWriter;

public class OutputFormatterColor extends OutputFormatter<OutputFormatterColor> {
  private static final Color frameColor = Color.YELLOW;
  private static final Color lineNumColor = Color.BLUE;
  private static final Color repetitionColor = Color.MAGENTA;

  private final StringBuilder builder = new StringBuilder();

  @Override
  public OutputFormatterColor createBlank() {
    return new OutputFormatterColor();
  }

  @Override
  public OutputFormatterColor margin(String marginMatter) {
    return fgBright(frameColor).a(marginMatter).reset();
  }

  @Override
  public OutputFormatterColor hint(String hint) {
    return fgBright(frameColor).bold().a(hint).reset();
  }

  @Override
  public OutputFormatterColor repetitions(int counter) {
    return bold().fg(repetitionColor).a(Integer.toString(counter)).reset();
  }

  @Override
  public OutputFormatterColor text(String text) {
    return a(text);
  }

  @Override
  public OutputFormatterColor errorHeader() {
    return fg(Color.RED).a("–– Pkl Error ––").newline();
  }

  @Override
  public OutputFormatterColor error(String message) {
    return fgBright(Color.RED).a(message).reset();
  }

  @Override
  public OutputFormatterColor lineNumber(String line) {
    return fgBright(lineNumColor).a(line).reset();
  }

  @Override
  public OutputFormatterColor repeat(int width, char ch) {
    for (var i = 0; i < width; i++) {
      a(ch);
    }
    return this;
  }

  @Override
  public OutputFormatterColor repeatError(int width, char ch) {
    return fg(Color.RED).repeat(width, ch).reset();
  }

  @Override
  public OutputFormatterColor newline() {
    return a('\n');
  }

  @Override
  public OutputFormatterColor append(String s) {
    return a(s);
  }

  @Override
  public OutputFormatterColor append(char ch) {
    return a(ch);
  }

  @Override
  public OutputFormatterColor append(Object obj) {
    return a(obj);
  }

  @Override
  public PrintWriter toPrintWriter() {
    return new PrintWriter(new StringBuilderWriter(builder));
  }

  @Override
  public String toString() {
    return builder.toString();
  }

  private OutputFormatterColor fgBright(Color color) {
    return escape(color.fgBrightCode());
  }

  private OutputFormatterColor fg(Color color) {
    return escape(color.fgCode());
  }

  private OutputFormatterColor a(String s) {
    builder.append(s);
    return this;
  }

  private OutputFormatterColor a(char ch) {
    builder.append(ch);
    return this;
  }

  private OutputFormatterColor a(Object obj) {
    builder.append(obj);
    return this;
  }

  private OutputFormatterColor bold() {
    return escape(22);
  }

  private OutputFormatterColor reset() {
    return escape(0);
  }

  private OutputFormatterColor escape(int i) {
    return a('\033').a('[').a(i).a('m');
  }

  private OutputFormatterColor escape(String s) {
    return a('\033').a('[').a(s).a('m');
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
