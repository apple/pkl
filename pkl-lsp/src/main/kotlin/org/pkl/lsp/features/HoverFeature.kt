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
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*

class HoverFeature(override val server: PklLSPServer) : Feature(server) {

  fun onHover(params: HoverParams): CompletableFuture<Hover> {
    fun run(mod: PklModule?): Hover? {
      if (mod == null) return null
      val line = params.position.line + 1
      val col = params.position.character + 1
      val hoverText = mod.findBySpan(line, col)?.let { resolveHover(it, line, col) } ?: return null
      return Hover(MarkupContent("markdown", hoverText))
    }
    return server.builder().runningBuild(params.textDocument.uri).thenApply(::run)
  }

  private fun resolveHover(node: Node, line: Int, col: Int): String? {
    return when (node) {
      is PklUnqualifiedAccessExpr -> resolveUnqualifiedAccess(node)?.toMarkdown()
      is PklQualifiedAccessExpr -> resolveQualifiedAccess(node)?.toMarkdown()
      is PklProperty -> resolveProperty(node)?.toMarkdown()
      is PklMethodHeader -> {
        val name = node.identifier
        // check if hovering over the method name
        if (name != null && name.span.matches(line, col)) {
          node.parent?.toMarkdown()
        } else null
      }
      is PklClassHeader -> {
        val name = node.identifier
        // check if hovering over the class name
        if (name != null && name.span.matches(line, col)) {
          // renders the class, which contains the doc comment
          node.parent?.toMarkdown()
        } else null
      }
      is PklModuleDeclaration -> {
        val name = node.moduleHeader?.qualifiedIdentifier
        // check if hovering over the module name
        if (name != null && name.span.matches(line, col)) {
          node.toMarkdown()
        } else null
      }
      else -> null
    }
  }
}
