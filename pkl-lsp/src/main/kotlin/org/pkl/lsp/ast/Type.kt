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

class TypeAnnotationImpl(override val parent: Node, ctx: TypeAnnotationContext) :
  AbstractNode(parent, ctx), TypeAnnotation {
  override val pklType: PklType? by lazy { children.firstInstanceOf<PklType>() }
}

class UnknownPklTypeImpl(override val parent: Node, ctx: UnknownTypeContext) :
  AbstractNode(parent, ctx), UnknownPklType

class NothingPklTypeImpl(override val parent: Node, ctx: NothingTypeContext) :
  AbstractNode(parent, ctx), NothingPklType

class ModulePklTypeImpl(override val parent: Node, ctx: ModuleTypeContext) :
  AbstractNode(parent, ctx), ModulePklType

class StringLiteralPklTypeImpl(override val parent: Node, ctx: StringLiteralTypeContext) :
  AbstractNode(parent, ctx), StringLiteralPklType {
  override val stringConstant: StringConstant by lazy {
    children.firstInstanceOf<StringConstant>()!!
  }
}

class DeclaredPklTypeImpl(override val parent: Node, override val ctx: DeclaredTypeContext) :
  AbstractNode(parent, ctx), DeclaredPklType {
  override val name: TypeName by lazy {
    toTypeName(children.firstInstanceOf<QualifiedIdentifier>()!!)
  }

  override val typeArgumentList: TypeArgumentList? by lazy {
    children.firstInstanceOf<TypeArgumentList>()
  }

  private fun toTypeName(ident: QualifiedIdentifier): TypeName {
    return TypeNameImpl(ident, ctx.qualifiedIdentifier())
  }
}

class TypeArgumentListImpl(override val parent: Node, ctx: TypeArgumentListContext) :
  AbstractNode(parent, ctx), TypeArgumentList {
  override val types: List<PklType> by lazy { children.filterIsInstance<PklType>() }
}

class ParenthesizedPklTypeImpl(override val parent: Node, ctx: ParenthesizedTypeContext) :
  AbstractNode(parent, ctx), ParenthesizedPklType {
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
}

class NullablePklTypeImpl(override val parent: Node, ctx: NullableTypeContext) :
  AbstractNode(parent, ctx), NullablePklType {
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
}

class ConstrainedPklTypeImpl(override val parent: Node, ctx: ConstrainedTypeContext) :
  AbstractNode(parent, ctx), ConstrainedPklType {
  override val type: PklType? by lazy { children.firstInstanceOf<PklType>() }
  override val exprs: List<Expr> by lazy { children.filterIsInstance<Expr>() }
}

class UnionPklTypeImpl(override val parent: Node, ctx: UnionTypeContext) :
  AbstractNode(parent, ctx), UnionPklType {
  override val typeList: List<PklType> by lazy { children.filterIsInstance<PklType>() }
  override val leftType: PklType by lazy { typeList[0] }
  override val rightType: PklType by lazy { typeList[1] }
}

class FunctionPklTypeImpl(override val parent: Node, ctx: FunctionTypeContext) :
  AbstractNode(parent, ctx), FunctionPklType {
  override val parameterList: List<PklType> by lazy {
    children.filterIsInstance<PklType>().dropLast(1)
  }
  override val returnType: PklType by lazy { children.filterIsInstance<PklType>().last() }
}

class DefaultUnionPklTypeImpl(override val parent: Node, ctx: DefaultUnionTypeContext) :
  AbstractNode(parent, ctx), DefaultUnionPklType {
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
}

class TypeAliasImpl(override val parent: Node?, ctx: TypeAliasContext) :
  AbstractNode(parent, ctx), TypeAlias {
  override val typeAliasHeader: TypeAliasHeader by lazy {
    children.firstInstanceOf<TypeAliasHeader>()!!
  }
  override val modifiers: List<Terminal>? by lazy { typeAliasHeader.modifiers }
  override val name: String by lazy { ctx.typeAliasHeader().Identifier().text }
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
  override val isRecursive: Boolean by lazy { isRecursive(mutableSetOf()) }
}

class TypeAliasHeaderImpl(override val parent: Node?, ctx: TypeAliasHeaderContext) :
  AbstractNode(parent, ctx), TypeAliasHeader {
  override val modifiers: List<Terminal>? by lazy { terminals.takeWhile { it.isModifier } }
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val typeParameterList: TypeParameterList? by lazy {
    children.firstInstanceOf<TypeParameterList>()
  }
}

class TypeNameImpl(ident: QualifiedIdentifier, override val ctx: QualifiedIdentifierContext) :
  AbstractNode(ident.parent, ctx), TypeName {
  override val module: Terminal? by lazy { ident.identifiers[0] }
  override val simpleTypeName: SimpleTypeName by lazy {
    SimpleTypeNameImpl(ident.identifiers.last(), ctx.Identifier().last())
  }
}

class SimpleTypeNameImpl(terminal: Terminal, override val ctx: ParseTree) :
  AbstractNode(terminal.parent, ctx), SimpleTypeName {
  override val identifier: Terminal? by lazy { terminal }
}
