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
package org.pkl.lsp

import org.pkl.core.Release
import org.pkl.core.Version
import org.pkl.lsp.ast.*
import org.pkl.lsp.type.Type

class PklBaseModule private constructor() {

  private val baseModule: PklModule = Stdlib.baseModule()
  val ctx: PklModule = baseModule

  val types: Map<String, Type>
  val methods: Map<String, PklClassMethod>

  init {

    val types = mutableMapOf<String, Type>()
    val methods = mutableMapOf<String, PklClassMethod>()

    for (member in baseModule.members) {
      when (member) {
        is PklClass ->
          when (val className = member.name) {
            // treat pkl.base#Class and pkl.base#TypeAlias as generic types even if not defined as
            // such in stdlib
            "Class",
            "TypeAlias" -> {
              val typeParameters =
                member.classHeader.typeParameterList?.typeParameters
                  ?: listOf(PklNodeFactory.createTypeParameter("Type"))
              types[className] = Type.Class(member, listOf(), listOf(), typeParameters)
            }
            else -> types[className] = Type.Class(member)
          }
        is PklTypeAlias -> types[member.name] = Type.Alias.unchecked(member, listOf(), listOf())
        is PklClassMethod -> methods[member.name] = member
        else -> {}
      }
    }

    this.types = types
    this.methods = methods
  }

  val pklVersion: Version
    get() = Release.current().version()

  val listConstructor: PklClassMethod = method("List")
  val setConstructor: PklClassMethod = method("Set")
  val mapConstructor: PklClassMethod = method("Map")

  val regexConstructor: PklClassMethod = method("Regex")

  val anyType: Type.Class = classType("Any")
  val nullType: Type.Class = classType("Null")
  val booleanType: Type.Class = classType("Boolean")
  val numberType: Type.Class = classType("Number")
  val intType: Type.Class = classType("Int")
  val floatType: Type.Class = classType("Float")
  val durationType: Type.Class = classType("Duration")
  val dataSizeType: Type.Class = classType("DataSize")
  val stringType: Type.Class = classType("String")
  val pairType: Type.Class = classType("Pair")
  val listType: Type.Class = classType("List")
  val setType: Type.Class = classType("Set")
  val collectionType: Type.Class = classType("Collection")
  val mapType: Type.Class = classType("Map")
  val intSeqType: Type.Class = classType("IntSeq")
  val listingType: Type.Class = classType("Listing")
  val mappingType: Type.Class = classType("Mapping")
  val dynamicType: Type.Class = classType("Dynamic")
  val typedType: Type.Class = classType("Typed")
  val objectType: Type = classType("Object")
  val classType: Type.Class = classType("Class")
  val typeAliasType: Type.Class = classType("TypeAlias")
  val moduleType: Type.Class = classType("Module")
  val annotationType: Type.Class = classType("Annotation")
  val deprecatedType: Type.Class = classType("Deprecated")
  val sourceCodeType: Type.Class = classType("SourceCode")
  val functionType: Type.Class = classType("Function")
  val function0Type: Type.Class = classType("Function0")
  val function1Type: Type.Class = classType("Function1")
  val function2Type: Type.Class = classType("Function2")
  val function3Type: Type.Class = classType("Function3")
  val function4Type: Type.Class = classType("Function4")
  val function5Type: Type.Class = classType("Function5")
  val mixinType: Type.Alias = aliasType("Mixin")
  val varArgsType: Type.Class = classType("VarArgs")
  val resourceType: Type.Class = classType("Resource")
  val moduleInfoType: Type.Class = classType("ModuleInfo")
  val regexType: Type.Class = classType("Regex")
  val valueRenderer: Type.Class = classType("ValueRenderer")

  val comparableType: Type = aliasType("Comparable")

  val iterableType: Type by lazy {
    Type.union(collectionType, mapType, dynamicType, listingType, mappingType, intSeqType, this)
  }

  fun spreadType(enclosingObjectClassType: Type.Class): Type {
    return when {
      enclosingObjectClassType.classEquals(listingType) -> {
        val elemType = enclosingObjectClassType.typeArguments[0]
        if (elemType.isSubtypeOf(intType, this))
          Type.union(
            collectionType.withTypeArguments(elemType),
            listingType.withTypeArguments(elemType),
            dynamicType,
            intSeqType,
            this
          )
        else
          Type.union(
            collectionType.withTypeArguments(elemType),
            listingType.withTypeArguments(elemType),
            dynamicType,
            this
          )
      }
      enclosingObjectClassType.classEquals(mappingType) -> {
        val keyType = enclosingObjectClassType.typeArguments[0]
        val elemType = enclosingObjectClassType.typeArguments[1]
        Type.union(
          mapType.withTypeArguments(keyType, elemType),
          mappingType.withTypeArguments(keyType, elemType),
          dynamicType,
          this
        )
      }
      enclosingObjectClassType.classEquals(dynamicType) -> iterableType
      // any other resolvable `Type.Class` is a Typed, which can only receive spreads from dynamics.
      else -> dynamicType
    }
  }

