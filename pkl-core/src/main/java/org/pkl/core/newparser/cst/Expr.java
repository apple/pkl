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

public sealed interface Expr {

  record This(Span span) implements Expr {}

  record Outer(Span span) implements Expr {}

  record Module(Span span) implements Expr {}

  record NullLiteral(Span span) implements Expr {}

  record BoolLiteral(boolean b, Span span) implements Expr {}

  record IntLiteral(String number, Span span) implements Expr {}

  record FloatLiteral(String number, Span span) implements Expr {}

  record StringConstant(String str, Span span) implements Expr {}

  record InterpolatedString(List<Expr> exprs, Span span) implements Expr {}

  record InterpolatedMultiString(List<Expr> exprs, Span span) implements Expr {}

  record Throw(Expr expr, Span span) implements Expr {}

  record Trace(Expr expr, Span span) implements Expr {}

  record ImportExpr(String importStr, Span span) implements Expr {}

  record ImportGlobExpr(String importStr, Span span) implements Expr {}

  record Read(Expr expr, Span span) implements Expr {}

  record ReadGlob(Expr expr, Span span) implements Expr {}

  record ReadNull(Expr expr, Span span) implements Expr {}

  record UnqualifiedAccess(Ident ident, @Nullable List<Expr> args, Span span) implements Expr {}

  record QualifiedAccess(
      Expr expr, Ident ident, boolean isNullable, @Nullable List<Expr> args, Span span)
      implements Expr {}

  record SuperAccess(Ident ident, List<Expr> args, Span span) implements Expr {}

  record SuperSubscript(Expr arg, Span span) implements Expr {}

  record Subscript(Expr expr, Expr arg, Span span) implements Expr {}

  record If(Expr cond, Expr then, Expr els, Span span) implements Expr {}

  record Let(Parameter par, Expr bindingExpr, Expr expr, Span span) implements Expr {}

  record FunctionLiteral(List<Parameter> args, Expr expr, Span span) implements Expr {}

  record Parenthesized(Expr expr, Span span) implements Expr {}

  record New(@Nullable Type type, ObjectBody body, Span span) implements Expr {}

  record Amends(Expr expr, ObjectBody body, Span span) implements Expr {}

  record NonNull(Expr expr, Span span) implements Expr {}

  record UnaryMinus(Expr expr, Span span) implements Expr {}

  record LogicalNot(Expr expr, Span span) implements Expr {}

  record BinaryOp(Expr left, Expr right, Operator op, Span span) implements Expr {}

  record TypeCheck(Expr expr, Type type, Span span) implements Expr {}

  record TypeCast(Expr expr, Type type, Span span) implements Expr {}

  /** This is a synthetic class only used at parse time. */
  record OperatorExpr(Operator op, Span span) implements Expr {}

  /** This is a synthetic class only used at parse time. */
  record TypeExpr(Type type) implements Expr {}

  default Span getSpan() {
    if (this instanceof This x) return x.span;
    if (this instanceof Outer x) return x.span;
    if (this instanceof Module x) return x.span;
    if (this instanceof NullLiteral x) return x.span;
    if (this instanceof BoolLiteral x) return x.span;
    if (this instanceof IntLiteral x) return x.span;
    if (this instanceof FloatLiteral x) return x.span;
    if (this instanceof StringConstant x) return x.span;
    if (this instanceof InterpolatedString x) return x.span;
    if (this instanceof InterpolatedMultiString x) return x.span;
    if (this instanceof Throw x) return x.span;
    if (this instanceof Trace x) return x.span;
    if (this instanceof ImportExpr x) return x.span;
    if (this instanceof ImportGlobExpr x) return x.span;
    if (this instanceof Read x) return x.span;
    if (this instanceof ReadGlob x) return x.span;
    if (this instanceof ReadNull x) return x.span;
    if (this instanceof UnqualifiedAccess x) return x.span;
    if (this instanceof QualifiedAccess x) return x.span;
    if (this instanceof SuperAccess x) return x.span;
    if (this instanceof SuperSubscript x) return x.span;
    if (this instanceof NonNull x) return x.span;
    if (this instanceof UnaryMinus x) return x.span;
    if (this instanceof LogicalNot x) return x.span;
    if (this instanceof If x) return x.span;
    if (this instanceof Let x) return x.span;
    if (this instanceof FunctionLiteral x) return x.span;
    if (this instanceof Parenthesized x) return x.span;
    if (this instanceof New x) return x.span;
    if (this instanceof Amends x) return x.span;
    if (this instanceof BinaryOp x) return x.span;
    if (this instanceof Subscript x) return x.span;
    if (this instanceof TypeCast x) return x.span;
    if (this instanceof TypeCheck x) return x.span;
    if (this instanceof OperatorExpr x) return x.span;
    if (this instanceof TypeExpr x) return x.type.getSpan();
    throw new RuntimeException("Unknown expr: " + this);
  }
}
