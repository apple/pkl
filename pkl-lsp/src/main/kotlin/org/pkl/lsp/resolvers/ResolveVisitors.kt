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

import kotlin.math.min
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.*
import org.pkl.lsp.type.*
import org.pkl.lsp.unexpectedType

interface ResolveVisitor<R> {
  /**
   * Note: [element] may be of type [PklImport], which visitors need to `resolve()` on their own if
   * so desired.
   */
  fun visit(name: String, element: Node, bindings: TypeParameterBindings): Boolean

  val result: R

  /**
   * Overriding this property enables resolvers to efficiently filter visited elements to improve
   * performance. However, it does not guarantee that only elements named [exactName] will be
   * visited.
   */
  val exactName: String?
    get() = null
}

fun ResolveVisitor<*>.visitIfNotNull(
  name: String?,
  element: Node?,
  bindings: TypeParameterBindings
): Boolean = if (name != null && element != null) visit(name, element, bindings) else true

interface FlowTypingResolveVisitor<R> : ResolveVisitor<R> {
  /** Conveys the fact that element [name] is (not) equal to [constant]. */
  fun visitEqualsConstant(name: String, constant: Any?, isNegated: Boolean): Boolean

  /** Conveys the fact that element [name] does (not) have type [pklType] (is-a). */
  fun visitHasType(
    name: String,
    pklType: PklType,
    bindings: TypeParameterBindings,
    isNegated: Boolean
  ): Boolean
}

/** Collect and post-process resolve results produced by [Resolvers]. */
object ResolveVisitors {
  fun paramTypesOfFirstMethodNamed(
    expectedName: String,
    base: PklBaseModule,
    resolveTypeParamsInParamTypes: Boolean = true
  ): ResolveVisitor<List<Type>?> =
    object : ResolveVisitor<List<Type>?> {
      override fun visit(name: String, element: Node, bindings: TypeParameterBindings): Boolean {
        if (name != expectedName) return true

        when (element) {
          is PklMethod -> {
            val parameters = element.methodHeader.parameterList?.elements ?: return false
            val effectiveBindings = if (resolveTypeParamsInParamTypes) bindings else mapOf()
            result =
              parameters.map {
                it.typedIdentifier
                  ?.typeAnnotation
                  ?.type
                  .toType(base, effectiveBindings, !resolveTypeParamsInParamTypes)
              }
          }
        }

        return false
      }

      // null -> method not found
      override var result: List<Type>? = null

      override val exactName: String
        get() = expectedName
    }

  fun typeOfFirstElementNamed(
    elementName: String,
    argumentList: PklArgumentList?,
    base: PklBaseModule,
    isNullSafeAccess: Boolean,
    preserveUnboundTypeVars: Boolean
  ): FlowTypingResolveVisitor<Type> =
    object : FlowTypingResolveVisitor<Type> {
      var isNonNull = false
      val excludedTypes = mutableListOf<Type>()

      override fun visitEqualsConstant(name: String, constant: Any?, isNegated: Boolean): Boolean {
        if (name != elementName) return true

        if (constant == null && isNegated) isNonNull = true
        return true
      }

      override fun visitHasType(
        name: String,
        pklType: PklType,
        bindings: TypeParameterBindings,
        isNegated: Boolean
      ): Boolean {
        if (name != elementName) return true

        val type = pklType.toType(base, bindings, preserveUnboundTypeVars)

        if (isNegated) {
          excludedTypes.add(type)
          return true
        }

        result = computeResultType(type)
        return false
      }

      override fun visit(name: String, element: Node, bindings: TypeParameterBindings): Boolean {
        if (name != elementName) return true

        val type =
          when (element) {
            is PklImport ->
              element.resolve().computeResolvedImportType(base, bindings, preserveUnboundTypeVars)
            is PklTypeParameter -> bindings[element] ?: Type.Unknown
            is PklMethod -> computeMethodReturnType(element, bindings)
            is PklClass -> base.classType.withTypeArguments(Type.Class(element))
            is PklTypeAlias -> base.typeAliasType.withTypeArguments(Type.alias(element))
            is PklNavigableElement ->
              element.computeResolvedImportType(base, bindings, preserveUnboundTypeVars)
            is PklParameter -> {
              element.typedIdentifier?.computeResolvedImportType(
                base,
                bindings,
                preserveUnboundTypeVars
              )
                ?: unexpectedType(element)
            }
            else -> unexpectedType(element)
          }

        result = computeResultType(type)
        return false
      }

