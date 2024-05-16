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

  val types: Map<String, Type>
  val methods: Map<String, ClassMethod>

  init {

    val types = mutableMapOf<String, Type>()
    val methods = mutableMapOf<String, ClassMethod>()

    for (member in baseModule.members) {
      when (member) {
        is Clazz ->
          when (val className = member.name) {
            // treat pkl.base#Class and pkl.base#TypeAlias as generic types even if not defined as
            // such in stdlib
            "Class",
            "TypeAlias" -> {
              val typeParameters =
                member.classHeader.typeParameterList?.typeParameters
                  ?: listOf(PklNodeFactory.createTypeParameter("Type"))
              // types[className] = Type.Class(member, listOf(), listOf(), typeParameters)
            }
          // else -> types[className] = Type.Class(member)
          }
        // is TypeAlias -> types[member.name] = Type.Alias.unchecked(member, listOf(), listOf())
        is ClassMethod -> methods[member.name] = member
        else -> {}
      }
    }

    this.types = types
    this.methods = methods
  }

  val pklVersion: Version
    get() = Release.current().version()

  val listConstructor: ClassMethod = method("List")
  val setConstructor: ClassMethod = method("Set")
  val mapConstructor: ClassMethod = method("Map")

  val regexConstructor: ClassMethod = method("Regex")

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

  private fun method(name: String): ClassMethod =
    methods[name]
    // The only known case where a non-optional pkl.base method or class can legitimately be missing
    // is when editing pkl.base in the Pkl project (e.g., pkl.base may not fully parse while being
    // edited).
    // However, a few users have reported the same problem, and presumably they weren't editing
    // pkl.base.
    // Since resolution and (to some extent) cause are unknown, throw an error (with some extra
    // info) for now.
      ?: throw AssertionError(
        "Cannot find stdlib method `base.$name`."
      )

  private fun classType(name: String): Type.Class =
    types[name] as Type.Class?
    // see comment for `method()`
      ?: throw AssertionError(
        "Cannot find stdlib class `base.$name`."
      )

  private fun aliasType(name: String): Type.Alias =
    types[name] as Type.Alias?
      ?: throw AssertionError(
        "Cannot find stdlib alias `base.$name`."
      )

  companion object {
    val instance: PklBaseModule by lazy { PklBaseModule() }
  }
}
