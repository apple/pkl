package org.pkl.core.newparser.cst;

import org.pkl.core.util.Nullable;
import org.pkl.core.newparser.Span;

public record Annotation(QualifiedIdent name, @Nullable ObjectBody body, Span span) {}
