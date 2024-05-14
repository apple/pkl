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

class ClassPropertyImpl(override val parent: Node, override val ctx: ClassPropertyContext) :
  AbstractNode(parent, ctx), ClassProperty {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }

  override val modifiers: List<Terminal> by lazy {
    buildList {
      children.forEach { node ->
        if (node is Terminal && modifierTypes.contains(node.type)) {
          add(node)
        }
      }
    }
  }

  override val typeAnnotation: TypeAnnotation? by lazy {
    children.firstInstanceOf<TypeAnnotation>()
  }

  override val expr: Expr? by lazy { children.firstInstanceOf<Expr>() }

  override val objectBody: ObjectBody? by lazy { getChild(ObjectBodyImpl::class) }

  override val name: String by lazy { ctx.Identifier().text }
}

class ClassMethodImpl(override val parent: Node, override val ctx: ClassMethodContext) :
  AbstractNode(parent, ctx), ClassMethod {
  override val methodHeader: MethodHeader by lazy { getChild(MethodHeaderImpl::class)!! }

  override val name: String by lazy { ctx.methodHeader().Identifier().text }

  override val modifiers: List<Terminal>? by lazy { methodHeader.modifiers }
}

class MethodHeaderImpl(override val parent: Node, override val ctx: MethodHeaderContext) :
  AbstractNode(parent, ctx), MethodHeader {
  override val parameterList: ParameterList? by lazy { getChild(ParameterListImpl::class) }

  override val typeParameterList: TypeParameterList? by lazy { getChild(TypeParameterList::class) }

  override val modifiers: List<Terminal> by lazy { terminals.takeWhile { it.isModifier } }

  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
}

class ParameterListImpl(override val parent: Node, override val ctx: ParameterListContext) :
  AbstractNode(parent, ctx), ParameterList

class ObjectBodyImpl(override val parent: Node, override val ctx: ObjectBodyContext) :
  AbstractNode(parent, ctx), ObjectBody {
  override val parameterList: ParameterList?
    get() = TODO("Not yet implemented")

  override val members: List<ObjectMember>?
    get() = TODO("Not yet implemented")
}

class ObjectPropertyImpl(override val parent: Node, override val ctx: ObjectPropertyContext) :
  AbstractNode(parent, ctx), ObjectProperty {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
}

class ObjectMethodImpl(override val parent: Node, override val ctx: ObjectMethodContext) :
  AbstractNode(parent, ctx), ObjectMethod

class ObjectEntryImpl(override val parent: Node, override val ctx: ObjectEntryContext) :
  AbstractNode(parent, ctx), ObjectEntry

class MemberPredicateImpl(override val parent: Node, override val ctx: MemberPredicateContext) :
  AbstractNode(parent, ctx), MemberPredicate

class ForGeneratorImpl(override val parent: Node, override val ctx: ForGeneratorContext) :
  AbstractNode(parent, ctx), ForGenerator

class WhenGeneratorImpl(override val parent: Node, override val ctx: WhenGeneratorContext) :
  AbstractNode(parent, ctx), WhenGenerator

class ObjectElementImpl(override val parent: Node, override val ctx: ObjectElementContext) :
  AbstractNode(parent, ctx), ObjectElement

class ObjectSpreadImpl(override val parent: Node, override val ctx: ObjectSpreadContext) :
  AbstractNode(parent, ctx), ObjectSpread
