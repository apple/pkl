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
package org.pkl.core;

import java.io.PrintWriter;
import org.pkl.core.runtime.OutputFormatterColor;
import org.pkl.core.runtime.OutputFormatterPlain;

public abstract class OutputFormatter<SELF extends OutputFormatter<SELF>> {
  public static OutputFormatter<?> create(boolean usingColor) {
    return usingColor ? new OutputFormatterColor() : new OutputFormatterPlain();
  }

  public abstract SELF createBlank();

  public abstract SELF margin(String marginMatter);

  public abstract SELF hint(String hint);

  public abstract SELF newline();

  public abstract SELF repetitions(int counter);

  public abstract SELF text(String text);

  public abstract SELF errorHeader();

  public abstract SELF error(String message);

  public abstract SELF lineNumber(String line);

  public abstract SELF repeat(int width, char ch);

  public abstract SELF repeatError(int width, char ch);

  public abstract SELF append(String s);

  public abstract SELF append(char ch);

  public abstract SELF append(Object obj);

  public abstract PrintWriter toPrintWriter();
}
