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
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings({"DataFlowIssue", "unchecked"})
public final class ModuleDecl extends AbstractNode {
  private final int modifiersOffset;
  private final int nameOffset;

  public ModuleDecl(List<Node> nodes, int modifiersOffset, int nameOffset, Span span) {
    super(span, nodes);
    this.modifiersOffset = modifiersOffset;
    this.nameOffset = nameOffset;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitModuleDecl(this);
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

  public @Nullable QualifiedIdentifier getName() {
    return (QualifiedIdentifier) children.get(nameOffset);
  }

  public @Nullable ExtendsOrAmendsClause getExtendsOrAmendsDecl() {
    return (ExtendsOrAmendsClause) children.get(nameOffset + 1);
  }

  public Span headerSpan() {
    Span start = null;
    Span end = null;
    for (var i = modifiersOffset; i < children.size(); i++) {
      var child = children.get(i);
      if (child != null) {
        if (start == null) {
          start = child.span();
        }
        end = child.span();
      }
    }
    return start.endWith(end);
  }
}
