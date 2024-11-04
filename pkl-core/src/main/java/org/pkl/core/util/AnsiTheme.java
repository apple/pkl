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
package org.pkl.core.util;

import java.util.EnumSet;
import java.util.Set;
import org.pkl.core.runtime.AnsiCodingStringBuilder.AnsiCode;

public final class AnsiTheme {
  private AnsiTheme() {}

  public final static AnsiCode ERROR_MESSAGE_HINT = AnsiCode.YELLOW;
  public final static AnsiCode ERROR_HEADER = AnsiCode.RED;
  public final static Set<AnsiCode> ERROR_MESSAGE = EnumSet.of(AnsiCode.RED, AnsiCode.BOLD);

  public final static AnsiCode STACK_FRAME = AnsiCode.FAINT;
  public final static AnsiCode STACK_TRACE_MARGIN = AnsiCode.YELLOW;
  public final static AnsiCode STACK_TRACE_LINE_NUMBER = AnsiCode.BLUE;
  public final static AnsiCode STACK_TRACE_LOOP_COUNT = AnsiCode.MAGENTA;
  public final static AnsiCode STACK_TRACE_CARET = AnsiCode.RED;

  public final static AnsiCode FAILING_TEST_MARK = AnsiCode.RED;
  public final static AnsiCode PASSING_TEST_MARK = AnsiCode.GREEN;
  public final static AnsiCode TEST_NAME = AnsiCode.FAINT;
  public final static AnsiCode TEST_FACT_SOURCE = AnsiCode.RED;
  public final static AnsiCode TEST_FAILURE_MESSAGE = AnsiCode.RED;
  public final static Set<AnsiCode> TEST_EXAMPLE_OUTPUT = EnumSet.of(AnsiCode.RED, AnsiCode.BOLD);
}
