package org.pkl.cst;

import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

public record Import(String url, boolean isGlob, @Nullable Ident alias, Span span) {}
