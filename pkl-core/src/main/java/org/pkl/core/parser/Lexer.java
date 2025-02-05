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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.pkl.core.util.ErrorMessages;

public class Lexer {

  private final char[] source;
  private final int size;
  protected int cursor = 0;
  protected int sCursor = 0;
  private char lookahead;
  private State state = State.DEFAULT;
  private final Deque<InterpolationScope> interpolationStack = new ArrayDeque<>();
  private boolean stringEnded = false;
  private boolean isEscape = false;
  // how many newlines exist between two subsequent tokens
  protected int newLinesBetween = 0;

  private static final char EOF = Short.MAX_VALUE;

  public Lexer(String input) {
    source = input.toCharArray();
    size = source.length;
    if (size > 0) {
      lookahead = source[cursor];
    } else {
      lookahead = EOF;
    }
  }

  // The span of the last lexed token
  public Span span() {
    return new Span(sCursor, cursor - sCursor);
  }

  // The text of the last lexed token
  public String text() {
    return new String(source, sCursor, cursor - sCursor);
  }

  public char[] getSource() {
    return source;
  }

  public String textFor(int offset, int size) {
    return new String(source, offset, size);
  }

  public Token next() {
    sCursor = cursor;
    newLinesBetween = 0;
    return switch (state) {
      case DEFAULT -> nextDefault();
      case STRING -> nextString();
    };
  }

  private Token nextDefault() {
    var ch = nextChar();
    // ignore spaces
    while (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\f' || ch == '\r') {
      sCursor = cursor;
      if (ch == '\n') {
        newLinesBetween++;
      }
      ch = nextChar();
    }
    return switch (ch) {
      case EOF -> {
        // when EOF is reached we overshot the span
        cursor--;
        yield Token.EOF;
      }
      case ';' -> Token.SEMICOLON;
      case '(' -> {
        var scope = interpolationStack.peek();
        if (scope != null) {
          scope.parens++;
        }
        yield Token.LPAREN;
      }
      case ')' -> {
        var scope = interpolationStack.peek();
        if (scope != null) {
          scope.parens--;
          if (scope.parens <= 0) {
            // interpolation is over. Back to string
            state = State.STRING;
          }
        }
        yield Token.RPAREN;
      }
      case '{' -> Token.LBRACE;
      case '}' -> Token.RBRACE;
      case ',' -> Token.COMMA;
      case '@' -> Token.AT;
      case ':' -> Token.COLON;
      case '+' -> Token.PLUS;
      case '%' -> Token.MOD;
      case '[' -> {
        if (lookahead == '[') {
          nextChar();
          yield Token.LPRED;
        } else yield Token.LBRACK;
      }
      case ']' -> Token.RBRACK;
      case '=' -> {
        if (lookahead == '=') {
          nextChar();
          yield Token.EQUAL;
        } else yield Token.ASSIGN;
      }
      case '>' -> {
        if (lookahead == '=') {
          nextChar();
          yield Token.GTE;
        } else yield Token.GT;
      }
      case '<' -> {
        if (lookahead == '=') {
          nextChar();
          yield Token.LTE;
        } else yield Token.LT;
      }
      case '-' -> {
        if (lookahead == '>') {
          nextChar();
          yield Token.ARROW;
        } else yield Token.MINUS;
      }
      case '!' -> {
        if (lookahead == '!') {
          nextChar();
          yield Token.NON_NULL;
        } else if (lookahead == '=') {
          nextChar();
          yield Token.NOT_EQUAL;
        } else yield Token.NOT;
      }
      case '?' -> {
        if (lookahead == '.') {
          nextChar();
          yield Token.QDOT;
        } else if (lookahead == '?') {
          nextChar();
          yield Token.COALESCE;
        } else yield Token.QUESTION;
      }
      case '&' -> {
        if (lookahead == '&') {
          nextChar();
          yield Token.AND;
        } else {
          throw unexpectedChar(ch, "`&&`");
        }
      }
      case '|' -> {
        if (lookahead == '>') {
          nextChar();
          yield Token.PIPE;
        } else if (lookahead == '|') {
          nextChar();
          yield Token.OR;
        } else {
          yield Token.UNION;
        }
      }
      case '*' -> {
        if (lookahead == '*') {
          nextChar();
          yield Token.POW;
        } else yield Token.STAR;
      }
      case '~' -> {
        if (lookahead == '/') {
          nextChar();
          yield Token.INT_DIV;
        } else {
          throw unexpectedChar(ch, "`~/`");
        }
      }
      case '.' -> {
        if (lookahead == '.') {
          nextChar();
          if (lookahead == '.') {
            nextChar();
            if (lookahead == '?') {
              nextChar();
              yield Token.QSPREAD;
            } else {
              yield Token.SPREAD;
            }
          } else {
            throw unexpectedChar("..", "`.`, `...` or `...?`");
          }
        } else if (lookahead >= 48 && lookahead <= 57) {
          yield lexNumber(ch);
        } else {
          yield Token.DOT;
        }
      }
      case '`' -> {
        lexQuotedIdent();
        yield Token.IDENT;
      }
      case '/' -> lexSlash();
      case '"' -> lexStringStart(0);
      case '#' -> {
        if (lookahead == '!') {
          yield lexShebang();
        } else {
          yield lexStringStartPounds();
        }
      }
      default -> {
        if (Character.isDigit(ch)) {
          yield lexNumber(ch);
        } else if (isIdentifierStart(ch)) {
          yield lexIdent();
        } else throw lexError("invalidCharacter", ch);
      }
    };
  }

