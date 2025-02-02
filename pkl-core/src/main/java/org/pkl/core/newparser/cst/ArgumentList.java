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
package org.pkl.core.newparser.cst;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.Span;

public class ArgumentList implements Node {
  private final List<Expr> args;
  private final Span span;
  private Node parent;

  public ArgumentList(List<Expr> args, Span span) {
    this.args = args;
    this.span = span;

    for (var arg : args) {
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
    return Collections.unmodifiableList(args);
  }

  public List<Expr> getArgs() {
    return args;
  }

  @Override
  public String toString() {
    return "ArgumentList{args=" + args + ", span=" + span + '}';
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
    return Objects.equals(args, that.args) && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(args, span);
  }
}
