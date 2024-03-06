package org.pkl.cst;

import org.pkl.parser.Span;

public record Modifier(ModifierValue value, Span span) {

  public enum ModifierValue {
    EXTERNAL,
    ABSTRACT,
    OPEN,
    LOCAL,
    HIDDEN,
    FIXED,
    CONST
  }
}