  private Token nextString() {
    var scope = interpolationStack.getFirst();
    if (stringEnded) {
      lexStringEnd(scope);
      stringEnded = false;
      interpolationStack.pop();
      state = State.DEFAULT;
      return Token.STRING_END;
    }
    if (lookahead == EOF) return Token.EOF;
    if (isEscape) {
      isEscape = false;
      // consume the `\#*`
      for (var i = 0; i < scope.pounds + 1; i++) {
        nextChar();
      }
      return lexEscape();
    }
    if (scope.quotes == 1) {
      lexString(scope.pounds);
    } else {
      if (lookahead == '\r') {
        nextChar();
        if (lookahead == '\n') {
          nextChar();
        }
        return Token.STRING_NEWLINE;
      }
      if (lookahead == '\n') {
        nextChar();
        return Token.STRING_NEWLINE;
      }
      lexMultiString(scope.pounds);
    }
    return Token.STRING_PART;
  }

  private Token lexStringStartPounds() {
    int pounds = 1;
    while (lookahead == '#') {
      nextChar();
      pounds++;
    }
    if (lookahead == EOF) {
      throw lexError(ErrorMessages.create("unexpectedEndOfFile"), span());
    }
    if (lookahead != '"') {
      throw unexpectedChar(lookahead, "`\"`");
    }
    nextChar();
    return lexStringStart(pounds);
  }

  private Token lexStringStart(int pounds) {
    var quotes = 1;
    if (lookahead == '"') {
      nextChar();
      if (lookahead == '"') {
        nextChar();
        quotes = 3;
      } else {
        backup();
      }
    }
    state = State.STRING;
    interpolationStack.push(new InterpolationScope(quotes, pounds));
    stringEnded = false;
    if (quotes == 1) return Token.STRING_START;
    return Token.STRING_MULTI_START;
  }

  private void lexStringEnd(InterpolationScope scope) {
    // don't actually need to check it here
    for (var i = 0; i < scope.quotes + scope.pounds; i++) {
      nextChar();
    }
  }

