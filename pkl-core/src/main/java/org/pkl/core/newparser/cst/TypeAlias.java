package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record TypeAlias(
    @Nullable DocComment docComment,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    Ident name,
    List<TypeParameter> typePars,
    Type type,
    Span span)
    implements ModuleEntry {}
