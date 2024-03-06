package org.pkl.cst;

import org.pkl.parser.Span;

public sealed interface ModuleEntry permits Clazz, TypeAlias, ClassEntry {
  default Span getSpan() {
    return switch (this) {
      case Clazz x -> x.span();
      case TypeAlias x -> x.span();
      case ClassEntry x -> x.getSpan();
    };
  }
}