  private void lexString(int pounds) {
    var poundsInARow = 0;
    var foundQuote = false;
    var foundBackslash = false;
    while (lookahead != EOF) {
      var ch = nextChar();
      switch (ch) {
        case '\n', '\r' ->
            throw lexError(ErrorMessages.create("singleQuoteStringNewline"), cursor - 1, 1);
        case '"' -> {
          if (pounds == 0) {
            backup();
            stringEnded = true;
            return;
          }
          foundQuote = true;
          foundBackslash = false;
          poundsInARow = 0;
        }
        case '\\' -> {
          foundQuote = false;
          foundBackslash = true;
          poundsInARow = 0;
          if (pounds == poundsInARow) {
            backup(pounds + 1);
            isEscape = true;
            return;
          }
        }
        case '#' -> {
          poundsInARow++;
          if (foundQuote && (pounds == poundsInARow)) {
            backup(pounds + 1);
            stringEnded = true;
            return;
          }
          if (foundBackslash && pounds == poundsInARow) {
            backup(pounds + 1);
            isEscape = true;
            return;
          }
        }
        default -> {
          foundQuote = false;
          foundBackslash = false;
          poundsInARow = 0;
        }
      }
    }
  }

  private void lexMultiString(int pounds) {
    var poundsInARow = 0;
    var quotesInARow = 0;
    var foundBackslash = false;
    while (lookahead != EOF && lookahead != '\n' && lookahead != '\r') {
      var ch = nextChar();
      switch (ch) {
        case '"' -> {
          quotesInARow++;
          if (quotesInARow == 3 && pounds == 0) {
            backup(3);
            stringEnded = true;
            return;
          }
          poundsInARow = 0;
          foundBackslash = false;
        }
        case '\\' -> {
          quotesInARow = 0;
          poundsInARow = 0;
          foundBackslash = true;
          if (pounds == poundsInARow) {
            backup(pounds + 1);
            isEscape = true;
            return;
          }
        }
        case '#' -> {
          poundsInARow++;
          if (quotesInARow == 3 && pounds == poundsInARow) {
            backup(pounds + 3);
            stringEnded = true;
            return;
          }
          if (foundBackslash && pounds == poundsInARow) {
            backup(pounds + 1);
            isEscape = true;
            return;
          }
        }
        default -> {
          quotesInARow = 0;
          poundsInARow = 0;
          foundBackslash = false;
        }
      }
    }
  }

  private Token lexEscape() {
    if (lookahead == EOF) throw unexpectedEndOfFile();
    var ch = nextChar();
    return switch (ch) {
      case 'n' -> Token.STRING_ESCAPE_NEWLINE;
      case '"' -> Token.STRING_ESCAPE_QUOTE;
      case '\\' -> Token.STRING_ESCAPE_BACKSLASH;
      case 't' -> Token.STRING_ESCAPE_TAB;
      case 'r' -> Token.STRING_ESCAPE_RETURN;
      case '(' -> {
        var scope = interpolationStack.getFirst();
        scope.parens++;
        state = State.DEFAULT;
        yield Token.INTERPOLATION_START;
      }
      case 'u' -> lexUnicodeEscape();
      default ->
          throw lexError(
              ErrorMessages.create("invalidCharacterEscapeSequence", "\\" + ch, "\\"),
              cursor - 2,
              2);
    };
  }

  private Token lexUnicodeEscape() {
    if (lookahead != '{') {
      throw unexpectedChar(lookahead, "`{`");
    }
    do {
      nextChar();
    } while (lookahead != '}' && lookahead != EOF && Character.isLetterOrDigit(lookahead));
    if (lookahead == '}') {
      // consume the close bracket
      nextChar();
    } else {
      throw lexError(ErrorMessages.create("unterminatedUnicodeEscapeSequence", text()), span());
    }
    return Token.STRING_ESCAPE_UNICODE;
  }

  private Token lexIdent() {
    while (isIdentifierPart(lookahead)) {
      nextChar();
    }

    var identStr = text();
    var ident = getKeywordOrIdent(identStr);
    return switch (ident) {
      case IMPORT -> {
        if (lookahead == '*') {
          nextChar();
          yield Token.IMPORT_STAR;
        } else yield Token.IMPORT;
      }
      case READ ->
          switch (lookahead) {
            case '*' -> {
              nextChar();
              yield Token.READ_STAR;
            }
            case '?' -> {
              nextChar();
              yield Token.READ_QUESTION;
            }
            default -> Token.READ;
          };
      default -> ident;
    };
  }

