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
package org.pkl.lsp.ast

import org.pkl.core.parser.antlr.PklParser.*
import org.pkl.lsp.*
import org.pkl.lsp.LSPUtil.firstInstanceOf
import org.pkl.lsp.resolvers.ResolveVisitor
import org.pkl.lsp.resolvers.Resolvers
import org.pkl.lsp.type.Type
import org.pkl.lsp.type.TypeParameterBindings
import org.pkl.lsp.type.computeExprType
import org.pkl.lsp.type.computeThisType

class PklThisExprImpl(override val parent: Node, override val ctx: ThisExprContext) :
  AbstractNode(parent, ctx), PklThisExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitThisExpr(this)
  }
}

class PklOuterExprImpl(override val parent: Node, override val ctx: OuterExprContext) :
  AbstractNode(parent, ctx), PklOuterExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitOuterExpr(this)
  }
}

class PklModuleExprImpl(override val parent: Node, override val ctx: ModuleExprContext) :
  AbstractNode(parent, ctx), PklModuleExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleExpr(this)
  }
}

class PklNullLiteralExprImpl(override val parent: Node, override val ctx: NullLiteralContext) :
  AbstractNode(parent, ctx), PklNullLiteralExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNullLiteralExpr(this)
  }
}

class PklTrueLiteralExprImpl(override val parent: Node, override val ctx: TrueLiteralContext) :
  AbstractNode(parent, ctx), PklTrueLiteralExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTrueLiteralExpr(this)
  }
}

class PklFalseLiteralExprImpl(override val parent: Node, override val ctx: FalseLiteralContext) :
  AbstractNode(parent, ctx), PklFalseLiteralExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFalseLiteralExpr(this)
  }
}

class PklIntLiteralExprImpl(override val parent: Node, override val ctx: IntLiteralContext) :
  AbstractNode(parent, ctx), PklIntLiteralExpr {

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitIntLiteralExpr(this)
  }
}

class PklFloatLiteralExprImpl(override val parent: Node, override val ctx: FloatLiteralContext) :
  AbstractNode(parent, ctx), PklFloatLiteralExpr {

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFloatLiteralExpr(this)
  }
}

class PklThrowExprImpl(override val parent: Node, override val ctx: ThrowExprContext) :
  AbstractNode(parent, ctx), PklThrowExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitThrowExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklTraceExprImpl(override val parent: Node, override val ctx: TraceExprContext) :
  AbstractNode(parent, ctx), PklTraceExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTraceExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklImportExprImpl(override val parent: Node, override val ctx: ImportExprContext) :
  AbstractNode(parent, ctx), PklImportExpr {
  override val isGlob: Boolean by lazy { ctx.IMPORT_GLOB() != null }

  override val moduleUri: PklModuleUri? by lazy { PklModuleUriImpl(this, ctx) }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitImportExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklReadExprImpl(override val parent: Node, override val ctx: ReadExprContext) :
  AbstractNode(parent, ctx), PklReadExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val isNullable: Boolean by lazy { ctx.READ_OR_NULL() != null }
  override val isGlob: Boolean by lazy { ctx.READ_GLOB() != null }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitReadExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklUnqualifiedAccessExprImpl(
  override val parent: Node,
  override val ctx: UnqualifiedAccessExprContext
) : AbstractNode(parent, ctx), PklUnqualifiedAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val memberNameText: String by lazy { identifier!!.text }
  override val argumentList: PklArgumentList? by lazy {
    children.firstInstanceOf<PklArgumentList>()
  }
  override val isNullSafeAccess: Boolean = false

  override fun <R> resolve(
    base: PklBaseModule,
    receiverType: Type?,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>
  ): R {
    return Resolvers.resolveUnqualifiedAccess(
      this,
      receiverType,
      isPropertyAccess,
      base,
      bindings,
      visitor
    )
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitUnqualifiedAccessExpr(this)
  }
}

class PklSingleLineStringLiteralImpl(
  override val parent: Node,
  override val ctx: SingleLineStringLiteralContext
) : AbstractNode(parent, ctx), PklSingleLineStringLiteral {
  override val parts: List<SingleLineStringPart> by lazy {
    children.filterIsInstance<SingleLineStringPart>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitStringLiteral(this)
  }
}

class SingleLineStringPartImpl(override val parent: Node, ctx: SingleLineStringPartContext) :
  AbstractNode(parent, ctx), SingleLineStringPart {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSingleLineStringPart(this)
  }
}

