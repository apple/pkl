/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.parser

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import org.assertj.core.api.SoftAssertions
import org.pkl.core.parser.antlr.PklParser.*
import org.pkl.core.parser.ast.*
import org.pkl.core.parser.ast.Annotation
import org.pkl.core.parser.ast.Expr.AmendsExpr
import org.pkl.core.parser.ast.Expr.BinaryOperatorExpr
import org.pkl.core.parser.ast.Expr.FunctionLiteralExpr
import org.pkl.core.parser.ast.Expr.IfExpr
import org.pkl.core.parser.ast.Expr.ImportExpr
import org.pkl.core.parser.ast.Expr.LetExpr
import org.pkl.core.parser.ast.Expr.LogicalNotExpr
import org.pkl.core.parser.ast.Expr.MultiLineStringLiteralExpr
import org.pkl.core.parser.ast.Expr.NewExpr
import org.pkl.core.parser.ast.Expr.NonNullExpr
import org.pkl.core.parser.ast.Expr.ParenthesizedExpr
import org.pkl.core.parser.ast.Expr.QualifiedAccessExpr
import org.pkl.core.parser.ast.Expr.ReadExpr
import org.pkl.core.parser.ast.Expr.SingleLineStringLiteralExpr
import org.pkl.core.parser.ast.Expr.SubscriptExpr
import org.pkl.core.parser.ast.Expr.SuperAccessExpr
import org.pkl.core.parser.ast.Expr.SuperSubscriptExpr
import org.pkl.core.parser.ast.Expr.ThrowExpr
import org.pkl.core.parser.ast.Expr.TraceExpr
import org.pkl.core.parser.ast.Expr.TypeCastExpr
import org.pkl.core.parser.ast.Expr.TypeCheckExpr
import org.pkl.core.parser.ast.Expr.UnaryMinusExpr
import org.pkl.core.parser.ast.Expr.UnqualifiedAccessExpr
import org.pkl.core.parser.ast.ObjectMember.ForGenerator
import org.pkl.core.parser.ast.ObjectMember.MemberPredicate
import org.pkl.core.parser.ast.ObjectMember.ObjectElement
import org.pkl.core.parser.ast.ObjectMember.ObjectEntry
import org.pkl.core.parser.ast.ObjectMember.ObjectMethod
import org.pkl.core.parser.ast.ObjectMember.ObjectProperty
import org.pkl.core.parser.ast.ObjectMember.ObjectSpread
import org.pkl.core.parser.ast.ObjectMember.WhenGenerator
import org.pkl.core.parser.ast.Parameter.TypedIdentifier
import org.pkl.core.parser.ast.Type.ConstrainedType
import org.pkl.core.parser.ast.Type.DeclaredType
import org.pkl.core.parser.ast.Type.FunctionType
import org.pkl.core.parser.ast.Type.NullableType
import org.pkl.core.parser.ast.Type.ParenthesizedType
import org.pkl.core.parser.ast.Type.StringConstantType
import org.pkl.core.parser.ast.Type.UnionType

class SpanComparison(val path: String, private val softly: SoftAssertions) {

  fun compare(module: Module, modCtx: ModuleContext) {
    compareSpan(module, modCtx)
    if (module.decl !== null) {
      compareModuleDecl(module.decl!!, modCtx.moduleDecl())
    }
    module.imports.zip(modCtx.importClause()).forEach { (i1, i2) -> compareImport(i1, i2) }
    module.classes.zip(modCtx.clazz()).forEach { (class1, class2) -> compareClass(class1, class2) }
    module.typeAliases.zip(modCtx.typeAlias()).forEach { (ta1, ta2) -> compareTypealias(ta1, ta2) }
    module.properties.zip(modCtx.classProperty()).forEach { (prop1, prop2) ->
      compareProperty(prop1, prop2)
    }
    module.methods.zip(modCtx.classMethod()).forEach { (m1, m2) -> compareMethod(m1, m2) }
  }

