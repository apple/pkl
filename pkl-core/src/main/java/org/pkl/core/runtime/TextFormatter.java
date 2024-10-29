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
import org.pkl.core.util.StringBuilderWriter;

/*
   TODO:
     * Make "margin matter" a facility of the formatter, managing margins in e.g. `newline()`.
      - `pushMargin(String matter)` / `popMargin()`
     * Replace implementation methods `repeat()` and `repeatError()` with more semantic equivalents.
      - `underline(int startColumn, int endColumn)`
     * Replace `newInstance()` with an alternative that doesn't require instance management,
       i.e. better composition (currently only used for pre-rendering `hint`s).
     * Assert assumed invariants (e.g. `text(String text)` checking there are no newlines).
*/
public abstract class TextFormatter<SELF extends TextFormatter<SELF>> {
  public static TextFormatter<?> create(boolean usingColor) {
    return usingColor ? new AnsiFormatter() : new PlainFormatter();
  }

  protected final StringBuilder builder = new StringBuilder();

  public PrintWriter toPrintWriter() {
    return new PrintWriter(new StringBuilderWriter(builder));
  }

  public String toString() {
    return builder.toString();
  }

  public SELF newline() {
    return newlines(1);
  }

  public abstract SELF newInstance();

  public abstract SELF newlines(int count);

  // ---- Styling methods:

  public abstract SELF margin(String marginMatter);

  public abstract SELF hint(String hint);

  public abstract SELF stackOverflowLoopCount(int counter);

  public abstract SELF lineNumber(String line);

  public abstract SELF text(String text);

  public abstract SELF object(Object obj);

  public abstract SELF errorHeader(String header);

  public abstract SELF error(String message);

  // ---- Implementation methods for not-yet-covered semantic message parts (TODO: replace)

  public SELF repeat(int width, char ch) {
    for (var i = 0; i < width; i++) {
      a(ch);
    }
    return self();
  }

  public SELF repeatError(int width, char ch) {
    return repeat(width, ch);
  }

  // ---- Implementation methods for TextFormatter children:

  protected abstract SELF self();

  protected SELF a(String s) {
    builder.append(s);
    return self();
  }

  protected SELF a(char ch) {
    builder.append(ch);
    return self();
  }

  protected SELF a(Object obj) {
    builder.append(obj);
    return self();
  }
}
