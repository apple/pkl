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

public final class ExtendsDecl implements Node {
  private final String url;
  private final Span span;
  private Node parent;

  public ExtendsDecl(String url, Span span) {
    this.url = url;
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

  public String getUrl() {
    return url;
  }

  @Override
  public String toString() {
    return "ExtendsDecl{" + "url='" + url + '\'' + ", span=" + span + ", parent=" + parent + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExtendsDecl that = (ExtendsDecl) o;
    return Objects.equals(url, that.url)
        && Objects.equals(span, that.span)
        && Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, span, parent);
  }
}