  private fun compareModuleDecl(node: ModuleDecl, ctx: ModuleDeclContext) {
    compareSpan(node, ctx)
    compareDocComment(node.docComment, ctx.DocComment())
    node.annotations.zip(ctx.annotation()).forEach { (a1, a2) -> compareAnnotation(a1, a2) }
    val header = ctx.moduleHeader()
    node.modifiers.zip(header.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
    compareQualifiedIdentifier(node.name, header.qualifiedIdentifier())
    compareExtendsOrAmendsClause(node.extendsOrAmendsDecl, header.moduleExtendsOrAmendsClause())
  }

  private fun compareImport(node: ImportClause, ctx: ImportClauseContext) {
    compareSpan(node, ctx)
    compareSpan(node.importStr, ctx.stringConstant())
    compareSpan(node.alias, ctx.Identifier())
  }

  private fun compareClass(node: Class, ctx: ClazzContext) {
    compareSpan(node, ctx)
    compareDocComment(node.docComment, ctx.DocComment())
    node.annotations.zip(ctx.annotation()).forEach { (a1, a2) -> compareAnnotation(a1, a2) }
    val header = ctx.classHeader()
    node.modifiers.zip(header.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
    compareSpan(node.classKeyword, header.CLASS())
    compareSpan(node.name, header.Identifier())
    compareTypeParameterList(node.typeParameterList, header.typeParameterList())
    compareType(node.superClass, header.type())
    compareClassBody(node.body, ctx.classBody())
    compareSpan(node.headerSpan, header)
  }

  private fun compareTypealias(node: TypeAlias, ctx: TypeAliasContext) {
    compareSpan(node, ctx)
    compareDocComment(node.docComment, ctx.DocComment())
    node.annotations.zip(ctx.annotation()).forEach { (a1, a2) -> compareAnnotation(a1, a2) }
    val header = ctx.typeAliasHeader()
    node.modifiers.zip(header.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
    compareSpan(node.typealiasKeyword, header.TYPE_ALIAS())
    compareSpan(node.name, header.Identifier())
    compareTypeParameterList(node.typeParameterList, header.typeParameterList())
    compareType(node.type, ctx.type())
    compareSpan(node.headerSpan, header)
  }

  private fun compareProperty(node: ClassProperty, ctx: ClassPropertyContext) {
    if (node.docComment === null) {
      compareSpan(node, ctx)
    }
    compareDocComment(node.docComment, ctx.DocComment())
    node.annotations.zip(ctx.annotation()).forEach { (a1, a2) -> compareAnnotation(a1, a2) }
    node.modifiers.zip(ctx.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
    compareSpan(node.name, ctx.Identifier())
    compareTypeAnnotation(node.typeAnnotation, ctx.typeAnnotation())
    compareExpr(node.expr, ctx.expr())
    node.bodyList.zip(ctx.objectBody()).forEach { (b1, b2) -> compareObjectBody(b1, b2) }
  }

  private fun compareMethod(node: ClassMethod, ctx: ClassMethodContext) {
    compareSpan(node, ctx)
    compareDocComment(node.docComment, ctx.DocComment())
    node.annotations.zip(ctx.annotation()).forEach { (a1, a2) -> compareAnnotation(a1, a2) }
    val header = ctx.methodHeader()
    compareSpan(node.headerSpan, header)
    node.modifiers.zip(header.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
    compareSpan(node.name, header.Identifier())
    compareTypeParameterList(node.typeParameterList, header.typeParameterList())
    compareParameterList(node.parameterList, header.parameterList())
    compareTypeAnnotation(node.typeAnnotation, header.typeAnnotation())
    compareExpr(node.expr, ctx.expr())
  }

  private fun compareAnnotation(node: Annotation, ctx: AnnotationContext) {
    compareSpan(node, ctx)
    compareType(node.type, ctx.type())
    compareObjectBody(node.body, ctx.objectBody())
  }

  private fun compareParameterList(node: ParameterList?, ctx: ParameterListContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.parameters.zip(ctx.parameter()).forEach { (p1, p2) -> compareParameter(p1, p2) }
  }

  private fun compareParameter(node: Parameter?, ctx: ParameterContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    if (node is TypedIdentifier) {
      val tident = ctx.typedIdentifier()
      compareSpan(node.identifier, tident.Identifier())
      compareTypeAnnotation(node.typeAnnotation, tident.typeAnnotation())
    }
  }

  private fun compareTypeParameterList(node: TypeParameterList?, ctx: TypeParameterListContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.parameters.zip(ctx.typeParameter()).forEach { (p1, p2) -> compareTypeParameter(p1, p2) }
  }

  private fun compareTypeParameter(node: TypeParameter, ctx: TypeParameterContext) {
    compareSpan(node, ctx)
    compareSpan(node.identifier, ctx.Identifier())
  }

  private fun compareClassBody(node: ClassBody?, ctx: ClassBodyContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.properties.zip(ctx.classProperty()).forEach { (p1, p2) -> compareProperty(p1, p2) }
    node.methods.zip(ctx.classMethod()).forEach { (m1, m2) -> compareMethod(m1, m2) }
  }

  private fun compareTypeAnnotation(node: TypeAnnotation?, ctx: TypeAnnotationContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    compareSpan(node.type, ctx.type())
  }

  private fun compareObjectBody(node: ObjectBody?, ctx: ObjectBodyContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.parameters.zip(ctx.parameter()).forEach { (p1, p2) -> compareParameter(p1, p2) }
    node.members.zip(ctx.objectMember()).forEach { (m1, m2) -> compareObjectMember(m1, m2) }
  }

  private fun compareType(node: Type?, actx: TypeContext?) {
    if (node === null) return
    val ctx = if (actx is DefaultUnionTypeContext) actx.type() else actx
    compareSpan(node, ctx!!)
    when (node) {
      is StringConstantType ->
        compareSpan(node.str, (ctx as StringLiteralTypeContext).stringConstant())
      is DeclaredType -> {
        val decl = ctx as DeclaredTypeContext
        compareQualifiedIdentifier(node.name, decl.qualifiedIdentifier())
        compareTypeArgumentList(node.args, ctx.typeArgumentList())
      }
      is ParenthesizedType -> compareType(node.type, (ctx as ParenthesizedTypeContext).type())
      is NullableType -> compareType(node.type, (ctx as NullableTypeContext).type())
      is ConstrainedType -> {
        val cons = ctx as ConstrainedTypeContext
        compareType(node.type, cons.type())
        node.exprs.zip(cons.expr()).forEach { (e1, e2) -> compareExpr(e1, e2) }
      }
      is UnionType -> {
        val flattened = ANTLRSexpRenderer.flattenUnion(ctx as UnionTypeContext)
        node.types.zip(flattened).forEach { (t1, t2) -> compareType(t1, t2) }
      }
      is FunctionType -> {
        val func = ctx as FunctionTypeContext
        node.args.zip(func.ps).forEach { (t1, t2) -> compareType(t1, t2) }
        compareType(node.ret, func.r)
      }
      else -> {}
    }
  }

  private fun compareObjectMember(node: ObjectMember, ctx: ObjectMemberContext) {
    compareSpan(node, ctx)
    when (node) {
      is ObjectElement -> compareExpr(node.expr, (ctx as ObjectElementContext).expr())
      is ObjectProperty -> {
        ctx as ObjectPropertyContext
        node.modifiers.zip(ctx.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
        compareSpan(node.identifier, ctx.Identifier())
        compareTypeAnnotation(node.typeAnnotation, ctx.typeAnnotation())
        compareExpr(node.expr, ctx.expr())
        if (node.bodyList.isNotEmpty()) {
          node.bodyList.zip(ctx.objectBody()).forEach { (b1, b2) -> compareObjectBody(b1, b2) }
        }
      }
      is ObjectMethod -> {
        ctx as ObjectMethodContext
        val header = ctx.methodHeader()
        node.modifiers.zip(header.modifier()).forEach { (m1, m2) -> compareSpan(m1, m2) }
        compareSpan(node.functionKeyword, header.FUNCTION())
        compareSpan(node.identifier, header.Identifier())
        compareTypeParameterList(node.typeParameterList, header.typeParameterList())
        compareParameterList(node.paramList, header.parameterList())
        compareTypeAnnotation(node.typeAnnotation, header.typeAnnotation())
        compareExpr(node.expr, ctx.expr())
        compareSpan(node.headerSpan(), header)
      }
      is MemberPredicate -> {
        ctx as MemberPredicateContext
        compareExpr(node.pred, ctx.k)
        compareExpr(node.expr, ctx.v)
        if (node.bodyList.isNotEmpty()) {
          node.bodyList.zip(ctx.objectBody()).forEach { (b1, b2) -> compareObjectBody(b1, b2) }
        }
      }
      is ObjectEntry -> {
        ctx as ObjectEntryContext
        compareExpr(node.key, ctx.k)
        compareExpr(node.value, ctx.v)
        if (node.bodyList.isNotEmpty()) {
          node.bodyList.zip(ctx.objectBody()).forEach { (b1, b2) -> compareObjectBody(b1, b2) }
        }
      }
      is ObjectSpread -> compareExpr(node.expr, (ctx as ObjectSpreadContext).expr())
      is WhenGenerator -> {
        ctx as WhenGeneratorContext
        compareExpr(node.predicate, ctx.expr())
        compareObjectBody(node.thenClause, ctx.b1)
        compareObjectBody(node.elseClause, ctx.b2)
      }
      is ForGenerator -> {
        ctx as ForGeneratorContext
        compareParameter(node.p1, ctx.t1)
        compareParameter(node.p2, ctx.t2)
        compareExpr(node.expr, ctx.expr())
        compareObjectBody(node.body, ctx.objectBody())
      }
    }
  }

  private fun compareExpr(node: Expr?, ctx: ExprContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    when (node) {
      is SingleLineStringLiteralExpr -> {
        node.parts.zip((ctx as SingleLineStringLiteralContext).singleLineStringPart()).forEach {
          (s1, s2) ->
          compareSpan(s1, s2)
        }
      }
      is MultiLineStringLiteralExpr -> {
        node.parts.zip((ctx as MultiLineStringLiteralContext).multiLineStringPart()).forEach {
          (s1, s2) ->
          compareSpan(s1, s2)
        }
      }
      is ThrowExpr -> compareExpr(node.expr, (ctx as ThrowExprContext).expr())
      is TraceExpr -> compareExpr(node.expr, (ctx as TraceExprContext).expr())
      is ImportExpr -> compareSpan(node.importStr, (ctx as ImportExprContext).stringConstant())
      is ReadExpr -> compareExpr(node.expr, (ctx as ReadExprContext).expr())
      is UnqualifiedAccessExpr -> {
        ctx as UnqualifiedAccessExprContext
        compareSpan(node.identifier, ctx.Identifier())
        compareArgumentList(node.argumentList, ctx.argumentList())
      }
      is QualifiedAccessExpr -> {
        ctx as QualifiedAccessExprContext
        compareExpr(node.expr, ctx.expr())
        compareSpan(node.identifier, ctx.Identifier())
        compareArgumentList(node.argumentList, ctx.argumentList())
      }
      is SuperAccessExpr -> {
        ctx as SuperAccessExprContext
        compareSpan(node.identifier, ctx.Identifier())
        compareArgumentList(node.argumentList, ctx.argumentList())
      }
      is SuperSubscriptExpr -> compareExpr(node.arg, (ctx as SuperSubscriptExprContext).expr())
      is SubscriptExpr -> {
        ctx as SubscriptExprContext
        compareExpr(node.expr, ctx.l)
        compareExpr(node.arg, ctx.r)
      }
      is IfExpr -> {
        ctx as IfExprContext
        compareExpr(node.cond, ctx.c)
        compareExpr(node.then, ctx.l)
        compareExpr(node.els, ctx.r)
      }
      is LetExpr -> {
        ctx as LetExprContext
        compareParameter(node.parameter, ctx.parameter())
        compareExpr(node.bindingExpr, ctx.l)
        compareExpr(node.expr, ctx.r)
      }
      is FunctionLiteralExpr -> {
        ctx as FunctionLiteralContext
        compareParameterList(node.parameterList, ctx.parameterList())
        compareExpr(node.expr, ctx.expr())
      }
      is ParenthesizedExpr -> compareExpr(node.expr, (ctx as ParenthesizedExprContext).expr())
      is NewExpr -> {
        ctx as NewExprContext
        compareType(node.type, ctx.type())
        compareObjectBody(node.body, ctx.objectBody())
      }
      is AmendsExpr -> {
        ctx as AmendExprContext
        compareExpr(node.expr, ctx.expr())
        compareObjectBody(node.body, ctx.objectBody())
      }
      is NonNullExpr -> compareExpr(node.expr, (ctx as NonNullExprContext).expr())
      is UnaryMinusExpr -> compareExpr(node.expr, (ctx as UnaryMinusExprContext).expr())
      is LogicalNotExpr -> compareExpr(node.expr, (ctx as LogicalNotExprContext).expr())
      is BinaryOperatorExpr -> {
        val (l, r) =
          when (ctx) {
            is ExponentiationExprContext -> ctx.l to ctx.r
            is MultiplicativeExprContext -> ctx.l to ctx.r
            is AdditiveExprContext -> ctx.l to ctx.r
            is ComparisonExprContext -> ctx.l to ctx.r
            is EqualityExprContext -> ctx.l to ctx.r
            is LogicalAndExprContext -> ctx.l to ctx.r
            is LogicalOrExprContext -> ctx.l to ctx.r
            is PipeExprContext -> ctx.l to ctx.r
            is NullCoalesceExprContext -> ctx.l to ctx.r
            else -> throw RuntimeException("unreacheable code")
          }
        compareExpr(node.left, l)
        compareExpr(node.right, r)
      }
      is TypeCheckExpr -> {
        ctx as TypeTestExprContext
        compareExpr(node.expr, ctx.expr())
        compareType(node.type, ctx.type())
      }
      is TypeCastExpr -> {
        ctx as TypeTestExprContext
        compareExpr(node.expr, ctx.expr())
        compareType(node.type, ctx.type())
      }
      else -> {}
    }
  }

  private fun compareArgumentList(node: ArgumentList?, ctx: ArgumentListContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.arguments.zip(ctx.expr()).forEach { (e1, e2) -> compareExpr(e1, e2) }
  }

  private fun compareTypeArgumentList(node: TypeArgumentList?, ctx: TypeArgumentListContext?) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.types.zip(ctx.type()).forEach { (t1, t2) -> compareType(t1, t2) }
  }

  private fun compareQualifiedIdentifier(
    node: QualifiedIdentifier?,
    ctx: QualifiedIdentifierContext?,
  ) {
    if (node === null) return
    compareSpan(node, ctx!!)
    node.identifiers.zip(ctx.Identifier()).forEach { (id1, id2) -> compareSpan(id1, id2) }
  }

  private fun compareExtendsOrAmendsClause(
    node: ExtendsOrAmendsClause?,
    ctx: ModuleExtendsOrAmendsClauseContext?,
  ) {
    if (node === null) return
    compareSpan(node, ctx!!)
    compareSpan(node.url, ctx.stringConstant())
  }

  private fun compareSpan(node: Node?, ctx: ParserRuleContext) {
    if (node != null) {
      compareSpan(node, ctx.start.startIndex, ctx.stop.stopIndex)
    }
  }

  private fun compareSpan(node: Node?, ctx: TerminalNode?) {
    if (node != null) {
      compareSpan(node, ctx!!.symbol.startIndex, ctx.symbol.stopIndex)
    }
  }

  private fun compareSpan(node: Node, charIndex: Int, tokenStop: Int) {
    compareSpan(node.span(), charIndex, tokenStop)
  }

  private fun compareSpan(span: Span, ctx: ParserRuleContext) {
    compareSpan(span, ctx.start.startIndex, ctx.stop.stopIndex)
  }

  private fun compareSpan(span: Span, charIndex: Int, tokenStop: Int) {
    val length = tokenStop - charIndex + 1
    softly.assertThat(span.charIndex).`as`("$span, index for path: $path").isEqualTo(charIndex)
    softly.assertThat(span.length).`as`("$span, length for path: $path").isEqualTo(length)
  }

  private fun compareDocComment(node: Node?, ctx: TerminalNode?) {
    if (node == null) return
    val charIndex = ctx!!.symbol.startIndex
    // for some reason antlr's doc coments are off by one
    val length = ctx.symbol.stopIndex - charIndex
    val span = node.span()
    softly.assertThat(span.charIndex).`as`("$span, index for path: $path").isEqualTo(charIndex)
    softly.assertThat(span.length).`as`("$span, length for path: $path").isEqualTo(length)
  }
}
