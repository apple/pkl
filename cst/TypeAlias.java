package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;
import org.pkl.parser.Token;

public record TypeAlias(
    @Nullable Token.Comment docComment,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    Ident name,
    List<TypeParameter> typePars,
    Type type,
    Span span)
    implements ModuleEntry {}
