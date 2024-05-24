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
package org.pkl.lsp.resolvers

import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.*
import org.pkl.lsp.type.Type
import org.pkl.lsp.type.TypeParameterBindings

object Resolvers {
  enum class LookupMode {
    NONE,
    LEXICAL,
    IMPLICIT_THIS,
    BASE
  }
  /** Note: For resolving [PklAccessExpr], use [PklAccessExpr.resolve] instead. */
  fun <R> resolveUnqualifiedAccess(
    position: Node,
    // optionally provide the type of `this` at [position]
    // to avoid its recomputation in case it is needed
    thisType: Type?,
    isProperty: Boolean,
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>
  ): R {

    return resolveUnqualifiedAccess(position, thisType, isProperty, true, base, bindings, visitor)
  }

  fun <R> resolveUnqualifiedAccess(
    position: Node,
    // optionally provide the type of `this` at [position]
    // to avoid its recomputation in case it is needed
    thisType: Type?,
    isProperty: Boolean,
    allowClasses: Boolean,
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>
  ): R {

    return if (isProperty) {
      resolveUnqualifiedVariableAccess(position, thisType, base, bindings, allowClasses, visitor)
        .first
    } else {
      resolveUnqualifiedMethodAccess(position, thisType, base, bindings, visitor).first
    }
  }

  /** Note: For resolving [AccessExpr], use [AccessExpr.resolve] instead. */
  fun <R> resolveQualifiedAccess(
    receiverType: Type,
    isProperty: Boolean,
    base: PklBaseModule,
    visitor: ResolveVisitor<R>
  ): R {

    receiverType.visitMembers(isProperty, allowClasses = true, base, visitor)
    return visitor.result
  }

  private fun <R> resolveUnqualifiedVariableAccess(
    position: Node,
    thisType: Type?,
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    allowClasses: Boolean,
    visitor: ResolveVisitor<R>
  ): Pair<R, LookupMode> {
    TODO("implement")
  }

  private fun <R> resolveUnqualifiedMethodAccess(
    position: Node,
    thisType: Type?,
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>
  ): Pair<R, LookupMode> {
    TODO("implement")
  }

  /** Propagates flow typing information from a satisfied boolean expression. */
  fun visitSatisfiedCondition(
    expr: PklExpr,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<*>
  ): Boolean {
    if (visitor !is FlowTypingResolveVisitor<*>) return true

    return when (expr) {
      // foo is Bar
      is PklTypeTestExpr -> {
        val leftExpr = expr.expr
        if (expr.operator == TypeTestOperator.AS) {
          true
        } else if (leftExpr is PklUnqualifiedAccessExpr && leftExpr.isPropertyAccess) {
          visitor.visitHasType(leftExpr.memberNameText, expr.type, bindings, false)
        } else if (leftExpr is PklThisExpr) {
          visitor.visitHasType(leftExpr.text, expr.type, bindings, false)
        } else true
      }
      // foo != null, null != foo
      is PklEqualityExpr -> {
        val leftExpr = expr.leftExpr
        val rightExpr = expr.rightExpr
        if (expr.operator.type == TokenType.NOT_EQUAL) {
          if (
            leftExpr is PklUnqualifiedAccessExpr &&
              leftExpr.isPropertyAccess &&
              expr.rightExpr is PklNullLiteralExpr
          ) {
            visitor.visitEqualsConstant(leftExpr.memberNameText, null, true)
          } else if (
            rightExpr is PklUnqualifiedAccessExpr &&
              rightExpr.isPropertyAccess &&
              expr.leftExpr is PklNullLiteralExpr
          ) {
            visitor.visitEqualsConstant(rightExpr.memberNameText, null, true)
          } else true
        } else true
      }
      // leftExpr && rightExpr
      is PklLogicalAndExpr -> {
        // Go right to left, effectively treating `rightExpr` as inner scope.
        // This has the following effect on type resolution
        // (note that `is` terminates resolution):
        // foo is Foo && foo is Bar -> Bar
        // foo is Foo? && foo != null -> Foo
        // foo != null && foo is Foo? -> Foo?
        // This should be good enough for now.
        // As long Pkl doesn't have interface types,
        // there is no good reason to write `foo is Foo && foo is Bar`,
        // and use cases for `foo != null && foo is Foo?`
        // (or `if (foo != null) if (foo is Foo?)`) are limited.
        val rightExpr = expr.rightExpr
        (visitSatisfiedCondition(rightExpr, bindings, visitor)) &&
          visitSatisfiedCondition(expr.leftExpr, bindings, visitor)
      }
      is PklLogicalNotExpr -> {
        val childExpr = expr.expr
        visitUnsatisfiedCondition(childExpr, bindings, visitor)
      }
      is PklParenthesizedExpr -> {
        val childExpr = expr.expr
        childExpr == null || visitSatisfiedCondition(childExpr, bindings, visitor)
      }
      else -> true
    }
  }

  /** Propagates flow typing information from an unsatisfied boolean expression. */
  private fun visitUnsatisfiedCondition(
    expr: PklExpr,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<*>
  ): Boolean {
    if (visitor !is FlowTypingResolveVisitor<*>) return true

    return when (expr) {
      // foo is Bar -negated-> !(foo is Bar)
      is PklTypeTestExpr -> {
        val leftExpr = expr.expr
        if (leftExpr is PklUnqualifiedAccessExpr && leftExpr.isPropertyAccess) {
          visitor.visitHasType(leftExpr.memberNameText, expr.type, bindings, true)
        } else if (leftExpr is PklThisExpr) {
          visitor.visitHasType(leftExpr.text, expr.type, bindings, true)
        } else true
      }
      // foo == null -negated-> foo != null
      // null == foo -negated-> null != foo
      is PklEqualityExpr -> {
        val leftExpr = expr.leftExpr
        val rightExpr = expr.rightExpr
        if (expr.operator.type == TokenType.EQUAL) {
          if (
            leftExpr is PklUnqualifiedAccessExpr &&
              leftExpr.isPropertyAccess &&
              expr.rightExpr is PklNullLiteralExpr
          ) {
            visitor.visitEqualsConstant(leftExpr.memberNameText, null, true)
          } else if (
            rightExpr is PklUnqualifiedAccessExpr &&
              rightExpr.isPropertyAccess &&
              expr.leftExpr is PklNullLiteralExpr
          ) {
            visitor.visitEqualsConstant(rightExpr.memberNameText, null, true)
          } else true
        } else true
      }
      // leftExpr || rightExpr -negated-> !leftExpr && !rightExpr
      is PklLogicalOrExpr -> {
        val rightExpr = expr.rightExpr
        (visitUnsatisfiedCondition(rightExpr, bindings, visitor)) &&
          visitUnsatisfiedCondition(expr.leftExpr, bindings, visitor)
      }
      // !expr -negated-> expr
      is PklLogicalNotExpr -> {
        val childExpr = expr.expr
        visitSatisfiedCondition(childExpr, bindings, visitor)
      }
      // (expr) -negated-> !expr
      is PklParenthesizedExpr -> {
        val childExpr = expr.expr
        childExpr == null || visitUnsatisfiedCondition(childExpr, bindings, visitor)
      }
      else -> true
    }
  }
}
