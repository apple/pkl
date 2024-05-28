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
import org.pkl.core.parser.antlr.PklParser.*
import org.pkl.lsp.LSPUtil.firstInstanceOf
import org.pkl.lsp.PklVisitor

class TypeAnnotationImpl(override val parent: Node, ctx: TypeAnnotationContext) :
  AbstractNode(parent, ctx), TypeAnnotation {
  override val pklType: PklType? by lazy { children.firstInstanceOf<PklType>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeAnnotation(this)
  }
}

class PklUnknownTypeImpl(override val parent: Node, ctx: UnknownTypeContext) :
  AbstractNode(parent, ctx), PklUnknownType {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitUnknownType(this)
  }
}

class PklNothingTypeImpl(override val parent: Node, ctx: NothingTypeContext) :
  AbstractNode(parent, ctx), PklNothingType {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNothingType(this)
  }
}

class PklModuleTypeImpl(override val parent: Node, ctx: ModuleTypeContext) :
  AbstractNode(parent, ctx), PklModuleType {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleType(this)
  }
}

class PklStringLiteralTypeImpl(override val parent: Node, ctx: StringLiteralTypeContext) :
  AbstractNode(parent, ctx), PklStringLiteralType {
  override val stringConstant: PklStringConstant by lazy {
    children.firstInstanceOf<PklStringConstant>()!!
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitStringLiteralType(this)
  }
}

class PklDeclaredTypeImpl(override val parent: Node, override val ctx: DeclaredTypeContext) :
  AbstractNode(parent, ctx), PklDeclaredType {
  override val name: TypeName by lazy {
    toTypeName(children.firstInstanceOf<QualifiedIdentifier>()!!)
  }

  override val typeArgumentList: TypeArgumentList? by lazy {
    children.firstInstanceOf<TypeArgumentList>()
  }

  private fun toTypeName(ident: QualifiedIdentifier): TypeName {
    return TypeNameImpl(ident, ctx.qualifiedIdentifier())
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitDeclaredType(this)
  }
}

class TypeArgumentListImpl(override val parent: Node, ctx: TypeArgumentListContext) :
  AbstractNode(parent, ctx), TypeArgumentList {
  override val types: List<PklType> by lazy { children.filterIsInstance<PklType>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeArgumentList(this)
  }
}

class PklParenthesizedTypeImpl(override val parent: Node, ctx: ParenthesizedTypeContext) :
  AbstractNode(parent, ctx), PklParenthesizedType {
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitParenthesizedType(this)
  }
}

class PklNullableTypeImpl(override val parent: Node, ctx: NullableTypeContext) :
  AbstractNode(parent, ctx), PklNullableType {
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNullableType(this)
  }
}

class PklConstrainedTypeImpl(override val parent: Node, ctx: ConstrainedTypeContext) :
  AbstractNode(parent, ctx), PklConstrainedType {
  override val type: PklType? by lazy { children.firstInstanceOf<PklType>() }
  override val exprs: List<PklExpr> by lazy { children.filterIsInstance<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitConstrainedType(this)
  }
}

class PklUnionTypeImpl(override val parent: Node, ctx: UnionTypeContext) :
  AbstractNode(parent, ctx), PklUnionType {
  override val typeList: List<PklType> by lazy { children.filterIsInstance<PklType>() }
  override val leftType: PklType by lazy { typeList[0] }
  override val rightType: PklType by lazy { typeList[1] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitUnionType(this)
  }
}

class PklFunctionTypeImpl(override val parent: Node, ctx: FunctionTypeContext) :
  AbstractNode(parent, ctx), PklFunctionType {
  override val parameterList: List<PklType> by lazy {
    children.filterIsInstance<PklType>().dropLast(1)
  }
  override val returnType: PklType by lazy { children.filterIsInstance<PklType>().last() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFunctionType(this)
  }
}

class PklDefaultUnionTypeImpl(override val parent: Node, ctx: DefaultUnionTypeContext) :
  AbstractNode(parent, ctx), PklDefaultUnionType {
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitDefaultUnionType(this)
  }
}

class PklTypeAliasImpl(override val parent: Node?, ctx: TypeAliasContext) :
  AbstractNode(parent, ctx), PklTypeAlias {
  override val typeAliasHeader: TypeAliasHeader by lazy {
    children.firstInstanceOf<TypeAliasHeader>()!!
  }
  override val modifiers: List<Terminal>? by lazy { typeAliasHeader.modifiers }
  override val name: String by lazy { ctx.typeAliasHeader().Identifier().text }
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
  override val isRecursive: Boolean by lazy { isRecursive(mutableSetOf()) }
  override val typeParameterList: PklTypeParameterList? by lazy {
    typeAliasHeader.typeParameterList
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeAlias(this)
  }
}

class TypeAliasHeaderImpl(override val parent: Node?, ctx: TypeAliasHeaderContext) :
  AbstractNode(parent, ctx), TypeAliasHeader {
  override val modifiers: List<Terminal>? by lazy { terminals.takeWhile { it.isModifier } }
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val typeParameterList: PklTypeParameterList? by lazy {
    children.firstInstanceOf<PklTypeParameterList>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeAliasHeader(this)
  }
}

class TypeNameImpl(ident: QualifiedIdentifier, override val ctx: QualifiedIdentifierContext) :
  AbstractNode(ident.parent, ctx), TypeName {
  override val module: Terminal? by lazy { ident.identifiers[0] }
  override val simpleTypeName: SimpleTypeName by lazy {
    SimpleTypeNameImpl(ident.identifiers.last(), ctx.Identifier().last())
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeName(this)
  }
}

class SimpleTypeNameImpl(terminal: Terminal, override val ctx: ParseTree) :
  AbstractNode(terminal.parent, ctx), SimpleTypeName {
  override val identifier: Terminal? by lazy { terminal }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSimpleTypeName(this)
  }
}
