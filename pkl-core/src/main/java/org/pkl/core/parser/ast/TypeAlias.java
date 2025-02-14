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

@SuppressWarnings({"unchecked", "DataFlowIssue"})
public final class TypeAlias extends AbstractNode {
  private final int modifiersOffset;
  private final int nameOffset;

  public TypeAlias(List<Node> children, int modifiersOffset, int nameOffset, Span span) {
    super(span, children);
    this.modifiersOffset = modifiersOffset;
    this.nameOffset = nameOffset;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitTypeAlias(this);
  }

  public @Nullable DocComment getDocComment() {
    return (DocComment) children.get(0);
  }

  public List<Annotation> getAnnotations() {
    return (List<Annotation>) children.subList(1, modifiersOffset);
  }

  public List<Modifier> getModifiers() {
    return (List<Modifier>) children.subList(modifiersOffset, nameOffset);
  }

  public Keyword getTypealiasKeyword() {
    return (Keyword) children.get(nameOffset);
  }

  public Identifier getName() {
    return (Identifier) children.get(nameOffset + 1);
  }

  public @Nullable TypeParameterList getTypeParameterList() {
    return (TypeParameterList) children.get(nameOffset + 2);
  }

  public Type getType() {
    return (Type) children.get(nameOffset + 3);
  }

  public Span getHeaderSpan() {
    Span start = null;
    for (var i = modifiersOffset; i < children.size(); i++) {
      var child = children.get(i);
      if (child != null) {
        start = child.span();
        break;
      }
    }
    var end = children.get(nameOffset + 1).span();
    var tparList = children.get(nameOffset + 2);
    if (tparList != null) {
      end = tparList.span();
    }
    return start.endWith(end);
  }
}
