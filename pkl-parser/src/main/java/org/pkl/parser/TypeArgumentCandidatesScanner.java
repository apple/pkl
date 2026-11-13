/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser;

import org.pkl.parser.util.IntArray;
import org.pkl.parser.util.SortedIntArray;

/**
 * Finds all {@code <} tokens might be the start of a method call's type argument list, e.g. the
 * {@code <Int>} in {@code foo<Int>(5)}.
 *
 * <p>A candidate must have balanced {@code <} and {@code >} pairs and followed by {@code (}.
 *
 * <p>Candidates are not <i>necessarily</i> starts of type argument lists. For example, {@code
 * foo<5>()} is not one because {@code 5} is not a type.
 *
 * <p>However, this class doesn't know what a type is; this is meant to be a quick scan of the whole
 * file to help minimize speculative parsing in the underlying parser.
 */
final class TypeArgumentCandidatesScanner {
  // marks a `(`, `[`, `{` or string interpolation on the stack; `<` is stored as its char index
  private static final int BRACKET = -100;

  // char index of every candidate, in the order they were discovered
  private final IntArray found = new IntArray();
  // `<` that are still open, and the brackets that enclose them
  private final IntArray pending = new IntArray();
  // char index of the `<` matching the most recent `>`, if we haven't seen the next token yet
  private int currentPending = -1;

  static SortedIntArray scan(Lexer lexer) {
    var scanner = new TypeArgumentCandidatesScanner();
    try {
      while (true) {
        var token = lexer.next();
        if (token == Token.EOF) break;
        scanner.accept(token, lexer.getStartCursor());
      }
    } catch (ParserError ignored) {
      // Lexing error. The parser will report it when it gets there; `<` that are still open at
      // this point are simply not candidates.
    }
    return scanner.candidates();
  }

  static SortedIntArray scanFromPending(Lexer lexer) {
    var scanner = new TypeArgumentCandidatesScanner();
    scanner.pending.push(lexer.getStartCursor());
    try {
      while (true) {
        var token = lexer.next();
        if (token == Token.EOF) break;
        scanner.accept(token, lexer.getStartCursor());
      }
    } catch (ParserError ignored) {
      // Lexing error. The parser will report it when it gets there; `<` that are still open at
      // this point are simply not candidates.
    }
    return scanner.candidates();
  }

  /**
   * The indices of the {@code <} tokens that might be type arg calls.
   *
   * <p>Must only call this after feeding all tokens.
   */
  SortedIntArray candidates() {
    return new SortedIntArray(found);
  }

  /** Feeds the next token. Call in source order, for every token including affixes. */
  void accept(Token token, int charIndex) {
    if (token.isAffix()) return;
    if (currentPending >= 0) {
      if (token == Token.LPAREN) {
        found.push(currentPending);
      }
      currentPending = -1;
    }
    switch (token) {
      case LT -> pending.push(charIndex);
      case GT -> {
        // a `>` without a matching `<` at the same level is a comparison operator
        if (pending.isNotEmpty() && pending.get(pending.size() - 1) != BRACKET) {
          currentPending = pending.pop();
        }
      }
      case LPAREN, LBRACK, LBRACE, INTERPOLATION_START -> pending.push(BRACKET);
      case RPAREN, RBRACK, RBRACE -> {
        // discard unmatched `<` (comparison operators) down to the enclosing bracket
        while (true) {
          if (pending.isEmpty() || pending.pop() == BRACKET) {
            break;
          }
        }
      }
      default -> {}
    }
  }

  public void addPending(int charIndex) {
    pending.push(charIndex);
  }
}
