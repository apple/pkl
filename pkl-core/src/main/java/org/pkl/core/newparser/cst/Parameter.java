package org.pkl.core.newparser.cst;

import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public sealed interface Parameter {
  default Span getSpan() {
    if (this instanceof Underscore x) return x.span;
    if (this instanceof TypedIdent x) return x.span;
    throw new RuntimeException("Unknown parameter " + this);
  }

  record Underscore(Span span) implements Parameter {}

  record TypedIdent(Ident ident, @Nullable Type type, Span span) implements Parameter {}
}
