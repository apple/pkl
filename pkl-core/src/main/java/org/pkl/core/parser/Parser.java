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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.pkl.core.parser.LexParseException.IncompleteInput;
import org.pkl.core.parser.antlr.PklLexer;
import org.pkl.core.parser.antlr.PklParser;
import org.pkl.core.parser.antlr.PklParser.*;
import org.pkl.core.util.Nullable;

public final class Parser {
  @TruffleBoundary
  public PklParser createParser(
      TokenStream stream, @Nullable List<LexParseException> errorCollector) {
    var parser = new PklParser(stream);
    parser.setErrorHandler(new ErrorStrategy());
    registerErrorListener(parser, errorCollector);
    return parser;
  }

  @TruffleBoundary
  public ModuleContext parseModule(CharStream source) throws LexParseException {
    return parseProduction(source, PklParser::module);
  }

  @TruffleBoundary
  public ModuleContext parseModule(String source) throws LexParseException {
    return parseModule(toCharStream(source));
  }

  @TruffleBoundary
  public ReplInputContext parseReplInput(CharStream source) throws LexParseException {
    var ctx = parseProduction(source, PklParser::replInput);
    checkIsCompleteInput(ctx);
    return ctx;
  }

  @TruffleBoundary
  public ReplInputContext parseReplInput(String source) throws LexParseException {
    return parseReplInput(toCharStream(source));
  }

  @TruffleBoundary
  public ExprInputContext parseExpressionInput(CharStream source) throws LexParseException {
    var ctx = parseProduction(source, PklParser::exprInput);
    checkIsCompleteInput(ctx);
    return ctx;
  }

  /**
   * Two-step parse as recommended in chapter "Maximizing Parser Speed" of "The Definitive ANTLR 4
   * Reference, 2nd Ed".
   */
  @TruffleBoundary
  public <T extends ParserRuleContext> T parseProduction(
      CharStream source, Function<PklParser, T> production) throws LexParseException {
    var lexer = Lexer.createLexer(source);
    var errorCollector = new ArrayList<LexParseException>();
    var parser = createParser(new CommonTokenStream(lexer), errorCollector);
    // TODO: investigate why SLL is often not enough to parse Pkl code
    parser.getInterpreter().setPredictionMode(PredictionMode.SLL);

    var result = production.apply(parser);

    // TODO: only necessary to retry for parse (vs. lex) errors?
    if (!errorCollector.isEmpty()) {
      errorCollector.clear();
      parser.reset();
      parser.getInterpreter().setPredictionMode(PredictionMode.LL);
      result = production.apply(parser);
    }

    var mostRelevant =
        errorCollector.stream().max(Comparator.comparingInt(LexParseException::getRelevance));
    if (mostRelevant.isPresent()) {
      throw mostRelevant.get().withPartialParseResult(result);
    }

    return result;
  }

  @TruffleBoundary
  public ExprInputContext parseExpressionInput(String source) throws LexParseException {
    return parseExpressionInput(toCharStream(source));
  }

  @SuppressWarnings("deprecation")
  private CharStream toCharStream(String source) {
    // `ANTLRInputStream` has been deprecated and should be replaced with `CharStreams.ofString()`.
    // It seems that the bugs we formerly encountered with `CharStreams.ofString()` are fixed in
    // 4.7.2.
    // However, switching to `CharStreams.ofString()` means that ANTLR's column numbers are measured
    // in number of code points,
    // which makes them incompatible with Truffle's `SourceSection` (which uses number of code
    // units).
    return new ANTLRInputStream(source);
  }

  // To improve error reporting, missing closing delimiters
  // are tolerated by the grammar and only caught in AstBuilder.
  // This method compensates by flagging a missing closing delimiter.
  private void checkIsCompleteInput(ParserRuleContext ctx) {
    if (ctx.getChildCount() == 1) return; // EOF

    var curr = ctx.getChild(ctx.getChildCount() - 2); // last child before EOF
    while (curr.getChildCount() > 0) {
      if (curr instanceof ClassBodyContext classBody) {
        if (classBody.err == null) throw incompleteInput(curr, "}");
        else return;
      }
      if (curr instanceof ParameterListContext parameterList) {
        if (parameterList.err == null) throw incompleteInput(curr, ")");
        else return;
      }
      if (curr instanceof ArgumentListContext argumentList) {
        if (argumentList.err == null) throw incompleteInput(curr, ")");
        else return;
      }
      if (curr instanceof TypeParameterListContext typeParameterList) {
        if (typeParameterList.err == null) throw incompleteInput(curr, ">");
        else return;
      }
      if (curr instanceof TypeArgumentListContext typeArgumentList) {
        if (typeArgumentList.err == null) throw incompleteInput(curr, ">");
        else return;
      }
      if (curr instanceof ParenthesizedTypeContext parenthesizedType) {
        if (parenthesizedType.err == null) throw incompleteInput(curr, ")");
        else return;
      }
      if (curr instanceof ConstrainedTypeContext constrainedType) {
        if (constrainedType.err == null) throw incompleteInput(curr, ")");
        else return;
      }
      if (curr instanceof ParenthesizedExprContext parenthesizedExpr) {
        if (parenthesizedExpr.err == null) throw incompleteInput(curr, ")");
        else return;
      }
      if (curr instanceof SuperSubscriptExprContext superSubscriptExpr) {
        if (superSubscriptExpr.err == null) throw incompleteInput(curr, "]");
        else return;
      }
      if (curr instanceof SubscriptExprContext subscriptExpr) {
        if (subscriptExpr.err == null) throw incompleteInput(curr, "]");
        else return;
      }
      if (curr instanceof ObjectBodyContext objectBody) {
        if (objectBody.err == null) throw incompleteInput(curr, "}");
        else return;
      }
      curr = curr.getChild(curr.getChildCount() - 1);
    }
  }

  private void registerErrorListener(
      PklParser parser, @Nullable List<LexParseException> errorCollector) {
    parser.removeErrorListeners();
    parser.addErrorListener(
        new BaseErrorListener() {
          @Override
          public <T extends Token> void syntaxError(
              Recognizer<T, ?> recognizer,
              T offendingToken,
              int line,
              int charPositionInLine,
              String msg,
              @Nullable RecognitionException e) {
            assert charPositionInLine == offendingToken.getCharPositionInLine();
            var length = offendingToken.getStopIndex() - offendingToken.getStartIndex() + 1;

            LexParseException exception;
            // For incomplete input similar to `foo { bar {`, e can (at least) be null,
            // NoViableAltException, or InputMismatchException. Therefore, just check for EOF.
            if (offendingToken.getType() == PklLexer.EOF) {
              exception =
                  new LexParseException.IncompleteInput(msg, line, charPositionInLine + 1, length);
            } else {
              exception =
                  new LexParseException.ParseError(
                      msg, line, charPositionInLine + 1, length, getAstDepth(e));
            }

            if (errorCollector != null) {
              errorCollector.add(exception);
            } else {
              throw exception;
            }
          }
        });
  }

  private LexParseException incompleteInput(ParseTree tree, String missingDelimiter) {
    var ctx = (ParserRuleContext) tree;
    return new IncompleteInput(
        "Missing closing delimiter `" + missingDelimiter + "`.",
        ctx.stop.getLine(),
        ctx.stop.getCharPositionInLine() + 1,
        ctx.stop.getStopIndex() - ctx.stop.getStartIndex() + 1);
  }

  private static int getAstDepth(@Nullable RecognitionException e) {
    if (e == null) return 0;

    var depth = 0;
    for (var context = e.getContext(); context != null; context = context.getParent()) {
      depth += 1;
    }

    return depth;
  }
}
