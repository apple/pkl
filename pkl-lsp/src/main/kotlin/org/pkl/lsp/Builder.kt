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
package org.pkl.lsp

import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.pkl.core.parser.LexParseException
import org.pkl.core.parser.Parser
import org.pkl.core.util.IoUtils
import org.pkl.lsp.cst.CstBuilder
import org.pkl.lsp.cst.ParseError
import org.pkl.lsp.cst.PklModule
import org.pkl.lsp.cst.Span

class Builder(private val server: PklLSPServer) {
  private var runningBuild: CompletableFuture<PklModule?> = CompletableFuture.supplyAsync(::noop)

  private val parser = Parser()

  fun requestBuild(file: URI) {
    val change = IoUtils.readString(file.toURL())
    requestBuild(file, change)
  }

  fun requestBuild(file: URI, change: String) {
    runningBuild = CompletableFuture.supplyAsync { build(file, change) }
  }

  private fun build(file: URI, change: String): PklModule? {
    try {
      server.logger().log("building $file")
      val moduleCtx = parser.parseModule(change)
      val cstBuilder = CstBuilder()
      val module = cstBuilder.visitModule(moduleCtx)
      makeDiagnostics(file, cstBuilder.errors())
      return module
    } catch (e: LexParseException) {
      server.logger().error("Error building $file: ${e.message}")
      makeDiagnostics(file, listOf(toParserError(e)))
      return null
    } catch (e: Exception) {
      server.logger().error("Error building $file: ${e.message}")
      return null
    }
  }

  private fun makeDiagnostics(file: URI, errors: List<ParseError>) {
    val diags =
      errors.map { err ->
        val msg = resolveErrorMessage(err.errorType)
        val diag = Diagnostic(LSPUtil.spanToRange(err.span), "$msg\n\n")
        diag.severity = DiagnosticSeverity.Error
        diag.source = "Pkl Language Server"
        server.logger().log("diagnostic: $msg at ${err.span}")
        diag
      }
    server.logger().log("Found ${diags.size} diagnostic errors for $file")
    val params = PublishDiagnosticsParams(file.toString(), diags)
    // Have to publish diagnostics even if there are no errors, so we clear previous problems
    server.client().publishDiagnostics(params)
  }

  companion object {
    private fun noop(): PklModule? {
      return null
    }

    private fun toParserError(ex: LexParseException): ParseError {
      val span = Span(ex.line, ex.column, ex.line, ex.column + ex.length)
      return ParseError(ex.message ?: "Parser error", span)
    }

    private fun resolveErrorMessage(key: String): String {
      val locale = Locale.getDefault()
      val bundle = ResourceBundle.getBundle("org.pkl.lsp.errors", locale)
      return if (bundle.containsKey(key)) bundle.getString(key) else key
    }
  }
}
