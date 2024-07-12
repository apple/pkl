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

import java.net.URI
import org.pkl.core.parser.antlr.PklParser
import org.pkl.core.parser.antlr.PklParser.ModuleHeaderContext
import org.pkl.lsp.*
import org.pkl.lsp.LSPUtil.firstInstanceOf

class PklModuleImpl(
  override val ctx: PklParser.ModuleContext,
  override val uri: URI,
  override val virtualFile: VirtualFile
) : AbstractNode(null, ctx), PklModule {
  override val isAmend: Boolean by lazy {
    declaration?.moduleExtendsAmendsClause?.isAmend
      ?: declaration?.moduleHeader?.moduleExtendsAmendsClause?.isAmend ?: false
  }

  override val declaration: PklModuleDeclaration? by lazy {
    getChild(PklModuleDeclarationImpl::class)
  }

  override val members: List<PklModuleMember> by lazy {
    children.filterIsInstance<PklModuleMember>()
  }

  override val imports: List<PklImport> by lazy { children.filterIsInstance<PklImport>() }

  override val typeAliases: List<PklTypeAlias> by lazy { children.filterIsInstance<PklTypeAlias>() }

  override val classes: List<PklClass> by lazy { children.filterIsInstance<PklClass>() }

  private val extendsAmendsUri: PklModuleUri? by lazy {
    declaration?.moduleExtendsAmendsClause?.moduleUri
  }

  override val supermodule: PklModule? by lazy { extendsAmendsUri?.resolve() }

  override val cache: ModuleMemberCache by lazy { ModuleMemberCache.create(this) }

  override val modifiers: List<Terminal>? by lazy { declaration?.moduleHeader?.modifiers }

  override val typeDefs: List<PklTypeDef> by lazy { children.filterIsInstance<PklTypeDef>() }

  override val typeDefsAndProperties: List<PklTypeDefOrProperty> by lazy {
    members.filterIsInstance<PklTypeDefOrProperty>()
  }

  override val properties: List<PklClassProperty> by lazy {
    members.filterIsInstance<PklClassProperty>()
  }

  override val methods: List<PklClassMethod> by lazy { members.filterIsInstance<PklClassMethod>() }

  override val shortDisplayName: String by lazy {
    declaration?.moduleHeader?.shortDisplayName
      ?: uri.toString().substringAfterLast('/').replace(".pkl", "")
  }

  override val moduleName: String? by lazy {
    declaration?.moduleHeader?.moduleName
      ?: uri.toString().substringAfterLast('/').replace(".pkl", "")
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModule(this)
  }
}

class PklAnnotationImpl(override val parent: Node, override val ctx: PklParser.AnnotationContext) :
  AbstractNode(parent, ctx), PklAnnotation {
  override val type: PklType? by lazy { children.firstInstanceOf<PklType>() }

  override val typeName: PklTypeName? by lazy { (type as? PklDeclaredType)?.name }

  override val objectBody: PklObjectBody? by lazy { children.firstInstanceOf<PklObjectBody>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitAnnotation(this)
  }
}

class PklModuleHeaderImpl(override val parent: Node, override val ctx: ModuleHeaderContext) :
  AbstractNode(parent, ctx), PklModuleHeader {
  override val qualifiedIdentifier: PklQualifiedIdentifier? by lazy {
    getChild(PklQualifiedIdentifierImpl::class)
  }

  override val moduleExtendsAmendsClause: PklModuleExtendsAmendsClause? by lazy {
    getChild(PklModuleExtendsAmendsClauseImpl::class)
  }

  override val modifiers: List<Terminal>? by lazy { terminals.takeWhile { it.isModifier } }

  override val shortDisplayName: String? by lazy {
    qualifiedIdentifier?.fullName?.substringAfterLast('.')
  }

  override val moduleName: String? by lazy { qualifiedIdentifier?.fullName }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleHeader(this)
  }
}

class PklModuleDeclarationImpl(
  override val parent: Node,
  override val ctx: PklParser.ModuleDeclContext
) : AbstractNode(parent, ctx), PklModuleDeclaration {

  override val annotations: List<PklAnnotation> by lazy {
    ctx.annotation().map { PklAnnotationImpl(this, it) }
  }

  override val moduleHeader: PklModuleHeader? by lazy {
    ctx.moduleHeader()?.let { PklModuleHeaderImpl(this, it) }
  }

  override val moduleExtendsAmendsClause: PklModuleExtendsAmendsClause? by lazy {
    moduleHeader?.moduleExtendsAmendsClause
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

class PklModuleExtendsAmendsClauseImpl(
  override val parent: Node,
  override val ctx: PklParser.ModuleExtendsOrAmendsClauseContext
) : AbstractNode(parent, ctx), PklModuleExtendsAmendsClause {
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
  override val classHeader: PklClassHeader by lazy { getChild(PklClassHeaderImpl::class)!! }

  override val annotations: List<PklAnnotation>? by lazy { getChildren(PklAnnotationImpl::class) }

  override val classBody: PklClassBody? by lazy { getChild(PklClassBodyImpl::class) }

  override val name: String by lazy { ctx.classHeader().Identifier().text }

  override val modifiers: List<Terminal>? by lazy { classHeader.modifiers }

  override val cache: ClassMemberCache by lazy { ClassMemberCache.create(this) }

  override val typeParameterList: PklTypeParameterList? by lazy { classHeader.typeParameterList }

  override val members: List<PklClassMember> by lazy { classBody?.members ?: emptyList() }

  override val properties: List<PklClassProperty> by lazy {
    members.filterIsInstance<PklClassProperty>()
  }

  override val methods: List<PklClassMethod> by lazy { members.filterIsInstance<PklClassMethod>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClass(this)
  }
}

class PklClassHeaderImpl(
  override val parent: Node,
  override val ctx: PklParser.ClassHeaderContext
) : AbstractNode(parent, ctx), PklClassHeader {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }

  override val modifiers: List<Terminal> by lazy { terminals.takeWhile { it.isModifier } }

  override val typeParameterList: PklTypeParameterList? by lazy {
    getChild(PklTypeParameterListImpl::class)
  }

  override val extends: PklType? by lazy { children.last() as? PklType }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClassHeader(this)
  }
}

class PklClassBodyImpl(override val parent: Node, override val ctx: PklParser.ClassBodyContext) :
  AbstractNode(parent, ctx), PklClassBody {
  override val members: List<PklClassMember> by lazy { children.filterIsInstance<PklClassMember>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClassBody(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else "}"
  }
}

class PklTypeParameterListImpl(
  override val parent: Node,
  override val ctx: PklParser.TypeParameterListContext
) : AbstractNode(parent, ctx), PklTypeParameterList {
  override val typeParameters: List<PklTypeParameter> by lazy {
    getChildren(PklTypeParameterImpl::class) ?: listOf()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeParameterList(this)
  }

  override fun checkClosingDelimiter(): String? {
    if (ctx.typeParameter().isNotEmpty() && ctx.errs.size != ctx.typeParameter().size - 1) {
      return ","
    }
    return if (ctx.err != null) null else ")"
  }
}

class PklTypeParameterImpl(
  override val parent: Node,
  override val ctx: PklParser.TypeParameterContext
) : AbstractNode(parent, ctx), PklTypeParameter {
  override val variance: Variance? by lazy {
    if (ctx.IN() != null) Variance.IN else if (ctx.OUT() != null) Variance.OUT else null
  }

  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }

  override val name: String by lazy { ctx.Identifier().text }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeParameter(this)
  }
}