class PklMultiLineStringLiteralImpl(
  override val parent: Node,
  override val ctx: MultiLineStringLiteralContext
) : AbstractNode(parent, ctx), PklMultiLineStringLiteral {
  override val parts: List<MultiLineStringPart> by lazy {
    children.filterIsInstance<MultiLineStringPart>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMlStringLiteral(this)
  }
}

class MultiLineStringPartImpl(override val parent: Node, ctx: MultiLineStringPartContext) :
  AbstractNode(parent, ctx), MultiLineStringPart {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMlStringPart(this)
  }
}

class PklNewExprImpl(override val parent: Node, override val ctx: NewExprContext) :
  AbstractNode(parent, ctx), PklNewExpr {
  override val type: PklType? by lazy { children.firstInstanceOf<PklType>() }
  override val objectBody: PklObjectBody? by lazy { children.firstInstanceOf<PklObjectBody>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNewExpr(this)
  }
}

class PklAmendExprImpl(override val parent: Node, override val ctx: AmendExprContext) :
  AbstractNode(parent, ctx), PklAmendExpr {
  override val parentExpr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }
  override val objectBody: PklObjectBody by lazy { children.firstInstanceOf<PklObjectBody>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitAmendEpxr(this)
  }
}

class PklSuperAccessExprImpl(override val parent: Node, override val ctx: SuperAccessExprContext) :
  AbstractNode(parent, ctx), PklSuperAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val memberNameText: String by lazy { identifier!!.text }
  override val isNullSafeAccess: Boolean = false
  override val argumentList: PklArgumentList? by lazy {
    children.firstInstanceOf<PklArgumentList>()
  }

  override fun <R> resolve(
    base: PklBaseModule,
    receiverType: Type?,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>
  ): R {
    // TODO: Pkl doesn't currently enforce that `super.foo`
    // has the same type as `this.foo` if `super.foo` is defined in a superclass.
    // In particular, covariant property types are used in the wild.
    val thisType = receiverType ?: computeThisType(base, bindings)
    return Resolvers.resolveQualifiedAccess(thisType, isPropertyAccess, base, visitor)
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSuperAccessExpr(this)
  }
}

class PklSuperSubscriptExprImpl(
  override val parent: Node,
  override val ctx: SuperSubscriptExprContext
) : AbstractNode(parent, ctx), PklSuperSubscriptExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSuperSubscriptExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else "]"
  }
}

class PklQualifiedAccessExprImpl(
  override val parent: Node,
  override val ctx: QualifiedAccessExprContext
) : AbstractNode(parent, ctx), PklQualifiedAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val memberNameText: String by lazy { identifier!!.text }
  override val isNullSafeAccess: Boolean by lazy { ctx.QDOT() != null }
  override val argumentList: PklArgumentList? by lazy {
    children.firstInstanceOf<PklArgumentList>()
  }
  override val receiverExpr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }

  override fun <R> resolve(
    base: PklBaseModule,
    receiverType: Type?,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>
  ): R {
    val myReceiverType: Type = receiverType ?: receiverExpr.computeExprType(base, bindings)
    return Resolvers.resolveQualifiedAccess(myReceiverType, isPropertyAccess, base, visitor)
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitQualifiedAccessExpr(this)
  }
}

class PklSubscriptExprImpl(override val parent: Node, override val ctx: SubscriptExprContext) :
  AbstractNode(parent, ctx), PklSubscriptExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSubscriptExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else "]"
  }
}

class PklNonNullExprImpl(override val parent: Node, override val ctx: NonNullExprContext) :
  AbstractNode(parent, ctx), PklNonNullExpr {
  override val expr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNonNullExpr(this)
  }
}

class PklUnaryMinusExprImpl(override val parent: Node, override val ctx: UnaryMinusExprContext) :
  AbstractNode(parent, ctx), PklUnaryMinusExpr {
  override val expr: PklExpr by lazy { ctx.expr().toNode(this) as PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitUnaryMinusExpr(this)
  }
}

class PklLogicalNotExprImpl(override val parent: Node, override val ctx: LogicalNotExprContext) :
  AbstractNode(parent, ctx), PklLogicalNotExpr {
  override val expr: PklExpr by lazy { ctx.expr().toNode(this) as PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLogicalNotExpr(this)
  }
}

class PklAdditiveExprImpl(override val parent: Node, override val ctx: AdditiveExprContext) :
  AbstractNode(parent, ctx), PklAdditiveExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitAdditiveExpr(this)
  }
}

class PklMultiplicativeExprImpl(
  override val parent: Node,
  override val ctx: MultiplicativeExprContext
) : AbstractNode(parent, ctx), PklMultiplicativeExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMultiplicativeExpr(this)
  }
}

