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

public class ExtendsOrAmendsDecl extends AbstractNode {
  private final Type type;

  public ExtendsOrAmendsDecl(StringConstant url, Type type, Span span) {
    super(span, List.of(url));
    this.type = type;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitExtendsOrAmendsDecl(this);
  }

  public StringConstant getUrl() {
    assert children != null;
    return (StringConstant) children.get(0);
  }

  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    return "ExtendsOrAmendsDecl{"
        + "type="
        + type
        + ", span="
        + span
        + ", children="
        + children
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
    ExtendsOrAmendsDecl that = (ExtendsOrAmendsDecl) o;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), type);
  }

  public enum Type {
    EXTENDS,
    AMENDS
  }
}
