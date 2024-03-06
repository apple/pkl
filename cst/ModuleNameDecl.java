package org.pkl.cst;

import java.util.List;
import org.pkl.parser.Span;

public record ModuleNameDecl(List<String> name, Span span) {}
