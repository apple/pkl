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

public class ArgumentList implements Node {
  private final List<Expr> arguments;
  private final Span span;
  private Node parent;

  public ArgumentList(List<Expr> arguments, Span span) {
    this.arguments = arguments;
    this.span = span;

    for (var arg : arguments) {
      arg.setParent(this);
    }
  }

  @Override
  public Span span() {
    return span;
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
    return Collections.unmodifiableList(arguments);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitArgumentList(this);
  }

  public List<Expr> getArguments() {
    return arguments;
  }

  @Override
  public String toString() {
    return "ArgumentList{arguments=" + arguments + ", span=" + span + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArgumentList that = (ArgumentList) o;
    return Objects.equals(arguments, that.arguments) && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(arguments, span);
  }
}