      /** Adjusts [type] based on additional known facts. */
      private fun computeResultType(type: Type): Type {
        val subtractedType = subtractExcludedTypes(type)
        return when {
          isNullSafeAccess -> subtractedType.nullable(base)
          isNonNull -> subtractedType.nonNull(base)
          else -> subtractedType
        }
      }

      // note: doesn't consider or carry over constraints
      private fun subtractExcludedTypes(type: Type): Type {
        if (excludedTypes.isEmpty()) return type

        return when (type) {
          is Type.Union -> {
            val excludeLeft = excludedTypes.any { type.leftType.isSubtypeOf(it, base) }
            val excludeRight = excludedTypes.any { type.rightType.isSubtypeOf(it, base) }
            when {
              excludeLeft && excludeRight -> Type.Nothing
              excludeLeft -> subtractExcludedTypes(type.rightType)
              excludeRight -> subtractExcludedTypes(type.leftType)
              else ->
                Type.Union.create(
                  subtractExcludedTypes(type.leftType),
                  subtractExcludedTypes(type.rightType),
                  base
                )
            }
          }
          else -> type
        }
      }

      private fun computeMethodReturnType(
        method: PklMethod,
        bindings: TypeParameterBindings
      ): Type {
        return when (method) {
          // infer return type of `base#Map()` from arguments (type signature is too weak)
          base.mapConstructor -> {
            val arguments = argumentList?.elements
            if (arguments == null || arguments.size < 2) {
              Type.Class(base.mapType.ctx)
            } else {
              var keyType = arguments[0].computeExprType(base, bindings)
              var valueType = arguments[1].computeExprType(base, bindings)
              for (i in 2 until arguments.size) {
                if (i % 2 == 0) {
                  keyType = Type.union(keyType, arguments[i].computeExprType(base, bindings), base)
                } else {
                  valueType =
                    Type.union(valueType, arguments[i].computeExprType(base, bindings), base)
                }
              }
              Type.Class(base.mapType.ctx, listOf(keyType, valueType))
            }
          }
          else -> {
            val typeParameterList = method.methodHeader.typeParameterList
            val allBindings =
              if (typeParameterList == null || typeParameterList.typeParameters.isEmpty()) {
                bindings
              } else {
                // try to infer method type parameters from method arguments
                val parameters = method.methodHeader.parameterList?.elements
                val arguments = argumentList?.elements
                if (parameters == null || arguments == null) bindings
                else {
                  val enhancedBindings = bindings.toMutableMap()
                  val parameterTypes =
                    parameters.map { it.type?.toType(base, bindings, true) ?: Type.Unknown }
                  val argumentTypes = arguments.map { it.computeExprType(base, bindings) }
                  for (i in 0 until min(parameterTypes.size, argumentTypes.size)) {
                    inferBindings(parameterTypes[i], argumentTypes, i, enhancedBindings)
                  }
                  enhancedBindings
                }
              }
            method.computeResolvedImportType(base, allBindings, preserveUnboundTypeVars)
          }
        }
      }

      override var result: Type = Type.Unknown

      override val exactName: String
        get() = elementName