  private void lexQuotedIdent() {
    while (lookahead != '`' && lookahead != '\n' && lookahead != '\r') {
      nextChar();
    }
    if (lookahead == '`') {
      nextChar();
    } else {
      throw unexpectedChar(lookahead, "backquote (`)");
    }
  }

  private Token lexNumber(char start) {
    if (start == '0') {
      if (lookahead == 'x' || lookahead == 'X') {
        nextChar();
        lexHexNumber();
        return Token.HEX;
      }
      if (lookahead == 'b' || lookahead == 'B') {
        nextChar();
        lexBinNumber();
        return Token.BIN;
      }
      if (lookahead == 'o' || lookahead == 'O') {
        nextChar();
        lexOctNumber();
        return Token.OCT;
      }
      if (lookahead == 'e' || lookahead == 'E') {
        nextChar();
        lexExponent();
        return Token.FLOAT;
      }
    } else if (start == '.') {
      lexDotNumber();
      return Token.FLOAT;
    }

    while ((lookahead >= 48 && lookahead <= 57) || lookahead == '_') {
      nextChar();
    }

    if (lookahead == 'e' || lookahead == 'E') {
      nextChar();
      lexExponent();
      return Token.FLOAT;
    } else if (lookahead == '.') {
      nextChar();
      if (lookahead == '_') {
        throw lexError("invalidSeparatorPosition");
      }
      if (lookahead < 48 || lookahead > 57) {
        backup();
        return Token.INT;
      }
      lexDotNumber();
      return Token.FLOAT;
    }
    return Token.INT;
  }

  private Token lexSlash() {
    switch (lookahead) {
      case '/':
        {
          nextChar();
          var token = lookahead == '/' ? Token.DOC_COMMENT : Token.LINE_COMMENT;
          while (lookahead != '\n' && lookahead != '\r' && lookahead != EOF) {
            nextChar();
          }
          return token;
        }
      case '*':
        {
          nextChar();
          lexBlockComment();
          return Token.BLOCK_COMMENT;
        }
      default:
        return Token.DIV;
    }
  }

  private void lexBlockComment() {
    if (lookahead == EOF) throw unexpectedEndOfFile();
    var prev = nextChar();
    // block comments in Pkl can stack
    var stack = 1;
    while (stack > 0 && lookahead != EOF) {
      if (prev == '*' && lookahead == '/') stack--;
      if (prev == '/' && lookahead == '*') stack++;
      prev = nextChar();
    }
    if (lookahead == EOF) throw unexpectedEndOfFile();
  }

  private void lexHexNumber() {
    if (lookahead == '_') {
      throw lexError("invalidSeparatorPosition");
    }
    if (!isHex(lookahead)) {
      throw unexpectedChar(lookahead, "`0` .. `9`, `a` .. `f` or `A` .. `F`");
    }
    while (isHex(lookahead) || lookahead == '_') {
      nextChar();
    }
  }

  private void lexBinNumber() {
    if (lookahead == '_') {
      throw lexError("invalidSeparatorPosition");
    }
    if (!(lookahead == '0' || lookahead == '1')) {
      throw unexpectedChar(lookahead, "`0` or `1`");
    }
    while (lookahead == '0' || lookahead == '1' || lookahead == '_') {
      nextChar();
    }
  }

  private void lexOctNumber() {
    if (lookahead == '_') {
      throw lexError("invalidSeparatorPosition");
    }
    var ch = (int) lookahead;
    if (!(ch >= 48 && ch <= 55)) {
      throw unexpectedChar((char) ch, "`0` .. `7`");
    }
    while ((ch >= 48 && ch <= 55) || ch == '_') {
      nextChar();
      ch = lookahead;
    }
  }

