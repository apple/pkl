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
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*

class CompletionFeature(override val server: PklLSPServer) : Feature(server) {
  fun onCompletion(
    params: CompletionParams
  ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
    fun run(mod: PklModule?): Either<MutableList<CompletionItem>, CompletionList> {
      val pklMod =
        mod
          ?: (server.builder().lastSuccessfulBuild(params.textDocument.uri)
            ?: return Either.forLeft(mutableListOf()))

      val line = params.position.line + 1
      val col = params.position.character + 1
      @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
      return when (params.context.triggerKind) {
        CompletionTriggerKind.Invoked -> Either.forLeft(mutableListOf())
        CompletionTriggerKind.TriggerForIncompleteCompletions -> Either.forLeft(mutableListOf())
        CompletionTriggerKind.TriggerCharacter -> {
          // go two position behind to find the actual node to complete
          val completions =
            pklMod.findBySpan(line, col - 2)?.resolveCompletion()
              ?: return Either.forLeft(mutableListOf())
          return Either.forLeft(completions)
        }
      }
    }
    return server.builder().runningBuild(params.textDocument.uri).thenApply(::run)
  }

  private fun Node.resolveCompletion(): MutableList<CompletionItem>? =
    when (this) {
      is PklUnqualifiedAccessExpr -> {
        // val typ = inferExprTypeFromContext(PklBaseModule.instance, mapOf(), false)
        resolveUnqualifiedAccess(this)?.complete()
      }
      is PklQualifiedAccessExpr -> resolveQualifiedAccess(this)?.complete()
      else -> null
    }

  private fun Node.complete(): MutableList<CompletionItem> =
    when (this) {
      is PklModule -> complete()
      is PklClass -> complete()
      is PklClassProperty -> complete()
      else -> mutableListOf()
    }

  private fun PklModule.complete(): MutableList<CompletionItem> =
    buildList {
        addAll(properties.map { it.toCompletionItem() })
        addAll(methods.map { it.toCompletionItem() })
      }
      .toMutableList()

  private fun PklClass.complete(): MutableList<CompletionItem> =
    buildList {
        addAll(properties.map { it.toCompletionItem() })
        addAll(methods.map { it.toCompletionItem() })
      }
      .toMutableList()

  private fun PklClassProperty.complete(): MutableList<CompletionItem> {
    type?.let {}

    return mutableListOf()
  }

  private fun PklClassProperty.toCompletionItem(): CompletionItem {
    val item = CompletionItem(name)
    item.kind = CompletionItemKind.Field
    item.detail = type?.text ?: "unknown"
    item.documentation = getDoc(this)
    return item
  }

  private fun PklClassMethod.toCompletionItem(): CompletionItem {
    val item = CompletionItem(name)
    item.kind = CompletionItemKind.Method
    item.detail = methodHeader.returnType?.text ?: "unknown"
    item.documentation = getDoc(this)
    return item
  }

  companion object {
    private fun getDoc(node: PklDocCommentOwner): Either<String, MarkupContent> {
      return Either.forRight(MarkupContent("markdown", node.parsedComment ?: ""))
    }
  }
}
