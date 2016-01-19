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
lexer grammar PklLexer;

@header {
package org.pkl.core.parser.antlr;
}

@members {
class StringInterpolationScope {
  int parenLevel = 0;
  int poundLength = 0;
}

java.util.Deque<StringInterpolationScope> interpolationScopes = new java.util.ArrayDeque<>();
StringInterpolationScope interpolationScope;

{ pushInterpolationScope(); }

void pushInterpolationScope() {
  interpolationScope = new StringInterpolationScope();
  interpolationScopes.push(interpolationScope);
}

void incParenLevel() {
  interpolationScope.parenLevel += 1;
}

void decParenLevel() {
  if (interpolationScope.parenLevel == 0) {
    // guard against syntax errors
    if (interpolationScopes.size() > 1) {
      interpolationScopes.pop();
      interpolationScope = interpolationScopes.peek();
      popMode();
    }
  } else {
    interpolationScope.parenLevel -= 1;
  }
}

boolean isPounds() {
  // optimize for common cases (0, 1)
  switch (interpolationScope.poundLength) {
    case 0: return true;
    case 1: return _input.LA(1) == '#';
    default:
      int poundLength = interpolationScope.poundLength;
      for (int i = 1; i <= poundLength; i++) {
        if (_input.LA(i) != '#') return false;
      }
      return true;
  }
}

boolean isQuote() {
  return _input.LA(1) == '"';
}

boolean endsWithPounds(String text) {
  assert text.length() >= 2;

  // optimize for common cases (0, 1)
  switch (interpolationScope.poundLength) {
    case 0: return true;
    case 1: return text.charAt(text.length() - 1) == '#';
    default:
      int poundLength = interpolationScope.poundLength;
      int textLength = text.length();
      if (textLength < poundLength) return false;

      int stop = textLength - poundLength;
      for (int i = textLength - 1; i >= stop; i--) {
        if (text.charAt(i) != '#') return false;
      }

      return true;
  }
}

void removeBackTicks() {
  String text = getText();
  setText(text.substring(1, text.length() - 1));
}

// look ahead in predicate rather than consume in grammar so that newlines
// go to NewlineSemicolonChannel, which is important for consumers of that channel
boolean isNewlineOrEof() {
  int input = _input.LA(1);
  return input == '\n' || input == '\r' || input == IntStream.EOF;
}

}

channels {
  NewlineSemicolonChannel,
  WhitespaceChannel,
  CommentsChannel,
  ShebangChannel
}

ABSTRACT     : 'abstract';
AMENDS       : 'amends';
AS           : 'as';
CLASS        : 'class';
CONST        : 'const';
ELSE         : 'else';
EXTENDS      : 'extends';
EXTERNAL     : 'external';
FALSE        : 'false';
FIXED        : 'fixed';
FOR          : 'for';
FUNCTION     : 'function';
HIDDEN_      : 'hidden';
IF           : 'if';
IMPORT       : 'import';
IMPORT_GLOB  : 'import*';
IN           : 'in';
IS           : 'is';
LET          : 'let';
LOCAL        : 'local';
MODULE       : 'module';
NEW          : 'new';
NOTHING      : 'nothing';
NULL         : 'null';
OPEN         : 'open';
OUT          : 'out';
OUTER        : 'outer';
READ         : 'read';
READ_GLOB    : 'read*';
READ_OR_NULL : 'read?';
SUPER        : 'super';
THIS         : 'this';
THROW        : 'throw';
TRACE        : 'trace';
TRUE         : 'true';
TYPE_ALIAS   : 'typealias';
UNKNOWN      : 'unknown';
WHEN         : 'when';

// reserved for future use, but not used today
PROTECTED : 'protected';
OVERRIDE  : 'override';
RECORD    : 'record';
DELETE    : 'delete';
CASE      : 'case';
SWITCH    : 'switch';
VARARG    : 'vararg';

LPAREN      : '(' { incParenLevel(); };
RPAREN      : ')' { decParenLevel(); };
LBRACE      : '{';
RBRACE      : '}';
LBRACK      : '[';
RBRACK      : ']';
LPRED       : '[['; // No RPRED, because that lexes too eager to allow nested index expressions, e.g. foo[bar[baz]]
COMMA       : ',';
DOT         : '.';
QDOT        : '?.';
COALESCE    : '??';
NON_NULL    : '!!';

AT          : '@';
ASSIGN      : '=';
GT          : '>';
LT          : '<';
NOT         : '!';
QUESTION    : '?';
COLON       : ':';
ARROW       : '->';
EQUAL       : '==';
NOT_EQUAL   : '!=';
LTE         : '<=';
GTE         : '>=';
AND         : '&&';
OR          : '||';
PLUS        : '+';
MINUS       : '-';
POW         : '**';
STAR        : '*';
DIV         : '/';
INT_DIV     : '~/';
MOD         : '%';
UNION       : '|';
PIPE        : '|>';
SPREAD      : '...';
QSPREAD     : '...?';
UNDERSCORE  : '_';

SLQuote    : '#'* '"'   { interpolationScope.poundLength = getText().length() - 1; } -> pushMode(SLString);
MLQuote    : '#'* '"""' { interpolationScope.poundLength = getText().length() - 3; } -> pushMode(MLString);

