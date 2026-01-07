/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser.syntax;

import java.util.Objects;
import org.pkl.parser.ParserVisitor;
import org.pkl.parser.Span;

public final class Modifier extends AbstractNode {
  private final ModifierValue value;

  public Modifier(ModifierValue value, Span span) {
    super(span, null);
    this.value = value;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (this == o) {
      return true;
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
