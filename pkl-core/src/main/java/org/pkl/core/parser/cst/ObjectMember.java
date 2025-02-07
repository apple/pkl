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
import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("ALL")
public abstract sealed class ObjectMember extends AbstractNode {

  public ObjectMember(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  public static final class ObjectElement extends ObjectMember {
    public ObjectElement(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectElement(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }
  }

  public static final class ObjectProperty extends ObjectMember {
    private final int identifierOffset;

    public ObjectProperty(List<Node> nodes, int identifierOffset, Span span) {
      super(span, nodes);
      this.identifierOffset = identifierOffset;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectProperty(this);
    }

    public List<Modifier> getModifiers() {
      return (List<Modifier>) children.subList(0, identifierOffset);
    }

    public Identifier getIdentifier() {
      return (Identifier) children.get(identifierOffset);
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      return (TypeAnnotation) children.get(identifierOffset + 1);
    }

    public @Nullable Expr getExpr() {
      return (Expr) children.get(identifierOffset + 2);
    }

    public @Nullable List<ObjectBody> getBodyList() {
      return (List<ObjectBody>) children.subList(identifierOffset + 3, children.size());
    }
  }

  public static final class ObjectMethod extends ObjectMember {
    private final int identifierOffset;

    public ObjectMethod(List<Node> nodes, int identifierOffset, Span span) {
      super(span, nodes);
      this.identifierOffset = identifierOffset;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectMethod(this);
    }

    public List<Modifier> getModifiers() {
      return (List<Modifier>) children.subList(0, identifierOffset);
    }

    public Identifier getIdentifier() {
      return (Identifier) children.get(identifierOffset);
    }

    public @Nullable TypeParameterList getTypeParameterList() {
      return (TypeParameterList) children.get(identifierOffset + 1);
    }

    public ParameterList getParamList() {
      return (ParameterList) children.get(identifierOffset + 2);
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      return (TypeAnnotation) children.get(identifierOffset + 3);
    }

    public Expr getExpr() {
      return (Expr) children.get(identifierOffset + 4);
    }

    public Span headerSpan() {
      Span end;
      var typeAnnotation = children.get(identifierOffset + 3);
      if (typeAnnotation == null) {
        end = children.get(identifierOffset + 2).span();
      } else {
        end = typeAnnotation.span();
      }
      return span.endWith(end);
    }
  }

  public static final class MemberPredicate extends ObjectMember {
    public MemberPredicate(List<Node> nodes, Span span) {
      super(span, nodes);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitMemberPredicate(this);
    }

    public Expr getPred() {
      return (Expr) children.get(0);
    }

    public @Nullable Expr getExpr() {
      return (Expr) children.get(1);
    }

    public List<ObjectBody> getBodyList() {
      return (List<ObjectBody>) children.subList(2, children.size());
    }
  }

  public static final class ObjectEntry extends ObjectMember {
    public ObjectEntry(List<Node> nodes, Span span) {
      super(span, nodes);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectEntry(this);
    }

    public Expr getKey() {
      return (Expr) children.get(0);
    }

    public @Nullable Expr getValue() {
      return (Expr) children.get(1);
    }

    public List<ObjectBody> getBodyList() {
      return (List<ObjectBody>) children.subList(2, children.size());
    }
  }

  public static final class ObjectSpread extends ObjectMember {
    private final boolean isNullable;

    public ObjectSpread(Expr expr, boolean isNullable, Span span) {
      super(span, List.of(expr));
      this.isNullable = isNullable;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectSpread(this);
    }

    public Expr getExpr() {
      return (Expr) children.get(0);
    }

    public boolean isNullable() {
      return isNullable;
    }

    @Override
    public String toString() {
      return "ObjectSpread{"
          + "isNullable="
          + isNullable
          + ", span="
          + span
          + ", children="
          + children
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
      if (!super.equals(o)) {
        return false;
      }
      ObjectSpread that = (ObjectSpread) o;
      return isNullable == that.isNullable;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), isNullable);
    }
  }

  public static final class WhenGenerator extends ObjectMember {
    public WhenGenerator(Expr cond, ObjectBody body, @Nullable ObjectBody elseClause, Span span) {
      super(span, Arrays.asList(cond, body, elseClause));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitWhenGenerator(this);
    }

    public Expr getCond() {
      return (Expr) children.get(0);
    }

    public ObjectBody getBody() {
      return (ObjectBody) children.get(1);
    }

    public @Nullable ObjectBody getElseClause() {
      return (ObjectBody) children.get(2);
    }
  }

  public static final class ForGenerator extends ObjectMember {
    public ForGenerator(
        Parameter p1, @Nullable Parameter p2, Expr expr, ObjectBody body, Span span) {
      super(span, Arrays.asList(p1, p2, expr, body));
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitForGenerator(this);
    }

    public Parameter getP1() {
      return (Parameter) children.get(0);
    }

    public @Nullable Parameter getP2() {
      return (Parameter) children.get(1);
    }

    public Expr getExpr() {
      return (Expr) children.get(2);
    }

    public ObjectBody getBody() {
      return (ObjectBody) children.get(3);
    }

    public Span forSpan() {
      return new Span(span.charIndex(), 3);
    }
  }
}
