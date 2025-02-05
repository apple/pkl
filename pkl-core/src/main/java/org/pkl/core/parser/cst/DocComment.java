/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

public final class DocComment implements Node {
  private final List<Span> spans;
  private Node parent;

  public DocComment(List<Span> spans) {
    this.spans = spans;
  }

  @Override
  public Span span() {
    return spans.get(0).endWith(spans.get(spans.size() - 1));
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
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitDocComment(this);
  }

  @Override
  public String text(char[] source) {
    var builder = new StringBuilder();
    for (var span : spans) {
      builder.append(new String(source, span.charIndex(), span.length()));
      builder.append('\n');
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return "DocComment{spans=" + spans + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DocComment that = (DocComment) o;
    return Objects.equals(spans, that.spans);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(spans);
  }
}
