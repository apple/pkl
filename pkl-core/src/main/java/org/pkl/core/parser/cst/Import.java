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

import java.util.Arrays;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("DataFlowIssue")
public final class Import extends AbstractNode {
  private final boolean isGlob;

  public Import(StringConstant importStr, boolean isGlob, @Nullable Identifier alias, Span span) {
    super(span, Arrays.asList(importStr, alias));
    this.isGlob = isGlob;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitImport(this);
  }

  public StringConstant getImportStr() {
    return (StringConstant) children.get(0);
  }

  public boolean isGlob() {
    return isGlob;
  }

  public @Nullable Identifier getAlias() {
    return (Identifier) children.get(1);
  }

  @Override
  public String toString() {
    return "Import{isGlob=" + isGlob + ", span=" + span + ", children=" + children + '}';
  }

  @SuppressWarnings("ConstantValue")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    Import anImport = (Import) o;
    return isGlob == anImport.isGlob;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), isGlob);
  }
}
