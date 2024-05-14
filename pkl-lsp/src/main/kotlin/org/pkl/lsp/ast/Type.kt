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
  AbstractNode(parent, ctx), StringLiteralPklType

class DeclaredPklTypeImpl(override val parent: Node, override val ctx: DeclaredTypeContext) :
  AbstractNode(parent, ctx), DeclaredPklType {
  override val name: TypeName by lazy {
    toTypeName(children.firstInstanceOf<QualifiedIdentifier>()!!)
  }

  private fun toTypeName(ident: QualifiedIdentifier): TypeName {
    return TypeNameImpl(ident, ctx.qualifiedIdentifier())
  }
}

class ParenthesizedPklTypeImpl(override val parent: Node, ctx: ParenthesizedTypeContext) :
  AbstractNode(parent, ctx), ParenthesizedPklType

class NullablePklTypeImpl(override val parent: Node, ctx: NullableTypeContext) :
  AbstractNode(parent, ctx), NullablePklType

class ConstrainedPklTypeImpl(override val parent: Node, ctx: ConstrainedTypeContext) :
  AbstractNode(parent, ctx), ConstrainedPklType

class UnionPklTypeImpl(override val parent: Node, ctx: UnionTypeContext) :
  AbstractNode(parent, ctx), UnionPklType

class FunctionPklTypeImpl(override val parent: Node, ctx: FunctionTypeContext) :
  AbstractNode(parent, ctx), FunctionPklType

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
