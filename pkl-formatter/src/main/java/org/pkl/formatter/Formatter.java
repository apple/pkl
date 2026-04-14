/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.formatter;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.pkl.parser.GenericParser;

/**
 * A formatter for Pkl files that applies canonical formatting rules.
 *
 * @see GrammarVersion
 */
public class Formatter {

  private final GrammarVersion grammarVersion;

  public Formatter(GrammarVersion grammarVersion) {
    this.grammarVersion = grammarVersion;
  }

  public Formatter() {
    this(GrammarVersion.latest());
  }

  /**
   * Formats a Pkl file from the given file path.
   *
   * @param path the path to the Pkl file to format
   * @param grammarVersion grammar compatibility version
   * @return the formatted Pkl source code as a string
   * @throws java.io.IOException if the file cannot be read
   * @deprecated use {@code format(Files.readString(path))} instead
   */
  @Deprecated
  public String format(Path path, GrammarVersion grammarVersion) throws IOException {
    return new Formatter(grammarVersion).format(Files.readString(path));
  }

  /**
   * Formats a Pkl file from the given file path.
   *
   * @param path the path to the Pkl file to format
   * @return the formatted Pkl source code as a string
   * @throws java.io.IOException if the file cannot be read
   * @deprecated use {@code format(Files.readString(path))} instead
   */
  @Deprecated
  public String format(Path path) throws IOException {
    return format(path, GrammarVersion.latest());
  }

  /**
   * Formats the given Pkl source code text.
   *
   * @param text the Pkl source code to format
   * @param grammarVersion grammar compatibility version
   * @return the formatted Pkl source code as a string
   * @deprecated use {@code new Formatter(grammarVersion).format(text)} instead
   */
  @Deprecated
  public String format(String text, GrammarVersion grammarVersion) {
    return new Formatter(grammarVersion).format(text);
  }

  /**
   * Formats the given Pkl source code text.
   *
   * @param text the Pkl source code to format
   * @return the formatted Pkl source code as a string
   */
  public String format(String text) {
    var sb = new StringBuilder();
    format(text, sb);
    return sb.toString();
  }

  /**
   * Formats the given Pkl source code text.
   *
   * <p>It is the caller's responsibility to close {@code input}, and, if applicable, {@code
   * output}.
   *
   * @param input the Pkl source code to format
   * @param output the formatted Pkl source code
   * @throws IOException if an I/O error occurs during reading or writing
   */
  public void format(Reader input, Appendable output) throws IOException {
    var sb = new StringBuilder();
    var buf = new char[8192];
    int n;
    while ((n = input.read(buf)) != -1) {
      sb.append(buf, 0, n);
    }
    format(sb.toString(), output);
  }

  private void format(String input, Appendable output) {
    var ast = new GenericParser().parseModule(input);
    var formatAst = new Builder(input, grammarVersion).format(ast);
    // force a line at the end of the file
    var nodes = new Nodes(List.of(formatAst, ForceLine.INSTANCE));
    new Generator(output).generate(nodes);
  }
}
