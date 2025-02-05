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

import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.ParserVisitor;
import org.pkl.core.newparser.Span;
import org.pkl.core.newparser.cst.Expr.StringConstant;
import org.pkl.core.util.Nullable;

public final class Import implements Node {
  private final StringConstant importStr;
  private final boolean isGlob;
  private final @Nullable Ident alias;
  private final Span span;
  private Node parent;

  public Import(StringConstant importStr, boolean isGlob, @Nullable Ident alias, Span span) {
    this.importStr = importStr;
    this.isGlob = isGlob;
    this.alias = alias;
    this.span = span;

    if (alias != null) {
      alias.setParent(this);
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
    return List.of(importStr);
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitImport(this);
  }

  public StringConstant getImportStr() {
    return importStr;
  }

  public boolean isGlob() {
    return isGlob;
  }

  public @Nullable Ident getAlias() {
    return alias;
  }

  @Override
  public String toString() {
    return "Import{"
        + "importStr="
        + importStr
        + ", isGlob="
        + isGlob
        + ", alias="
        + alias
        + ", span="
        + span
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
    Import anImport = (Import) o;
    return isGlob == anImport.isGlob
        && Objects.equals(importStr, anImport.importStr)
        && Objects.equals(alias, anImport.alias)
        && Objects.equals(span, anImport.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importStr, isGlob, alias, span);
  }
}
