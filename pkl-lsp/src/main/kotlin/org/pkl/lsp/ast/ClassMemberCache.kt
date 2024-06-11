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
package org.pkl.lsp.ast

import org.pkl.lsp.resolvers.ResolveVisitor
import org.pkl.lsp.resolvers.visitIfNotNull
import org.pkl.lsp.type.TypeParameterBindings

/** Caches non-local, i.e., externally accessible, members of a class. */
class ClassMemberCache(
  val methods: Map<String, PklClassMethod>,
  /** Property definitions */
  val properties: Map<String, PklClassProperty>,
  /**
   * The leaf-most properties, may or may not have a type annotation.
   *
   * A child that overrides a parent without a type annotation is a "leaf property".
   */
  val leafProperties: Map<String, PklClassProperty>,

  // cache invalidation is hard (and I don't know how exactly it works in intellij).
  // let's play it safe and make a class cache depend on the class' enclosing module and its
  // superclass cache's dependencies.
  // this can be revisited if performance issues arise.
  val dependencies: List<Any>
) {

  fun visitPropertiesOrMethods(
    isProperty: Boolean,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<*>
  ): Boolean {
    return if (isProperty) doVisit(properties, bindings, visitor)
    else doVisit(methods, bindings, visitor)
  }

  private fun doVisit(
    members: Map<String, Node>,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<*>
  ): Boolean {

    val exactName = visitor.exactName
    if (exactName != null) {
      return visitor.visitIfNotNull(exactName, members[exactName], bindings)
    }

    for ((name, member) in members) {
      if (!visitor.visit(name, member, bindings)) return false
    }

    return true
  }

  companion object {
    fun create(clazz: PklClass): ClassMemberCache {
      val properties = mutableMapOf<String, PklClassProperty>()
      val leafProperties = mutableMapOf<String, PklClassProperty>()
      val methods = mutableMapOf<String, PklClassMethod>()
      val dependencies = mutableListOf<Any>(clazz)

      clazz.superclass?.let { superclass ->
        val superclassCache = superclass.cache
        properties.putAll(superclassCache.properties)
        leafProperties.putAll(superclassCache.leafProperties)
        methods.putAll(superclassCache.methods)
        dependencies.addAll(superclassCache.dependencies)
      }

      clazz.supermodule?.let { supermodule ->
        val supermoduleCache = supermodule.cache
        properties.putAll(supermoduleCache.properties)
        leafProperties.putAll(supermoduleCache.leafProperties)
        methods.putAll(supermoduleCache.methods)
        dependencies.addAll(supermoduleCache.dependencies)
      }

      clazz.enclosingModule?.let { dependencies.add(it) }

      val body = clazz.classBody
      if (body != null) {
        for (member in body.members) {
          when (member) {
            is PklClassMethod -> {
              if (!member.isLocal) {
                member.name.let { methods[it] = member }
              }
            }
            is PklClassProperty -> {
              if (!member.isLocal) {
                val name = member.name
                // record [member] if it (re)defines a property;
                // don't record [member] if it amends/overrides a property defined in a superclass.
                if (member.typeAnnotation?.type != null || properties[name] == null) {
                  properties[name] = member
                }
                leafProperties[name] = member
              }
            }
          }
        }
      }

      return ClassMemberCache(methods, properties, leafProperties, dependencies)
    }
  }
}
