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
package org.pkl.core.parser.ast;

import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public final class Modifier extends AbstractNode {
  private final ModifierValue value;

  public Modifier(ModifierValue value, Span span) {
    super(span, null);
    this.value = value;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitModifier(this);
  }

  public ModifierValue getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "Modifier{value=" + value + ", span=" + span + '}';
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
    Modifier modifier = (Modifier) o;
    return value == modifier.value && Objects.equals(span, modifier.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, span);
  }

  public enum ModifierValue {
    EXTERNAL,
    ABSTRACT,
    OPEN,
    LOCAL,
    HIDDEN,
    FIXED,
    CONST
  }
}
