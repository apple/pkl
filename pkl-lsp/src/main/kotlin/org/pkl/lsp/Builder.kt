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
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.pkl.core.parser.LexParseException
import org.pkl.core.parser.Parser
import org.pkl.core.util.IoUtils
import org.pkl.lsp.LSPUtil.toRange
import org.pkl.lsp.analyzers.Analyzer
import org.pkl.lsp.analyzers.AnnotationAnalyzer
import org.pkl.lsp.analyzers.ModifierAnalyzer
import org.pkl.lsp.analyzers.PklDiagnostic
import org.pkl.lsp.ast.Node
import org.pkl.lsp.ast.PklModule
import org.pkl.lsp.ast.PklModuleImpl
import org.pkl.lsp.ast.Span

class Builder(private val server: PklLSPServer) {
  private val runningBuild: MutableMap<String, CompletableFuture<PklModule?>> = mutableMapOf()
  private val successfulBuilds: MutableMap<String, PklModule> = mutableMapOf()

  private val parser = Parser()

  private val analyzers: List<Analyzer> =
    listOf(ModifierAnalyzer(server), AnnotationAnalyzer(server))

  fun runningBuild(uri: String): CompletableFuture<PklModule?> =
    runningBuild[uri] ?: CompletableFuture.supplyAsync(::noop)

  fun requestBuild(file: URI) {
    val change = IoUtils.readString(file.toURL())
    requestBuild(file, change)
  }

  fun requestBuild(file: URI, change: String) {
    runningBuild[file.toString()] = CompletableFuture.supplyAsync { build(file, change) }
  }

  fun lastSuccessfulBuild(uri: String): PklModule? = successfulBuilds[uri]

  private fun build(file: URI, change: String): PklModule? {
    return try {
      server.logger().log("building $file")
      val moduleCtx = parser.parseModule(change)
      val module = PklModuleImpl(moduleCtx, file)
      val diagnostics = analyze(module)
      makeDiagnostics(file, diagnostics)
      successfulBuilds[file.toString()] = module
      return module
    } catch (e: LexParseException) {
      server.logger().error("Parser Error building $file: ${e.message}")
      makeParserDiagnostics(file, listOf(toParserError(e)))
      null
    } catch (e: Exception) {
      server.logger().error("Error building $file: ${e.message} ${e.stackTraceToString()}")
      null
    }
  }

  private fun analyze(node: Node): List<Diagnostic> {
    return buildList<PklDiagnostic> {
      for (analyzer in analyzers) {
        analyzer.analyze(node, this)
      }
    }
  }

  private fun makeParserDiagnostics(file: URI, errors: List<ParseError>) {
    val diags =
      errors.map { err ->
        val msg = resolveErrorMessage(err.errorType, *err.args)
        val diag = Diagnostic(err.span.toRange(), "$msg\n\n")
        diag.severity = DiagnosticSeverity.Error
        diag.source = "Pkl Language Server"
        server.logger().log("diagnostic: $msg at ${err.span}")
        diag
      }
    makeDiagnostics(file, diags)
  }

  private fun makeDiagnostics(file: URI, diags: List<Diagnostic>) {
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

    private fun resolveErrorMessage(key: String, vararg args: Any): String {
      val locale = Locale.getDefault()
      val bundle = ResourceBundle.getBundle("org.pkl.lsp.errorMessages", locale)
      return if (bundle.containsKey(key)) {
        val msg = bundle.getString(key)
        if (args.isNotEmpty()) {
          val formatter = MessageFormat(msg, locale)
          formatter.format(args)
        } else msg
      } else key
    }
  }
}

class ParseError(val errorType: String, val span: Span, vararg val args: Any)
