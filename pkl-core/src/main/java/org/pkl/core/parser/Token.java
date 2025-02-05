/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

public enum Token {
  ABSTRACT,
  AMENDS,
  AS,
  CLASS,
  CONST,
  ELSE,
  EXTENDS,
  EXTERNAL,
  FALSE,
  FIXED,
  FOR,
  FUNCTION,
  HIDDEN,
  IF,
  IMPORT,
  IMPORT_STAR,
  IN,
  IS,
  LET,
  LOCAL,
  MODULE,
  NEW,
  NOTHING,
  NULL,
  OPEN,
  OUT,
  OUTER,
  READ,
  READ_STAR,
  READ_QUESTION,
  SUPER,
  THIS,
  THROW,
  TRACE,
  TRUE,
  TYPE_ALIAS,
  UNKNOWN,
  WHEN,

  // reserved for future use
  PROTECTED,
  OVERRIDE,
  RECORD,
  DELETE,
  CASE,
  SWITCH,
  VARARG,

  // punctuation
  LPAREN,
  RPAREN,
  LBRACE,
  RBRACE,
  LBRACK,
  RBRACK,
  LPRED,
  COMMA,
  DOT,
  QDOT,
  COALESCE,
  NON_NULL,
  AT,
  ASSIGN,
  GT,
  LT,

  // rest
  NOT,
  QUESTION,
  COLON,
  ARROW,
  EQUAL,
  NOT_EQUAL,
  LTE,
  GTE,
  AND,
  OR,
  PLUS,
  MINUS,
  POW,
  STAR,
  DIV,
  INT_DIV,
  MOD,
  UNION,
  PIPE,
  SPREAD,
  QSPREAD,
  UNDERSCORE,
  EOF,
  SEMICOLON,

  INT,
  FLOAT,
  BIN,
  OCT,
  HEX,
  IDENT,
  LINE_COMMENT,
  BLOCK_COMMENT,
  DOC_COMMENT,
  SHEBANG,
  INTERPOLATION_START,
  STRING_START,
  STRING_MULTI_START,
  STRING_NEWLINE,
  STRING_ESCAPE_NEWLINE,
  STRING_ESCAPE_TAB,
  STRING_ESCAPE_RETURN,
  STRING_ESCAPE_QUOTE,
  STRING_ESCAPE_BACKSLASH,
  STRING_ESCAPE_UNICODE,
  STRING_END,
  STRING_PART;

  public boolean isModifier() {
    return switch (this) {
      case EXTERNAL, ABSTRACT, OPEN, LOCAL, HIDDEN, FIXED, CONST -> true;
      default -> false;
    };
  }

  public boolean isKeyword() {
    return switch (this) {
      case ABSTRACT,
          AMENDS,
          AS,
          CLASS,
          CONST,
          ELSE,
          EXTENDS,
          EXTERNAL,
          FALSE,
          FIXED,
          FOR,
          FUNCTION,
          HIDDEN,
          IF,
          IMPORT,
          IMPORT_STAR,
          IN,
          IS,
          LET,
          LOCAL,
          MODULE,
          NEW,
          NOTHING,
          NULL,
          OPEN,
          OUT,
          OUTER,
          READ,
          READ_STAR,
          READ_QUESTION,
          SUPER,
          THIS,
          THROW,
          TRACE,
          TRUE,
          TYPE_ALIAS,
          UNKNOWN,
          WHEN,
          UNDERSCORE,
          PROTECTED,
          OVERRIDE,
          RECORD,
          DELETE,
          CASE,
          SWITCH,
          VARARG ->
          true;
      default -> false;
    };
  }

  public String text() {
    if (this == UNDERSCORE) {
      return "_";
    }
    return name().toLowerCase();
  }
}
