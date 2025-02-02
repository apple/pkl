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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.Span;

public sealed interface StringPart extends Node {
  final class StringConstantParts implements StringPart {
    private final List<StringConstantPart> parts;
    private final Span span;
    private Node parent;

    public StringConstantParts(List<StringConstantPart> parts, Span span) {
      this.parts = parts;
      this.span = span;

      for (var part : parts) {
        part.setParent(this);
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
      return Collections.unmodifiableList(parts);
    }

    public List<StringConstantPart> getParts() {
      return parts;
    }

    @Override
    public String toString() {
      return "StringConstantParts{parts=" + parts + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringConstantParts that = (StringConstantParts) o;
      return Objects.equals(parts, that.parts) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(parts, span);
    }
  }

  final class StringInterpolation implements StringPart {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public StringInterpolation(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
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
      return List.of(expr);
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "StringInterpolation{expr=" + expr + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StringInterpolation that = (StringInterpolation) o;
      return Objects.equals(expr, that.expr) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span);
    }
  }
}
