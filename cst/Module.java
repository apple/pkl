package org.pkl.cst;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

public record Module(
    @Nullable ModuleDecl decl, List<Import> imports, List<ModuleEntry> entries, Span span) {}
