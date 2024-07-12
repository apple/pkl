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

import java.util.*
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.*
import org.pkl.lsp.resolvers.ResolveVisitors

private val cache: IdentityHashMap<Node, Type> = IdentityHashMap()

fun Node?.computeExprType(base: PklBaseModule, bindings: TypeParameterBindings): Type {
  return when {
    this == null || this !is PklExpr -> Type.Unknown
    bindings.isEmpty() -> {
      val type = cache[this]
      if (type != null) {
        type
      } else {
        val typ = doComputeExprType(base, bindings)
        cache[this] = typ
        typ
      }
    }
    else -> doComputeExprType(base, bindings)
  }
}

private fun Node.doComputeExprType(base: PklBaseModule, bindings: TypeParameterBindings): Type {
  return when (this) {
    is PklUnqualifiedAccessExpr -> {
      val visitor =
        ResolveVisitors.typeOfFirstElementNamed(
          memberNameText,
          argumentList,
          base,
          isNullSafeAccess,
          false
        )
      resolve(base, null, bindings, visitor)
    }
    is PklQualifiedAccessExpr -> {
      val visitor =
        ResolveVisitors.typeOfFirstElementNamed(
          memberNameText,
          argumentList,
          base,
          isNullSafeAccess,
          false
        )
      resolve(base, null, bindings, visitor)
    }
    is PklSuperAccessExpr -> {
      val visitor =
        ResolveVisitors.typeOfFirstElementNamed(
          memberNameText,
          argumentList,
          base,
          isNullSafeAccess,
          false
        )
      resolve(base, null, bindings, visitor)
    }
    is PklTrueLiteralExpr,
    is PklFalseLiteralExpr -> base.booleanType
    is PklSingleLineStringLiteral -> {
      val unescaped = escapedText()
      if (unescaped == null) base.stringType else Type.StringLiteral(unescaped)
    }
    is PklMultiLineStringLiteral -> {
      val unescaped = escapedText()
      if (unescaped == null) base.stringType else Type.StringLiteral(unescaped)
    }
    is PklNullLiteralExpr -> base.nullType
    is PklIntLiteralExpr -> base.intType
    is PklFloatLiteralExpr -> base.floatType

    // inferring Listing/Mapping type parameters from elements/entries is tricky
    // because the latter are in turn inferred from Listing/Mapping types (e.g., in PklNewExpr)
    is PklAmendExpr -> parentExpr.computeExprType(base, bindings).amended(base)
    is PklNewExpr ->
      (type?.toType(base, bindings) ?: inferExprTypeFromContext(base, bindings)).instantiated(base)
    is PklThisExpr -> computeThisType(base, bindings)
    is PklOuterExpr -> Type.Unknown // TODO
    is PklSubscriptExpr -> {
      val receiverType = leftExpr.computeExprType(base, bindings)
      doComputeSubscriptExprType(receiverType, base)
    }
    is PklSuperSubscriptExpr -> {
      val receiverType = computeThisType(base, bindings)
      doComputeSubscriptExprType(receiverType, base)
    }
    is PklEqualityExpr -> base.booleanType
    is PklComparisonExpr -> base.booleanType
    is PklLogicalAndExpr -> base.booleanType
    is PklLogicalOrExpr -> base.booleanType
    is PklLogicalNotExpr -> base.booleanType
    is PklTypeTestExpr ->
      if (operator == TypeTestOperator.IS) {
        base.booleanType
      } else {
        type.toType(base, bindings)
      }
    is PklModuleExpr -> enclosingModule?.computeResolvedImportType(base, mapOf()) ?: Type.Unknown
    is PklUnaryMinusExpr -> {
      when (expr.computeExprType(base, bindings)) {
        base.intType -> base.intType
        base.booleanType -> base.booleanType
        else -> Type.Unknown
      }
    }
    is PklAdditiveExpr -> {
      val leftType = leftExpr.computeExprType(base, bindings)
      val rightType = rightExpr.computeExprType(base, bindings)
      val op = operator.type
      when (leftType) {
        base.intType ->
          when (rightType) {
            base.intType -> base.intType
            base.booleanType -> base.booleanType
            else -> Type.Unknown
          }
        base.floatType ->
          when (rightType) {
            base.intType,
            base.floatType -> base.floatType
            else -> Type.Unknown
          }
        base.stringType ->
          if (op == TokenType.PLUS) {
            when (rightType) {
              base.stringType,
              is Type.StringLiteral -> base.stringType
              else -> Type.Unknown
            }
          } else Type.Unknown
        is Type.StringLiteral ->
          if (op == TokenType.PLUS) {
            when (rightType) {
              base.stringType -> base.stringType
              is Type.StringLiteral -> Type.StringLiteral(leftType.value + rightType.value)
              else -> Type.Unknown
            }
          } else Type.Unknown
        Type.Unknown ->
          // could be more aggressive here and try to infer the result type from the right type
          // (e.g., unknown + float = float)
          Type.Unknown
        else -> {
          val leftClassType = leftType.toClassType(base) ?: return Type.Unknown
          val rightClassType = rightType.toClassType(base) ?: return Type.Unknown
          when {
            leftClassType.classEquals(base.listType) ->
              if (op == TokenType.PLUS) {
                when {
                  rightClassType.classEquals(base.listType) ||
                    rightClassType.classEquals(base.setType) -> {
                    val typeArgs =
                      Type.union(
                        leftClassType.typeArguments[0],
                        rightClassType.typeArguments[0],
                        base
                      )
                    base.listType.withTypeArguments(typeArgs)
                  }
                  else -> Type.Unknown
                }
              } else Type.Unknown
            leftClassType.classEquals(base.setType) ->
              if (op == TokenType.PLUS) {
                when {
                  rightClassType.classEquals(base.listType) ||
                    rightClassType.classEquals(base.setType) -> {
                    val typeArgs =
                      Type.union(
                        leftClassType.typeArguments[0],
                        rightClassType.typeArguments[0],
                        base
                      )
                    base.setType.withTypeArguments(typeArgs)
                  }
                  else -> Type.Unknown
                }
              } else Type.Unknown
            leftClassType.classEquals(base.mapType) ->
              if (op == TokenType.PLUS) {
                when {
                  rightClassType.classEquals(base.mapType) -> {
                    val keyTypeArgs =
                      Type.union(
                        leftClassType.typeArguments[0],
                        rightClassType.typeArguments[0],
                        base
                      )
                    val valueTypeArgs =
                      Type.union(
                        leftClassType.typeArguments[1],
                        rightClassType.typeArguments[1],
                        base
                      )
                    base.mapType.withTypeArguments(keyTypeArgs, valueTypeArgs)
                  }
                  else -> Type.Unknown
                }
              } else Type.Unknown
            else -> Type.Unknown
          }
        }
      }
    }
    is PklMultiplicativeExpr -> {
      val leftType = leftExpr.computeExprType(base, bindings)
      val rightType = rightExpr.computeExprType(base, bindings)
      when (operator.type) {
        TokenType.STAR ->
          when (leftType) {
            base.intType ->
              when (rightType) {
                base.intType -> base.intType
                base.floatType -> base.floatType
                else -> Type.Unknown
              }
            base.floatType ->
              when (rightType) {
                base.intType,
                base.floatType,
                Type.Unknown -> base.floatType
                else -> Type.Unknown
              }
            Type.Unknown -> {
              when (rightType) {
                base.floatType -> base.floatType
                else -> Type.Unknown
              }
            }
            else -> Type.Unknown
          }
        TokenType.DIV ->
          when (leftType) {
            base.intType,
            base.floatType,
            Type.Unknown ->
              when (rightType) {
                base.intType,
                base.floatType,
                Type.Unknown -> base.floatType
                else -> Type.Unknown
              }
            else -> Type.Unknown
          }
        TokenType.INT_DIV ->
          when (leftType) {
            base.intType,
            base.floatType,
            Type.Unknown ->
              when (rightType) {
                base.intType,
                base.floatType,
                Type.Unknown -> base.intType
                else -> Type.Unknown
              }
            else -> Type.Unknown
          }
        TokenType.MOD ->
          when (leftType) {
            base.intType ->
              when (rightType) {
                base.intType -> base.intType
                base.floatType -> base.floatType
                else -> Type.Unknown
              }
            base.floatType ->
              when (rightType) {
                base.intType,
                base.floatType,
                Type.Unknown -> base.floatType
                else -> Type.Unknown
              }
            Type.Unknown ->
              when (rightType) {
                base.floatType -> base.floatType
                else -> Type.Unknown
              }
            else -> Type.Unknown
          }
        else -> {
          // rdar://74188588 (not sure how this can happen)
          // logger.error("Unexpected multiplicative operator: ${operator.elementType}")
          Type.Unknown
        }
      }
    }
    is PklExponentiationExpr -> base.numberType
    is PklLetExpr -> bodyExpr.computeExprType(base, bindings)
    is PklThrowExpr -> Type.Nothing
    is PklTraceExpr -> expr.computeExprType(base, bindings)
    is PklImportExpr -> resolve().computeResolvedImportType(base, mapOf(), false)
    is PklReadExpr -> {
      val result =
        when (val resourceUriExpr = expr) {
          is PklSingleLineStringLiteral -> inferResourceType(resourceUriExpr, base)
          // support `read("env:" + ...)`
          is PklAdditiveExpr -> {
            when (val leftExpr = resourceUriExpr.leftExpr) {
              is PklSingleLineStringLiteral -> inferResourceType(leftExpr, base)
              else -> Type.union(base.stringType, base.resourceType, base)
            }
          }
          else -> Type.union(base.stringType, base.resourceType, base)
        }
      if (isNullable) result.nullable(base)
      else if (isGlob) base.mappingType.withTypeArguments(base.stringType, result) else result
    }
    is PklIfExpr ->
      Type.union(
        thenExpr.computeExprType(base, bindings),
        elseExpr.computeExprType(base, bindings),
        base
      )
    is PklNullCoalesceExpr ->
      Type.union(
        leftExpr.computeExprType(base, bindings).nonNull(base),
        rightExpr.computeExprType(base, bindings),
        base
      )
    is PklNonNullExpr -> expr.computeExprType(base, bindings).nonNull(base)
    is PklPipeExpr -> {
      val rightType = rightExpr.computeExprType(base, bindings)
      val classType = rightType.toClassType(base)
      when {
        classType != null && classType.isFunctionType -> classType.typeArguments.last()
        rightType == Type.Unknown -> Type.Unknown
        else -> Type.Unknown
      }
    }
    is PklFunctionLiteralExpr -> {
      val parameterTypes = parameterList.elements.map { it.type.toType(base, bindings) }
      val returnType = expr.computeExprType(base, bindings)
      when (parameterTypes.size) {
        0 -> base.function0Type.withTypeArguments(parameterTypes + returnType)
        1 -> base.function1Type.withTypeArguments(parameterTypes + returnType)
        2 -> base.function2Type.withTypeArguments(parameterTypes + returnType)
        3 -> base.function3Type.withTypeArguments(parameterTypes + returnType)
        4 -> base.function4Type.withTypeArguments(parameterTypes + returnType)
        5 -> base.function5Type.withTypeArguments(parameterTypes + returnType)
        else ->
          base.functionType.withTypeArguments(
            listOf(returnType)
          ) // approximation (invalid Pkl code)
      }
    }
    is PklParenthesizedExpr -> expr.computeExprType(base, bindings)
    else -> Type.Unknown
  }
}

private fun doComputeSubscriptExprType(receiverType: Type, base: PklBaseModule) =
  when (receiverType) {
    is Type.StringLiteral -> base.stringType
    else -> {
      val receiverClassType = receiverType.toClassType(base)
      when {
        receiverClassType == null -> Type.Unknown
        receiverClassType.classEquals(base.stringType) -> base.stringType
        receiverClassType.classEquals(base.listType) -> receiverClassType.typeArguments[0]
        receiverClassType.classEquals(base.setType) -> receiverClassType.typeArguments[0]
        receiverClassType.classEquals(base.listingType) -> receiverClassType.typeArguments[0]
        receiverClassType.classEquals(base.mapType) -> receiverClassType.typeArguments[1]
        receiverClassType.classEquals(base.mappingType) -> receiverClassType.typeArguments[1]
        else -> Type.Unknown
      }
    }
  }
