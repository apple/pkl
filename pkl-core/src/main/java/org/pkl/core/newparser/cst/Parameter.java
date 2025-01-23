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
package org.pkl.core.newparser.cst;

import java.util.Objects;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

public sealed interface Parameter extends Node {

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
    public String toString() {
      return "Underscore{" + "span=" + span + ", parent=" + parent + '}';
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
      return Objects.equals(span, that.span) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(span, parent);
    }
  }

  final class TypedIdent implements Parameter {
    private final Ident ident;
    private final @Nullable Type type;
    private final Span span;
    private Node parent;

    public TypedIdent(Ident ident, @Nullable Type type, Span span) {
      this.ident = ident;
      this.type = type;
      this.span = span;

      ident.setParent(this);
      if (type != null) {
        type.setParent(this);
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

    public Ident getIdent() {
      return ident;
    }

    public @Nullable Type getType() {
      return type;
    }

    @Override
    public String toString() {
      return "TypedIdent{"
          + "ident="
          + ident
          + ", type="
          + type
          + ", span="
          + span
          + ", parent="
          + parent
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
      TypedIdent that = (TypedIdent) o;
      return Objects.equals(ident, that.ident)
          && Objects.equals(type, that.type)
          && Objects.equals(span, that.span)
          && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ident, type, span, parent);
    }
  }
}
