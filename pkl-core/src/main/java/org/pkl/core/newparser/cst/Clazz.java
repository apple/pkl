package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record Clazz(
    @Nullable DocComment docComment,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    Ident name,
    List<TypeParameter> typePars,
    @Nullable QualifiedIdent superClass,
    List<ClassEntry> body,
    Span span)
    implements ModuleEntry {}
