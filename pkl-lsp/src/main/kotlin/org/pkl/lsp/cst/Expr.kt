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

sealed class Expr(override val span: Span) : PklNode(span) {
  data class This(override val span: Span) : Expr(span)

  data class Outer(override val span: Span) : Expr(span)

  data class Module(override val span: Span) : Expr(span)

  data class Null(override val span: Span) : Expr(span)

  data class BooleanLiteral(val b: Boolean, override val span: Span) : Expr(span)

  data class IntLiteral(val i: Long, override val span: Span) : Expr(span)

  data class FloatLiteral(val f: Double, override val span: Span) : Expr(span)

  data class ConstantString(val s: String, override val span: Span) : Expr(span)

  data class InterpolatedString(val exprs: List<Expr>, override val span: Span) : Expr(span) {
    init {
      for (it in exprs) it.parent = this
    }
  }

  data class Throw(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class Trace(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class Import(val uri: Ident, override val span: Span) : Expr(span)

  data class ImportGlob(val uri: Ident, override val span: Span) : Expr(span)

  data class Read(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class ReadGlob(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class ReadNull(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class UnqualifiedAccess(val ident: Ident, override val span: Span) : Expr(span)

  data class UnqualifiedMethodAccess(
    val ident: Ident,
    val args: List<Expr>,
    override val span: Span
  ) : Expr(span) {
    init {
      for (it in args) it.parent = this
    }
  }

  data class QualifiedAccess(
    val expr: Expr,
    val ident: Ident,
    val isNullable: Boolean,
    override val span: Span
  ) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class QualifiedMethodAccess(
    val expr: Expr,
    val ident: Ident,
    val isNullable: Boolean,
    val args: List<Expr>,
    override val span: Span
  ) : Expr(span) {
    init {
      expr.parent = this
      for (it in args) it.parent = this
    }
  }

  data class SuperAccess(val ident: Ident, override val span: Span) : Expr(span)

  data class SuperMethodAccess(val ident: Ident, val args: List<Expr>, override val span: Span) :
    Expr(span) {
    init {
      for (it in args) it.parent = this
    }
  }

  data class SuperSubscript(val arg: Expr, override val span: Span) : Expr(span) {
    init {
      arg.parent = this
    }
  }

  data class Subscript(val expr: Expr, val arg: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
      arg.parent = this
    }
  }

  data class If(val cond: Expr, val then: Expr, val els: Expr, override val span: Span) :
    Expr(span) {
    init {
      cond.parent = this
      then.parent = this
      els.parent = this
    }
  }

  data class Let(val param: Parameter, val binding: Expr, val expr: Expr, override val span: Span) :
    Expr(span) {
    init {
      param.parent = this
      binding.parent = this
      expr.parent = this
    }
  }

  data class FunctionLiteral(val args: List<Parameter>, val expr: Expr, override val span: Span) :
    Expr(span) {
    init {
      expr.parent = this
      for (it in args) it.parent = this
    }
  }

  data class Parenthesised(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class New(val type: Type?, val body: ObjectBody, override val span: Span) : Expr(span) {
    init {
      type?.parent = this
      body.parent = this
    }
  }

  data class Amends(val expr: Expr, val body: ObjectBody, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
      body.parent = this
    }
  }

  data class NonNull(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class UnaryMinus(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class LogicalNot(val expr: Expr, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class BinaryOp(val left: Expr, val right: Expr, val op: Operation, override val span: Span) :
    Expr(span) {
    init {
      left.parent = this
      right.parent = this
    }
  }

  data class TypeTest(val expr: Expr, val type: Type, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
    }
  }

  data class TypeCast(val expr: Expr, val type: Type, override val span: Span) : Expr(span) {
    init {
      expr.parent = this
      type.parent = this
    }
  }
}

enum class Operation {
  POW,
  MULT,
  DIV,
  INT_DIV,
  MOD,
  PLUS,
  MINUS,
  LT,
  GT,
  LTE,
  GTE,
  IS,
  AS,
  EQ_EQ,
  NOT_EQ,
  AND,
  OR,
  PIPE,
  NULL_COALESCE
}
