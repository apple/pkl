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

import java.util.IdentityHashMap
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.*
import org.pkl.lsp.resolvers.ResolveVisitors
import org.pkl.lsp.resolvers.Resolvers

private val value = Any()

fun Node?.computeResolvedImportType(
  base: PklBaseModule,
  bindings: TypeParameterBindings,
  preserveUnboundTypeVars: Boolean = false,
  canInferExprBody: Boolean = true,
  cache: IdentityHashMap<Node, Any> = IdentityHashMap()
): Type {
  if (this == null) return Type.Unknown
  if (cache.containsKey(this)) return Type.Unknown
  cache[this] = value

  return when (this) {
    is PklModule -> Type.module(this, shortDisplayName)
    is PklClass -> Type.Class(this)
    is PklTypeAlias -> Type.alias(this)
    is PklMethod ->
      when {
        methodHeader.returnType != null ->
          methodHeader.returnType.toType(base, bindings, preserveUnboundTypeVars)
        else ->
          when {
            canInferExprBody && !isOverridable -> body.computeExprType(base, bindings)
            else -> Type.Unknown
          }
      }
    is PklProperty ->
      when {
        type != null -> type.toType(base, bindings, preserveUnboundTypeVars)
        else ->
          when {
            canInferExprBody && isLocal -> expr.computeExprType(base, bindings)
            isDefinition -> Type.Unknown
            else -> {
              val receiverType = computeThisType(base, bindings)
              val visitor =
                ResolveVisitors.typeOfFirstElementNamed(
                  name,
                  null,
                  base,
                  false,
                  preserveUnboundTypeVars
                )
              Resolvers.resolveQualifiedAccess(receiverType, true, base, visitor)
            }
          }
      }
    is PklMemberPredicate -> {
      val receiverClassType =
        computeThisType(base, bindings).toClassType(base) ?: return Type.Unknown
      val baseType =
        when {
          receiverClassType.classEquals(base.listingType) -> receiverClassType.typeArguments[0]
          receiverClassType.classEquals(base.mappingType) -> receiverClassType.typeArguments[1]
          else -> Type.Unknown
        }
      // flow typing support, e.g. `[[this is Person]] { ... }`
      val cond = conditionExpr ?: return baseType
      val visitor =
        ResolveVisitors.typeOfFirstElementNamed(
          "this",
          null,
          base,
          isNullSafeAccess = false,
          preserveUnboundTypeVars = false
        )
      if (
        Resolvers.visitSatisfiedCondition(cond, bindings, visitor) || visitor.result == Type.Unknown
      )
        baseType
      else visitor.result
    }
    is PklObjectEntry,
    is PklObjectElement -> {
      val receiverClassType =
        computeThisType(base, bindings).toClassType(base) ?: return Type.Unknown
      when {
        receiverClassType.classEquals(base.listingType) -> receiverClassType.typeArguments[0]
        receiverClassType.classEquals(base.mappingType) -> receiverClassType.typeArguments[1]
        else -> Type.Unknown
      }
    }
    is PklTypedIdentifier -> {
      val type = typeAnnotation?.type
      when {
        type != null -> type.toType(base, bindings, preserveUnboundTypeVars)
        else -> { // try to infer identifier type
          when (val identifierOwner = parent) {
            is PklLetExpr -> identifierOwner.varExpr.computeExprType(base, bindings)
            is PklForGenerator -> {
              val iterableType = identifierOwner.iterableExpr.computeExprType(base, bindings)
              val iterableClassType = iterableType.toClassType(base) ?: return Type.Unknown
              val keyValueVars = identifierOwner.parameters.mapNotNull { it.typedIdentifier }
              when {
                keyValueVars.size > 1 && keyValueVars[0] == this -> {
                  when {
                    iterableClassType.classEquals(base.intSeqType) -> base.intType
                    iterableClassType.classEquals(base.listType) ||
                      iterableClassType.classEquals(base.setType) ||
                      iterableClassType.classEquals(base.listingType) -> base.intType
                    iterableClassType.classEquals(base.mapType) ||
                      iterableClassType.classEquals(base.mappingType) ->
                      iterableClassType.typeArguments[0]
                    iterableClassType.isSubtypeOf(base.typedType, base) -> base.stringType
                    else -> Type.Unknown
                  }
                }
                else -> {
                  when {
                    iterableClassType.classEquals(base.intSeqType) -> base.intType
                    iterableClassType.classEquals(base.listType) ||
                      iterableClassType.classEquals(base.setType) ||
                      iterableClassType.classEquals(base.listingType) ->
                      iterableClassType.typeArguments[0]
                    iterableClassType.classEquals(base.mapType) ||
                      iterableClassType.classEquals(base.mappingType) ->
                      iterableClassType.typeArguments[1]
                    iterableClassType.isSubtypeOf(base.typedType, base) ->
                      Type.Unknown // could strengthen value type to union of property types
                    else -> Type.Unknown
                  }
                }
              }
            }
            is PklParameterList ->
              when (val parameterListOwner = identifierOwner.parent) {
                is PklFunctionLiteralExpr -> {
                  val functionType = parameterListOwner.inferExprTypeFromContext(base, bindings)
                  getFunctionParameterType(this, identifierOwner, functionType, base)
                }
                is PklObjectBody ->
                  when (val objectBodyOwner = parameterListOwner.parent) {
                    is PklExpr -> {
                      val functionType = objectBodyOwner.computeExprType(base, bindings)
                      getFunctionParameterType(this, identifierOwner, functionType, base)
                    }
                    is PklObjectBodyOwner -> {
                      @Suppress("BooleanLiteralArgument")
                      val functionType =
                        objectBodyOwner.computeResolvedImportType(
                          base,
                          bindings,
                          false,
                          false,
                          cache
                        )
                      getFunctionParameterType(this, identifierOwner, functionType, base)
                    }
                    else -> Type.Unknown
                  }
                else -> Type.Unknown
              }
            else -> Type.Unknown
          }
        }
      }
    }
    is PklTypeParameter -> Type.Unknown
    else -> Type.Unknown
  }
}

private fun getFunctionParameterType(
  parameter: PklTypedIdentifier,
  parameterList: PklParameterList,
  functionType: Type,
  base: PklBaseModule
): Type {

  return when (functionType) {
    // e.g., `(String) -> Int` or `Function1<String, Int>`
    is Type.Class -> {
      when {
        functionType.isFunctionType -> {
          val typedIdents = parameterList.elements.mapNotNull { it.typedIdentifier }
          val paramIndex = typedIdents.indexOf(parameter)
          val typeArguments = functionType.typeArguments
          if (paramIndex >= typeArguments.lastIndex) Type.Unknown else typeArguments[paramIndex]
        }
        else -> Type.Unknown
      }
    }
    is Type.Alias ->
      getFunctionParameterType(parameter, parameterList, functionType.unaliased(base), base)
    else -> Type.Unknown
  }
}
