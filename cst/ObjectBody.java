package org.pkl.cst;

import java.util.List;
import org.pkl.parser.Span;

public record ObjectBody(List<Parameter> pars, List<ObjectMember> members, Span span) {}
