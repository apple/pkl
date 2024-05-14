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

import org.pkl.lsp.ast.Node
import org.pkl.lsp.ast.PklType
import org.pkl.lsp.type.TypeParameterBindings

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
object ResolveVisitors {}
