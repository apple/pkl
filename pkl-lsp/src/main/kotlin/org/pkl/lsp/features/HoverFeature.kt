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
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*
import org.pkl.lsp.resolvers.ResolveVisitors
import org.pkl.lsp.resolvers.Resolvers
import org.pkl.lsp.type.Type
import org.pkl.lsp.type.computeResolvedImportType
import org.pkl.lsp.type.computeThisType

class HoverFeature(override val server: PklLSPServer) : Feature(server) {

  fun onHover(params: HoverParams): CompletableFuture<Hover> {
    fun run(mod: PklModule?): Hover {
      if (mod == null) return Hover(listOf())
      val line = params.position.line + 1
      val col = params.position.character + 1
      val hoverText =
        mod.findBySpan(line, col)?.let { resolveHover(it, line, col) } ?: return Hover(listOf())
      return Hover(MarkupContent("markdown", hoverText))
    }
    return server.builder().runningBuild(params.textDocument.uri).thenApply(::run)
  }

  private fun resolveHover(node: Node, line: Int, col: Int, originalNode: Node? = null): String? {
    val base = PklBaseModule.instance
    return when (node) {
      is PklUnqualifiedAccessExpr -> {
        val element = resolveUnqualifiedAccess(node) ?: return null
        if (originalNode != null) element.toMarkdown(originalNode)
        else resolveHover(element, line, col, node)
      }
      is PklQualifiedAccessExpr -> resolveQualifiedAccess(node)?.toMarkdown(originalNode)
      is PklSuperAccessExpr -> {
        if (node.matches(line, col)) {
          resolveSuperAccess(node)?.toMarkdown(originalNode)
        } else null
      }
      is PklProperty ->
        when {
          originalNode != null -> node.toMarkdown(originalNode)
          node.matches(line, col) -> {
            resolveProperty(node)?.toMarkdown(node)
          }
          else -> null
        }
      is PklMethod -> node.toMarkdown(originalNode)
      is PklMethodHeader -> {
        val name = node.identifier
        // check if hovering over the method name
        if (name != null && name.span.matches(line, col)) {
          node.parent?.toMarkdown(originalNode)
        } else null
      }
      is PklClass -> if (originalNode != null) node.toMarkdown(originalNode) else null
      is PklClassHeader -> {
        val name = node.identifier
        // check if hovering over the class name
        if (name != null && name.span.matches(line, col)) {
          // renders the class, which contains the doc comment
          node.parent?.toMarkdown(originalNode)
        } else null
      }
      is PklQualifiedIdentifier ->
        when (val par = node.parent) {
          // render the module declaration
          is PklModuleHeader -> par.parent?.toMarkdown(originalNode)
          is PklDeclaredType -> {
            val mname = par.name.moduleName
            if (mname != null && mname.span.matches(line, col)) {
              mname.resolve()?.toMarkdown(originalNode)
            } else par.name.resolve()?.toMarkdown(originalNode)
          }
          else -> null
        }
      is PklDeclaredType -> node.name.resolve()?.toMarkdown(originalNode)
      is PklStringConstant ->
        when (val parent = node.parent) {
          is PklImportBase -> {
            when (val res = parent.resolve()) {
              is SimpleModuleResolutionResult -> res.resolved?.toMarkdown(originalNode)
              is GlobModuleResolutionResult -> null // TODO: globs
            }
          }
          is PklModuleExtendsAmendsClause -> parent.moduleUri?.resolve()?.toMarkdown(originalNode)
          else -> null
        }
      is PklImport -> {
        if (node.matches(line, col)) {
          when (val res = node.resolve()) {
            is SimpleModuleResolutionResult -> res.resolved?.toMarkdown(originalNode)
            is GlobModuleResolutionResult -> null // TODO: globs
          }
        } else null
      }
      // render the typealias which contains the doc comments
      is PklTypeAliasHeader -> node.parent?.toMarkdown(originalNode)
      is PklTypedIdentifier -> node.toMarkdown(originalNode)
      is PklThisExpr -> node.computeThisType(base, mapOf()).toMarkdown()
      else -> null
    }
  }