  private void lexExponent() {
    if (lookahead == '+' || lookahead == '-') {
      nextChar();
    }
    if (lookahead == '_') {
      throw lexError("invalidSeparatorPosition");
    }
    if (lookahead < 48 || lookahead > 57) {
      throw unexpectedChar(lookahead, "`0` .. `9`");
    }
    while ((lookahead >= 48 && lookahead <= 57) || lookahead == '_') {
      nextChar();
    }
  }

  private void lexDotNumber() {
    if (lookahead == '_') {
      throw lexError("invalidSeparatorPosition");
    }
    while ((lookahead >= 48 && lookahead <= 57) || lookahead == '_') {
      nextChar();
    }
    if (lookahead == 'e' || lookahead == 'E') {
      nextChar();
      lexExponent();
    }
  }

  private Token lexShebang() {
    do {
      nextChar();
    } while (lookahead != '\n' && lookahead != '\r' && lookahead != EOF);
    return Token.SHEBANG;
  }

  private boolean isHex(char ch) {
    var code = (int) ch;
    return (code >= 48 && code <= 57) || (code >= 97 && code <= 102) || (code >= 65 && code <= 70);
  }

  private static boolean isIdentifierStart(char c) {
    return c == '_' || c == '$' || Character.isUnicodeIdentifierStart(c);
  }

  private static boolean isIdentifierPart(char c) {
    return c != EOF && (c == '$' || Character.isUnicodeIdentifierPart(c));
  }

  private char nextChar() {
    var tmp = lookahead;
    cursor++;
    if (cursor >= size) {
      lookahead = EOF;
    } else {
      lookahead = source[cursor];
    }
    return tmp;
  }

  private void backup() {
    lookahead = source[--cursor];
  }

  private void backup(int amount) {
    cursor -= amount;
    lookahead = source[cursor];
  }

  private ParserError lexError(String msg, Object... args) {
    var length = lookahead == EOF ? 0 : 1;
    var index = lookahead == EOF ? cursor - 1 : cursor;
    return new ParserError(ErrorMessages.create(msg, args), new Span(index, length));
  }

  private ParserError lexError(String msg, int charIndex, int length) {
    return new ParserError(msg, new Span(charIndex, length));
  }

  private ParserError lexError(String msg, Span span) {
    return new ParserError(msg, span);
  }

  private ParserError unexpectedChar(char got, String didYouMean) {
    return lexError("unexpectedCharacter", got, didYouMean);
  }

  private ParserError unexpectedChar(String got, String didYouMean) {
    return lexError("unexpectedCharacter", got, didYouMean);
  }

  private ParserError unexpectedEndOfFile() {
    return lexError(ErrorMessages.create("unexpectedEndOfFile"), cursor, 0);
  }

  @TruffleBoundary
  public static boolean isRegularIdentifier(String identifier) {
    if (identifier.isEmpty()) return false;

    if (isKeyword(identifier)) return false;

    var firstCp = identifier.codePointAt(0);
    return (firstCp == '$' || firstCp == '_' || Character.isUnicodeIdentifierStart(firstCp))
        && identifier
            .codePoints()
            .skip(1)
            .allMatch(cp -> cp == '$' || Character.isUnicodeIdentifierPart(cp));
  }

  @TruffleBoundary
  public static String maybeQuoteIdentifier(String identifier) {
    return isRegularIdentifier(identifier) ? identifier : "`" + identifier + "`";
  }

  @SuppressWarnings("SuspiciousArrayMethodCall")
  private static boolean isKeyword(String text) {
    var index = Arrays.binarySearch(KEYWORDS, text);
    return index >= 0;
  }

  @SuppressWarnings("SuspiciousArrayMethodCall")
  private static Token getKeywordOrIdent(String text) {
    var index = Arrays.binarySearch(KEYWORDS, text);
    if (index < 0) return Token.IDENT;
    return KEYWORDS[index].token;
  }

  private static final KeywordEntry[] KEYWORDS = new KeywordEntry[43];

