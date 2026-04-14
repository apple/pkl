/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import static org.pkl.parser.Token.FALSE;
import static org.pkl.parser.Token.NULL;
import static org.pkl.parser.Token.STRING_ESCAPE_BACKSLASH;
import static org.pkl.parser.Token.STRING_ESCAPE_NEWLINE;
import static org.pkl.parser.Token.STRING_ESCAPE_QUOTE;
import static org.pkl.parser.Token.STRING_ESCAPE_RETURN;
import static org.pkl.parser.Token.STRING_ESCAPE_TAB;
import static org.pkl.parser.Token.STRING_ESCAPE_UNICODE;
import static org.pkl.parser.Token.TRUE;

import java.util.EnumSet;
import org.pkl.parser.Lexer;
import org.pkl.parser.ParserError;
import org.pkl.parser.Token;

/** Syntax highlighter that emits ansi color codes. */
public final class SyntaxHighlighter {
  private SyntaxHighlighter() {}

  private static final EnumSet<Token> stringEscape =
      EnumSet.of(
          STRING_ESCAPE_NEWLINE,
          STRING_ESCAPE_TAB,
          STRING_ESCAPE_RETURN,
          STRING_ESCAPE_QUOTE,
          STRING_ESCAPE_BACKSLASH,
          STRING_ESCAPE_UNICODE);

  private static final EnumSet<Token> constant = EnumSet.of(TRUE, FALSE, NULL);

  private static final EnumSet<Token> operator =
      EnumSet.of(
          Token.COALESCE,
          Token.LT,
          Token.GT,
          Token.NOT,
          Token.EQUAL,
          Token.NOT_EQUAL,
          Token.LTE,
          Token.GTE,
          Token.AND,
          Token.OR,
          Token.PLUS,
          Token.MINUS,
          Token.POW,
          Token.STAR,
          Token.DIV,
          Token.INT_DIV,
          Token.MOD,
          Token.PIPE);

  private static final EnumSet<Token> keyword =
      EnumSet.of(
          Token.AMENDS,
          Token.AS,
          Token.EXTENDS,
          Token.CLASS,
          Token.TYPE_ALIAS,
          Token.FUNCTION,
          Token.MODULE,
          Token.IMPORT,
          Token.IMPORT_STAR,
          Token.READ,
          Token.READ_STAR,
          Token.READ_QUESTION,
          Token.TRACE,
          Token.THROW,
          Token.UNKNOWN,
          Token.NOTHING,
          Token.OUTER,
          Token.SUPER,
          Token.THIS,
          Token.HIDDEN,
          Token.ABSTRACT,
          Token.CONST,
          Token.FIXED,
          Token.LOCAL,
          Token.OPEN);

  private static final EnumSet<Token> control =
      EnumSet.of(
          Token.NEW, Token.IF, Token.ELSE, Token.WHEN, Token.FOR, Token.IN, Token.OUT, Token.LET);

  private static final EnumSet<Token> number =
      EnumSet.of(Token.INT, Token.FLOAT, Token.BIN, Token.OCT, Token.HEX);

  public static void writeTo(AnsiStringBuilder out, String src) {
    var prevLength = out.length();
    try {
      var lexer = new Lexer(src);
      doHighlightNormal(out, lexer.next(), lexer, Token.EOF);
    } catch (ParserError err) {
      // bail out and emit everything un-highlighted
      out.setLength(prevLength);
      out.append(src);
    }
  }

  private static void highlightString(AnsiStringBuilder out, Lexer lexer, Token token) {
    out.append(
        AnsiTheme.SYNTAX_STRING,
        () -> {
          var next = token;
          while (next != Token.STRING_END && next != Token.EOF) {
            if (stringEscape.contains(next)) {
              out.append(AnsiTheme.SYNTAX_STRING_ESCAPE, lexer.text());
              next = advance(out, lexer);
              continue;
            } else if (next == Token.INTERPOLATION_START) {
              out.append(AnsiTheme.SYNTAX_STRING_ESCAPE, lexer.text());
              out.appendSandboxed(() -> doHighlightNormal(out, lexer.next(), lexer, Token.RPAREN));
              out.append(AnsiTheme.SYNTAX_STRING_ESCAPE, lexer.text());
              lexer.next();
            }
            out.append(lexer.text());
            next = advance(out, lexer);
          }
          out.append(lexer.text());
        });
  }

  private static void doHighlightNormal(AnsiStringBuilder out, Token next, Lexer lexer, Token end) {
    {
      while (next != end && next != Token.EOF) {
        if (constant.contains(next)) {
          out.append(AnsiTheme.SYNTAX_CONSTANT, lexer.text());
        } else if (operator.contains(next)) {
          out.append(AnsiTheme.SYNTAX_OPERATOR, lexer.text());
        } else if (control.contains(next)) {
          out.append(AnsiTheme.SYNTAX_CONTROL, lexer.text());
        } else if (keyword.contains(next)) {
          out.append(AnsiTheme.SYNTAX_KEYWORD, lexer.text());
        } else if (next.isAffix()) {
          out.append(AnsiTheme.SYNTAX_COMMENT, lexer.text());
        } else if (number.contains(next)) {
          out.append(AnsiTheme.SYNTAX_NUMBER, lexer.text());
        } else if (next == Token.STRING_MULTI_START || next == Token.STRING_START) {
          highlightString(out, lexer, next);
        } else {
          out.append(lexer.text());
        }
        next = advance(out, lexer);
      }
    }
  }

  private static Token advance(AnsiStringBuilder out, Lexer lexer) {
    var prevCursor = lexer.getCursor();
    var next = lexer.next();
    // fill in any whitespace (includes semicolons)
    if (lexer.getStartCursor() > prevCursor) {
      out.append(lexer.textFor(prevCursor, lexer.getStartCursor() - prevCursor));
    }
    return next;
  }
}