  private fun Node.render(originalNode: Node?): String =
    when (this) {
      is PklProperty ->
        buildString {
          append(modifiers.render())
          if (isLocal || isFixedOrConst) {
            append(renderTypeAnnotation(name, type, this@render, originalNode))
          } else {
            append(name)
            append(": ")
            append(type?.render(originalNode) ?: "unknown")
          }
        }
      is PklStringLiteralType -> "\"$text\""
      is PklMethod -> {
        val modifiers = modifiers.render()
        modifiers + methodHeader.render(originalNode)
      }
      is PklMethodHeader ->
        buildString {
          append("function ")
          append(identifier?.text ?: "<method>>")
          append(typeParameterList?.render(originalNode) ?: "")
          append(parameterList?.render(originalNode) ?: "()")
          append(returnType?.render(originalNode)?.let { ": $it" } ?: "")
        }
      is PklParameterList -> {
        elements.joinToString(", ", prefix = "(", postfix = ")") { it.render(originalNode) }
      }
      is PklTypeParameterList -> {
        typeParameters.joinToString(", ", prefix = "<", postfix = ">") { it.render(originalNode) }
      }
      is PklParameter ->
        if (isUnderscore) {
          "_"
        } else {
          // cannot be null here if it's not underscore
          typedIdentifier!!.render(originalNode)
        }
      is PklTypeAnnotation -> ": ${type!!.render(originalNode)}"
      is PklTypedIdentifier ->
        renderTypeAnnotation(identifier?.text, typeAnnotation?.type, this, originalNode)!!
      is PklTypeParameter -> {
        val vari = variance?.name?.lowercase()?.let { "$it " } ?: ""
        "$vari$name"
      }
      is PklClass ->
        buildString {
          append(modifiers.render())
          append("class ")
          append(classHeader.identifier?.text ?: "<class>")
          typeParameterList?.let { append(it.render(originalNode)) }
          supertype?.let {
            append(" extends ")
            append(it.render(originalNode))
          }
        }
      is PklModule -> declaration?.render(originalNode) ?: "module $moduleName"
      is PklModuleDeclaration ->
        buildString {
          append(modifiers.render())
          append("module ")
          // can never be null
          append(moduleHeader!!.render(originalNode))
        }
      is PklModuleHeader ->
        buildString {
          append(moduleName ?: enclosingModule?.moduleName ?: "<module>")
          moduleExtendsAmendsClause?.let {
            append(if (it.isAmend) " amends " else " extends ")
            append(it.moduleUri!!.stringConstant.text)
          }
        }
      is PklImport ->
        buildString {
          if (isGlob) {
            append("import* ")
          } else {
            append("import ")
          }
          moduleUri?.stringConstant?.escapedText()?.let { append("\"$it\"") }
          val definitionType =
            resolve().computeResolvedImportType(PklBaseModule.instance, mapOf(), false)
          append(": ")
          definitionType.render(this, DefaultTypeNameRenderer)
        }
      is PklTypeAlias -> typeAliasHeader.render(originalNode)
      is PklTypeAliasHeader ->
        buildString {
          append(modifiers.render())
          append("typealias ")
          append(identifier?.text)
          typeParameterList?.let { append(it.render(originalNode)) }
        }
      is PklType -> render()
      else -> text
    }

  // render modifiers
  private fun List<Terminal>?.render(): String {
    return this?.let { if (isEmpty()) "" else joinToString(" ", postfix = " ") { it.text } } ?: ""
  }

  private fun renderTypeAnnotation(
    name: String?,
    type: PklType?,
    node: Node,
    originalNode: Node?
  ): String? {
    if (name == null) return null
    return buildString {
      append(name)
      when {
        originalNode?.isAncestor(node) == false -> {
          val visitor =
            ResolveVisitors.typeOfFirstElementNamed(
              name,
              null,
              PklBaseModule.instance,
              isNullSafeAccess = false,
              preserveUnboundTypeVars = false
            )
          val computedType =
            Resolvers.resolveUnqualifiedAccess(
              originalNode,
              null,
              true,
              PklBaseModule.instance,
              mapOf(),
              visitor
            )
          append(": ")
          computedType.render(this)
        }
        type != null -> {
          append(": ")
          append(type.render(originalNode))
        }
        else -> {
          val computedType = node.computeResolvedImportType(PklBaseModule.instance, mapOf())
          append(": ")
          computedType.render(this)
        }
      }
    }
  }

  private fun Type.toMarkdown(): String {
    val markdown = render()
    val ctx = getNode()
    return when {
      ctx is PklModule && ctx.declaration != null ->
        showDocCommentAndModule(ctx.declaration!!, markdown)
      else -> showDocCommentAndModule(ctx, markdown)
    }
  }

  private fun Node.toMarkdown(originalNode: Node?): String {
    val markdown = render(originalNode)
    return when {
      this is PklModule && declaration != null -> showDocCommentAndModule(declaration!!, markdown)
      else -> showDocCommentAndModule(this, markdown)
    }
  }

  private fun showDocCommentAndModule(node: Node?, text: String): String {
    val markdown = "```pkl\n$text\n```"
    val withDoc =
      if (node is PklDocCommentOwner) {
        node.parsedComment?.let { "$markdown\n\n---\n\n$it" } ?: markdown
      } else markdown
    val module = (if (node is PklModule) node else node?.enclosingModule)
    val footer =
      if (module != null) {
        "\n\n---\n\nin [${module.moduleName}](${module.toCommandURIString()})"
      } else ""
    return "$withDoc$footer"
  }
}
