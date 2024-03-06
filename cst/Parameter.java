package org.pkl.cst;

import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

public sealed interface Parameter {
  default Span getSpan() {
    return switch (this) {
      case Underscore u -> u.span;
      case TypedIdent t -> t.span;
    };
  }

  record Underscore(Span span) implements Parameter {}

  record TypedIdent(Ident ident, @Nullable Type type, Span span) implements Parameter {}
}
