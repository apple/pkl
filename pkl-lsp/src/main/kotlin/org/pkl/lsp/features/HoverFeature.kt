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
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*
import org.pkl.lsp.resolvers.ResolveVisitors
import org.pkl.lsp.resolvers.Resolvers
import org.pkl.lsp.type.computeThisType

class HoverFeature(private val server: PklLSPServer) {

  fun onHover(params: HoverParams): CompletableFuture<Hover> {
    // return server.builder().runningBuild().thenApply { Hover() }
    fun run(mod: PklModule?): Hover? {
      if (mod == null) return null
      // val uri = URI(params.textDocument.uri)
      val line = params.position.line + 1
      val col = params.position.character + 1
      val hoverText = mod.findBySpan(line, col)?.let { resolveHover(it) } ?: return null
      server.logger().log("hover text: $hoverText")
      return Hover(MarkupContent("markdown", hoverText))
    }
    return server.builder().runningBuild().thenApply(::run)
  }

  private fun resolveHover(node: Node): String? {
    return when (node) {
      is PklProperty -> resolveProperty(node)?.toMarkdown()
      is PklUnqualifiedAccessExpr -> resolveUnqualifiedAccess(node)?.toMarkdown()
      is PklQualifiedAccessExpr -> resolveQualifiedAccess(node)?.toMarkdown()
      else -> null
    }
  }

  private fun resolveQualifiedAccess(node: PklQualifiedAccessExpr): Node? {
    val base = PklBaseModule.instance
    val visitor = ResolveVisitors.firstElementNamed(node.memberNameText, base)
    // TODO: check if receiver is PklModule
    return node.resolve(base, null, mapOf(), visitor)
  }

  private fun resolveUnqualifiedAccess(node: PklUnqualifiedAccessExpr): Node? {
    val base = PklBaseModule.instance
    val visitor = ResolveVisitors.firstElementNamed(node.memberNameText, base)
    return node.resolve(base, null, mapOf(), visitor)
  }

  private fun resolveProperty(prop: PklProperty): Node? {
    val base = PklBaseModule.instance
    val name = prop.name
    val visitor = ResolveVisitors.firstElementNamed(name, base)
    return when {
      prop.type != null -> visitor.result
      prop.isLocal -> visitor.result
      else -> {
        val receiverType = prop.computeThisType(base, mapOf())
        Resolvers.resolveQualifiedAccess(receiverType, true, base, visitor)
      }
    }
  }
}
