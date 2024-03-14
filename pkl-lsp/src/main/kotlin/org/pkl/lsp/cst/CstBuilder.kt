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
import org.antlr.v4.runtime.tree.TerminalNode
import org.pkl.core.ast.builder.AstBuilder
import org.pkl.core.parser.antlr.PklLexer
import org.pkl.core.parser.antlr.PklParser
import org.pkl.core.parser.antlr.PklParserBaseVisitor

class CstBuilder : PklParserBaseVisitor<Any>() {

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
    val args = ctx.typeArgumentList().ts.map(::visitType)
    return Type.Declared(ids, args, toSpan(ctx))
  }

  override fun visitNullableType(ctx: PklParser.NullableTypeContext): Type {
    return Type.Nullable(visitType(ctx.type()), toSpan(ctx))
  }

  override fun visitConstrainedType(ctx: PklParser.ConstrainedTypeContext): Type {
    // TODO: check `,` and `)`
    val type = ctx.type().accept(this) as Type
    val exprs = ctx.es.map(::visitExpr)
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
    return Type.Function(ctx.ps.map(::visitType), visitType(ctx.r), toSpan(ctx))
  }

  override fun visitType(ctx: PklParser.TypeContext): Type {
    return ctx.accept(this) as Type
  }

  override fun visitTypeAnnotation(ctx: PklParser.TypeAnnotationContext?): Type? {
    if (ctx == null) return null
    return visitType(ctx.type())
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
      radix =
        when (type) {
          'x' -> 16
          'b' -> 2
          else -> 8
        }

      text = text.substring(2)
      if (text.startsWith("_")) {
        errors += ParseError("invalidEscapeInNumber", span)
      }
    }

    if (ctx.getParent() is PklParser.UnaryMinusExprContext) {
      text = "-$text"
    }
    text = text.replace("_", "")
    val num =
      try {
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

    if (ctx.getParent() is PklParser.UnaryMinusExprContext) {
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

    val num =
      try {
        text.toDouble()
      } catch (_: NumberFormatException) {
        // keep going to find more errors
        errors += ParseError("floatTooLarge", span)
        1.0
      }
    return Expr.FloatLiteral(num, span)
  }

  override fun visitSingleLineStringLiteral(ctx: PklParser.SingleLineStringLiteralContext): Expr {
    // TODO: check delimiters
    val singlePart = ctx.singleLineStringPart()
    return when (singlePart.size) {
      0 -> Expr.ConstantString("", toSpan(ctx))
      1 -> visitSingleLineStringPart(singlePart.first())
      else -> Expr.InterpolatedString(singlePart.map(::visitSingleLineStringPart), toSpan(ctx))
    }
  }

  override fun visitSingleLineStringPart(ctx: PklParser.SingleLineStringPartContext): Expr {
    if (ctx.e != null) {
      return Expr.InterpolatedString(listOf(visitExpr(ctx.e)), toSpan(ctx))
    }
    return Expr.ConstantString(doVisitSingleLineConstantStringPart(ctx.ts), toSpan(ctx))
  }

  override fun visitMultiLineStringPart(ctx: PklParser.MultiLineStringPartContext?): Any {
    throw RuntimeException("unreacheable code: visitMultiLineStringPart")
  }

  override fun visitMultiLineStringLiteral(ctx: PklParser.MultiLineStringLiteralContext): Expr {
    val multiPart = ctx.multiLineStringPart()

    if (multiPart.isEmpty()) {
      errors += ParseError("stringContentMustBeginOnNewLine", toSpan(ctx))
    }

    val firstPart = multiPart[0]
    if (firstPart.e != null || firstPart.ts[0].type != PklLexer.MLNewline) {
      errors += ParseError("stringContentMustBeginOnNewLine", toSpan(ctx))
    }

    val lastPart = multiPart[multiPart.size - 1]
    val commonIndent: String = getCommonIndent(lastPart, ctx.t2)

    if (multiPart.size == 1) {
      return Expr.ConstantString(
        doVisitMultiLineConstantStringPart(
          firstPart.ts,
          commonIndent,
          isStringStart = true,
          isStringEnd = true
        ),
        toSpan(ctx)
      )
    }

    val multiPartExprs = ArrayList<Expr>(multiPart.size)
    val lastIndex = multiPart.size - 1

    for (i in 0..lastIndex) {
      val part = multiPart[i]
      multiPartExprs +=
        if (part.e != null) {
          Expr.InterpolatedString(listOf(visitExpr(part.e)), toSpan(part))
        } else {
          Expr.ConstantString(
            doVisitMultiLineConstantStringPart(part.ts, commonIndent, i == 0, i == lastIndex),
            toSpan(part)
          )
        }
    }

    return Expr.InterpolatedString(multiPartExprs, toSpan(ctx))
  }

  override fun visitThrowExpr(ctx: PklParser.ThrowExprContext): Expr {
    // TODO: check closing delimiter
    return Expr.Throw(visitExpr(ctx.expr()), toSpan(ctx))
  }

  override fun visitTraceExpr(ctx: PklParser.TraceExprContext): Expr {
    // TODO: check closing delimiter
    return Expr.Trace(visitExpr(ctx.expr()), toSpan(ctx))
  }

  override fun visitExpr(ctx: PklParser.ExprContext): Expr {
    return ctx.accept(this) as Expr
  }

  override fun visitImportExpr(ctx: PklParser.ImportExprContext): Expr {
    // TODO: check closing delimiter
    val isGlob = ctx.t.type == PklLexer.IMPORT_GLOB
    val uri = visitStringConstant(ctx.stringConstant())
    if (isGlob && uri.value.startsWith("...")) {
      errors += ParseError("cannotGlobTripleDots", uri.span)
    }
    return if (isGlob) {
      Expr.ImportGlob(uri, toSpan(ctx))
    } else {
      Expr.Import(uri, toSpan(ctx))
    }
  }

  override fun visitReadExpr(ctx: PklParser.ReadExprContext): Expr {
    // TODO: check closing delimiter
    val exprCtx = ctx.expr()
    val span = toSpan(ctx)

    val token = ctx.t.type
    return when (token) {
      PklLexer.READ -> Expr.Read(visitExpr(exprCtx), span)
      PklLexer.READ_OR_NULL -> Expr.ReadNull(visitExpr(exprCtx), span)
      else -> Expr.ReadGlob(visitExpr(exprCtx), span)
    }
  }

  override fun visitUnqualifiedAccessExpr(ctx: PklParser.UnqualifiedAccessExprContext): Expr {
    val ident = toIdent(ctx.Identifier())

    if (ctx.argumentList() == null) {
      return Expr.UnqualifiedAccess(ident, toSpan(ctx))
    }

    return Expr.UnqualifiedMethodAccess(ident, visitArgumentList(ctx.argumentList()), toSpan(ctx))
  }

  override fun visitArgumentList(ctx: PklParser.ArgumentListContext): List<Expr> {
    // TODO: check closing delimiter and commas
    return ctx.es.map(::visitExpr)
  }

  override fun visitQualifiedAccessExpr(ctx: PklParser.QualifiedAccessExprContext): Expr {
    val expr = visitExpr(ctx.expr())
    val ident = toIdent(ctx.Identifier())
    val isNullable = ctx.t.type == PklLexer.QDOT
    return if (ctx.argumentList() != null) {
      Expr.QualifiedMethodAccess(
        expr,
        ident,
        isNullable,
        visitArgumentList(ctx.argumentList()),
        toSpan(ctx)
      )
    } else {
      Expr.QualifiedAccess(expr, ident, isNullable, toSpan(ctx))
    }
  }

  override fun visitSuperAccessExpr(ctx: PklParser.SuperAccessExprContext): Expr {
    val member = toIdent(ctx.Identifier())
    val argCtx = ctx.argumentList()

    return if (argCtx != null) {
      Expr.SuperMethodAccess(member, visitArgumentList(argCtx), toSpan(ctx))
    } else {
      Expr.SuperAccess(member, toSpan(ctx))
    }
  }

  override fun visitSuperSubscriptExpr(ctx: PklParser.SuperSubscriptExprContext): Expr {
    // TODO: check closing delimiter
    return Expr.SuperSubscript(visitExpr(ctx.e), toSpan(ctx))
  }

  override fun visitSubscriptExpr(ctx: PklParser.SubscriptExprContext): Expr {
    // TODO: check closing delimiter
    return Expr.Subscript(visitExpr(ctx.l), visitExpr(ctx.r), toSpan(ctx))
  }

  override fun visitIfExpr(ctx: PklParser.IfExprContext): Expr {
    // TODO: check closing delimiter
    return Expr.If(visitExpr(ctx.c), visitExpr(ctx.l), visitExpr(ctx.r), toSpan(ctx))
  }

  override fun visitLetExpr(ctx: PklParser.LetExprContext): Expr {
    // TODO: check closing delimiter
    val param = visitParameter(ctx.parameter())
    val binding = visitExpr(ctx.l)
    val expr = visitExpr(ctx.r)
    return Expr.Let(param, binding, expr, toSpan(ctx))
  }

  override fun visitFunctionLiteral(ctx: PklParser.FunctionLiteralContext): Expr {
    val span = toSpan(ctx)
    val params = visitParameterList(ctx.parameterList())
    if (params.size > 5) {
      errors += ParseError("tooManyFunctionParameters", span)
    }
    return Expr.FunctionLiteral(params, visitExpr(ctx.expr()), span)
  }

  override fun visitParenthesizedExpr(ctx: PklParser.ParenthesizedExprContext): Expr {
    // TODO: check closing delimiter
    return Expr.Parenthesised(visitExpr(ctx.expr()), toSpan(ctx))
  }

  override fun visitNewExpr(ctx: PklParser.NewExprContext): Expr {
    val type = ctx.type()?.let(::visitType)
    val body = visitObjectBody(ctx.objectBody())
    return Expr.New(type, body, toSpan(ctx))
  }

  override fun visitAmendExpr(ctx: PklParser.AmendExprContext): Expr {
    val parentExpr = ctx.expr()
    if (
      !(parentExpr is PklParser.NewExprContext ||
        parentExpr is PklParser.AmendExprContext ||
        parentExpr is PklParser.ParenthesizedExprContext)
    ) {
      errors +=
        ParseError("unexpectedCurlyProbablyAmendsExpression", toSpan(ctx.objectBody().start))
    }
    val expr = visitExpr(parentExpr)
    val body = visitObjectBody(ctx.objectBody())
    return Expr.Amends(expr, body, toSpan(ctx))
  }

  override fun visitNonNullExpr(ctx: PklParser.NonNullExprContext): Expr {
    return Expr.NonNull(visitExpr(ctx.expr()), toSpan(ctx))
  }

  override fun visitUnaryMinusExpr(ctx: PklParser.UnaryMinusExprContext): Expr {
    val expr = visitExpr(ctx.expr())
    if (expr is Expr.IntLiteral || expr is Expr.FloatLiteral) {
      // already handled
      return expr
    }
    return Expr.UnaryMinus(expr, toSpan(ctx))
  }

  override fun visitLogicalNotExpr(ctx: PklParser.LogicalNotExprContext): Expr {
    return Expr.LogicalNot(visitExpr(ctx.expr()), toSpan(ctx))
  }

  override fun visitLogicalAndExpr(ctx: PklParser.LogicalAndExprContext): Expr {
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), Operation.AND, toSpan(ctx))
  }

  override fun visitLogicalOrExpr(ctx: PklParser.LogicalOrExprContext): Expr {
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), Operation.OR, toSpan(ctx))
  }

  override fun visitAdditiveExpr(ctx: PklParser.AdditiveExprContext): Expr {
    val type = if (ctx.t.type == PklLexer.PLUS) Operation.PLUS else Operation.MINUS
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), type, toSpan(ctx))
  }

  override fun visitMultiplicativeExpr(ctx: PklParser.MultiplicativeExprContext): Expr {
    val type =
      when (ctx.t.type) {
        PklLexer.STAR -> Operation.MULT
        PklLexer.DIV -> Operation.DIV
        PklLexer.INT_DIV -> Operation.INT_DIV
        PklLexer.MOD -> Operation.MOD
        else -> throw RuntimeException("unreacheable code")
      }
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), type, toSpan(ctx))
  }

  override fun visitComparisonExpr(ctx: PklParser.ComparisonExprContext): Expr {
    val type =
      when (ctx.t.type) {
        PklLexer.GT -> Operation.GT
        PklLexer.GTE -> Operation.GTE
        PklLexer.LT -> Operation.LT
        PklLexer.LTE -> Operation.LTE
        else -> throw RuntimeException("unreacheable code")
      }
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), type, toSpan(ctx))
  }

  override fun visitEqualityExpr(ctx: PklParser.EqualityExprContext): Expr {
    val type =
      when (ctx.t.type) {
        PklLexer.EQUAL -> Operation.EQ_EQ
        PklLexer.NOT_EQUAL -> Operation.NOT_EQ
        else -> throw RuntimeException("unreacheable code")
      }
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), type, toSpan(ctx))
  }

  override fun visitPipeExpr(ctx: PklParser.PipeExprContext): Expr {
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), Operation.PIPE, toSpan(ctx))
  }

  override fun visitNullCoalesceExpr(ctx: PklParser.NullCoalesceExprContext): Expr {
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), Operation.NULL_COALESCE, toSpan(ctx))
  }

  override fun visitExponentiationExpr(ctx: PklParser.ExponentiationExprContext): Expr {
    return Expr.BinaryOp(visitExpr(ctx.l), visitExpr(ctx.r), Operation.POW, toSpan(ctx))
  }

  override fun visitTypeAlias(ctx: PklParser.TypeAliasContext): TypeAlias {
    // TODO: add doc comment and annotations
    val header = ctx.typeAliasHeader()
    val modifiers =
      checkModifiers(
        header.modifier(),
        "invalidModifier",
        ModifierValue.LOCAL,
        ModifierValue.EXTERNAL
      )
    val name = toIdent(header.Identifier())
    val typePars = visitTypeParameterList(header.typeParameterList())
    val type = visitType(ctx.type())
    return TypeAlias(null, listOf(), modifiers, name, typePars, type, toSpan(ctx))
  }

  override fun visitAnnotation(ctx: PklParser.AnnotationContext): Annotation {
    val type = visitType(ctx.type())
    val body = ctx.objectBody()?.let(::visitObjectBody)
    return Annotation(type, body, toSpan(ctx))
  }

  override fun visitImportClause(ctx: PklParser.ImportClauseContext): Import {
    val isGlobImport = ctx.t.type == PklLexer.IMPORT_GLOB
    val importUri = visitStringConstant(ctx.stringConstant())
    if (isGlobImport && importUri.value.startsWith("...")) {
      errors += ParseError("cannotGlobTripleDots", toSpan(ctx.stringConstant()))
    }
    val alias = ctx.Identifier()?.let(::toIdent)
    return Import(importUri, isGlobImport, alias, toSpan(ctx))
  }

  override fun visitClazz(ctx: PklParser.ClazzContext): Clazz {
    // TODO: add doc comment and annotations
    val header = ctx.classHeader()
    val modifiers =
      checkModifiers(
        header.modifier(),
        "invalidModifier",
        ModifierValue.LOCAL,
        ModifierValue.OPEN,
        ModifierValue.ABSTRACT,
        ModifierValue.EXTERNAL
      )
    val name = toIdent(header.Identifier())
    val typePars = visitTypeParameterList(header.typeParameterList())
    val superCLass = header.type()?.let(::visitType)
    val body = visitClassBody(ctx.classBody())
    return Clazz(null, listOf(), modifiers, name, typePars, superCLass, body, toSpan(ctx))
  }

  override fun visitClassBody(ctx: PklParser.ClassBodyContext): List<ClassEntry> {
    // TODO: check delimiter
    val properties = ctx.classProperty().map(::visitClassProperty)
    val methods = ctx.classMethod().map(::visitClassMethod)
    return properties + methods
  }

  override fun visitClassProperty(ctx: PklParser.ClassPropertyContext): ClassEntry {
    // TODO: add doc comment and annotations
    val modifiers =
      checkModifiers(
        ctx.modifier(),
        "invalidModifier",
        ModifierValue.ABSTRACT,
        ModifierValue.LOCAL,
        ModifierValue.HIDDEN,
        ModifierValue.EXTERNAL,
        ModifierValue.FIXED,
        ModifierValue.CONST
      )
    val name = toIdent(ctx.Identifier())
    val typeAnnotation = ctx.typeAnnotation()?.let(::visitTypeAnnotation)
    val expr = ctx.expr()?.let(::visitExpr)
    val body = ctx.objectBody()?.map(::visitObjectBody)
    val span = toSpan(ctx)
    return when {
      expr != null -> ClassPropertyExpr(null, listOf(), modifiers, name, typeAnnotation, expr, span)
      body != null -> ClassPropertyBody(null, listOf(), modifiers, name, typeAnnotation, body, span)
      else -> {
        assert(typeAnnotation != null) // parser makes sure that holds
        ClassProperty(null, listOf(), modifiers, name, typeAnnotation!!, span)
      }
    }
  }

  override fun visitClassMethod(ctx: PklParser.ClassMethodContext): ClassMethod {
    // TODO: add doc comment and annotations
    val header = ctx.methodHeader()
    val modifiers =
      checkModifiers(
        header.modifier(),
        "invalidModifier",
        ModifierValue.ABSTRACT,
        ModifierValue.LOCAL,
        ModifierValue.EXTERNAL,
        ModifierValue.CONST
      )
    val name = toIdent(header.Identifier())
    val typeParams = visitTypeParameterList(header.typeParameterList())
    val params = visitParameterList(header.parameterList())
    val typeAnnotation = header.typeAnnotation()?.let(::visitTypeAnnotation)
    val expr = ctx.expr()?.let(::visitExpr)
    return ClassMethod(
      null,
      listOf(),
      modifiers,
      name,
      typeParams,
      params,
      typeAnnotation,
      expr,
      toSpan(ctx)
    )
  }

  override fun visitModuleDecl(ctx: PklParser.ModuleDeclContext?): ModuleDecl? {
    // TODO: add doc comment and annotations
    if (ctx == null) return null

    val header = ctx.moduleHeader() ?: return null
    val modifiers = header.modifier()?.let {
      checkModifiers(
        it,
        "invalidModifier",
        ModifierValue.ABSTRACT,
        ModifierValue.OPEN
      )
    } ?: listOf()
    val name = header.qualifiedIdentifier()?.let {
      ModuleNameDecl(visitQualifiedIdentifier(it), toSpan(it))
    }
    // cannot be null
    val extendsOrAmends = header.moduleExtendsOrAmendsClause()
    val uri = extendsOrAmends.stringConstant()
    var extends: ExtendsDecl? = null
    var amends: AmendsDecl? = null
    if (extendsOrAmends.t.type == PklLexer.AMENDS) {
      amends = AmendsDecl(visitStringConstant(uri), toSpan(uri))
    } else {
      extends = ExtendsDecl(visitStringConstant(uri), toSpan(uri))
    }
    return ModuleDecl(null, listOf(), modifiers, name, extends, amends)
  }

  override fun visitModule(ctx: PklParser.ModuleContext): PklModule {
    val decl = visitModuleDecl(ctx.moduleDecl())
    val imports = ctx.importClause()?.map(::visitImportClause) ?: listOf()
    val classes = ctx.cs.map(::visitClazz)
    val typeAliases = ctx.ts.map(::visitTypeAlias)
    val properties = ctx.ps.map(::visitClassProperty)
    val methods = ctx.ms.map(::visitClassMethod)
    return PklModule(decl, imports, classes + typeAliases + properties + methods)
  }
  
  override fun visitTypeTestExpr(ctx: PklParser.TypeTestExprContext): Expr {
    val expr = visitExpr(ctx.l)
    val type = visitType(ctx.r)
    val span = toSpan(ctx)
    return if (ctx.t.type == PklLexer.IS) {
      Expr.TypeTest(expr, type, span)
    } else {
      Expr.TypeCast(expr, type, span)
    }
  }

  override fun visitObjectBody(ctx: PklParser.ObjectBodyContext): ObjectBody {
    // TODO: check closing delimiter and commas
    val params = ctx.ps.map(::visitParameter)
    val members = doVisitObjectMemberList(ctx.objectMember())
    return ObjectBody(params, members, toSpan(ctx))
  }

  override fun visitObjectMember(ctx: PklParser.ObjectMemberContext): ObjectMember {
    return ctx.accept(this) as ObjectMember
  }

  override fun visitObjectProperty(ctx: PklParser.ObjectPropertyContext): ObjectMember {
    val span = toSpan(ctx)
    val modifiers =
      checkModifiers(
        ctx.modifier(),
        "invalidModifierForObjectPropertyOrMethod",
        ModifierValue.LOCAL
      )
    val ident = toIdent(ctx.Identifier())
    val typeAnnotation = visitTypeAnnotation(ctx.typeAnnotation())
    return if (ctx.expr() != null) {
      ObjectMember.Property(modifiers, ident, typeAnnotation, visitExpr(ctx.expr()), span)
    } else {
      val bodies = ctx.objectBody().map(::visitObjectBody)
      ObjectMember.PropertyBody(modifiers, ident, bodies, span)
    }
  }

  override fun visitObjectMethod(ctx: PklParser.ObjectMethodContext): ObjectMember {
    val span = toSpan(ctx)
    val method = ctx.methodHeader()
    val modifiers =
      checkModifiers(
        method.modifier(),
        "invalidModifierForObjectPropertyOrMethod",
        ModifierValue.LOCAL
      )
    val ident = toIdent(method.Identifier())
    val typeParams = visitTypeParameterList(method.typeParameterList())
    val params = visitParameterList(method.parameterList())
    val typeAnnotation = visitTypeAnnotation(method.typeAnnotation())
    val expr = visitExpr(ctx.expr())
    return ObjectMember.Method(modifiers, ident, typeParams, params, typeAnnotation, expr, span)
  }

  override fun visitMemberPredicate(ctx: PklParser.MemberPredicateContext): ObjectMember {
    // TODO: check delimiters
    val pred = visitExpr(ctx.k)
    val span = toSpan(ctx)
    return if (ctx.v != null) {
      ObjectMember.MemberPredicate(pred, visitExpr(ctx.v), span)
    } else {
      val bodies = ctx.objectBody().map(::visitObjectBody)
      ObjectMember.MemberPredicateBody(pred, bodies, span)
    }
  }

  override fun visitObjectEntry(ctx: PklParser.ObjectEntryContext): ObjectMember {
    // TODO: check delimiters
    val pred = visitExpr(ctx.k)
    val span = toSpan(ctx)
    return if (ctx.v != null) {
      ObjectMember.Entry(pred, visitExpr(ctx.v), span)
    } else {
      val bodies = ctx.objectBody().map(::visitObjectBody)
      ObjectMember.EntryBody(pred, bodies, span)
    }
  }

  override fun visitObjectElement(ctx: PklParser.ObjectElementContext): ObjectMember {
    return ObjectMember.Element(visitExpr(ctx.expr()), toSpan(ctx))
  }

  override fun visitObjectSpread(ctx: PklParser.ObjectSpreadContext): ObjectMember {
    val isNullable = ctx.QSPREAD() != null
    return ObjectMember.Spread(visitExpr(ctx.expr()), isNullable, toSpan(ctx))
  }

  override fun visitWhenGenerator(ctx: PklParser.WhenGeneratorContext): ObjectMember {
    // TODO: check delimiters
    val pred = visitExpr(ctx.e)
    val body = visitObjectBody(ctx.b1)
    val elseBody = ctx.b2?.let(::visitObjectBody)
    return ObjectMember.WhenGenerator(pred, body, elseBody, toSpan(ctx))
  }

  override fun visitForGenerator(ctx: PklParser.ForGeneratorContext): ObjectMember {
    // TODO: check delimiters
    val p1 = visitParameter(ctx.t1)
    val p2 = ctx.t2?.let(::visitParameter)
    val expr = visitExpr(ctx.e)
    val body = visitObjectBody(ctx.objectBody())
    return ObjectMember.ForGenerator(p1, p2, expr, body, toSpan(ctx))
  }

  override fun visitTypeParameter(ctx: PklParser.TypeParameterContext): TypeParameter {
    val ident = toIdent(ctx.Identifier())
    val span = toSpan(ctx)
    return when {
      ctx.IN() != null -> TypeParameter(Variance.IN, ident, span)
      ctx.OUT() != null -> TypeParameter(Variance.OUT, ident, span)
      else -> TypeParameter(null, ident, span)
    }
  }

  override fun visitTypeParameterList(
    ctx: PklParser.TypeParameterListContext?
  ): List<TypeParameter> {
    // TODO: check delimiters and commas
    if (ctx == null) return listOf()
    return ctx.ts.map(::visitTypeParameter)
  }

  override fun visitModifier(ctx: PklParser.ModifierContext): Modifier {
    val span = toSpan(ctx)
    val value =
      when (ctx.t.type) {
        PklLexer.EXTERNAL -> ModifierValue.EXTERNAL
        PklLexer.ABSTRACT -> ModifierValue.ABSTRACT
        PklLexer.LOCAL -> ModifierValue.LOCAL
        PklLexer.HIDDEN_ -> ModifierValue.HIDDEN
        PklLexer.FIXED -> ModifierValue.FIXED
        PklLexer.CONST -> ModifierValue.CONST
        else -> {
          errors += ParseError("invalidModifier", span)
          ModifierValue.HIDDEN
        }
      }
    return Modifier(value, span)
  }

  override fun visitParameter(ctx: PklParser.ParameterContext): Parameter {
    if (ctx.UNDERSCORE() != null) return Parameter.Underscore(toSpan(ctx))
    val ident = toIdent(ctx.typedIdentifier().Identifier())
    val typeAnnotation = visitTypeAnnotation(ctx.typedIdentifier().typeAnnotation())
    return Parameter.TypedIdent(ident, typeAnnotation, toSpan(ctx))
  }

  override fun visitParameterList(ctx: PklParser.ParameterListContext?): List<Parameter> {
    // TODO: check closing delimiter and commas
    if (ctx == null) return listOf()
    return ctx.ts.map(::visitParameter)
  }

  // Helpers

  fun errors(): List<ParseError> = errors

  private fun doVisitObjectMemberList(
    members: List<PklParser.ObjectMemberContext>?
  ): List<ObjectMember> {
    if (members == null) return listOf()
    return members.map(::visitObjectMember)
  }

  private fun checkModifiers(
    modifiers: List<PklParser.ModifierContext>,
    error: String,
    vararg valids: ModifierValue
  ): List<Modifier> {
    return modifiers.map {
      val mod = visitModifier(it)
      if (mod.mod !in valids) {
        errors += ParseError(error, toSpan(it))
      }
      mod
    }
  }

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

  private fun doVisitMultiLineConstantStringPart(
    tokens: List<Token>,
    commonIndent: String,
    isStringStart: Boolean,
    isStringEnd: Boolean
  ): String {
    var startIndex = 0
    if (isStringStart) {
      // skip leading newline token
      startIndex = 1
    }

    var endIndex = tokens.size - 1
    if (isStringEnd) {
      endIndex -=
        if (tokens[endIndex].type == PklLexer.MLNewline) {
          // skip trailing newline token
          1
        } else {
          // skip trailing newline and whitespace (common indent) tokens
          2
        }
    }

    val builder = StringBuilder()
    var isLineStart = isStringStart

    for (i in startIndex..endIndex) {
      val token = tokens[i]

      when (token.type) {
        PklLexer.MLNewline -> {
          builder.append('\n')
          isLineStart = true
        }
        PklLexer.MLCharacters -> {
          val text = token.text
          if (isLineStart) {
            if (text.startsWith(commonIndent)) {
              builder.append(text, commonIndent.length, text.length)
            } else {
              errors += ParseError("stringIndentationMustMatchLastLine", toSpan(token))
            }
          } else {
            builder.append(text)
          }
          isLineStart = false
        }
        PklLexer.MLCharacterEscape -> {
          if (isLineStart && commonIndent.isNotEmpty()) {
            errors += ParseError("stringIndentationMustMatchLastLine", toSpan(token))
          }
          builder.append(doParseCharacterEscapeSequence(token))
          isLineStart = false
        }
        PklLexer.MLUnicodeEscape -> {
          if (isLineStart && commonIndent.isNotEmpty()) {
            errors += ParseError("stringIndentationMustMatchLastLine", toSpan(token))
          }
          builder.appendCodePoint(doParseUnicodeEscapeSequence(token))
          isLineStart = false
        }
        else -> throw RuntimeException("unreacheable code: doVisitMultiLineConstantStringPart")
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

  private fun getCommonIndent(
    lastPart: PklParser.MultiLineStringPartContext,
    endQuoteToken: Token
  ): String {
    if (lastPart.e != null) {
      errors += ParseError("closingStringDelimiterMustBeginOnNewLine", toSpan(lastPart))
    }

    val tokens = lastPart.ts
    assert(tokens.size >= 1)
    val lastToken = tokens[tokens.size - 1]

    if (lastToken.type == PklLexer.MLNewline) {
      return ""
    }

    if (tokens.size > 1) {
      val lastButOneToken = tokens[tokens.size - 2]
      if (lastButOneToken.type == PklLexer.MLNewline && AstBuilder.isIndentChars(lastToken)) {
        return lastToken.text
      }
    }

    errors += ParseError("closingStringDelimiterMustBeginOnNewLine", toSpan(endQuoteToken))
    return ""
  }

  companion object {
    private fun toIdent(token: Token): Ident = Ident(token.text, toSpan(token))

    private fun toIdent(node: TerminalNode): Ident = Ident(node.text, toSpan(node.symbol))

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
