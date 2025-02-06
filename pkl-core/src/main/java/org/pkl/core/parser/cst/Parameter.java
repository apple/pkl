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

public sealed interface Parameter extends Node {

  @Override
  default <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitParameter(this);
  }

  final class Underscore implements Parameter {
    private final Span span;
    private Node parent;

    public Underscore(Span span) {
      this.span = span;
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
      return List.of();
    }

    @Override
    public String toString() {
      return "Underscore{" + "span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Underscore that = (Underscore) o;
      return Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(span);
    }
  }

  final class TypedIdentifier implements Parameter {
    private final Identifier identifier;
    private final @Nullable TypeAnnotation typeAnnotation;
    private final Span span;
    private Node parent;

    public TypedIdentifier(
        Identifier identifier, @Nullable TypeAnnotation typeAnnotation, Span span) {
      this.identifier = identifier;
      this.typeAnnotation = typeAnnotation;
      this.span = span;

      identifier.setParent(this);
      if (typeAnnotation != null) {
        typeAnnotation.setParent(this);
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
      var children = new ArrayList<Node>();
      children.add(identifier);
      if (typeAnnotation != null) {
        children.add(typeAnnotation);
      }
      return children;
    }

    public Identifier getIdentifier() {
      return identifier;
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      return typeAnnotation;
    }

    @Override
    public String toString() {
      return "TypedIdentifier{"
          + "identifier="
          + identifier
          + ", type="
          + typeAnnotation
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
      TypedIdentifier that = (TypedIdentifier) o;
      return Objects.equals(identifier, that.identifier)
          && Objects.equals(typeAnnotation, that.typeAnnotation)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(identifier, typeAnnotation, span);
    }
  }
}
