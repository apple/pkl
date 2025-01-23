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

import java.util.Objects;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

public class Annotation implements Node {
  private final QualifiedIdent name;
  private final @Nullable ObjectBody body;
  private final Span span;
  private Node parent;

  public Annotation(QualifiedIdent name, @Nullable ObjectBody body, Span span) {
    this.name = name;
    this.body = body;
    this.span = span;

    name.setParent(this);
    if (body != null) {
      body.setParent(this);
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

  public QualifiedIdent getName() {
    return name;
  }

  public @Nullable ObjectBody getBody() {
    return body;
  }

  @Override
  public String toString() {
    return "Annotation{"
        + "name="
        + name
        + ", body="
        + body
        + ", span="
        + span
        + ", parent="
        + parent
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Annotation that = (Annotation) o;
    return Objects.equals(name, that.name)
        && Objects.equals(body, that.body)
        && Objects.equals(span, that.span)
        && Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, body, span, parent);
  }
}
