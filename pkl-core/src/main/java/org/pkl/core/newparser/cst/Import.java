package org.pkl.core.newparser.cst;

import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record Import(String url, boolean isGlob, @Nullable Ident alias, Span span) {}
