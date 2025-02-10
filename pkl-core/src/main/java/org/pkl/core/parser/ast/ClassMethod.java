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
package org.pkl.core.parser.ast;

import java.util.List;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("unchecked")
public class ClassMethod extends AbstractNode {
  private final int modifiersOffset;
  private final int nameOffset;
  private final Span headerSpan;

  public ClassMethod(
      List<Node> nodes, int modifiersOffset, int nameOffset, Span headerSpan, Span span) {
    super(span, nodes);
    this.headerSpan = headerSpan;
    this.modifiersOffset = modifiersOffset;
    this.nameOffset = nameOffset;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClassMethod(this);
  }

  public @Nullable DocComment getDocComment() {
    assert children != null;
    return (DocComment) children.get(0);
  }

  public List<Annotation> getAnnotations() {
    assert children != null;
    return (List<Annotation>) children.subList(1, modifiersOffset);
  }

  public List<Modifier> getModifiers() {
    assert children != null;
    return (List<Modifier>) children.subList(modifiersOffset, nameOffset);
  }

  public Identifier getName() {
    assert children != null;
    return (Identifier) children.get(nameOffset);
  }

  public @Nullable TypeParameterList getTypeParameterList() {
    assert children != null;
    return (TypeParameterList) children.get(nameOffset + 1);
  }

  public ParameterList getParameterList() {
    assert children != null;
    return (ParameterList) children.get(nameOffset + 2);
  }

  public @Nullable TypeAnnotation getTypeAnnotation() {
    assert children != null;
    return (TypeAnnotation) children.get(nameOffset + 3);
  }

  public @Nullable Expr getExpr() {
    assert children != null;
    return (Expr) children.get(nameOffset + 4);
  }

  public Span getHeaderSpan() {
    return headerSpan;
  }
}
