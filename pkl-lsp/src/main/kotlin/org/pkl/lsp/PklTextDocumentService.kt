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
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.pkl.core.util.IoUtils
import org.pkl.lsp.features.CompletionFeature
import org.pkl.lsp.features.GoToDefinitionFeature
import org.pkl.lsp.features.HoverFeature

class PklTextDocumentService(private val server: PklLSPServer) : TextDocumentService {

  private val hover = HoverFeature(server)
  private val definition = GoToDefinitionFeature(server)
  private val completion = CompletionFeature(server)

  override fun didOpen(params: DidOpenTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val vfile = VirtualFile.fromUri(uri, server.logger()) ?: return
    server.builder().requestBuild(uri, vfile, params.textDocument.text)
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val vfile = VirtualFile.fromUri(uri, server.logger()) ?: return
    server.builder().requestBuild(uri, vfile, params.contentChanges[0].text)
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    // noop
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    if (!uri.scheme.equals("file", ignoreCase = true)) {
      // you can only save files
      server.logger().error("Saved non file URI: $uri")
      return
    }
    val file = Paths.get(uri).toFile()
    if (file.isFile && file.extension == "pkl") {
      val contents = IoUtils.readString(uri.toURL())
      server.builder().requestBuild(uri, FsFile(file), contents)
    }
  }

  override fun hover(params: HoverParams): CompletableFuture<Hover> {
    return hover.onHover(params)
  }

  override fun definition(
    params: DefinitionParams
  ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
    return definition.onGoToDefinition(params)
  }

  override fun completion(
    params: CompletionParams
  ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
    return completion.onCompletion(params)
  }
}
