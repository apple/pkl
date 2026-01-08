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

public final class ModuleDecl extends AbstractNode {
  private final int modifiersOffset;
  private final int nameOffset;

  public ModuleDecl(List<Node> nodes, int modifiersOffset, int nameOffset, Span span) {
    super(span, nodes);
    this.modifiersOffset = modifiersOffset;
    this.nameOffset = nameOffset;
  }

  @Override
  public <T> T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitModuleDecl(this);
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

  public @Nullable Keyword getModuleKeyword() {
    assert children != null;
    return (Keyword) children.get(nameOffset);
  }

  public @Nullable QualifiedIdentifier getName() {
    assert children != null;
    return (QualifiedIdentifier) children.get(nameOffset + 1);
  }

  public @Nullable ExtendsOrAmendsClause getExtendsOrAmendsDecl() {
    assert children != null;
    return (ExtendsOrAmendsClause) children.get(nameOffset + 2);
  }

  @SuppressWarnings("DuplicatedCode")
  public Span headerSpan() {
    Span start = null;
    assert children != null;
    for (var i = modifiersOffset; i < children.size(); i++) {
      var child = children.get(i);
      if (child != null) {
        start = child.span();
        break;
      }
    }
    var extendsOrAmends = children.get(nameOffset + 2);
    assert start != null;
    if (extendsOrAmends != null) {
      return start.endWith(extendsOrAmends.span());
    }
    var end = children.get(nameOffset + 1);
    assert end != null;
    return start.endWith(end.span());
  }
}
