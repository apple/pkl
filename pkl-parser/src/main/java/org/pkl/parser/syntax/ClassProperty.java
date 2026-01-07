/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.parser.syntax;

import java.util.List;
import org.pkl.parser.ParserVisitor;
import org.pkl.parser.Span;
import org.pkl.parser.util.Nullable;

public final class ClassProperty extends AbstractNode {
  private final int modifiersOffset;
  private final int nameOffset;

  public ClassProperty(List<Node> nodes, int modifiersOffset, int nameOffset, Span span) {
    super(span, nodes);
    this.modifiersOffset = modifiersOffset;
    this.nameOffset = nameOffset;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClassProperty(this);
  }

  public @Nullable DocComment getDocComment() {
    assert children != null;
    return (DocComment) children.get(0);
  }

  @SuppressWarnings("unchecked")
  public List<Annotation> getAnnotations() {
    assert children != null;
    return (List<Annotation>) children.subList(1, modifiersOffset);
  }

  @SuppressWarnings("unchecked")
  public List<Modifier> getModifiers() {
    assert children != null;
    return (List<Modifier>) children.subList(modifiersOffset, nameOffset);
  }

  public Identifier getName() {
    assert children != null;
    var ret = (Identifier) children.get(nameOffset);
    assert ret != null;
    return ret;
  }

  public @Nullable TypeAnnotation getTypeAnnotation() {
    assert children != null;
    return (TypeAnnotation) children.get(nameOffset + 1);
  }

  public @Nullable Expr getExpr() {
    assert children != null;
    return (Expr) children.get(nameOffset + 2);
  }

  @SuppressWarnings("unchecked")
  public List<ObjectBody> getBodyList() {
    assert children != null;
    return (List<ObjectBody>) children.subList(nameOffset + 3, children.size());
  }
}
