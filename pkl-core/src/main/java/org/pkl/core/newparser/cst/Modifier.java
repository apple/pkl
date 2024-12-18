package org.pkl.core.newparser.cst;

import org.pkl.core.newparser.Span;

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
