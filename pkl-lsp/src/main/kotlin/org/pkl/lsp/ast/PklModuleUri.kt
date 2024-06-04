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

import java.io.File
import java.net.URI
import org.antlr.v4.runtime.tree.ParseTree
import org.pkl.lsp.LSPUtil.firstInstanceOf
import org.pkl.lsp.PklVisitor
import org.pkl.lsp.Stdlib
import org.pkl.lsp.parseUriOrNull

class PklModuleUriImpl(override val parent: Node, override val ctx: ParseTree) :
  AbstractNode(parent, ctx), PklModuleUri {
  override val stringConstant: PklStringConstant by lazy {
    children.firstInstanceOf<PklStringConstant>()!!
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleUri(this)
  }

  override fun resolve(
    targetUri: String,
    moduleUri: String,
    sourceFile: File,
    enclosingModule: PklModule?
  ): PklModule? {
    // if `targetUri == "..."`, add enough context to make it resolvable on its own
    val effectiveTargetUri =
      when (targetUri) {
        "..." ->
          when {
            moduleUri == "..." -> ".../${sourceFile.name}"
            moduleUri.startsWith(".../") -> {
              val nextPathSegment = moduleUri.drop(4).substringBefore("/")
              if (nextPathSegment.isEmpty()) return null
              ".../$nextPathSegment/.."
            }
            else -> return null
          }
        else -> targetUri
      }

    return resolveVirtual(effectiveTargetUri, sourceFile, enclosingModule)?.mod
  }

  private fun resolveVirtual(
    targetUriStr: String,
    sourceFile: File,
    enclosingModule: PklModule?
  ): ResolvedModule? {

    val targetUri = parseUriOrNull(targetUriStr) ?: return null

    when (targetUri.scheme) {
      "pkl" ->
        return Stdlib.getModule(targetUri.schemeSpecificPart)?.let { ResolvedModule(it, it.uri) }
      // unsupported scheme
      else -> return null
    }
  }

  private data class ResolvedModule(val mod: PklModule?, val uri: URI)
}
