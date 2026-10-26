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

import java.util.Arrays;
import java.util.BitSet;

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
final class TypeArgumentCandidates {
  // marks a `(`, `[`, `{` or string interpolation on the stack; `<` is stored as its char index
  private static final int BRACKET = -1;

  private final BitSet candidates = new BitSet();
  private int[] stack = new int[16];
  private int size = 0;
  // char index of the `<` matching the most recent `>`, if we haven't seen the next token yet
  private int pending = -1;

  static BitSet scan(Lexer lexer) {
    var scanner = new TypeArgumentCandidates();
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
    return scanner.candidates;
  }

  BitSet candidates() {
    return candidates;
  }

  /** Feeds the next token. Call in source order, for every token including affixes. */
  void accept(Token token, int charIndex) {
    if (token.isAffix()) return;
    if (pending >= 0) {
      if (token == Token.LPAREN) {
        candidates.set(pending);
      }
      pending = -1;
    }
    switch (token) {
      case LT -> push(charIndex);
      case GT -> {
        // a `>` without a matching `<` at the same level is a comparison operator
        if (size > 0 && stack[size - 1] != BRACKET) {
          size--;
          pending = stack[size];
        }
      }
      case LPAREN, LBRACK, LBRACE, INTERPOLATION_START -> push(BRACKET);
      case RPAREN, RBRACK, RBRACE -> {
        // discard unmatched `<` (comparison operators) down to the enclosing bracket
        while (size > 0) {
          size--;
          if (stack[size] == BRACKET) break;
        }
      }
      default -> {}
    }
  }

  private void push(int value) {
    if (size == stack.length) {
      stack = Arrays.copyOf(stack, size * 2);
    }
    stack[size++] = value;
  }
}
