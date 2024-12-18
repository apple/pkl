package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record ModuleDecl(
    @Nullable DocComment docComment,
    @Nullable List<Annotation> annotations,
    List<Modifier> modifiers,
    @Nullable QualifiedIdent name,
    @Nullable ExtendsDecl extendsUrl,
    @Nullable AmendsDecl amendsUrl,
    Span span) {}
