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
package org.pkl.core.util;

import java.io.Writer;

public class StringBuilderWriter extends Writer {
  private final StringBuilder builder;

  public StringBuilderWriter(StringBuilder builder) {
    this.builder = builder;
  }

  @Override
  public void write(int c) {
    builder.append((char) c);
  }

  @Override
  public void write(char[] cbuf) {
    builder.append(cbuf);
  }

  @Override
  public void write(String str) {
    builder.append(str);
  }

  @Override
  public void write(String str, int off, int len) {
    builder.append(str, off, off + len);
  }

  @Override
  public void write(char[] cbuf, int off, int len) {
    builder.append(cbuf, off, len);
  }

  @Override
  public Writer append(char c) {
    builder.append(c);
    return this;
  }

  @Override
  public Writer append(CharSequence csq) {
    builder.append(csq);
    return this;
  }

  @Override
  public Writer append(CharSequence csq, int start, int end) {
    builder.append(csq, start, end);
    return this;
  }

  @Override
  public void flush() {} // do nothing

  @Override
  public void close() {} // do nothing
}
