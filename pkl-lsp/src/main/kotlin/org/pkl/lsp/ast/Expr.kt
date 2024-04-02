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

class ThisExprImpl(override val parent: Node, override val ctx: ThisExprContext) :
  AbstractNode(parent, ctx), ThisExpr

class OuterExprImpl(override val parent: Node, override val ctx: OuterExprContext) :
  AbstractNode(parent, ctx), OuterExpr

class ModuleExprImpl(override val parent: Node, override val ctx: ModuleExprContext) :
  AbstractNode(parent, ctx), ModuleExpr

class NullLiteralExprImpl(override val parent: Node, override val ctx: NullLiteralContext) :
  AbstractNode(parent, ctx), NullLiteralExpr

class TrueLiteralExprImpl(override val parent: Node, override val ctx: TrueLiteralContext) :
  AbstractNode(parent, ctx), TrueLiteralExpr

class FalseLiteralExprImpl(override val parent: Node, override val ctx: FalseLiteralContext) :
  AbstractNode(parent, ctx), FalseLiteralExpr

class IntLiteralExprImpl(override val parent: Node, override val ctx: IntLiteralContext) :
  AbstractNode(parent, ctx), IntLiteralExpr

class FloatLiteralExprImpl(override val parent: Node, override val ctx: FloatLiteralContext) :
  AbstractNode(parent, ctx), FloatLiteralExpr

class ThrowExprImpl(override val parent: Node, override val ctx: ThrowExprContext) :
  AbstractNode(parent, ctx), ThrowExpr

class TraceExprImpl(override val parent: Node, override val ctx: TraceExprContext) :
  AbstractNode(parent, ctx), TraceExpr

class ImportExprImpl(override val parent: Node, override val ctx: ImportExprContext) :
  AbstractNode(parent, ctx), ImportExpr {
  override val isGlob: Boolean by lazy { ctx.IMPORT_GLOB() != null }

  override val moduleUri: String
    get() = TODO("Not yet implemented")
}

class ReadExprImpl(override val parent: Node, override val ctx: ReadExprContext) :
  AbstractNode(parent, ctx), ReadExpr

class UnqualifiedAccessExprImpl(
  override val parent: Node,
  override val ctx: UnqualifiedAccessExprContext
) : AbstractNode(parent, ctx), UnqualifiedAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
}

class SingleLineStringLiteralImpl(
  override val parent: Node,
  override val ctx: SingleLineStringLiteralContext
) : AbstractNode(parent, ctx), SingleLineStringLiteral

class MultilineStringLiteralImpl(
  override val parent: Node,
  override val ctx: MultiLineStringLiteralContext
) : AbstractNode(parent, ctx), MultilineStringLiteral

class NewExprImpl(override val parent: Node, override val ctx: NewExprContext) :
  AbstractNode(parent, ctx), NewExpr

class AmendExprImpl(override val parent: Node, override val ctx: AmendExprContext) :
  AbstractNode(parent, ctx), AmendExpr

class SuperAccessExprImpl(override val parent: Node, override val ctx: SuperAccessExprContext) :
  AbstractNode(parent, ctx), SuperAccessExpr

class SuperSubscriptExprImpl(
  override val parent: Node,
  override val ctx: SuperSubscriptExprContext
) : AbstractNode(parent, ctx), SuperSubscriptExpr

class QualifiedAccessExprImpl(
  override val parent: Node,
  override val ctx: QualifiedAccessExprContext
) : AbstractNode(parent, ctx), QualifiedAccessExpr

class SubscriptExprImpl(override val parent: Node, override val ctx: SubscriptExprContext) :
  AbstractNode(parent, ctx), SubscriptExpr

class NonNullExprImpl(override val parent: Node, override val ctx: NonNullExprContext) :
  AbstractNode(parent, ctx), NonNullExpr

class UnaryMinusExprImpl(override val parent: Node, override val ctx: UnaryMinusExprContext) :
  AbstractNode(parent, ctx), UnaryMinusExpr

class LogicalNotExprImpl(override val parent: Node, override val ctx: LogicalNotExprContext) :
  AbstractNode(parent, ctx), LogicalNotExpr

class ExponentiationExprImpl(
  override val parent: Node,
  override val ctx: ExponentiationExprContext
) : AbstractNode(parent, ctx), ExponentiationExpr

class MultiplicativeExprImpl(
  override val parent: Node,
  override val ctx: MultiplicativeExprContext
) : AbstractNode(parent, ctx), MultiplicativeExpr

class AdditiveExprImpl(override val parent: Node, override val ctx: AdditiveExprContext) :
  AbstractNode(parent, ctx), AdditiveExpr

class ComparisonExprImpl(override val parent: Node, override val ctx: ComparisonExprContext) :
  AbstractNode(parent, ctx), ComparisonExpr

class TypeTestExprImpl(override val parent: Node, override val ctx: TypeTestExprContext) :
  AbstractNode(parent, ctx), TypeTestExpr

class EqualityExprImpl(override val parent: Node, override val ctx: EqualityExprContext) :
  AbstractNode(parent, ctx), EqualityExpr

class LogicalAndExprImpl(override val parent: Node, override val ctx: LogicalAndExprContext) :
  AbstractNode(parent, ctx), LogicalAndExpr

class LogicalOrExprImpl(override val parent: Node, override val ctx: LogicalOrExprContext) :
  AbstractNode(parent, ctx), LogicalOrExpr

class PipeExprImpl(override val parent: Node, override val ctx: PipeExprContext) :
  AbstractNode(parent, ctx), PipeExpr

class NullCoalesceExprImpl(override val parent: Node, override val ctx: NullCoalesceExprContext) :
  AbstractNode(parent, ctx), NullCoalesceExpr

class IfExprImpl(override val parent: Node, override val ctx: IfExprContext) :
  AbstractNode(parent, ctx), IfExpr

class LetExprImpl(override val parent: Node, override val ctx: LetExprContext) :
  AbstractNode(parent, ctx), LetExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
}

class FunctionLiteralImpl(override val parent: Node, override val ctx: FunctionLiteralContext) :
  AbstractNode(parent, ctx), FunctionLiteral

class ParenthesizedExprImpl(override val parent: Node, override val ctx: ParenthesizedExprContext) :
  AbstractNode(parent, ctx), ParenthesizedExpr
