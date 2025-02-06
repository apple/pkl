/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.core.parser.cst;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public final class ObjectBody implements Node {
  private final List<Parameter> parameters;
  private final List<ObjectMemberNode> members;
  private final Span span;
  private Node parent;

  public ObjectBody(List<Parameter> parameters, List<ObjectMemberNode> members, Span span) {
    this.parameters = parameters;
    this.members = members;
    this.span = span;

    for (var par : parameters) {
      par.setParent(this);
    }
    for (var member : members) {
      member.setParent(this);
    }
  }

  @Override
  public Span span() {
    return span;
  }

  @Override
  public Node parent() {
    return parent;
  }

  @Override
  public void setParent(Node parent) {
    this.parent = parent;
  }

  @Override
  public List<Node> children() {
    var children = new ArrayList<Node>(parameters.size() + members.size());
    children.addAll(parameters);
    children.addAll(members);
    return children;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitObjectBody(this);
  }

  public List<Parameter> getParameters() {
    return parameters;
  }

  public List<ObjectMemberNode> getMembers() {
    return members;
  }

  @Override
  public String toString() {
    return "ObjectBody{parameters=" + parameters + ", members=" + members + ", span=" + span + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ObjectBody that = (ObjectBody) o;
    return Objects.equals(parameters, that.parameters)
        && Objects.equals(members, that.members)
        && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parameters, members, span);
  }
}
