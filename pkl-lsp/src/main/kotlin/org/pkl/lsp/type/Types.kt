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
package org.pkl.lsp.type

import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.Clazz
import org.pkl.lsp.ast.PklModule
import org.pkl.lsp.ast.TypeAlias
import org.pkl.lsp.ast.TypeParameter
import org.pkl.lsp.resolvers.ResolveVisitor

sealed class Type(val constraints: List<ConstraintExpr> = listOf()) {

  open val hasConstraints: Boolean = constraints.isNotEmpty()

  abstract fun visitMembers(
    isProperty: Boolean,
    allowClasses: Boolean,
    base: PklBaseModule,
    visitor: ResolveVisitor<*>
  ): Boolean

  object Unknown : Type() {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean = true
  }

  object Nothing : Type() {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean = true
  }

  class Variable(val ctx: TypeParameter, constraints: List<ConstraintExpr> = listOf()) :
    Type(constraints) {

    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean = true
  }

  class Module
  private constructor(
    val ctx: PklModule,
    val referenceName: String,
    constraints: List<ConstraintExpr>
  ) : Type(constraints) {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean {
      return if (allowClasses) {
        ctx.cache.visitTypeDefsAndPropertiesOrMethods(isProperty, visitor)
      } else {
        ctx.cache.visitPropertiesOrMethods(isProperty, visitor)
      }
    }
  }

  class Class(
    val ctx: Clazz,
    specifiedTypeArguments: List<Type> = listOf(),
    constraints: List<ConstraintExpr> = listOf(),
    // enables the illusion that pkl.base#Class and pkl.base#TypeAlias
    // have a type parameter even though they currently don't
    private val typeParameters: List<TypeParameter> =
      ctx.classHeader.typeParameterList?.typeParameters ?: listOf()
  ) : Type(constraints) {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean {
      // return psi.cache.visitPropertiesOrMethods(isProperty, bindings, visitor)
      return true
    }
  }

  // from a typing perspective, type aliases are transparent, but from a tooling/abstraction
  // perspective, they aren't.
  // this raises questions such as how to define Object.equals() and whether/how to support other
  // forms of equality.
  class Alias
  private constructor(
    val ctx: TypeAlias,
    specifiedTypeArguments: List<Type>,
    constraints: List<ConstraintExpr>
  ) : Type(constraints) {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean {
      // return psi.body.toType(base, bindings).visitMembers(isProperty, allowClasses, base,
      // visitor)
      return true
    }
  }

  class StringLiteral(val value: String, constraints: List<ConstraintExpr> = listOf()) :
    Type(constraints) {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean {
      // return base.stringType.visitMembers(isProperty, allowClasses, base, visitor)
      return true
    }
  }

  class Union
  private constructor(val leftType: Type, val rightType: Type, constraints: List<ConstraintExpr>) :
    Type(constraints) {
    override fun visitMembers(
      isProperty: Boolean,
      allowClasses: Boolean,
      base: PklBaseModule,
      visitor: ResolveVisitor<*>
    ): Boolean {
      //      if (isUnionOfStringLiterals) {
      //        // visit pkl.base#String once rather than for every string literal
      //        // (unions of 70+ string literals have been seen in the wild)
      //        return base.stringType.visitMembers(isProperty, allowClasses, base, visitor)
      //      }
      //
      //      return leftType.visitMembers(isProperty, allowClasses, base, visitor) &&
      //        rightType.visitMembers(isProperty, allowClasses, base, visitor)
      return true
    }
  }
}

typealias TypeParameterBindings = Map<TypeParameter, Type>