class PklComparisonExprImpl(override val parent: Node, override val ctx: ComparisonExprContext) :
  AbstractNode(parent, ctx), PklComparisonExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitComparisonExpr(this)
  }
}

class PklEqualityExprImpl(override val parent: Node, override val ctx: EqualityExprContext) :
  AbstractNode(parent, ctx), PklEqualityExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitEqualityExpr(this)
  }
}

class PklExponentiationExprImpl(
  override val parent: Node,
  override val ctx: ExponentiationExprContext
) : AbstractNode(parent, ctx), PklExponentiationExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitExponentiationExpr(this)
  }
}

class PklLogicalAndExprImpl(override val parent: Node, override val ctx: LogicalAndExprContext) :
  AbstractNode(parent, ctx), PklLogicalAndExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLogicalAndExpr(this)
  }
}

class PklLogicalOrExprImpl(override val parent: Node, override val ctx: LogicalOrExprContext) :
  AbstractNode(parent, ctx), PklLogicalOrExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLogicalOrExpr(this)
  }
}

class PklNullCoalesceExprImpl(
  override val parent: Node,
  override val ctx: NullCoalesceExprContext
) : AbstractNode(parent, ctx), PklNullCoalesceExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNullCoalesceExpr(this)
  }
}

class PklTypeTestExprImpl(override val parent: Node, override val ctx: TypeTestExprContext) :
  AbstractNode(parent, ctx), PklTypeTestExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
  override val operator: TypeTestOperator by lazy {
    if (ctx.IS() != null) TypeTestOperator.IS else TypeTestOperator.AS
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeTestExpr(this)
  }
}

class PklPipeExprImpl(override val parent: Node, override val ctx: PipeExprContext) :
  AbstractNode(parent, ctx), PklPipeExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitPipeExpr(this)
  }
}

class PklIfExprImpl(override val parent: Node, override val ctx: IfExprContext) :
  AbstractNode(parent, ctx), PklIfExpr {
  override val conditionExpr: PklExpr by lazy { ctx.c.toNode(this) as PklExpr }
  override val thenExpr: PklExpr by lazy { ctx.l.toNode(this) as PklExpr }
  override val elseExpr: PklExpr by lazy { ctx.r.toNode(this) as PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitIfExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklLetExprImpl(override val parent: Node, override val ctx: LetExprContext) :
  AbstractNode(parent, ctx), PklLetExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val varExpr: PklExpr? by lazy { ctx.l.toNode(this) as PklExpr }
  override val bodyExpr: PklExpr? by lazy { ctx.r.toNode(this) as PklExpr }
  override val parameter: PklParameter? by lazy { children.firstInstanceOf<PklParameter>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLetExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklFunctionLiteralExprImpl(
  override val parent: Node,
  override val ctx: FunctionLiteralContext
) : AbstractNode(parent, ctx), PklFunctionLiteralExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val parameterList: PklParameterList by lazy {
    children.firstInstanceOf<PklParameterList>()!!
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFunctionLiteral(this)
  }
}

class PklParenthesizedExprImpl(
  override val parent: Node,
  override val ctx: ParenthesizedExprContext
) : AbstractNode(parent, ctx), PklParenthesizedExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitParenthesizedExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklTypedIdentifierImpl(override val parent: Node, override val ctx: TypedIdentifierContext) :
  AbstractNode(parent, ctx), PklTypedIdentifier {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val typeAnnotation: PklTypeAnnotation? by lazy {
    children.firstInstanceOf<PklTypeAnnotation>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypedIdentifier(this)
  }
}

class PklParameterImpl(override val parent: Node, override val ctx: ParameterContext) :
  AbstractNode(parent, ctx), PklParameter {
  override val isUnderscore: Boolean by lazy { ctx.UNDERSCORE() != null }
  override val typedIdentifier: PklTypedIdentifier? by lazy {
    children.firstInstanceOf<PklTypedIdentifier>()
  }
  override val type: PklType? by lazy { typedIdentifier?.typeAnnotation?.type }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitParameter(this)
  }
}

class PklArgumentListImpl(override val parent: Node, override val ctx: ArgumentListContext) :
  AbstractNode(parent, ctx), PklArgumentList {
  override val elements: List<PklExpr> by lazy { children.filterIsInstance<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitArgumentList(this)
  }

  override fun checkClosingDelimiter(): String? {
    if (ctx.expr().isNotEmpty() && ctx.errs.size != ctx.expr().size - 1) {
      return ","
    }
    return if (ctx.err != null) null else ")"
  }
}
