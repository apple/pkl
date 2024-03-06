package org.pkl.cst;

import org.jetbrains.annotations.Nullable;
import org.pkl.parser.Span;

public record Annotation(Type type, @Nullable ObjectBody body, Span span) {}
