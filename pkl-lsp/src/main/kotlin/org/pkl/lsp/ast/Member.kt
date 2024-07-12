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
import org.pkl.lsp.PklVisitor

class PklClassPropertyImpl(override val parent: Node, override val ctx: ClassPropertyContext) :
  AbstractNode(parent, ctx), PklClassProperty {
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

  override val typeAnnotation: PklTypeAnnotation? by lazy {
    children.firstInstanceOf<PklTypeAnnotation>()
  }

  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override val objectBody: PklObjectBody? by lazy { getChild(PklObjectBodyImpl::class) }

  override val name: String by lazy { ctx.Identifier().text }

  override val type: PklType? by lazy { typeAnnotation?.type }

  override val isDefinition: Boolean by lazy { expr != null }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClassProperty(this)
  }
}

class PklClassMethodImpl(override val parent: Node, override val ctx: ClassMethodContext) :
  AbstractNode(parent, ctx), PklClassMethod {
  override val methodHeader: PklMethodHeader by lazy { getChild(PklMethodHeaderImpl::class)!! }

  override val name: String by lazy { ctx.methodHeader().Identifier().text }

  override val modifiers: List<Terminal>? by lazy { methodHeader.modifiers }

  override val body: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitClassMethod(this)
  }
}

class PklMethodHeaderImpl(override val parent: Node, override val ctx: MethodHeaderContext) :
  AbstractNode(parent, ctx), PklMethodHeader {
  override val parameterList: PklParameterList? by lazy { getChild(PklParameterListImpl::class) }

  override val typeParameterList: PklTypeParameterList? by lazy {
    getChild(PklTypeParameterList::class)
  }

  override val modifiers: List<Terminal> by lazy { terminals.takeWhile { it.isModifier } }

  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }

  override val returnType: PklType? by lazy { children.firstInstanceOf<PklTypeAnnotation>()?.type }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMethodHeader(this)
  }
}

class PklParameterListImpl(override val parent: Node, override val ctx: ParameterListContext) :
  AbstractNode(parent, ctx), PklParameterList {
  override val elements: List<PklParameter> by lazy { children.filterIsInstance<PklParameter>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitParameterList(this)
  }

  override fun checkClosingDelimiter(): String? {
    if (ctx.parameter().isNotEmpty() && ctx.errs.size != ctx.parameter().size - 1) {
      return ","
    }
    return if (ctx.err != null) null else ")"
  }
}

class PklObjectBodyImpl(override val parent: Node, override val ctx: ObjectBodyContext) :
  AbstractNode(parent, ctx), PklObjectBody {
  override val parameterList: List<PklParameter> by lazy {
    children.filterIsInstance<PklParameter>()
  }

  override val members: List<PklObjectMember> by lazy {
    children.filterIsInstance<PklObjectMember>()
  }

  override val properties: List<PklObjectProperty> by lazy {
    members.filterIsInstance<PklObjectProperty>()
  }

  override val methods: List<PklObjectMethod> by lazy {
    members.filterIsInstance<PklObjectMethod>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitObjectBody(this)
  }

  override fun checkClosingDelimiter(): String? {
    if (ctx.parameter().isNotEmpty() && ctx.errs.size != ctx.parameter().size - 1) {
      return ","
    }
    return if (ctx.err != null) null else "}"
  }
}

class PklObjectPropertyImpl(override val parent: Node, override val ctx: ObjectPropertyContext) :
  AbstractNode(parent, ctx), PklObjectProperty {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val modifiers: List<Terminal> by lazy { terminals.takeWhile { it.isModifier } }
  override val name: String by lazy { ctx.Identifier().text }
  override val type: PklType? = null
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val isDefinition: Boolean by lazy { expr != null }
  override val typeAnnotation: PklTypeAnnotation? by lazy {
    children.firstInstanceOf<PklTypeAnnotation>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitObjectProperty(this)
  }
}

class PklObjectMethodImpl(override val parent: Node, override val ctx: ObjectMethodContext) :
  AbstractNode(parent, ctx), PklObjectMethod {
  override val methodHeader: PklMethodHeader by lazy { getChild(PklMethodHeaderImpl::class)!! }
  override val modifiers: List<Terminal>? by lazy { methodHeader.modifiers }
  override val body: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }
  override val name: String by lazy { methodHeader.identifier!!.text }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitObjectMethod(this)
  }
}

class PklObjectEntryImpl(override val parent: Node, override val ctx: ObjectEntryContext) :
  AbstractNode(parent, ctx), PklObjectEntry {
  override val keyExpr: PklExpr? by lazy { ctx.k.toNode(this) as? PklExpr }
  override val valueExpr: PklExpr? by lazy { ctx.v.toNode(this) as? PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitObjectEntry(this)
  }
}

class PklMemberPredicateImpl(override val parent: Node, override val ctx: MemberPredicateContext) :
  AbstractNode(parent, ctx), PklMemberPredicate {
  override val conditionExpr: PklExpr? by lazy { ctx.k.toNode(this) as? PklExpr }
  override val valueExpr: PklExpr? by lazy { ctx.v?.toNode(this) as? PklExpr }
  override val objectBodyList: List<PklObjectBody> by lazy {
    children.filterIsInstance<PklObjectBody>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMemberPredicate(this)
  }
}

class PklForGeneratorImpl(override val parent: Node, override val ctx: ForGeneratorContext) :
  AbstractNode(parent, ctx), PklForGenerator {
  override val iterableExpr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val parameters: List<PklParameter> by lazy { children.filterIsInstance<PklParameter>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitForGenerator(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklWhenGeneratorImpl(override val parent: Node, override val ctx: WhenGeneratorContext) :
  AbstractNode(parent, ctx), PklWhenGenerator {
  override val conditionExpr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val thenBody: PklObjectBody? by lazy { ctx.b1.toNode(this) as? PklObjectBody }
  override val elseBody: PklObjectBody? by lazy { ctx.b2?.toNode(this) as? PklObjectBody }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitWhenGenerator(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklObjectElementImpl(override val parent: Node, override val ctx: ObjectElementContext) :
  AbstractNode(parent, ctx), PklObjectElement {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitObjectElement(this)
  }
}

class PklObjectSpreadImpl(override val parent: Node, override val ctx: ObjectSpreadContext) :
  AbstractNode(parent, ctx), PklObjectSpread {
  override val expr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }
  override val isNullable: Boolean by lazy { ctx.QSPREAD() != null }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitObjectSpread(this)
  }
}
