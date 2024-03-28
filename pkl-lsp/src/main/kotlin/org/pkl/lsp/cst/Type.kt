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

sealed class Type(override val span: Span) : PklNode(span) {
  data class Unknown(override val span: Span) : Type(span)

  data class Nothing(override val span: Span) : Type(span)

  data class Module(override val span: Span) : Type(span)

  data class StringConstant(val string: String, override val span: Span) : Type(span)

  data class Declared(val idents: List<Ident>, val args: List<Type>, override val span: Span) :
    Type(span) {
    init {
      for (it in args) it.parent = this
    }
  }

  data class Parenthesised(val type: Type, override val span: Span) : Type(span) {
    init {
      type.parent = this
    }
  }

  data class Nullable(val type: Type, override val span: Span) : Type(span) {
    init {
      type.parent = this
    }
  }

  data class Constrained(val type: Type, val exprs: List<Expr>, override val span: Span) :
    Type(span) {
    init {
      type.parent = this
      for (it in exprs) it.parent = this
    }
  }

  data class DefaultUnion(val type: Type, override val span: Span) : Type(span) {
    init {
      type.parent = this
    }
  }

  data class Union(val left: Type, val right: Type, override val span: Span) : Type(span) {
    init {
      left.parent = this
      right.parent = this
    }
  }

  data class Function(val args: List<Type>, val ret: Type, override val span: Span) : Type(span) {
    init {
      ret.parent = this
      for (it in args) it.parent = this
    }
  }
}

enum class Variance {
  IN,
  OUT
}

data class TypeParameter(val variance: Variance?, val ident: Ident, override val span: Span) :
  PklNode(span)
