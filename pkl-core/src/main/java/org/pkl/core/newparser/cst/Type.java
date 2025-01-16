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

public sealed interface Type {
  default Span getSpan() {
    if (this instanceof UnknownType x) return x.span;
    if (this instanceof NothingType x) return x.span;
    if (this instanceof ModuleType x) return x.span;
    if (this instanceof StringConstantType x) return x.span;
    if (this instanceof DeclaredType x) return x.span;
    if (this instanceof ParenthesizedType x) return x.span;
    if (this instanceof NullableType x) return x.span;
    if (this instanceof ConstrainedType x) return x.span;
    if (this instanceof DefaultUnionType x) return x.span;
    if (this instanceof UnionType x) return x.span;
    if (this instanceof FunctionType x) return x.span;
    throw new RuntimeException("Unknown type " + this);
  }

  record UnknownType(Span span) implements Type {}

  record NothingType(Span span) implements Type {}

  record ModuleType(Span span) implements Type {}

  record StringConstantType(String str, Span span) implements Type {}

  record DeclaredType(QualifiedIdent name, List<Type> args, Span span) implements Type {}

  record ParenthesizedType(Type type, Span span) implements Type {}

  record NullableType(Type type, Span span) implements Type {}

  record ConstrainedType(Type type, List<Expr> expr, Span span) implements Type {}

  record DefaultUnionType(Type type, Span span) implements Type {}

  record UnionType(Type left, Type right, Span span) implements Type {}

  record FunctionType(List<Type> args, Type ret, Span span) implements Type {}
}
