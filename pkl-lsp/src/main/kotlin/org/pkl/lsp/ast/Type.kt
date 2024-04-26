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

import org.pkl.core.parser.antlr.PklParser.*
import org.pkl.lsp.LSPUtil.firstInstanceOf

class TypeAnnotationImpl(override val parent: Node, ctx: TypeAnnotationContext) :
  AbstractNode(parent, ctx), TypeAnnotation {
  override val type: Type? by lazy { children.firstInstanceOf<Type>() }
}

class UnknownTypeImpl(override val parent: Node, ctx: UnknownTypeContext) :
  AbstractNode(parent, ctx), UnknownType

class NothingTypeImpl(override val parent: Node, ctx: NothingTypeContext) :
  AbstractNode(parent, ctx), NothingType

class ModuleTypeImpl(override val parent: Node, ctx: ModuleTypeContext) :
  AbstractNode(parent, ctx), ModuleType

class StringLiteralTypeImpl(override val parent: Node, ctx: StringLiteralTypeContext) :
  AbstractNode(parent, ctx), StringLiteralType

class DeclaredTypeImpl(override val parent: Node, ctx: DeclaredTypeContext) :
  AbstractNode(parent, ctx), DeclaredType

class ParenthesizedTypeImpl(override val parent: Node, ctx: ParenthesizedTypeContext) :
  AbstractNode(parent, ctx), ParenthesizedType

class NullableTypeImpl(override val parent: Node, ctx: NullableTypeContext) :
  AbstractNode(parent, ctx), NullableType

class ConstrainedTypeImpl(override val parent: Node, ctx: ConstrainedTypeContext) :
  AbstractNode(parent, ctx), ConstrainedType

class UnionTypeImpl(override val parent: Node, ctx: UnionTypeContext) :
  AbstractNode(parent, ctx), UnionType

class FunctionTypeImpl(override val parent: Node, ctx: FunctionTypeContext) :
  AbstractNode(parent, ctx), FunctionType
