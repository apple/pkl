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

import org.pkl.core.parser.antlr.PklParser
import org.pkl.core.parser.antlr.PklParser.ModuleHeaderContext
import org.pkl.lsp.LSPUtil.firstInstanceOf

class PklModuleImpl(override val ctx: PklParser.ModuleContext) :
  AbstractNode(null, ctx), PklModule {
  override val isAmend: Boolean by lazy {
    declaration?.moduleExtendsAmendsClause?.isAmend
      ?: declaration?.moduleHeader?.moduleExtendsAmendsClause?.isAmend ?: false
  }

  override val declaration: ModuleDeclaration? by lazy { getChild(ModuleDeclarationImpl::class) }

  override val members: List<ModuleMember> by lazy { children.filterIsInstance<ModuleMember>() }

  override val imports: List<ImportClause>? by lazy { getChildren(ImportClauseImpl::class) }

  override val typeAliases: List<TypeAlias> by lazy { children.filterIsInstance<TypeAlias>() }

  override val classes: List<Clazz> by lazy { children.filterIsInstance<Clazz>() }

  // TODO: resolve the super module
  override val supermodule: PklModule? by lazy { null }

  override val cache: ModuleMemberCache by lazy { ModuleMemberCache.create(this) }

  override val modifiers: List<Terminal>? by lazy { terminals.takeWhile { it.isModifier } }
}

class AnnotationImpl(override val parent: Node, override val ctx: PklParser.AnnotationContext) :
  AbstractNode(parent, ctx), Annotation {
  override val typeName: TypeName
    get() = TODO("Not yet implemented")

  override val objectBody: ObjectBody
    get() = TODO("Not yet implemented")
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
}

class ModuleDeclarationImpl(
  override val parent: Node,
  override val ctx: PklParser.ModuleDeclContext
) : AbstractNode(parent, ctx), ModuleDeclaration {

  override val annotations: List<Annotation> by lazy {
    ctx.annotation().map { AnnotationImpl(this, it) }
  }

  override val moduleHeader: ModuleHeader? by lazy {
    ctx.moduleHeader()?.let { ModuleHeaderImpl(this, it) }
  }

  override val moduleExtendsAmendsClause: ModuleExtendsAmendsClause? by lazy {
    children.firstInstanceOf<ModuleExtendsAmendsClause>()
  }

  override val modifiers: List<Terminal> by lazy { moduleHeader?.modifiers ?: emptyList() }
}

class ImportClauseImpl(override val parent: Node, override val ctx: PklParser.ImportClauseContext) :
  AbstractNode(parent, ctx), ImportClause {
  override val identifier: Terminal? by lazy { ctx.Identifier()?.toTerminal(this) }

  override val isGlob: Boolean by lazy { ctx.IMPORT_GLOB() != null }

  override val moduleUri: String
    get() = TODO("Not yet implemented")
}

class ModuleExtendsAmendsClauseImpl(
  override val parent: Node,
  override val ctx: PklParser.ModuleExtendsOrAmendsClauseContext
) : AbstractNode(parent, ctx), ModuleExtendsAmendsClause {
  override val isAmend: Boolean
    get() = ctx.AMENDS() != null

  override val isExtend: Boolean
    get() = ctx.EXTENDS() != null

  override val moduleUri: String? by lazy { getChild(StringConstantImpl::class)?.value }
}

class ClazzImpl(override val parent: Node, override val ctx: PklParser.ClazzContext) :
  AbstractNode(parent, ctx), Clazz {
  override val classHeader: ClassHeader by lazy { getChild(ClassHeaderImpl::class)!! }

  override val annotations: List<Annotation>? by lazy { getChildren(AnnotationImpl::class) }

  override val classBody: ClassBody? by lazy { getChild(ClassBodyImpl::class) }

  override val name: String by lazy { ctx.classHeader().Identifier().text }

  override val modifiers: List<Terminal>? by lazy { classHeader.modifiers }

  override val cache: ClassMemberCache by lazy { ClassMemberCache.create(this) }
}

class ClassHeaderImpl(override val parent: Node, override val ctx: PklParser.ClassHeaderContext) :
  AbstractNode(parent, ctx), ClassHeader {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }

  override val modifiers: List<Terminal> by lazy { terminals.takeWhile { it.isModifier } }

  override val typeParameterList: TypeParameterList? by lazy {
    getChild(TypeParameterListImpl::class)
  }

  override val extends: PklType? by lazy { children.last() as? PklType }
}

class ClassBodyImpl(override val parent: Node, override val ctx: PklParser.ClassBodyContext) :
  AbstractNode(parent, ctx), ClassBody {
  override val members: List<ClassMember> by lazy { children.filterIsInstance<ClassMember>() }
}

class TypeParameterListImpl(
  override val parent: Node,
  override val ctx: PklParser.TypeParameterListContext
) : AbstractNode(parent, ctx), TypeParameterList {
  override val typeParameters: List<TypeParameter> by lazy {
    getChildren(TypeParameterImpl::class) ?: listOf()
  }
}

class TypeParameterImpl(
  override val parent: Node,
  override val ctx: PklParser.TypeParameterContext
) : AbstractNode(parent, ctx), TypeParameter {
  override val variance: Variance? by lazy {
    if (ctx.IN() != null) Variance.IN else if (ctx.OUT() != null) Variance.OUT else null
  }

  override val name: String by lazy { ctx.Identifier().text }
}
