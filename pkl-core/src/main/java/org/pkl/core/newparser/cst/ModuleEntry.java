package org.pkl.core.newparser.cst;

import org.pkl.core.newparser.Span;

public sealed interface ModuleEntry permits Clazz, TypeAlias, ClassEntry {
  default Span getSpan() {
    if (this instanceof Clazz x) return x.span();
    if (this instanceof TypeAlias x) return x.span();
    if (this instanceof ClassEntry x) return x.getSpan();
    throw new RuntimeException("Unknown module entry " + this);
  }
}
