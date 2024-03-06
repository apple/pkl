package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;
import org.pkl.parser.Token;

public record ModuleDecl(
    @Nullable Token.Comment docComment,
    @Nullable List<Annotation> annotations,
    List<Modifier> modifiers,
    @Nullable ModuleNameDecl name,
    @Nullable ExtendsDecl extendsUrl,
    @Nullable AmendsDecl amendsUrl,
    Span span) {}
