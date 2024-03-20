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

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import kotlin.system.exitProcess

class PklLSPServer(private val verbose: Boolean) : LanguageServer, LanguageClientAware {

  val workspaceService: PklWorkspaceService = PklWorkspaceService()
  val textDocumentService: PklTextDocumentService = PklTextDocumentService(this)

  private lateinit var client: LanguageClient
  private val builder: Builder = Builder()

  override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
    val res = InitializeResult(ServerCapabilities())
    res.capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
    
    return CompletableFuture.supplyAsync { res }
  }

  override fun shutdown(): CompletableFuture<Any> {
    return CompletableFuture.supplyAsync(::Object)
  }

  override fun exit() {
    exitProcess(0)
  }

  override fun getTextDocumentService(): TextDocumentService = textDocumentService

  override fun getWorkspaceService(): WorkspaceService = workspaceService
  
  fun builder() = builder

  override fun connect(client: LanguageClient) {
    this.client = client
  }
}
