package org.pkl.core.newparser.cst;

import java.util.List;
import org.pkl.core.newparser.Span;

public record ObjectBody(List<Parameter> pars, List<ObjectMember> members, Span span) {}
