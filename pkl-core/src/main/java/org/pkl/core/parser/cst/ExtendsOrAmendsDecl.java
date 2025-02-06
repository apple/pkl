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

import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public class ExtendsOrAmendsDecl implements Node {
  private final StringConstant url;
  private final Type type;
  private final Span span;
  private Node parent;

  public ExtendsOrAmendsDecl(StringConstant url, Type type, Span span) {
    this.url = url;
    this.type = type;
    this.span = span;
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
    return List.of(url);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitExtendsOrAmendsDecl(this);
  }

  public StringConstant getUrl() {
    return url;
  }

  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    return "AmendsDecl{" + "url=" + url + ", span=" + span + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExtendsOrAmendsDecl that = (ExtendsOrAmendsDecl) o;
    return Objects.equals(url, that.url) && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, span);
  }

  public enum Type {
    EXTENDS,
    AMENDS
  }
}
