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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.*
import org.pkl.core.util.IoUtils

class PklLSPServer(private val verbose: Boolean) : LanguageServer, LanguageClientAware {

  private val workspaceService: PklWorkspaceService = PklWorkspaceService()
  private val textService: PklTextDocumentService = PklTextDocumentService(this)

  private lateinit var client: LanguageClient
  private lateinit var logger: ClientLogger
  private val builder: Builder = Builder(this)

  private val cacheDir: Path = Files.createTempDirectory("pklLSP")
  val stdlibDir = cacheDir.resolve("stdlib")

  override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
    val res = InitializeResult(ServerCapabilities())
    res.capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

    // hover capability
    res.capabilities.setHoverProvider(true)
    // go to definition capability
    res.capabilities.definitionProvider = Either.forLeft(true)
    // auto completion capability
    res.capabilities.completionProvider = CompletionOptions(false, listOf("."))

    // cache the stdlib, so we can open it in the client
    CompletableFuture.supplyAsync(::cacheStdlib)

    return CompletableFuture.supplyAsync { res }
  }

  override fun shutdown(): CompletableFuture<Any> {
    return CompletableFuture.supplyAsync(::Object)
  }

  override fun exit() {
    exitProcess(0)
  }

  override fun getTextDocumentService(): TextDocumentService = textService

  override fun getWorkspaceService(): WorkspaceService = workspaceService

  fun builder(): Builder = builder

  fun client(): LanguageClient = client

  fun logger(): ClientLogger = logger

  override fun connect(client: LanguageClient) {
    this.client = client
    logger = ClientLogger(client, verbose)
  }

  @JsonRequest(value = "pkl/fileContents")
  fun fileContentsRequest(param: TextDocumentIdentifier): CompletableFuture<String> {
    return CompletableFuture.supplyAsync {
      val uri = URI.create(param.uri)
      val origin = Origin.valueOf(uri.authority.uppercase())
      val path = uri.path.drop(1)
      logger.log("parsed uri: $uri")
      when (origin) {
        Origin.HTTPS -> CacheManager.findHttpContent(URI.create(path)) ?: ""
        Origin.STDLIB -> {
          val name = path.replace("pkl:", "")
          IoUtils.readClassPathResourceAsString(javaClass, "/org/pkl/core/stdlib/$name")
        }
        // if origin is file either we have a bug or this is an absolute file import
        // from inside a non-file module, which shouldn't be allowed
        Origin.FILE -> ""
        else -> ""
      }
    }
  }

  private fun cacheStdlib() {
    stdlibDir.toFile().mkdirs()
    for ((name, _) in Stdlib.allModules()) {
      val file = stdlibDir.resolve("$name.pkl")
      val text = IoUtils.readClassPathResourceAsString(javaClass, "/org/pkl/core/stdlib/$name.pkl")
      Files.writeString(file, text, Charsets.UTF_8)
    }
  }
}
