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
package org.pkl.core.parser.syntax;

import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public final class TypeParameter extends AbstractNode {
  private final @Nullable Variance variance;

  public TypeParameter(@Nullable Variance variance, Identifier identifier, Span span) {
    super(span, List.of(identifier));
    this.variance = variance;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitTypeParameter(this);
  }

  public @Nullable Variance getVariance() {
    return variance;
  }

  public Identifier getIdentifier() {
    assert children != null;
    return (Identifier) children.get(0);
  }

  @Override
  public String toString() {
    return "TypeParameter{"
        + "variance="
        + variance
        + ", children="
        + children
        + ", span="
        + span
        + '}';
  }

  @SuppressWarnings("ConstantValue")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    TypeParameter that = (TypeParameter) o;
    return variance == that.variance;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), variance);
  }

  public enum Variance {
    IN,
    OUT
  }
}
