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
package org.pkl.lsp.ast

import java.io.File
import kotlin.io.path.invariantSeparatorsPathString
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.inferImportPropertyName
import org.pkl.lsp.resolvers.ResolveVisitors
import org.pkl.lsp.resolvers.Resolvers
import org.pkl.lsp.type.Type
import org.pkl.lsp.type.TypeParameterBindings
import org.pkl.lsp.type.computeResolvedImportType
import org.pkl.lsp.unexpectedType

val PklClass.supertype: PklType?
  get() = classHeader.extends

val PklClass.superclass: PklClass?
  get() {
    return when (val st = supertype) {
      is PklDeclaredType -> st.name.resolve() as? PklClass?
      is PklModuleType -> null // see PklClass.supermodule
      null ->
        when {
          isPklBaseAnyClass -> null
          else -> PklBaseModule.instance.typedType.ctx
        }
      else -> unexpectedType(st)
    }
  }

// Non-null when [this] extends a module (class).
// Ideally, [Clazz.superclass] would cover this case,
// but we don't have a common abstraction for Clazz and PklModule(Class),
// and it seems challenging to introduce one.
val PklClass.supermodule: PklModule?
  get() {
    return when (val st = supertype) {
      is PklDeclaredType -> st.name.resolve() as? PklModule?
      is PklModuleType -> enclosingModule
      else -> null
    }
  }

val PklClass.isPklBaseAnyClass: Boolean
  get() {
    return name == "Any" && this === PklBaseModule.instance.anyType.ctx
  }

fun PklTypeName.resolve(): Node? = simpleTypeName.resolve()

fun SimpleTypeName.resolve(): Node? {
  val typeName = parentOfType<PklTypeName>() ?: return null
  // TODO: check if module name is not null
  // val moduleName = typeName.moduleName
  val simpleTypeNameText = identifier?.text ?: return null
  val base = PklBaseModule.instance

  return Resolvers.resolveUnqualifiedTypeName(
    this,
    base,
    mapOf(),
    ResolveVisitors.firstElementNamed(simpleTypeNameText, base)
  )
}

fun PklClass.isSubclassOf(other: PklClass): Boolean {
  // optimization
  if (this === other) return true

  // optimization
  // TODO: check if this works
  if (!other.isAbstractOrOpen) return this == other

  var clazz: PklClass? = this
  while (clazz != null) {
    // TODO: check if this works
    if (clazz == other) return true
    if (clazz.supermodule != null) {
      return PklBaseModule.instance.moduleType.ctx.isSubclassOf(other)
    }
    clazz = clazz.superclass
  }
  return false
}

fun PklClass.isSubclassOf(other: PklModule): Boolean {
  // optimization
  if (!other.isAbstractOrOpen) return false

  var clazz = this
  var superclass = clazz.superclass
  while (superclass != null) {
    clazz = superclass
    superclass = superclass.superclass
  }
  var module = clazz.supermodule
  while (module != null) {
    // TODO: check if this works
    if (module == other) return true
    module = module.supermodule
  }
  return false
}

val PklImport.memberName: String?
  get() =
    identifier?.text
      ?: moduleUri?.stringConstant?.escapedText()?.let { inferImportPropertyName(it) }

fun PklStringConstant.escapedText(): String? = getEscapedText()

fun PklSingleLineStringLiteral.escapedText(): String? =
  parts.mapNotNull { it.getEscapedText() }.joinToString("")

fun PklMultiLineStringLiteral.escapedText(): String? =
  parts.mapNotNull { it.getEscapedText() }.joinToString("")

private fun Node.getEscapedText(): String? = buildString {
  for (terminal in terminals) {
    when (terminal.type) {
      TokenType.SLQuote,
      TokenType.SLEndQuote,
      TokenType.MLQuote,
      TokenType.MLEndQuote -> {} // ignore open/close quotes
      TokenType.SLCharacters,
      TokenType.MLCharacters -> append(terminal.text)
      TokenType.SLCharacterEscape,
      TokenType.MLCharacterEscape -> {
        val text = terminal.text
        when (text[text.lastIndex]) {
          'n' -> append('\n')
          'r' -> append('\r')
          't' -> append('\t')
          '\\' -> append('\\')
          '"' -> append('"')
          else -> throw AssertionError("Unknown char escape: $text")
        }
      }
      TokenType.SLUnicodeEscape,
      TokenType.MLUnicodeEscape -> {
        val text = terminal.text
        val index = text.indexOf('{') + 1
        if (index != -1) {
          val hexString = text.substring(index, text.length - 1)
          try {
            append(Character.toChars(Integer.parseInt(hexString, 16)))
          } catch (ignored: NumberFormatException) {} catch (ignored: IllegalArgumentException) {}
        }
      }
      TokenType.MLNewline -> append('\n')
      else ->
        // interpolated or invalid string -> bail out
        return null
    }
  }
}

