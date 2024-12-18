package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public sealed interface ClassEntry extends ModuleEntry {

  record ClassProperty(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      Type type,
      Span span)
      implements ClassEntry {}

  record ClassPropertyExpr(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      @Nullable Type type,
      Expr expr,
      Span span)
      implements ClassEntry {}

  record ClassPropertyBody(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      List<ObjectBody> bodyList,
      Span span)
      implements ClassEntry {}

  record ClassMethod(
      @Nullable DocComment docComment,
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
    if (this instanceof ClassProperty x) return x.span;
    if (this instanceof ClassPropertyExpr x) return x.span;
    if (this instanceof ClassPropertyBody x) return x.span;
    if (this instanceof ClassMethod x) return x.span;
    throw new RuntimeException("unknown class entry " + this);
  }
}
