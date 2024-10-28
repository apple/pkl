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

public class OutputFormatterPlain extends OutputFormatter<OutputFormatterPlain> {
  private final StringBuilder builder = new StringBuilder();

  @Override
  public OutputFormatterPlain createBlank() {
    return new OutputFormatterPlain();
  }

  @Override
  public OutputFormatterPlain margin(String marginMatter) {
    return append(marginMatter);
  }

  @Override
  public OutputFormatterPlain hint(String hint) {
    return append(hint);
  }

  @Override
  public OutputFormatterPlain newline() {
    return append('\n');
  }

  @Override
  public OutputFormatterPlain repetitions(int counter) {
    return append(counter);
  }

  @Override
  public OutputFormatterPlain text(String text) {
    return append(text);
  }

  @Override
  public OutputFormatterPlain errorHeader() {
    return append("–– Pkl Error ––\n");
  }

  @Override
  public OutputFormatterPlain error(String message) {
    return append(message);
  }

  @Override
  public OutputFormatterPlain lineNumber(String line) {
    return append(line);
  }

  @Override
  public OutputFormatterPlain repeat(int width, char ch) {
    return append(String.valueOf(ch).repeat(Math.max(0, width)));
  }

  @Override
  public OutputFormatterPlain repeatError(int width, char ch) {
    return repeat(width, ch);
  }

  @Override
  public OutputFormatterPlain append(String s) {
    builder.append(s);
    return this;
  }

  @Override
  public OutputFormatterPlain append(char ch) {
    builder.append(ch);
    return this;
  }

  @Override
  public OutputFormatterPlain append(Object obj) {
    builder.append(obj);
    return this;
  }

  @Override
  public PrintWriter toPrintWriter() {
    return new PrintWriter(new StringBuilderWriter(builder));
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
