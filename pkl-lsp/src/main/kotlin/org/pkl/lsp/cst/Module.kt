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
package org.pkl.lsp.cst

data class Module(
  val decl: ModuleDecl?,
  val imports: List<Import>,
  val entries: List<ModuleEntry>,
  val span: Span
)

data class ModuleDecl(
  val docComment: DocComment?,
  val annotations: List<Annotation>,
  val modifiers: List<Modifier>,
  val name: ModuleNameDecl?,
  val extendsDecl: ExtendsDecl?,
  val amendsDecl: AmendsDecl?
)

data class ExtendsDecl(val url: Ident, val span: Span)

data class AmendsDecl(val url: Ident, val span: Span)

data class ModuleNameDecl(val name: List<Ident>, val span: Span) {
  val nameString = name.joinToString(".") { it.value }
}

data class Annotation(val type: Type, val body: ObjectBody?, val span: Span)

sealed class ModuleEntry(
  open val docComment: DocComment?,
  open val annotations: List<Annotation>,
  open val modifiers: List<Modifier>,
  open val name: Ident,
  open val span: Span
)

data class TypeAlias(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  val typePars: List<TypeParameter>,
  val type: Type,
  override val span: Span
) : ModuleEntry(docComment, annotations, modifiers, name, span)

data class Clazz(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  val typePars: List<TypeParameter>,
  val superClass: Type?,
  val body: List<ClassEntry>,
  override val span: Span
) : ModuleEntry(docComment, annotations, modifiers, name, span)

sealed class ClassEntry(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  override val span: Span
) : ModuleEntry(docComment, annotations, modifiers, name, span)

data class ClassProperty(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  val type: Type,
  override val span: Span
) : ClassEntry(docComment, annotations, modifiers, name, span)

data class ClassPropertyExpr(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  val type: Type?,
  val expr: Expr,
  override val span: Span
) : ClassEntry(docComment, annotations, modifiers, name, span)

data class ClassPropertyBody(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  val type: Type?,
  val bodyList: List<ObjectBody>,
  override val span: Span
) : ClassEntry(docComment, annotations, modifiers, name, span)

data class ClassMethod(
  override val docComment: DocComment?,
  override val annotations: List<Annotation>,
  override val modifiers: List<Modifier>,
  override val name: Ident,
  val typePars: List<TypeParameter>,
  val args: List<Parameter>,
  val returnType: Type?,
  val expr: Expr?,
  override val span: Span
) : ClassEntry(docComment, annotations, modifiers, name, span)
