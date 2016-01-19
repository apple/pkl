/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.parser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

public abstract class LexParseException extends RuntimeException {
  // line of the error's start position, 1-based
  private final int line;

  // column of the error's start position, 1-based
  private final int column;

  // number of characters, starting from line/column, belonging to the offending token
  private final int length;

  private final int relevance;

  private @Nullable ParserRuleContext partialParseResult;

  public LexParseException(String message, int line, int column, int length, int relevance) {
    super(format(message));
    this.line = line;
    this.column = column;
    this.length = length;
    this.relevance = relevance;
    partialParseResult = null;
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  public int getLength() {
    return length;
  }

  public int getRelevance() {
    return relevance;
  }

  public @Nullable ParserRuleContext getPartialParseResult() {
    return partialParseResult;
  }

  public LexParseException withPartialParseResult(ParserRuleContext partialParseResult) {
    this.partialParseResult = partialParseResult;
    return this;
  }

  public static class LexError extends LexParseException {
    public LexError(String message, int line, int column, int length) {
      super(message, line, column, length, Integer.MAX_VALUE);
    }
  }

  public static class ParseError extends LexParseException {
    public ParseError(String message, int line, int column, int length, int relevance) {
      super(message, line, column, length, relevance);
    }
  }

  public static class IncompleteInput extends ParseError {
    public IncompleteInput(String message, int line, int column, int length) {
      super(message, line, column, length, Integer.MAX_VALUE - 1);
    }
  }

  // format ANTLR error messages like Pkl's own error messages
  private static String format(String msg) {
    var result = IoUtils.capitalize(msg);
    result = result.replace("'", "`");
    if (!result.contains(":") && !result.endsWith(")") && !result.endsWith(".")) result += ".";
    return result;
  }
}
