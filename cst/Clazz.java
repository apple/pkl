package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;
import org.pkl.parser.Token;

public record Clazz(
    @Nullable Token.Comment docComment,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    Ident name,
    List<TypeParameter> typePars,
    @Nullable Type superClass,
    List<ClassEntry> body,
    Span span)
    implements ModuleEntry {}