fun PklTypeAlias.isRecursive(seen: MutableSet<PklTypeAlias>): Boolean =
  !seen.add(this) || type.isRecursive(seen)

private fun PklType?.isRecursive(seen: MutableSet<PklTypeAlias>): Boolean =
  when (this) {
    is PklDeclaredType -> {
      val resolved = name.resolve()
      resolved is PklTypeAlias && resolved.isRecursive(seen)
    }
    is PklNullableType -> type.isRecursive(seen)
    is PklDefaultUnionType -> type.isRecursive(seen)
    is PklUnionType -> leftType.isRecursive(seen) || rightType.isRecursive(seen)
    is PklConstrainedType -> type.isRecursive(seen)
    is PklParenthesizedType -> type.isRecursive(seen)
    else -> false
  }

val Node.isInPklBaseModule: Boolean
  get() = enclosingModule?.declaration?.moduleHeader?.qualifiedIdentifier?.fullName == "pkl.base"

interface TypeNameRenderer {
  fun render(name: PklTypeName, appendable: Appendable)

  fun render(type: Type.Class, appendable: Appendable)

  fun render(type: Type.Alias, appendable: Appendable)

  fun render(type: Type.Module, appendable: Appendable)
}

object DefaultTypeNameRenderer : TypeNameRenderer {
  override fun render(name: PklTypeName, appendable: Appendable) {
    appendable.append(name.simpleTypeName.identifier?.text)
  }

  override fun render(type: Type.Class, appendable: Appendable) {
    appendable.append(type.ctx.name)
  }

  override fun render(type: Type.Alias, appendable: Appendable) {
    appendable.append(type.ctx.name)
  }

  override fun render(type: Type.Module, appendable: Appendable) {
    appendable.append(type.referenceName)
  }
}

val PklModuleMember.owner: PklTypeDefOrModule?
  get() = parentOfTypes(PklClass::class, PklModule::class)

val PklMethod.isOverridable: Boolean
  get() =
    when {
      isLocal -> false
      isAbstract -> true
      this is PklObjectMethod -> false
      this is PklClassMethod -> owner?.isAbstractOrOpen ?: false
      else -> unexpectedType(this)
    }

inline fun <reified T : Node> Node.parentOfType(): T? {
  return parentOfTypes(T::class)
}

fun PklImportBase.resolve(): ModuleResolutionResult =
  if (isGlob) GlobModuleResolutionResult(moduleUri?.resolveGlob() ?: emptyList())
  else SimpleModuleResolutionResult(moduleUri?.resolve())

fun PklImportBase.resolveModules(): List<PklModule> =
  resolve().let { result ->
    when (result) {
      is SimpleModuleResolutionResult -> result.resolved?.let(::listOf) ?: emptyList()
      else -> {
        result as GlobModuleResolutionResult
        result.resolved
      }
    }
  }

fun PklModuleUri.resolveGlob(): List<PklModule> = TODO("implement") // resolveModuleUriGlob(this)

fun PklModuleUri.resolve(): PklModule? =
  this.stringConstant.escapedText()?.let { text ->
    // TODO: get the actual file
    resolve(text, text, File("."), enclosingModule)
  }

sealed class ModuleResolutionResult {
  abstract fun computeResolvedImportType(
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    preserveUnboundedVars: Boolean
  ): Type
}

class SimpleModuleResolutionResult(val resolved: PklModule?) : ModuleResolutionResult() {
  override fun computeResolvedImportType(
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    preserveUnboundedVars: Boolean
  ): Type {
    return resolved.computeResolvedImportType(base, bindings, preserveUnboundedVars)
  }
}

