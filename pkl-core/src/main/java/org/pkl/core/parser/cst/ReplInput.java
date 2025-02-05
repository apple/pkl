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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public class ReplInput implements Node {
  private final List<Node> nodes;
  private final Span span;

  public ReplInput(List<Node> nodes, Span span) {
    this.nodes = nodes;
    this.span = span;
  }

  @Override
  public Span span() {
    return span;
  }

  @Override
  public @Nullable Node parent() {
    return null;
  }

  @Override
  public void setParent(Node parent) {}

  @Override
  public List<Node> children() {
    return Collections.unmodifiableList(nodes);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitReplInput(this);
  }

  public List<Node> getNodes() {
    return nodes;
  }

  @Override
  public String toString() {
    return "ReplInput{nodes=" + nodes + ", span=" + span + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReplInput replInput = (ReplInput) o;
    return Objects.equals(nodes, replInput.nodes) && Objects.equals(span, replInput.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodes, span);
  }
}
