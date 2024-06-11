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

import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.PklSingleLineStringLiteral
import org.pkl.lsp.ast.TokenType

fun inferResourceType(resourceUri: PklSingleLineStringLiteral, base: PklBaseModule): Type {
  // note that [resourceUri] could be an interpolated string.
  // we only operate on the first string part.
  val firstChild =
    resourceUri.parts.firstOrNull()
      ?: return Type.union(base.stringType, base.resourceType, base) // empty string -> bail out
  val stringChars = firstChild.text

  return when {
    firstChild.terminals.firstOrNull()?.type != TokenType.SLCharacters ->
      // starts with escape sequence or interpolation expression -> bail out
      Type.union(base.stringType, base.resourceType, base)
    stringChars.startsWith("env:", ignoreCase = true) -> base.stringType
    stringChars.startsWith("prop:", ignoreCase = true) -> base.stringType

    // `secret:` is a common user-defined resource reader -> guess that type is string
    stringChars.startsWith("secret:", ignoreCase = true) -> base.stringType
    stringChars.startsWith("file:", ignoreCase = true) -> base.resourceType
    stringChars.startsWith("http:", ignoreCase = true) -> base.resourceType
    stringChars.startsWith("https:", ignoreCase = true) -> base.resourceType
    !stringChars.contains(":") -> base.resourceType
    else ->
      // bail out
      Type.union(base.stringType, base.resourceType, base)
  }
}
