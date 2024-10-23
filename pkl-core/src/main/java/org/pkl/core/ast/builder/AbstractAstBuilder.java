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
package org.pkl.core.ast.builder;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.pkl.core.parser.antlr.PklLexer;
import org.pkl.core.parser.antlr.PklParser.ModifierContext;
import org.pkl.core.parser.antlr.PklParserBaseVisitor;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.Nullable;

public abstract class AbstractAstBuilder<T> extends PklParserBaseVisitor<T> {

  protected final Source source;

  protected AbstractAstBuilder(Source source) {
    this.source = source;
  }

  protected abstract VmExceptionBuilder exceptionBuilder();

  protected String doVisitSingleLineConstantStringPart(List<Token> ts) {
    if (ts.isEmpty()) return "";

    var builder = new StringBuilder();
    for (var token : ts) {
      switch (token.getType()) {
        case PklLexer.SLCharacters -> builder.append(token.getText());
        case PklLexer.SLCharacterEscape -> builder.append(parseCharacterEscapeSequence(token));
        case PklLexer.SLUnicodeEscape -> builder.appendCodePoint(parseUnicodeEscapeSequence(token));
        default -> throw exceptionBuilder().unreachableCode().build();
      }
    }

    return builder.toString();
  }

  protected int parseUnicodeEscapeSequence(Token token) {
    var text = token.getText();
    var lastIndex = text.length() - 1;

    if (text.charAt(lastIndex) != '}') {
      throw exceptionBuilder()
          .evalError("unterminatedUnicodeEscapeSequence", token.getText())
          .withSourceSection(createSourceSection(token))
          .build();
    }

    var startIndex = text.indexOf('{', 2);
    assert startIndex != -1; // guaranteed by lexer

    try {
      return Integer.parseInt(text.substring(startIndex + 1, lastIndex), 16);
    } catch (NumberFormatException e) {
      throw exceptionBuilder()
          .evalError("invalidUnicodeEscapeSequence", token.getText(), text.substring(0, startIndex))
          .withSourceSection(createSourceSection(token))
          .build();
    }
  }

  protected String parseCharacterEscapeSequence(Token token) {
    var text = token.getText();
    var lastChar = text.charAt(text.length() - 1);

    return switch (lastChar) {
      case 'n' -> "\n";
      case 'r' -> "\r";
      case 't' -> "\t";
      case '"' -> "\"";
      case '\\' -> "\\";
      default ->
          throw exceptionBuilder()
              .evalError(
                  "invalidCharacterEscapeSequence", text, text.substring(0, text.length() - 1))
              .withSourceSection(createSourceSection(token))
              .build();
    };
  }

  protected final SourceSection createSourceSection(ParserRuleContext ctx) {
    return createSourceSection(ctx.getStart(), ctx.getStop());
  }

  protected final SourceSection createSourceSection(TerminalNode node) {
    return createSourceSection(node.getSymbol());
  }

  protected final @Nullable SourceSection createSourceSection(@Nullable Token token) {
    return token != null ? createSourceSection(token, token) : null;
  }

  protected final SourceSection createSourceSection(Token start, Token stop) {
    return source.createSection(
        start.getStartIndex(), stop.getStopIndex() - start.getStartIndex() + 1);
  }

  protected final SourceSection createSourceSection(
      List<? extends ModifierContext> modifierCtxs, int symbol) {

    var modifierCtx =
        modifierCtxs.stream().filter(ctx -> ctx.t.getType() == symbol).findFirst().orElseThrow();

    return createSourceSection(modifierCtx);
  }

  protected static SourceSection createSourceSection(Source source, ParserRuleContext ctx) {
    var start = ctx.start.getStartIndex();
    var stop = ctx.stop.getStopIndex();
    return source.createSection(start, stop - start + 1);
  }

  protected static @Nullable SourceSection createSourceSection(
      Source source, @Nullable Token token) {
    if (token == null) return null;

    var start = token.getStartIndex();
    var stop = token.getStopIndex();
    return source.createSection(start, stop - start + 1);
  }
}
