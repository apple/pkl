/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

public sealed interface ObjectMember {

  record ObjectElement(Expr expr, Span span) implements ObjectMember {}

  record ObjectProperty(
      List<Modifier> modifiers, Ident ident, @Nullable Type type, Expr expr, Span span)
      implements ObjectMember {}

  record ObjectBodyProperty(
      List<Modifier> modifiers, Ident ident, List<ObjectBody> bodyList, Span span)
      implements ObjectMember {}

  record ObjectMethod(
      List<Modifier> modifiers,
      Ident ident,
      List<TypeParameter> typePars,
      List<Parameter> args,
      @Nullable Type returnType,
      Expr expr,
      Span span)
      implements ObjectMember {}

  record MemberPredicate(Expr pred, Expr expr, Span span) implements ObjectMember {}

  record MemberPredicateBody(Expr key, List<ObjectBody> bodyList, Span span)
      implements ObjectMember {}

  record ObjectEntry(Expr key, Expr value, Span span) implements ObjectMember {}

  record ObjectEntryBody(Expr key, List<ObjectBody> bodyList, Span span) implements ObjectMember {}

  record ObjectSpread(Expr expr, boolean isNullable, Span span) implements ObjectMember {}

  record WhenGenerator(Expr cond, ObjectBody body, @Nullable ObjectBody elseClause, Span span)
      implements ObjectMember {}

  record ForGenerator(Parameter p1, @Nullable Parameter p2, Expr expr, ObjectBody body, Span span)
      implements ObjectMember {}

  default Span getSpan() {
    if (this instanceof ObjectElement o) return o.span;
    if (this instanceof ObjectProperty o) return o.span;
    if (this instanceof ObjectBodyProperty o) return o.span;
    if (this instanceof ObjectMethod o) return o.span;
    if (this instanceof MemberPredicate o) return o.span;
    if (this instanceof MemberPredicateBody o) return o.span;
    if (this instanceof ObjectEntry o) return o.span;
    if (this instanceof ObjectEntryBody o) return o.span;
    if (this instanceof ObjectSpread o) return o.span;
    if (this instanceof WhenGenerator o) return o.span;
    if (this instanceof ForGenerator o) return o.span;
    throw new RuntimeException("Unknown object member " + this);
  }
}
