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

sealed class ObjectMember(open val span: Span) {
  data class Element(val expr: Expr, override val span: Span) : ObjectMember(span)

  data class Property(
    val modifiers: List<Modifier>,
    val ident: Ident,
    val type: Type?,
    val expr: Expr,
    override val span: Span
  ) : ObjectMember(span)

  data class PropertyBody(
    val modifiers: List<Modifier>,
    val ident: Ident,
    val bodyList: List<ObjectBody>,
    override val span: Span
  ) : ObjectMember(span)

  data class Method(
    val modifiers: List<Modifier>,
    val ident: Ident,
    val typePars: List<TypeParameter>,
    val args: List<Parameter>,
    val returnType: Type?,
    val expr: Expr,
    override val span: Span
  ) : ObjectMember(span)

  data class MemberPredicate(val pred: Expr, val expr: Expr, override val span: Span) :
    ObjectMember(span)

  data class MemberPredicateBody(
    val key: Expr,
    val bodyList: List<ObjectBody>,
    override val span: Span
  ) : ObjectMember(span)

  data class Entry(val key: Expr, val value: Expr, override val span: Span) : ObjectMember(span)

  data class EntryBody(val key: Expr, val bodyList: List<ObjectBody>, override val span: Span) :
    ObjectMember(span)

  data class Spread(val expr: Expr, val isNullable: Boolean, override val span: Span) :
    ObjectMember(span)

  data class WhenGenerator(
    val cond: Expr,
    val body: ObjectBody,
    val elseBody: ObjectBody?,
    override val span: Span
  ) : ObjectMember(span)

  data class ForGenerator(
    val p1: Parameter,
    val p2: Parameter?,
    val expr: Expr,
    val body: ObjectBody,
    override val span: Span
  ) : ObjectMember(span)
}

data class ObjectBody(val pars: List<Parameter>, val members: List<ObjectMember>, val span: Span)