      private fun inferBindings(
        declaredType: Type,
        computedTypes: List<Type>,
        index: Int,
        collector: MutableMap<PklTypeParameter, Type>
      ) {

        val computedType = computedTypes[index]

        when (declaredType) {
          is Type.Variable -> collector[declaredType.ctx] = computedType
          is Type.Class -> {
            when {
              declaredType.classEquals(base.varArgsType) -> {
                val unionType = Type.union(computedTypes.drop(index), base)
                inferBindings(declaredType.typeArguments[0], listOf(unionType), 0, collector)
              }
              else -> {
                val declaredTypeArgs = declaredType.typeArguments
                val computedTypeArgs = computedType.toClassType(base)?.typeArguments ?: return
                for (i in 0..min(declaredTypeArgs.lastIndex, computedTypeArgs.lastIndex)) {
                  inferBindings(declaredTypeArgs[i], computedTypeArgs, i, collector)
                }
              }
            }
          }
          is Type.Alias ->
            inferBindings(declaredType.aliasedType(base), computedTypes, index, collector)
          else -> {}
        }
      }
    }

  fun firstElementNamed(
    expectedName: String,
    base: PklBaseModule,
    resolveImports: Boolean = true
  ): ResolveVisitor<Node?> =
    object : ResolveVisitor<Node?> {
      override fun visit(name: String, element: Node, bindings: TypeParameterBindings): Boolean {
        if (name != expectedName) return true

        when {
          element is PklImport -> {
            result =
              if (resolveImports && !element.isGlob) {
                val resolved = element.resolve() as SimpleModuleResolutionResult
                resolved.resolved
              } else {
                element
              }
          }
          element is PklTypeParameter && bindings.contains(element) ->
            visit(name, toFirstDefinition(element, base, bindings), mapOf())
          element is PklNavigableElement -> result = element
          element is PklParameter -> result = element.typedIdentifier
          element is PklExpr -> return true
          else -> unexpectedType(element)
        }

        return false
      }

      override var result: Node? = null

      override val exactName: String
        get() = expectedName
    }

  @Suppress("unused")
  fun elementsNamed(
    expectedName: String,
    base: PklBaseModule,
    resolveImports: Boolean = true
  ): ResolveVisitor<List<Node>> =
    object : ResolveVisitor<List<Node>> {
      override fun visit(name: String, element: Node, bindings: TypeParameterBindings): Boolean {
        if (name != expectedName) return true

        when {
          element is PklImport -> {
            if (resolveImports) {
              result.addAll(element.resolveModules())
            } else {
              result.add(element)
            }
          }
          element is PklTypeParameter && bindings.contains(element) -> {
            for (definition in toDefinitions(element, base, bindings)) {
              visit(name, definition, mapOf())
            }
          }
          element is PklNavigableElement -> result.add(element)
          element is PklExpr -> return true
          else -> unexpectedType(element)
        }

        return true
      }

      override var result: MutableList<Node> = mutableListOf()

      override val exactName: String
        get() = expectedName
    }

  private fun toDefinitions(
    typeParameter: PklTypeParameter,
    base: PklBaseModule,
    bindings: TypeParameterBindings
  ): List<PklNavigableElement> {
    val type = bindings[typeParameter] ?: Type.Unknown
    return type.resolveToDefinitions(base)
  }

  fun toFirstDefinition(
    typeParameter: PklTypeParameter,
    base: PklBaseModule,
    bindings: TypeParameterBindings
  ): PklNavigableElement = toDefinitions(typeParameter, base, bindings)[0]
}

/**
 * Only visits the first encountered property/method with a given name. Used to enforce scoping
 * rules.
 */
fun <R> ResolveVisitor<R>.withoutShadowedElements(): ResolveVisitor<R> =
  object : ResolveVisitor<R> by this {
    private val visitedProperties = mutableSetOf<String>()
    private val visitedMethods = mutableSetOf<String>()

    override fun visit(name: String, element: Node, bindings: TypeParameterBindings): Boolean {
      return when (element) {
        is PklMethod ->
          if (visitedMethods.add(name)) {
            this@withoutShadowedElements.visit(name, element, bindings)
          } else true
        is PklExpr -> {
          // expression such as `<name> is Foo` doesn't shadow enclosing definition of <name>
          this@withoutShadowedElements.visit(name, element, bindings)
        }
        else ->
          if (visitedProperties.add(name)) {
            this@withoutShadowedElements.visit(name, element, bindings)
          } else true
      }
    }
  }
