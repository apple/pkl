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

public class ClassBody implements Node {
  private final List<ClassPropertyEntry> properties;
  private final List<ClassMethod> methods;
  private final Span span;
  private Node parent;

  public ClassBody(List<ClassPropertyEntry> properties, List<ClassMethod> methods, Span span) {
    this.properties = properties;
    this.methods = methods;
    this.span = span;

    for (var prop : properties) {
      prop.setParent(this);
    }
    for (var method : methods) {
      method.setParent(this);
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
    var children = new ArrayList<Node>(properties.size() + methods.size());
    children.addAll(properties);
    children.addAll(methods);
    return children;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClassBody(this);
  }

  public List<ClassPropertyEntry> getProperties() {
    return properties;
  }

  public List<ClassMethod> getMethods() {
    return methods;
  }

  @Override
  public String toString() {
    return "ClassBody{"
        + "properties="
        + properties
        + ", methods="
        + methods
        + ", span="
        + span
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClassBody classBody = (ClassBody) o;
    return Objects.equals(properties, classBody.properties)
        && Objects.equals(methods, classBody.methods)
        && Objects.equals(span, classBody.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(properties, methods, span);
  }
}