  private record KeywordEntry(String name, Token token) implements Comparable<String> {
    @Override
    public int compareTo(String o) {
      return name.compareTo(o);
    }
  }

  static {
    KEYWORDS[0] = new KeywordEntry("_", Token.UNDERSCORE);
    KEYWORDS[1] = new KeywordEntry("abstract", Token.ABSTRACT);
    KEYWORDS[2] = new KeywordEntry("amends", Token.AMENDS);
    KEYWORDS[3] = new KeywordEntry("as", Token.AS);
    KEYWORDS[4] = new KeywordEntry("case", Token.CASE);
    KEYWORDS[5] = new KeywordEntry("class", Token.CLASS);
    KEYWORDS[6] = new KeywordEntry("const", Token.CONST);
    KEYWORDS[7] = new KeywordEntry("delete", Token.DELETE);
    KEYWORDS[8] = new KeywordEntry("else", Token.ELSE);
    KEYWORDS[9] = new KeywordEntry("extends", Token.EXTENDS);
    KEYWORDS[10] = new KeywordEntry("external", Token.EXTERNAL);
    KEYWORDS[11] = new KeywordEntry("false", Token.FALSE);
    KEYWORDS[12] = new KeywordEntry("fixed", Token.FIXED);
    KEYWORDS[13] = new KeywordEntry("for", Token.FOR);
    KEYWORDS[14] = new KeywordEntry("function", Token.FUNCTION);
    KEYWORDS[15] = new KeywordEntry("hidden", Token.HIDDEN);
    KEYWORDS[16] = new KeywordEntry("if", Token.IF);
    KEYWORDS[17] = new KeywordEntry("import", Token.IMPORT);
    KEYWORDS[18] = new KeywordEntry("in", Token.IN);
    KEYWORDS[19] = new KeywordEntry("is", Token.IS);
    KEYWORDS[20] = new KeywordEntry("let", Token.LET);
    KEYWORDS[21] = new KeywordEntry("local", Token.LOCAL);
    KEYWORDS[22] = new KeywordEntry("module", Token.MODULE);
    KEYWORDS[23] = new KeywordEntry("new", Token.NEW);
    KEYWORDS[24] = new KeywordEntry("nothing", Token.NOTHING);
    KEYWORDS[25] = new KeywordEntry("null", Token.NULL);
    KEYWORDS[26] = new KeywordEntry("open", Token.OPEN);
    KEYWORDS[27] = new KeywordEntry("out", Token.OUT);
    KEYWORDS[28] = new KeywordEntry("outer", Token.OUTER);
    KEYWORDS[29] = new KeywordEntry("override", Token.OVERRIDE);
    KEYWORDS[30] = new KeywordEntry("protected", Token.PROTECTED);
    KEYWORDS[31] = new KeywordEntry("read", Token.READ);
    KEYWORDS[32] = new KeywordEntry("record", Token.RECORD);
    KEYWORDS[33] = new KeywordEntry("super", Token.SUPER);
    KEYWORDS[34] = new KeywordEntry("switch", Token.SWITCH);
    KEYWORDS[35] = new KeywordEntry("this", Token.THIS);
    KEYWORDS[36] = new KeywordEntry("throw", Token.THROW);
    KEYWORDS[37] = new KeywordEntry("trace", Token.TRACE);
    KEYWORDS[38] = new KeywordEntry("true", Token.TRUE);
    KEYWORDS[39] = new KeywordEntry("typealias", Token.TYPE_ALIAS);
    KEYWORDS[40] = new KeywordEntry("unknown", Token.UNKNOWN);
    KEYWORDS[41] = new KeywordEntry("vararg", Token.VARARG);
    KEYWORDS[42] = new KeywordEntry("when", Token.WHEN);
  }

  private static class InterpolationScope {
    final int quotes;
    final int pounds;
    int parens = 0;

    protected InterpolationScope(int quotes, int pounds) {
      this.quotes = quotes;
      this.pounds = pounds;
    }
  }

  private enum State {
    DEFAULT,
    STRING
  }
}
