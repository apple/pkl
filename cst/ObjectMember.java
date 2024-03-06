package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

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
    return switch (this) {
      case ObjectElement o -> o.span;
      case ObjectProperty o -> o.span;
      case ObjectBodyProperty o -> o.span;
      case ObjectMethod o -> o.span;
      case MemberPredicate m -> m.span;
      case MemberPredicateBody m -> m.span;
      case ObjectEntry o -> o.span;
      case ObjectEntryBody o -> o.span;
      case ObjectSpread o -> o.span;
      case WhenGenerator w -> w.span;
      case ForGenerator f -> f.span;
    };
  }
}
