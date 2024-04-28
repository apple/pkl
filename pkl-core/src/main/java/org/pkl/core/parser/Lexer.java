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
package org.pkl.core.parser;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.*;
import org.pkl.core.parser.antlr.PklLexer;
import org.pkl.core.util.Nullable;

public final class Lexer {
  public static final Set<Integer> KEYWORD_TYPES;
  public static final Set<String> KEYWORD_NAMES;

  static {
    var keywordTypes = new HashSet<Integer>();
    var keywordNames = new HashSet<String>();
    var vocabulary = PklLexer.VOCABULARY;

    for (var i = 0; i <= vocabulary.getMaxTokenType(); i++) {
      var literal = vocabulary.getLiteralName(i);
      if (literal == null) continue;
      // remove leading and trailing quotes
      literal = literal.substring(1, literal.length() - 1);
      if (Character.isLetter(literal.charAt(0)) || literal.equals("_")) {
        keywordTypes.add(i);
        keywordNames.add(literal);
      }
    }

    KEYWORD_TYPES = Collections.unmodifiableSet(keywordTypes);
    KEYWORD_NAMES = Collections.unmodifiableSet(keywordNames);
  }

  @TruffleBoundary
  public static PklLexer createLexer(CharStream source) {
    var lexer = new PklLexer(source);
    lexer.removeErrorListeners();
    lexer.addErrorListener(
        new ANTLRErrorListener<>() {
          @Override
          public <T extends Integer> void syntaxError(
              Recognizer<T, ?> recognizer,
              T offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            var lexer = ((org.antlr.v4.runtime.Lexer) recognizer);
            throw new LexParseException.LexError(
                msg,
                line,
                charPositionInLine + 1,
                lexer._input.index() - lexer._tokenStartCharIndex);
          }
        });
    return lexer;
  }

  @TruffleBoundary
  public static boolean isKeyword(@Nullable Token token) {
    return token != null && KEYWORD_TYPES.contains(token.getType());
  }

  @TruffleBoundary
  public static boolean isRegularIdentifier(String identifier) {
    if (identifier.isEmpty()) return false;

    if (KEYWORD_NAMES.contains(identifier)) return false;

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
}
