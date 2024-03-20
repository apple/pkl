package org.pkl.lsp

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.services.TextDocumentService
import java.net.URI
import java.nio.file.Paths

class PklTextDocumentService(private val server: PklLSPServer) : TextDocumentService {
  override fun didOpen(params: DidOpenTextDocumentParams) {
    TODO("Not yet implemented")
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val file = Paths.get(uri).toFile()
    if (file.isFile && file.extension == "pkl") {
      server.builder().requestBuild(uri)
    }
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    //noop
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    val uri = URI(params.textDocument.uri)
    val file = Paths.get(uri).toFile()
    if (file.isFile && file.extension == "pkl") {
      server.builder().requestBuild(uri)
    }
  }
}
