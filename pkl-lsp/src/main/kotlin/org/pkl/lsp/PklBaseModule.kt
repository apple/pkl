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

  companion object {
    val instance: PklBaseModule by lazy { PklBaseModule() }
  }
}
