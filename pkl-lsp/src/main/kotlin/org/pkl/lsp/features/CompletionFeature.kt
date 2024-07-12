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
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*
import org.pkl.lsp.type.computeResolvedImportType
import org.pkl.lsp.type.computeThisType
import org.pkl.lsp.type.toType

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

  private fun Node.resolveCompletion(): MutableList<CompletionItem>? {
    val showTypes = parentOfType<PklNewExpr>() != null
    val module = if (this is PklModule) this else enclosingModule
    return when (this) {
      is PklUnqualifiedAccessExpr -> resolveUnqualifiedAccess(this)?.complete(showTypes, module)
      is PklQualifiedAccessExpr -> resolveQualifiedAccess(this)?.complete(showTypes, module)
      is PklSingleLineStringLiteral,
      is PklMultiLineStringLiteral -> PklBaseModule.instance.stringType.ctx.complete()
      is PklQualifiedIdentifier ->
        when (val par = parent) {
          is PklDeclaredType -> par.name.resolve()?.complete(showTypes, module)
          else -> null
        }
      is PklModule -> complete(showTypes, module)
      is PklThisExpr -> {
        val base = PklBaseModule.instance
        computeThisType(base, mapOf()).toClassType(base)?.ctx?.complete()
      }
      else -> null
    }
  }

  private fun Node.complete(
    showTypes: Boolean,
    sourceModule: PklModule?
  ): MutableList<CompletionItem> =
    when (this) {
      is PklModule -> complete(showTypes, sourceModule)
      is PklClass -> complete()
      is PklClassProperty -> complete()
      else -> mutableListOf()
    }

  private fun PklModule.complete(
    showTypes: Boolean,
    sourceModule: PklModule?
  ): MutableList<CompletionItem> =
    if (showTypes) {
      completeTypes(sourceModule)
    } else {
      val list = completeProps(sourceModule)
      list.addAll(completeTypes(sourceModule))
      list
    }

  private fun PklModule.completeTypes(sourceModule: PklModule?): MutableList<CompletionItem> {
    val sameModule = this == sourceModule
    return buildList {
        addAll(typeDefs.filter { sameModule || !it.isLocal }.map { it.toCompletionItem() })
      }
      .toMutableList()
  }

  private fun PklModule.completeProps(sourceModule: PklModule?): MutableList<CompletionItem> {
    val sameModule = this == sourceModule
    return buildList {
        addAll(properties.filter { sameModule || !it.isLocal }.map { it.toCompletionItem() })
        addAll(methods.filter { sameModule || !it.isLocal }.map { it.toCompletionItem() })
      }
      .toMutableList()
  }

  private fun PklClass.complete(): MutableList<CompletionItem> =
    buildList {
        addAll(properties.map { it.toCompletionItem() })
        addAll(methods.map { it.toCompletionItem() })
      }
      .toMutableList()

  private fun PklClassProperty.complete(): MutableList<CompletionItem> {
    val base = PklBaseModule.instance
    val typ = type?.toType(base, mapOf()) ?: computeResolvedImportType(base, mapOf())
    val clazz = typ.toClassType(base) ?: return mutableListOf()
    return clazz.ctx.complete()
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

  private fun PklTypeDef.toCompletionItem(): CompletionItem {
    val item = CompletionItem(name)
    item.kind = CompletionItemKind.Class
    item.detail =
      when (this) {
        is PklTypeAlias -> type.render()
        is PklClass -> classHeader.render()
      }
    item.documentation = getDoc(this)
    return item
  }

  private fun PklClassHeader.render(): String {
    return buildString {
      if (modifiers != null) {
        append(modifiers!!.joinToString(" ", postfix = " ") { it.text })
      }
      append(identifier?.text ?: "<class>")
      if (extends != null) {
        append(' ')
        append(extends!!.render())
      }
    }
  }

  companion object {
    private fun getDoc(node: PklDocCommentOwner): Either<String, MarkupContent> {
      return Either.forRight(MarkupContent("markdown", node.parsedComment ?: ""))
    }
  }
}