class GlobModuleResolutionResult(val resolved: List<PklModule>) : ModuleResolutionResult() {
  override fun computeResolvedImportType(
    base: PklBaseModule,
    bindings: TypeParameterBindings,
    preserveUnboundedVars: Boolean
  ): Type {
    if (resolved.isEmpty())
      return base.mappingType.withTypeArguments(base.stringType, base.moduleType)
    val allTypes =
      resolved.map {
        it.computeResolvedImportType(base, bindings, preserveUnboundedVars) as Type.Module
      }
    val firstType = allTypes.first()
    val unifiedType =
      allTypes.drop(1).fold<Type.Module, Type>(firstType) { acc, type ->
        val currentModule = acc as? Type.Module ?: return@fold acc
        inferCommonType(base, currentModule, type)
      }
    return base.mappingType.withTypeArguments(base.stringType, unifiedType)
  }

  private fun inferCommonType(base: PklBaseModule, modA: Type.Module, modB: Type.Module): Type {
    return when {
      modA.isSubtypeOf(modB, base) -> modB
      modB.isSubtypeOf(modA, base) -> modA
      else -> {
        val superModA = modA.supermodule() ?: return base.moduleType
        val superModB = modB.supermodule() ?: return base.moduleType
        inferCommonType(base, superModA, superModB)
      }
    }
  }
}

/** Find the deepest node that matches [line] and [col]. */
fun Node.findBySpan(line: Int, col: Int, includeTerminals: Boolean = false): Node? {
  if (!includeTerminals && this is Terminal) return null
  val hit = if (span.matches(line, col)) this else null
  val childHit = children.firstNotNullOfOrNull { it.findBySpan(line, col) }
  return childHit ?: hit
}

fun Node.toMarkdown(): String {
  val markdown = render()
  return showDocCommentAndModule(this, markdown)
}

fun Node.render(): String =
  when (this) {
    is PklProperty -> {
      val modifiers = modifiers.render()
      "$modifiers$name: ${type?.render() ?: "unknown"}"
    }
    is PklStringLiteralType -> "\"$text\""
    is PklMethod -> {
      val modifiers = modifiers.render()
      modifiers + methodHeader.render()
    }
    is PklMethodHeader -> {
      val name = identifier?.text ?: "???"
      val typePars = typeParameterList?.render() ?: ""
      val pars = parameterList?.render() ?: "()"
      val retType = returnType?.render()?.let { ": $it" } ?: ""
      "$name$typePars$pars$retType"
    }
    is PklParameterList -> {
      elements.joinToString(", ", prefix = "(", postfix = ")") { it.render() }
    }
    is PklTypeParameterList -> {
      typeParameters.joinToString(", ", prefix = "<", postfix = ">") { it.render() }
    }
    is PklParameter ->
      if (isUnderscore) {
        "_"
      } else {
        // cannot be null here if it's not underscore
        typedIdentifier!!.render()
      }
    is PklTypeAnnotation -> ": ${pklType!!.render()}"
    is PklTypedIdentifier -> {
      val name = identifier!!.text
      typeAnnotation?.let { "$name${it.render()}" } ?: name
    }
    is PklTypeParameter -> {
      val vari = variance?.name?.lowercase()?.let { "$it " } ?: ""
      "$vari$name"
    }
    is PklClass -> {
      val modifiers = modifiers.render()
      val name = classHeader.identifier?.text ?: "???"
      "$modifiers$name"
    }
    is PklModuleDeclaration -> {
      val modifiers = modifiers.render()
      // can never be null
      val idents = moduleHeader!!.qualifiedIdentifier!!.render()
      "${modifiers}module $idents"
    }
    is PklQualifiedIdentifier -> identifiers.joinToString(".") { it.text }
    else -> text
  }

// render modifiers
private fun List<Terminal>?.render(): String {
  return this?.let { if (isEmpty()) "" else joinToString(" ", postfix = " ") { it.text } } ?: ""
}

private fun showDocCommentAndModule(node: Node, text: String): String {
  val markdown = "```pkl\n$text\n```"
  return if (node is PklDocCommentOwner) {
    node.parsedComment?.let { "$markdown\n\n---\n\n$it" } ?: markdown
  } else markdown
}

fun Node.toURIString(server: PklLSPServer): String {
  val mod = if (this is PklModule) this else enclosingModule!!
  val uri = mod.uri.toString()
  return if (uri.startsWith("pkl:")) {
    val name = uri.replace("pkl:", "")
    val uriStr = server.stdlibDir.resolve("$name.pkl").invariantSeparatorsPathString
    "pkl:$uriStr"
  } else uri
}
