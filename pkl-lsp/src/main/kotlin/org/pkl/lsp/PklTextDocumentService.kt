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
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.services.TextDocumentService

class PklTextDocumentService(private val server: PklLSPServer) : TextDocumentService {
  override fun didOpen(params: DidOpenTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val file = Paths.get(uri).toFile()
    if (file.isFile && file.extension == "pkl") {
      server.builder().requestBuild(uri)
    }
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val file = Paths.get(uri).toFile()
    if (file.isFile && file.extension == "pkl") {
      server.builder().requestBuild(uri, params.contentChanges[0].text)
    }
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    // noop
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val file = Paths.get(uri).toFile()
    if (file.isFile && file.extension == "pkl") {
      server.builder().requestBuild(uri)
    }
  }
}
