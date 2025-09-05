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

public class Node {
  public final List<Node> children;
  public final FullSpan span;
  public final NodeType type;
  private @Nullable String text;

  public Node(NodeType type, FullSpan span) {
    this(type, span, Collections.emptyList());
  }

  public Node(NodeType type, FullSpan span, List<Node> children) {
    this.type = type;
    this.span = span;
    this.children = Collections.unmodifiableList(children);
  }

  public Node(NodeType type, List<Node> children) {
    this.type = type;
    if (children.isEmpty()) throw new RuntimeException("No children or span given for node");
    var end = children.get(children.size() - 1).span;
    this.span = children.get(0).span.endWith(end);
    this.children = Collections.unmodifiableList(children);
  }

  public String text(char[] source) {
    if (text == null) {
      text = new String(source, span.charIndex(), span.length());
    }
    return text;
  }

  /** Returns the first child of type {@code type} or {@code null}. */
  public @Nullable Node findChildByType(NodeType type) {
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
    Node node = (Node) o;
    return Objects.equals(children, node.children)
        && Objects.equals(span, node.span)
        && Objects.equals(type, node.type);
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
