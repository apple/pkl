package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

public sealed interface Expr {

  record This(Span span) implements Expr {}

  record Outer(Span span) implements Expr {}

  record Module(Span span) implements Expr {}

  record Null(Span span) implements Expr {}

  record BoolLiteral(boolean b, Span span) implements Expr {}

  record IntLiteral(long l, Span span) implements Expr {}

  record FloatLiteral(double d, Span span) implements Expr {}

  record SingleString(String str, Span span) implements Expr {}

  record MultiString(String str, Span span) implements Expr {}

  record Throw(Expr expr, Span span) implements Expr {}

  record Trace(Expr expr, Span span) implements Expr {}

  record ImportExpr(String importStr, Span span) implements Expr {}

  record ImportGlobExpr(String importStr, Span span) implements Expr {}

  record Read(Expr expr, Span span) implements Expr {}

  record ReadGlob(Expr expr, Span span) implements Expr {}

  record ReadNull(Expr expr, Span span) implements Expr {}

  record UnqualifiedAccess(Ident ident, List<Expr> args, Span span) implements Expr {}

  record QualifiedAccess(Expr expr, Ident ident, boolean isNullable, List<Expr> args, Span span)
      implements Expr {}

  record SuperAccess(Ident ident, List<Expr> args, Span span) implements Expr {}

  record SuperSubscript(Expr arg, Span span) implements Expr {}

  record Subscript(Expr expr, Expr arg, Span span) implements Expr {}

  record If(Expr cond, Expr then, Expr els, Span span) implements Expr {}

  record Let(Parameter par, Expr bindingExpr, Expr expr, Span span) implements Expr {}

  record FunctionLiteral(List<Parameter> args, Expr expr, Span span) implements Expr {}

  record Parenthesised(Expr expr, Span span) implements Expr {}

  record New(@Nullable Type type, ObjectBody body, Span span) implements Expr {}

  record Amends(Expr expr, ObjectBody body, Span span) implements Expr {}

  record NonNull(Expr expr, Span span) implements Expr {}

  record UnaryMinus(Expr expr, Span span) implements Expr {}

  record LogicalNot(Expr expr, Span span) implements Expr {}

  record BinaryOp(Expr expr1, Expr expr2, Operation op, Span span) implements Expr {}

  default Span getSpan() {
    return switch (this) {
      case This x -> x.span;
      case Outer x -> x.span;
      case Module x -> x.span;
      case Null x -> x.span;
      case BoolLiteral x -> x.span;
      case IntLiteral x -> x.span;
      case FloatLiteral x -> x.span;
      case SingleString x -> x.span;
      case MultiString x -> x.span;
      case Throw x -> x.span;
      case Trace x -> x.span;
      case ImportExpr x -> x.span;
      case ImportGlobExpr x -> x.span;
      case Read x -> x.span;
      case ReadGlob x -> x.span;
      case ReadNull x -> x.span;
      case UnqualifiedAccess x -> x.span;
      case QualifiedAccess x -> x.span;
      case SuperAccess x -> x.span;
      case SuperSubscript x -> x.span;
      case Subscript x -> x.span;
      case NonNull x -> x.span;
      case UnaryMinus x -> x.span;
      case LogicalNot x -> x.span;
      case If x -> x.span;
      case Let x -> x.span;
      case FunctionLiteral x -> x.span;
      case Parenthesised x -> x.span;
      case New x -> x.span;
      case Amends x -> x.span;
      case BinaryOp x -> x.span;
    };
  }
}
