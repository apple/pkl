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
package org.pkl.parser.syntax.generic;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.pkl.parser.util.Nullable;

public class GenNode {
  public final List<GenNode> children;
  public final FullSpan span;
  public final NodeType type;
  public @Nullable GenNode parent;

  public GenNode(NodeType type, FullSpan span) {
    this(type, span, Collections.emptyList());
  }

  public GenNode(NodeType type, FullSpan span, List<GenNode> children) {
    this.type = type;
    this.span = span;
    this.children = Collections.unmodifiableList(children);
    for (var child : this.children) {
      child.parent = this;
    }
  }

  public GenNode(NodeType type, List<GenNode> children) {
    this.type = type;
    if (children.isEmpty()) throw new RuntimeException("No children or span given for node");
    var end = children.get(children.size() - 1).span;
    this.span = children.get(0).span.endWith(end);
    this.children = Collections.unmodifiableList(children);
    for (var child : this.children) {
      child.parent = this;
    }
  }

  public String text(char[] source) {
    return new String(source, span.charIndex(), span.length());
  }

  public @Nullable GenNode findChildByType(NodeType type) {
    for (var child : children) {
      if (child.type == type) return child;
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenNode genNode = (GenNode) o;
    return Objects.equals(children, genNode.children)
        && Objects.equals(span, genNode.span)
        && Objects.equals(type, genNode.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(children, span, type);
  }

  @Override
  public String toString() {
    return "GenNode{type='" + type + "', span=" + span + ", children=" + children + '}';
  }
}