  val additiveOperandType: Type by lazy {
    Type.union(stringType, numberType, durationType, dataSizeType, collectionType, mapType, this)
  }

  val multiplicativeOperandType: Type by lazy {
    Type.union(numberType, durationType, dataSizeType, this)
  }

  val subscriptableType: Type by lazy {
    Type.union(stringType, collectionType, mapType, listingType, mappingType, dynamicType, this)
  }

  private val intPsiCache by lazy { intType.ctx.cache }

  private val floatPsiCache by lazy { floatType.ctx.cache }

  val booleanXorMethod: PklClassMethod by lazy { booleanType.ctx.cache.methods.getValue("xor") }
  val booleanImpliesMethod: PklClassMethod by lazy {
    booleanType.ctx.cache.methods.getValue("implies")
  }

  val intIsPositiveProperty: PklClassProperty by lazy {
    intPsiCache.properties.getValue("isPositive")
  }
  val intIsNonZeroProperty: PklClassProperty by lazy {
    intPsiCache.properties.getValue("isNonZero")
  }
  val intIsOddProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("isOdd") }
  val intIsEvenProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("isEven") }
  val intNsProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("ns") }
  val intUsProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("us") }
  val intMsProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("ms") }
  val intSProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("s") }
  val intMinProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("min") }
  val intHProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("h") }
  val intDProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("d") }
  val intBProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("b") }
  val intKbProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("kb") }
  val intMbProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("mb") }
  val intGbProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("gb") }
  val intTbProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("tb") }
  val intPbProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("pb") }
  val intKibProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("kib") }
  val intMibProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("mib") }
  val intGibProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("gib") }
  val intTibProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("tib") }
  val intPibProperty: PklClassProperty by lazy { intPsiCache.properties.getValue("pib") }
  val intIsBetweenMethod: PklClassMethod by lazy { intPsiCache.methods.getValue("isBetween") }

  val floatIsPositiveProperty: PklClassProperty by lazy {
    floatPsiCache.properties.getValue("isPositive")
  }
  val floatIsNonZeroProperty: PklClassProperty by lazy {
    floatPsiCache.properties.getValue("isNonZero")
  }
  val floatIsFiniteProperty: PklClassProperty by lazy {
    floatPsiCache.properties.getValue("isFinite")
  }
  val floatIsInfiniteProperty: PklClassProperty by lazy {
    floatPsiCache.properties.getValue("isInfinite")
  }
  val floatIsNaNProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("isNaN") }
  val floatNsProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("ns") }
  val floatUsProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("us") }
  val floatMsProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("ms") }
  val floatSProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("s") }
  val floatMinProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("min") }
  val floatHProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("h") }
  val floatDProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("d") }
  val floatBProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("b") }
  val floatKbProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("kb") }
  val floatMbProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("mb") }
  val floatGbProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("gb") }
  val floatTbProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("tb") }
  val floatPbProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("pb") }
  val floatKibProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("kib") }
  val floatMibProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("mib") }
  val floatGibProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("gib") }
  val floatTibProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("tib") }
  val floatPibProperty: PklClassProperty by lazy { floatPsiCache.properties.getValue("pib") }
  val floatIsBetweenMethod: PklClassMethod by lazy { floatPsiCache.methods.getValue("isBetween") }

  val durationIsBetweenMethod: PklClassMethod by lazy {
    durationType.ctx.cache.methods.getValue("isBetween")
  }
  val durationIsPositiveProperty: PklClassProperty by lazy {
    durationType.ctx.cache.properties.getValue("isPositive")
  }

  val dataSizeIsPositiveProperty: PklClassProperty by lazy {
    dataSizeType.ctx.cache.properties.getValue("isPositive")
  }
  val dataSizeIsBinaryUnitProperty: PklClassProperty by lazy {
    dataSizeType.ctx.cache.properties.getValue("isBinaryUnit")
  }
  val dataSizeIsDecimalUnitProperty: PklClassProperty by lazy {
    dataSizeType.ctx.cache.properties.getValue("isDecimalUnit")
  }
  val dataSizeIsBetweenMethod: PklClassMethod by lazy {
    dataSizeType.ctx.cache.methods.getValue("isBetween")
  }

  val stringIsEmptyProperty: PklClassProperty by lazy {
    stringType.ctx.cache.properties.getValue("isEmpty")
  }
  val stringIsRegexProperty: PklClassProperty by lazy {
    stringType.ctx.cache.properties.getValue("isRegex")
  }
  val stringLengthProperty: PklClassProperty by lazy {
    stringType.ctx.cache.properties.getValue("length")
  }
  val stringMatchesMethod: PklClassMethod by lazy {
    stringType.ctx.cache.methods.getValue("matches")
  }
  val stringContainsMethod: PklClassMethod by lazy {
    stringType.ctx.cache.methods.getValue("contains")
  }
  val stringStartsWithMethod: PklClassMethod by lazy {
    stringType.ctx.cache.methods.getValue("startsWith")
  }
  val stringEndsWithMethod: PklClassMethod by lazy {
    stringType.ctx.cache.methods.getValue("endsWith")
  }

  val listIsDistinctProperty: PklClassProperty? by lazy {
    listType.ctx.cache.properties["isDistinct"]
  }
  val listIsDistinctByMethod: PklClassMethod? by lazy { listType.ctx.cache.methods["isDistinctBy"] }
  val listIsEmptyProperty: PklClassProperty by lazy {
    listType.ctx.cache.properties.getValue("isEmpty")
  }
  val listFoldMethod: PklClassMethod? by lazy { listType.ctx.cache.methods["fold"] }
  val listFoldIndexedMethod: PklClassMethod? by lazy { listType.ctx.cache.methods["foldIndexed"] }
  val listJoinMethod: PklClassMethod by lazy { listType.ctx.cache.methods.getValue("join") }
  val listLengthProperty: PklClassProperty by lazy {
    listType.ctx.cache.properties.getValue("length")
  }

  val listingDefaultProperty: PklClassProperty by lazy {
    listingType.ctx.cache.properties.getValue("default")
  }
  val listingToListMethod: PklClassMethod by lazy {
    listingType.ctx.cache.methods.getValue("toList")
  }

  val setIsEmptyProperty: PklClassProperty by lazy {
    setType.ctx.cache.properties.getValue("isEmpty")
  }
  val setLengthProperty: PklClassProperty by lazy {
    setType.ctx.cache.properties.getValue("length")
  }

  val mapContainsKeyMethod: PklClassMethod by lazy {
    mapType.ctx.cache.methods.getValue("containsKey")
  }
  val mapFoldMethod: PklClassMethod? by lazy { mapType.ctx.cache.methods["fold"] }
  val mapGetOrNullMethod: PklClassMethod by lazy { mapType.ctx.cache.methods.getValue("getOrNull") }
  val mapIsEmptyProperty: PklClassProperty by lazy {
    mapType.ctx.cache.properties.getValue("isEmpty")
  }
  val mapKeysProperty: PklClassProperty by lazy { mapType.ctx.cache.properties.getValue("keys") }
  val mapLengthProperty: PklClassProperty by lazy {
    mapType.ctx.cache.properties.getValue("length")
  }

  val mappingDefaultProperty: PklClassProperty by lazy {
    mappingType.ctx.cache.properties.getValue("default")
  }
  val mappingToMapMethod: PklClassMethod by lazy { mappingType.ctx.cache.methods.getValue("toMap") }

  private fun method(name: String): PklClassMethod =
    methods[name]
    // The only known case where a non-optional pkl.base method or class can legitimately be missing
    // is when editing pkl.base in the Pkl project (e.g., pkl.base may not fully parse while being
    // edited).
    // However, a few users have reported the same problem, and presumably they weren't editing
    // pkl.base.
    // Since resolution and (to some extent) cause are unknown, throw an error (with some extra
    // info) for now.
    ?: throw AssertionError("Cannot find stdlib method `base.$name`.")

  private fun classType(name: String): Type.Class =
    types[name] as Type.Class?
    // see comment for `method()`
    ?: throw AssertionError("Cannot find stdlib class `base.$name`.")

  private fun aliasType(name: String): Type.Alias =
    types[name] as Type.Alias? ?: throw AssertionError("Cannot find stdlib alias `base.$name`.")

  companion object {
    val instance: PklBaseModule by lazy { PklBaseModule() }
  }
}
