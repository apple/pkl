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

import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public final class TypeParameter implements Node {
  private final @Nullable Variance variance;
  private final Identifier identifier;
  private final Span span;
  private Node parent;

  public TypeParameter(@Nullable Variance variance, Identifier identifier, Span span) {
    this.variance = variance;
    this.identifier = identifier;
    this.span = span;

    identifier.setParent(this);
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
    return List.of(identifier);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitTypeParameter(this);
  }

  public @Nullable Variance getVariance() {
    return variance;
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  @Override
  public String toString() {
    return "TypeParameter{"
        + "variance="
        + variance
        + ", identifier="
        + identifier
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
    TypeParameter that = (TypeParameter) o;
    return variance == that.variance
        && Objects.equals(identifier, that.identifier)
        && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variance, identifier, span);
  }

  public enum Variance {
    IN,
    OUT
  }
}
