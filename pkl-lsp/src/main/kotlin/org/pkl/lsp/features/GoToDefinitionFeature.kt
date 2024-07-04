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
package org.pkl.lsp.features

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.pkl.lsp.LSPUtil.toRange
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*

class GoToDefinitionFeature(override val server: PklLSPServer) : Feature(server) {

  fun onGoToDefinition(
    params: DefinitionParams
  ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
    fun run(mod: PklModule?): Either<MutableList<out Location>, MutableList<out LocationLink>> {
      if (mod == null) return Either.forLeft(mutableListOf())
      val line = params.position.line + 1
      val col = params.position.character + 1
      val location =
        mod.findBySpan(line, col)?.let { resolveDeclaration(it) }
          ?: return Either.forLeft(mutableListOf())
      return Either.forLeft(mutableListOf(location))
    }
    return server.builder().runningBuild(params.textDocument.uri).thenApply(::run)
  }

  private fun resolveDeclaration(node: Node): Location? {
    return when (node) {
      // is PklProperty -> resolveProperty(node)?.toLocation()
      is PklUnqualifiedAccessExpr -> resolveUnqualifiedAccess(node)?.toLocation()
      is PklQualifiedAccessExpr -> resolveQualifiedAccess(node)?.toLocation()
      is PklStringConstant ->
        when (val parent = node.parent) {
          is PklImportBase ->
            when (val res = parent.resolve()) {
              is SimpleModuleResolutionResult -> res.resolved?.toLocation()
              is GlobModuleResolutionResult -> null // TODO: globs
            }
          is PklModuleExtendsAmendsClause -> parent.moduleUri?.resolve()?.toLocation()
          else -> null
        }
      else -> null
    }
  }

  private fun Node.toLocation(): Location {
    val range = spanNoDocs().toRange()
    val uri = toURIString()
    return Location(uri, range)
  }

  // returns the span of this node without doc comments
  private fun Node.spanNoDocs(): Span {
    return children
      .find { it !is Terminal || it.type != TokenType.DocComment }
      ?.let { Span.from(it.span, span) }
      ?: span
  }
}
