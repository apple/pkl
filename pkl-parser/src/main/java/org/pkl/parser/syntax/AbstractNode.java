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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.pkl.parser.Span;

public abstract class AbstractNode implements Node {
  protected final Span span;
  protected final List<? extends @Nullable Node> children;
  protected @Nullable Node parent;

  public AbstractNode(Span span, List<? extends @Nullable Node> children) {
    this.span = span;
    if (children.isEmpty()) {
      // optimization: always store an unwrapped List.of()
      this.children = List.of();
    } else {
      this.children = Collections.unmodifiableList(children);
    }
    for (var node : children) {
      if (node != null) {
        node.setParent(this);
      }
    }
  }

  @Override
  public Span span() {
    return span;
  }

  @Override
  public @Nullable Node parent() {
    return parent;
  }

  @Override
  public void setParent(Node parent) {
    this.parent = parent;
  }

  @Override
  public List<? extends @Nullable Node> children() {
    return children;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractNode that = (AbstractNode) o;
    return Objects.equals(span, that.span) && Objects.deepEquals(children, that.children);
  }

  @Override
  public int hashCode() {
    return Objects.hash(span, children);
  }

  @Override
  public String toString() {
    var name = getClass().getSimpleName();
    return name + "{span=" + span + ", children=" + children + '}';
  }
}
