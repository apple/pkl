/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.pkl.core.util.AnsiStringBuilder.AnsiCode;

public final class AnsiTheme {
  private AnsiTheme() {}

  public static final AnsiCode ERROR_MESSAGE_HINT = AnsiCode.YELLOW;
  public static final AnsiCode ERROR_HEADER = AnsiCode.RED;
  public static final EnumSet<AnsiCode> ERROR_MESSAGE = EnumSet.of(AnsiCode.RED, AnsiCode.BOLD);

  public static final AnsiCode STACK_FRAME = AnsiCode.FAINT;
  public static final AnsiCode STACK_TRACE_MARGIN = AnsiCode.YELLOW;
  public static final AnsiCode STACK_TRACE_LINE_NUMBER = AnsiCode.FAINT;
  public static final AnsiCode STACK_TRACE_LOOP_COUNT = AnsiCode.MAGENTA;
  public static final AnsiCode STACK_TRACE_CARET = AnsiCode.RED;

  public static final AnsiCode FAILING_TEST_MARK = AnsiCode.RED;
  public static final AnsiCode PASSING_TEST_MARK = AnsiCode.GREEN;
  public static final AnsiCode TEST_NAME = AnsiCode.FAINT;
  public static final AnsiCode TEST_FACT_SOURCE = AnsiCode.RED;
  public static final AnsiCode TEST_FAILURE_MESSAGE = AnsiCode.RED;
  public static final EnumSet<AnsiCode> TEST_EXAMPLE_OUTPUT =
      EnumSet.of(AnsiCode.RED, AnsiCode.BOLD);

  public static final AnsiCode SYNTAX_KEYWORD = AnsiCode.BLUE;
  public static final AnsiCode SYNTAX_NUMBER = AnsiCode.GREEN;
  public static final AnsiCode SYNTAX_STRING = AnsiCode.YELLOW;
  public static final AnsiCode SYNTAX_STRING_ESCAPE = AnsiCode.BRIGHT_YELLOW;
  public static final AnsiCode SYNTAX_COMMENT = AnsiCode.FAINT;
  public static final AnsiCode SYNTAX_OPERATOR = AnsiCode.RESET;
  public static final AnsiCode SYNTAX_CONTROL = AnsiCode.BLUE;
  public static final AnsiCode SYNTAX_CONSTANT = AnsiCode.CYAN;
}
