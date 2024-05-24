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

import org.antlr.v4.runtime.tree.ParseTree
import org.pkl.core.parser.antlr.PklParser
import org.pkl.core.parser.antlr.PklParser.ModuleHeaderContext
import org.pkl.lsp.LSPUtil.firstInstanceOf
import org.pkl.lsp.PklVisitor

class PklModuleImpl(override val ctx: PklParser.ModuleContext) :
  AbstractNode(null, ctx), PklModule {
  override val isAmend: Boolean by lazy {
    declaration?.moduleExtendsAmendsClause?.isAmend
      ?: declaration?.moduleHeader?.moduleExtendsAmendsClause?.isAmend ?: false
  }

  override val declaration: ModuleDeclaration? by lazy { getChild(ModuleDeclarationImpl::class) }

  override val members: List<PklModuleMember> by lazy {
    children.filterIsInstance<PklModuleMember>()
  }

  override val imports: List<PklImport>? by lazy { getChildren(PklImportImpl::class) }

  override val typeAliases: List<PklTypeAlias> by lazy { children.filterIsInstance<PklTypeAlias>() }

  override val classes: List<PklClass> by lazy { children.filterIsInstance<PklClass>() }

  // TODO: resolve the super module
  override val supermodule: PklModule? by lazy { null }

  override val cache: ModuleMemberCache by lazy { ModuleMemberCache.create(this) }

  override val modifiers: List<Terminal>? by lazy { terminals.takeWhile { it.isModifier } }

  // TODO: fetch the name of the module from uri
  override val shortDisplayName: String by lazy {
    declaration?.moduleHeader?.shortDisplayName
      ?: throw RuntimeException("could not fetch uri name")
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModule(this)
  }
}

class PklAnnotationImpl(override val parent: Node, override val ctx: PklParser.AnnotationContext) :
  AbstractNode(parent, ctx), PklAnnotation {
  override val typeName: TypeName
    get() = TODO("Not yet implemented")

  override val objectBody: PklObjectBody
    get() = TODO("Not yet implemented")

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitAnnotation(this)
  }
}

class ModuleHeaderImpl(override val parent: Node, override val ctx: ModuleHeaderContext) :
  AbstractNode(parent, ctx), ModuleHeader {
  override val qualifiedIdentifier: QualifiedIdentifier? by lazy {
    getChild(QualifiedIdentifierImpl::class)
  }

  override val moduleExtendsAmendsClause: ModuleExtendsAmendsClause? by lazy {
    getChild(ModuleExtendsAmendsClauseImpl::class)
  }

  override val modifiers: List<Terminal>? by lazy { terminals.takeWhile { it.isModifier } }

  override val shortDisplayName: String? by lazy {
    qualifiedIdentifier?.fullName?.substringAfterLast('.')
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleHeader(this)
  }
}

class ModuleDeclarationImpl(
  override val parent: Node,
  override val ctx: PklParser.ModuleDeclContext
) : AbstractNode(parent, ctx), ModuleDeclaration {

  override val annotations: List<PklAnnotation> by lazy {
    ctx.annotation().map { PklAnnotationImpl(this, it) }
  }

  override val moduleHeader: ModuleHeader? by lazy {
    ctx.moduleHeader()?.let { ModuleHeaderImpl(this, it) }
  }

  override val moduleExtendsAmendsClause: ModuleExtendsAmendsClause? by lazy {
    children.firstInstanceOf<ModuleExtendsAmendsClause>()
  }

  override val modifiers: List<Terminal> by lazy { moduleHeader?.modifiers ?: emptyList() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleDeclaration(this)
  }
}

class PklImportImpl(override val parent: Node, override val ctx: PklParser.ImportClauseContext) :
  AbstractNode(parent, ctx), PklImport {
  override val identifier: Terminal? by lazy { ctx.Identifier()?.toTerminal(this) }

  override val isGlob: Boolean by lazy { ctx.IMPORT_GLOB() != null }

  override val moduleUri: PklModuleUri? by lazy { PklModuleUriImpl(this, ctx) }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitImport(this)
  }
}

class ModuleExtendsAmendsClauseImpl(
  override val parent: Node,
  override val ctx: PklParser.ModuleExtendsOrAmendsClauseContext
) : AbstractNode(parent, ctx), ModuleExtendsAmendsClause {
  override val isAmend: Boolean
    get() = ctx.AMENDS() != null

  override val isExtend: Boolean
    get() = ctx.EXTENDS() != null

  override val moduleUri: PklModuleUri? by lazy { PklModuleUriImpl(this, ctx) }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleExtendsAmendsClause(this)
  }
}

class PklClassImpl(override val parent: Node, override val ctx: PklParser.ClazzContext) :
  AbstractNode(parent, ctx), PklClass {
  override val classHeader: ClassHeader by lazy { getChild(ClassHeaderImpl::class)!! }

  override val annotations: List<PklAnnotation>? by lazy { getChildren(PklAnnotationImpl::class) }

  override val classBody: ClassBody? by lazy { getChild(ClassBodyImpl::class) }

  override val name: String by lazy { ctx.classHeader().Identifier().text }

  override val modifiers: List<Terminal>? by lazy { classHeader.modifiers }

  override val cache: ClassMemberCache by lazy { ClassMemberCache.create(this) }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClass(this)
  }
}

class ClassHeaderImpl(override val parent: Node, override val ctx: PklParser.ClassHeaderContext) :
  AbstractNode(parent, ctx), ClassHeader {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }

  override val modifiers: List<Terminal> by lazy { terminals.takeWhile { it.isModifier } }

  override val typeParameterList: TypeParameterList? by lazy {
    getChild(TypeParameterListImpl::class)
  }

  override val extends: PklType? by lazy { children.last() as? PklType }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClassHeader(this)
  }
}

class ClassBodyImpl(override val parent: Node, override val ctx: PklParser.ClassBodyContext) :
  AbstractNode(parent, ctx), ClassBody {
  override val members: List<ClassMember> by lazy { children.filterIsInstance<ClassMember>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClassBody(this)
  }
}

class TypeParameterListImpl(
  override val parent: Node,
  override val ctx: PklParser.TypeParameterListContext
) : AbstractNode(parent, ctx), TypeParameterList {
  override val typeParameters: List<PklTypeParameter> by lazy {
    getChildren(PklTypeParameterImpl::class) ?: listOf()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeParameterList(this)
  }
}

class PklTypeParameterImpl(
  override val parent: Node,
  override val ctx: PklParser.TypeParameterContext
) : AbstractNode(parent, ctx), PklTypeParameter {
  override val variance: Variance? by lazy {
    if (ctx.IN() != null) Variance.IN else if (ctx.OUT() != null) Variance.OUT else null
  }

  override val name: String by lazy { ctx.Identifier().text }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeParameter(this)
  }
}

class PklModuleUriImpl(override val parent: Node, override val ctx: ParseTree) :
  AbstractNode(parent, ctx), PklModuleUri {
  override val stringConstant: PklStringConstant by lazy {
    children.firstInstanceOf<PklStringConstant>()!!
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleUri(this)
  }
}
