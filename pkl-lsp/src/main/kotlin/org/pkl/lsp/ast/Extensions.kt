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
