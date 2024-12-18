package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record Module(
  @Nullable ModuleDecl decl, List<Import> imports, List<ModuleEntry> entries, Span span) {}
