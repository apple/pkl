package org.pkl.cst;

import java.util.List;
import org.pkl.parser.Span;

public sealed interface Type {
  default Span getSpan() {
    return switch (this) {
      case UnknownType u -> u.span;
      case NothingType n -> n.span;
      case ModuleType m -> m.span;
      case StringConstantType s -> s.span;
      case DeclaredType d -> d.span;
      case ParenthesisedType p -> p.span;
      case NullableType n -> n.span;
      case ConstrainedType c -> c.span;
      case DefaultUnionType d -> d.span;
      case UnionType u -> u.span;
      case FunctionType f -> f.span;
    };
  }

  record UnknownType(Span span) implements Type {}

  record NothingType(Span span) implements Type {}

  record ModuleType(Span span) implements Type {}

  record StringConstantType(String str, Span span) implements Type {}

  record DeclaredType(List<Ident> name, List<Type> args, Span span) implements Type {}

  record ParenthesisedType(Type type, Span span) implements Type {}

  record NullableType(Type type, Span span) implements Type {}

  record ConstrainedType(Type type, List<Expr> expr, Span span) implements Type {}

  record DefaultUnionType(Type type, Span span) implements Type {}

  record UnionType(Type left, Type right, Span span) implements Type {}

  record FunctionType(List<Type> args, Type ret, Span span) implements Type {}
}
