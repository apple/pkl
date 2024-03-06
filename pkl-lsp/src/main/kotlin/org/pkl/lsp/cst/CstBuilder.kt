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
package org.pkl.lsp.cst

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.pkl.core.parser.antlr.PklLexer
import org.pkl.core.parser.antlr.PklParser
import org.pkl.core.parser.antlr.PklParser.UnaryMinusExprContext
import org.pkl.core.parser.antlr.PklParserVisitor

class CstBuilder : PklParserVisitor<Any> {
  
  private val errors = mutableListOf<ParseError>()
  
  override fun visitUnknownType(ctx: PklParser.UnknownTypeContext): Type {
    return Type.Unknown(toSpan(ctx))
  }

  override fun visitNothingType(ctx: PklParser.NothingTypeContext): Type {
    return Type.Nothing(toSpan(ctx))
  }

  override fun visitModuleType(ctx: PklParser.ModuleTypeContext): Type {
    return Type.Module(toSpan(ctx))
  }

  override fun visitStringConstant(ctx: PklParser.StringConstantContext): Ident {
    val str = doVisitSingleLineConstantStringPart(ctx.ts)
    return Ident(str, toSpan(ctx))
  }

  override fun visitStringLiteralType(ctx: PklParser.StringLiteralTypeContext): Any {
    val ident = visitStringConstant(ctx.stringConstant())
    return Type.StringConstant(ident.value, toSpan(ctx))
  }

  override fun visitParenthesizedType(ctx: PklParser.ParenthesizedTypeContext): Type {
    val type = visitType(ctx.type())
    return Type.Parenthesised(type, toSpan(ctx))
  }

  override fun visitDeclaredType(ctx: PklParser.DeclaredTypeContext): Type {
    val ids = visitQualifiedIdentifier(ctx.qualifiedIdentifier())
    val args = ctx.typeArgumentList().ts.map(this::visitType)
    return Type.Declared(ids, args, toSpan(ctx))
  }

  override fun visitNullableType(ctx: PklParser.NullableTypeContext): Type {
    return Type.Nullable(visitType(ctx.type()), toSpan(ctx))
  }

  override fun visitConstrainedType(ctx: PklParser.ConstrainedTypeContext): Type {
    // TODO: check `,` and `)`
    val type = ctx.type().accept(this) as Type
    val exprs = ctx.es.map(this::visitExpr)
    return Type.Constrained(type, exprs, toSpan(ctx))
  }

  override fun visitDefaultUnionType(ctx: PklParser.DefaultUnionTypeContext): Type {
    // TODO: check if valid
    return Type.DefaultUnion(visitType(ctx.type()), toSpan(ctx))
  }

  override fun visitUnionType(ctx: PklParser.UnionTypeContext): Any {
    // TODO: check if valid
    return Type.Union(visitType(ctx.l), visitType(ctx.r), toSpan(ctx))
  }

  override fun visitFunctionType(ctx: PklParser.FunctionTypeContext): Any {
    return Type.Function(ctx.ps.map(this::visitType), visitType(ctx.r), toSpan(ctx))
  }

  override fun visitType(ctx: PklParser.TypeContext): Type {
    return ctx.accept(this) as Type
  }

  override fun visitQualifiedIdentifier(ctx: PklParser.QualifiedIdentifierContext): List<Ident> {
    return ctx.ts.map(::toIdent)
  }

  override fun visitThisExpr(ctx: PklParser.ThisExprContext): Expr {
    return Expr.This(toSpan(ctx))
  }

  override fun visitOuterExpr(ctx: PklParser.OuterExprContext): Expr {
    return Expr.Outer(toSpan(ctx))
  }

  override fun visitModuleExpr(ctx: PklParser.ModuleExprContext): Expr {
    return Expr.Module(toSpan(ctx))
  }

  override fun visitNullLiteral(ctx: PklParser.NullLiteralContext): Expr {
    return Expr.Null(toSpan(ctx))
  }

  override fun visitTrueLiteral(ctx: PklParser.TrueLiteralContext): Expr {
    return Expr.BooleanLiteral(true, toSpan(ctx))
  }

  override fun visitFalseLiteral(ctx: PklParser.FalseLiteralContext): Expr {
    return Expr.BooleanLiteral(false, toSpan(ctx))
  }