IntLiteral
  : DecimalLiteral
  | HexadecimalLiteral
  | BinaryLiteral
  | OctalLiteral
;

// leading zeros are allowed (cf. Swift)
fragment DecimalLiteral
  : DecimalDigit DecimalDigitCharacters?
  ;

fragment DecimalDigitCharacters
  : DecimalDigitCharacter+
  ;

fragment DecimalDigitCharacter
  : DecimalDigit
  | '_'
  ;

fragment DecimalDigit
  : [0-9]
  ;

fragment HexadecimalLiteral
  : '0x' HexadecimalCharacter+ // intentionally allow underscore after '0x'; e.g. `0x_ab`. We will throw an error in AstBuilder.
  ;

fragment HexadecimalCharacter
  : [0-9a-fA-F_]
  ;

fragment BinaryLiteral
  : '0b' BinaryCharacter+ // intentionally allow underscore after '0b'; e.g. `0b_11`. We will throw an error in AstBuilder.
  ;

fragment BinaryCharacter
  : [01_]
  ;

fragment OctalLiteral
  : '0o' OctalCharacter+ // intentionally allow underscore after '0o'; e.g. `0o_34`. We will throw an error in AstBuilder.
  ;

fragment OctalCharacter
  : [0-7_]
  ;

FloatLiteral
  : DecimalLiteral? '.' '_'? DecimalLiteral Exponent? // intentionally allow underscore. We will throw an error in AstBuilder.
  | DecimalLiteral Exponent
  ;

fragment Exponent
  : [eE] [+-]? '_'? DecimalLiteral // intentionally allow underscore. We will throw an error in AstBuilder.
  ;

Identifier
  : RegularIdentifier
  | QuotedIdentifier { removeBackTicks(); }
  ;

// Note: Keep in sync with Lexer.isRegularIdentifier()
fragment RegularIdentifier
  : IdentifierStart IdentifierPart*
  ;

fragment QuotedIdentifier
  : '`' (~'`')+ '`'
  ;

fragment
IdentifierStart
  : [a-zA-Z$_] // handle common cases without a predicate
  | . {Character.isUnicodeIdentifierStart(_input.LA(-1))}?
  ;

fragment
IdentifierPart
  : [a-zA-Z0-9$_] // handle common cases without a predicate
  | . {Character.isUnicodeIdentifierPart(_input.LA(-1))}?
  ;

NewlineSemicolon
  : [\r\n;]+ -> channel(NewlineSemicolonChannel)
  ;

// Note: Java, Scala, and Swift treat \f as whitespace; Dart doesn't.
// Python and C also include vertical tab.
// C# also includes Unicode class Zs (separator, space).
Whitespace
  : [ \t\f]+ -> channel(WhitespaceChannel)
  ;

DocComment
  : ([ \t\f]* '///' .*? (Newline|EOF))+
  ;

BlockComment
  : '/*' (BlockComment | .)*? '*/' -> channel(CommentsChannel)
  ;

LineComment
  : '//' .*? {isNewlineOrEof()}? -> channel(CommentsChannel)
  ;

ShebangComment
  : '#!' .*? {isNewlineOrEof()}? -> channel(ShebangChannel)
  ;

// strict: '\\' Pounds 'u{' HexDigit (HexDigit (HexDigit (HexDigit (HexDigit (HexDigit (HexDigit HexDigit? )?)?)?)?)?)? '}'
fragment UnicodeEscape
  : '\\' Pounds 'u{' ~[}\r\n "]* '}'?
  ;

// strict: '\\' Pounds [tnr"\\]
fragment CharacterEscape
  : '\\' Pounds .
  ;

fragment Pounds
  :      { interpolationScope.poundLength == 0 }?
  | '#'  { interpolationScope.poundLength == 1 }?
  | '#'+ { endsWithPounds(getText()) }?
  ;

fragment Newline
  : '\n' | '\r' '\n'?
  ;

mode SLString;

// strict: '"' Pounds
SLEndQuote
  : ('"' Pounds | Newline ) -> popMode
  ;

SLInterpolation
  : '\\' Pounds '(' { pushInterpolationScope(); } -> pushMode(DEFAULT_MODE)
  ;

SLUnicodeEscape
  : UnicodeEscape
  ;

SLCharacterEscape
  : CharacterEscape
  ;

SLCharacters
  : ~["\\\r\n]+ SLCharacters?
  | ["\\] {!isPounds()}? SLCharacters?
  ;

mode MLString;

MLEndQuote
  : '"""' Pounds -> popMode
  ;

MLInterpolation
  : '\\' Pounds '(' { pushInterpolationScope(); } -> pushMode(DEFAULT_MODE)
  ;

MLUnicodeEscape
  : UnicodeEscape
  ;

MLCharacterEscape
  : CharacterEscape
  ;

MLNewline
  : Newline
  ;

MLCharacters
  : ~["\\\r\n]+ MLCharacters?
  | ('\\' | '"""') {!isPounds()}? MLCharacters?
  | '"' '"'? {!isQuote()}? MLCharacters?
  ;
