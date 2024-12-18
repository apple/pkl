package org.pkl.core.newparser.cst;

import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record TypeParameter(@Nullable Variance variance, Ident ident, Span span) {

  public enum Variance {
    IN,
    OUT
  }
}
