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

import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.type.Type

val Clazz.supertype: PklType?
  get() = classHeader.extends

val Clazz.superclass: Clazz?
  get() {
    // TODO
    //    return when (val st = supertype) {
    //      is DeclaredPklType -> st.typeName.resolve() as? PklClass?
    //      is ModulePklType -> null // see PklClass.supermodule
    //      null ->
    //        when {
    //          isPklBaseAnyClass -> null
    //          else -> project.pklBaseModule.typedType.psi
    //        }
    //      else -> unexpectedType(st)
    //    }
    return null
  }

// Non-null when [this] extends a module (class).
// Ideally, [Clazz.superclass] would cover this case,
// but we don't have a common abstraction for Clazz and PklModule(Class),
// and it seems challenging to introduce one.
val Clazz.supermodule: PklModule?
  get() {
    return when (val st = supertype) {
      is DeclaredPklType -> st.name.resolve() as? PklModule?
      is ModulePklType -> enclosingModule
      else -> null
    }
  }

// TODO
fun TypeName.resolve(): Node? = TODO("not implemented")

fun SimpleTypeName.resolve(): Node? = TODO("not implemented")

fun Clazz.isSubclassOf(other: Clazz): Boolean {
  // optimization
  if (this === other) return true

  // optimization
  // TODO: check if this works
  if (!other.isAbstractOrOpen) return this == other

  var clazz: Clazz? = this
  while (clazz != null) {
    // TODO: check if this works
    if (clazz == other) return true
    if (clazz.supermodule != null) {
      return PklBaseModule.instance.moduleType.ctx.isSubclassOf(other)
    }
    clazz = clazz.superclass
  }
  return false
}

fun Clazz.isSubclassOf(other: PklModule): Boolean {
  // optimization
  if (!other.isAbstractOrOpen) return false

  var clazz = this
  var superclass = clazz.superclass
  while (superclass != null) {
    clazz = superclass
    superclass = superclass.superclass
  }
  var module = clazz.supermodule
  while (module != null) {
    // TODO: check if this works
    if (module == other) return true
    module = module.supermodule
  }
  return false
}

// TODO: actually escape the text
fun StringConstant.escapedText(): String? = this.value

fun TypeAlias.isRecursive(seen: MutableSet<TypeAlias>): Boolean =
  !seen.add(this) || type.isRecursive(seen)

private fun PklType?.isRecursive(seen: MutableSet<TypeAlias>): Boolean =
  when (this) {
    is DeclaredPklType -> {
      val resolved = name.resolve()
      resolved is TypeAlias && resolved.isRecursive(seen)
    }
    is NullablePklType -> type.isRecursive(seen)
    is DefaultUnionPklType -> type.isRecursive(seen)
    is UnionPklType -> leftType.isRecursive(seen) || rightType.isRecursive(seen)
    is ConstrainedPklType -> type.isRecursive(seen)
    is ParenthesizedPklType -> type.isRecursive(seen)
    else -> false
  }

val Node.isInPklBaseModule: Boolean
  get() = enclosingModule?.declaration?.moduleHeader?.qualifiedIdentifier?.fullName == "pkl.base"

interface TypeNameRenderer {
  fun render(name: TypeName, appendable: Appendable)

  fun render(type: Type.Class, appendable: Appendable)

  fun render(type: Type.Alias, appendable: Appendable)

  fun render(type: Type.Module, appendable: Appendable)
}

object DefaultTypeNameRenderer : TypeNameRenderer {
  override fun render(name: TypeName, appendable: Appendable) {
    appendable.append(name.simpleTypeName.identifier?.text)
  }

  override fun render(type: Type.Class, appendable: Appendable) {
    appendable.append(type.ctx.name)
  }

  override fun render(type: Type.Alias, appendable: Appendable) {
    appendable.append(type.ctx.name)
  }

  override fun render(type: Type.Module, appendable: Appendable) {
    appendable.append(type.referenceName)
  }
}
