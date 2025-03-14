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
package org.pkl.parser.syntax;

import java.util.Objects;
import org.pkl.parser.ParserVisitor;
import org.pkl.parser.Span;

public final class Identifier extends AbstractNode {
  private final String value;

  public Identifier(String value, Span span) {
    super(span, null);
    this.value = value;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitIdentifier(this);
  }

  public String getValue() {
    return removeBackticks(value);
  }

  public String getRawValue() {
    return value;
  }

  private static String removeBackticks(String text) {
    if (!text.isEmpty() && text.charAt(0) == '`') {
      // lexer makes sure there's a ` at the end
      return text.substring(1, text.length() - 1);
    }
    return text;
  }

  @Override
  public String toString() {
    return "Identifier{value='" + value + '\'' + ", span=" + span + '}';
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
    Identifier identifier = (Identifier) o;
    return Objects.equals(value, identifier.value) && Objects.equals(span, identifier.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, span);
  }
}
