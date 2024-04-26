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
import org.pkl.lsp.PklLSPServer

class HoverFeature(private val server: PklLSPServer) {

  fun onHover(params: HoverParams): CompletableFuture<Hover> {
    return server.builder().runningBuild().thenApply { Hover() }
    //    fun run(mod: Module?): Hover? {
    //      if (mod == null) return null
    //      val uri = URI(params.textDocument.uri)
    //      val line = params.position.line + 1
    //      val col = params.position.character + 1
    //      server.logger().log("received hover request at ($line - $col)")
    ////      val hoverText = findContext(mod, line, col)?.resolve() ?: return null
    ////      server.logger().log("hover text: $hoverText")
    ////      return Hover(MarkupContent("markdown", hoverText))
    //    }
    //    return server.builder().runningBuild().thenApply(::run)
  }

  sealed class HoverCtx {
    class Module(val name: String) : HoverCtx()

    class Clazz(val name: String, val parent: String?) : HoverCtx()

    class Prop(val name: String, val type: String?) : HoverCtx()

    fun resolve(): String =
      when (this) {
        is Module -> "**module** $name"
        is Clazz -> "**class** $name"
        is Prop -> "**$name**"
      }
  }
  //
  //  private fun findContext(mod: PklModule, line: Int, col: Int): HoverCtx? {
  //    // search module declaration
  //    val decl = mod.decl
  //    if (decl != null && decl.span.matches(line, col)) {
  //      if (decl.nameSpan != null && decl.nameSpan.matches(line, col)) {
  //        return HoverCtx.Module(decl.name!!.nameString)
  //      }
  //    }
  //
  //    for (entry in mod.entries) {
  //      if (entry.name.span.matches(line, col)) {
  //        return when (entry) {
  //          is Clazz -> HoverCtx.Clazz(entry.name.value, null)
  //          is ClassEntry -> HoverCtx.Prop(entry.name.value, null)
  //          else -> null
  //        }
  //      }
  //    }
  //    return null
  //  }
}
