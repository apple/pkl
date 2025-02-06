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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public final class QualifiedIdentifier implements Node {
  private final List<Identifier> identifiers;
  private Node parent;

  public QualifiedIdentifier(List<Identifier> identifiers) {
    this.identifiers = identifiers;

    for (var identifier : identifiers) {
      identifier.setParent(this);
    }
  }

  public Span span() {
    var start = identifiers.get(0).span();
    var end = identifiers.get(identifiers.size() - 1).span();
    return start.endWith(end);
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
    return Collections.unmodifiableList(identifiers);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitQualifiedIdentifier(this);
  }

  public List<Identifier> getIdentifiers() {
    return identifiers;
  }

  public String text() {
    return identifiers.stream().map(Identifier::getValue).collect(Collectors.joining("."));
  }

  @Override
  public String toString() {
    return "QualifiedIdentifier{identifiers=" + identifiers + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QualifiedIdentifier that = (QualifiedIdentifier) o;
    return Objects.equals(identifiers, that.identifiers);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(identifiers);
  }
}