  override fun visitIntLiteral(ctx: PklParser.IntLiteralContext): Expr {
    var text = ctx.IntLiteral().text
    val span = toSpan(ctx)

    var radix = 10
    if (text.startsWith("0x") || text.startsWith("0b") || text.startsWith("0o")) {
      val type = text[1]
      radix = when (type) {
          'x' -> 16
          'b' -> 2
          else -> 8
      }

      text = text.substring(2)
      if (text.startsWith("_")) {
        errors += ParseError("invalidEscapeInNumber", span)
      }
    }

    if (ctx.getParent() is UnaryMinusExprContext) {
      text = "-$text"
    }
    text = text.replace("_", "")
    val num = try {
      text.toLong(radix)
    } catch (_: NumberFormatException) {
      // keep going to find more errors
      errors += ParseError("intTooLarge", span)
      1
    }
    return Expr.IntLiteral(num, span)
  }

  override fun visitFloatLiteral(ctx: PklParser.FloatLiteralContext): Expr {
    var text = ctx.FloatLiteral().text
    val span = toSpan(ctx)

    if (ctx.getParent() is UnaryMinusExprContext) {
      text = "-$text"
    }

    val dotIdx = text.indexOf('.')
    if (dotIdx != -1 && text[dotIdx + 1] == '_') {
      errors += ParseError("invalidEscapeInNumber", span)
    }
    var exponentIdx = text.indexOf('e')
    if (exponentIdx == -1) {
      exponentIdx = text.indexOf('E')
    }
    if (exponentIdx != -1 && text[exponentIdx + 1] == '_') {
      errors += ParseError("invalidEscapeInNumber", span)
    }

    text = text.replace("_", "")

    val num = try {
      text.toDouble()
    } catch (_: NumberFormatException) {
      // keep going to find more errors
      errors += ParseError("floatTooLarge", span)
      1.0
    }
    return Expr.FloatLiteral(num, span)
  }

  override fun visitSingleLineStringLiteral(ctx: PklParser.SingleLineStringLiteralContext): Expr {
    val singlePart = ctx.singleLineStringPart()
    if (singlePart.isEmpty()) return Expr.ConstantString("", toSpan(ctx))
    
    
  }

  override fun visitSingleLineStringPart(ctx: PklParser.SingleLineStringPartContext): String {
    TODO("Not yet implemented")
  }
  
  override fun visitExpr(ctx: PklParser.ExprContext): Expr {
    return ctx.accept(this) as Expr
  }

  // Helpers
  
  fun errors(): List<ParseError> = errors
  
  private fun doVisitSingleLineConstantStringPart(ts: List<Token>): String {
    if (ts.isEmpty()) return ""

    val builder = StringBuilder()
    for (token in ts) {
      when (token.type) {
        PklLexer.SLCharacters -> builder.append(token.text)
        PklLexer.SLCharacterEscape -> builder.append(doParseCharacterEscapeSequence(token))
        PklLexer.SLUnicodeEscape -> builder.appendCodePoint(doParseUnicodeEscapeSequence(token))
        else -> throw RuntimeException("unreacheableCode")
      }
    }

    return builder.toString()
  }

  private fun doParseUnicodeEscapeSequence(token: Token): Int {
    val text = token.text
    val lastIndex = text.length - 1

    if (text[lastIndex] != '}') {
      errors.add(ParseError("unterminatedUnicodeEscapeSequence", toSpan(token)))
    }

    val startIndex = text.indexOf('{', 2)
    assert(startIndex != -1) // guaranteed by lexer
    try {
      return text.substring(startIndex + 1, lastIndex).toInt(16)
    } catch (e: NumberFormatException) {
      errors.add(ParseError("invalidUnicodeEscapeSequence", toSpan(token)))
      return -1
    }
  }

  private fun doParseCharacterEscapeSequence(token: Token): String {
    val text = token.text
    val lastChar = text[text.length - 1]

    when (lastChar) {
      'n' -> return "\n"
      'r' -> return "\r"
      't' -> return "\t"
      '"' -> return "\""
      '\\' -> return "\\"
      else -> {
        errors.add(ParseError("invalidCharacterEscapeSequence", toSpan(token)))
        return ""
      }
    }
  }

  companion object {
    private fun toIdent(token: Token): Ident = Ident(token.text, toSpan(token))
    private fun toSpan(ctx: ParserRuleContext): Span {
      val begin = ctx.start
      val end = ctx.stop
      return Span(begin.line, begin.line + begin.stopIndex, end.line, end.line + end.stopIndex)
    }

    private fun toSpan(token: Token): Span =
      Span(token.line, token.line + token.stopIndex, token.line, token.line + token.stopIndex)
  }
}

data class ParseError(val errorType: String, val span: Span)

/*
pkl eval \
     -p input=rio.yml \
     -o rio.pkl \
     package://artifacts.apple.com/pkl/pkl/rio@1.3.1#/convert.pkl
 */
