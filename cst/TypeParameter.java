package org.pkl.cst;

import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

public record TypeParameter(@Nullable Variance variance, Ident ident, Span span) {

  public enum Variance {
    IN,
    OUT
  }
}
