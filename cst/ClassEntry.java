package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;
import org.pkl.parser.Token;

public sealed interface ClassEntry extends ModuleEntry {

  record ClassProperty(
      @Nullable Token.Comment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      Type type,
      Span span)
      implements ClassEntry {}

  record ClassPropertyExpr(
      @Nullable Token.Comment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      @Nullable Type type,
      Expr expr,
      Span span)
      implements ClassEntry {}

  record ClassPropertyBody(
      @Nullable Token.Comment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      @Nullable Type type,
      List<ObjectBody> bodyList,
      Span span)
      implements ClassEntry {}

  record ClassMethod(
      @Nullable Token.Comment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      List<TypeParameter> typePars,
      List<Parameter> args,
      @Nullable Type returnType,
      @Nullable Expr expr,
      Span span)
      implements ClassEntry {}

  default Span getSpan() {
    return switch (this) {
      case ClassProperty x -> x.span;
      case ClassPropertyExpr x -> x.span;
      case ClassPropertyBody x -> x.span;
      case ClassMethod x -> x.span;
    };
  }
}
