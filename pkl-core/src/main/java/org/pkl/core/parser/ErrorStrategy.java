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
package org.pkl.core.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.pkl.core.parser.antlr.PklParser;

final class ErrorStrategy extends DefaultErrorStrategy {
  @Override
  protected void reportNoViableAlternative(Parser parser, NoViableAltException e) {
    var builder = new StringBuilder();
    var offendingToken = e.getOffendingToken();

    if (Lexer.isKeyword(offendingToken)) {
      appendKeywordNotAllowedMessage(builder, e.getOffendingToken(), e.getExpectedTokens());
    } else {
      builder.append("No viable alternative at input ");
      var tokens = parser.getInputStream();
      if (e.getStartToken().getType() == Token.EOF) {
        builder.append("<EOF>");
      } else {
        builder.append(escapeWSAndQuote(tokens.getText(e.getStartToken(), offendingToken)));
      }
    }

    parser.notifyErrorListeners(offendingToken, builder.toString(), e);
  }

  @Override
  protected void reportInputMismatch(Parser parser, InputMismatchException e) {
    var builder = new StringBuilder();
    var offendingToken = e.getOffendingToken();

    if (Lexer.isKeyword(offendingToken)) {
      appendKeywordNotAllowedMessage(builder, e.getOffendingToken(), e.getExpectedTokens());
    } else {
      // improve formatting compared to DefaultErrorStrategy
      builder
          .append("Mismatched input: ")
          .append(getTokenErrorDisplay(offendingToken))
          .append(". ");
      appendExpectedTokensMessage(builder, parser);
    }

    parser.notifyErrorListeners(offendingToken, builder.toString(), e);
  }

  // improve formatting compared to DefaultErrorStrategy
  @Override
  protected void reportUnwantedToken(Parser parser) {
    if (inErrorRecoveryMode(parser)) return;

    beginErrorCondition(parser);
    var builder = new StringBuilder();
    var currentToken = parser.getCurrentToken();

    if (Lexer.isKeyword(currentToken)) {
      appendKeywordNotAllowedMessage(builder, currentToken, parser.getExpectedTokens());
    } else {
      builder.append("Extraneous input: ").append(getTokenErrorDisplay(currentToken)).append(". ");
      appendExpectedTokensMessage(builder, parser);
    }

    parser.notifyErrorListeners(currentToken, builder.toString(), null);
  }

  // improve formatting compared to DefaultErrorStrategy
  protected void reportMissingToken(Parser parser) {
    if (inErrorRecoveryMode(parser)) return;

    beginErrorCondition(parser);
    var builder = new StringBuilder();
    var currentToken = parser.getCurrentToken();

    var expecting = getExpectedTokens(parser);
    builder
        .append("Missing ")
        .append(expecting.toString(parser.getVocabulary()))
        .append(" at ")
        .append(getTokenErrorDisplay(currentToken))
        .append(". ");

    parser.notifyErrorListeners(currentToken, builder.toString(), null);
  }

  private void appendExpectedTokensMessage(StringBuilder builder, Parser parser) {
    var expectedTokens = parser.getExpectedTokens();
    var size = expectedTokens.size();
    if (size == 0) return;

    builder.append(size == 1 ? "Expected: " : "Expected one of: ");
    var msg = expectedTokens.toString(parser.getVocabulary());
    if (msg.startsWith("{")) msg = msg.substring(1);
    if (msg.endsWith("}")) msg = msg.substring(0, msg.length() - 1);
    builder.append(msg);
  }

  private void appendKeywordNotAllowedMessage(
      StringBuilder builder, Token offendingToken, IntervalSet expectedTokens) {
    builder.append("Keyword `").append(offendingToken.getText()).append("` is not allowed here.");
    if (expectedTokens.contains(PklParser.Identifier)) {
      builder.append(" (If you must use this name as identifier, enclose it in backticks.)");
    }
  }
}
