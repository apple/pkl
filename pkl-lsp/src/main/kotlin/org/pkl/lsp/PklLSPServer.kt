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
import org.eclipse.lsp4j.services.*

class PklLSPServer(private val verbose: Boolean) : LanguageServer, LanguageClientAware {
  override fun initialize(p0: InitializeParams?): CompletableFuture<InitializeResult> {
    TODO("Not yet implemented")
  }

  override fun shutdown(): CompletableFuture<Any> {
    TODO("Not yet implemented")
  }

  override fun exit() {
    TODO("Not yet implemented")
  }

  override fun getTextDocumentService(): TextDocumentService {
    TODO("Not yet implemented")
  }

  override fun getWorkspaceService(): WorkspaceService {
    TODO("Not yet implemented")
  }

  override fun connect(p0: LanguageClient?) {
    TODO("Not yet implemented")
  }
}
