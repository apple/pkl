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
package org.pkl.parser.syntax;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.pkl.parser.ParserVisitor;
import org.pkl.parser.Span;
import org.pkl.parser.util.Nullable;

public abstract sealed class ObjectMember extends AbstractNode {

  public ObjectMember(Span span, @Nullable List<? extends @Nullable Node> children) {
    super(span, children);
  }

  public static final class ObjectElement extends ObjectMember {
    public ObjectElement(Expr expr, Span span) {
      super(span, List.of(expr));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectElement(this);
    }

    public Expr getExpr() {
      assert children != null;
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
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectProperty(this);
    }

    @SuppressWarnings("unchecked")
    public List<Modifier> getModifiers() {
      assert children != null;
      return (List<Modifier>) children.subList(0, identifierOffset);
    }

    public Identifier getIdentifier() {
      assert children != null;
      return (Identifier) children.get(identifierOffset);
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      assert children != null;
      return (TypeAnnotation) children.get(identifierOffset + 1);
    }

    public @Nullable Expr getExpr() {
      assert children != null;
      return (Expr) children.get(identifierOffset + 2);
    }

    @SuppressWarnings("unchecked")
    public List<ObjectBody> getBodyList() {
      assert children != null;
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
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectMethod(this);
    }

    @SuppressWarnings("unchecked")
    public List<Modifier> getModifiers() {
      assert children != null;
      return (List<Modifier>) children.subList(0, identifierOffset);
    }

    public Keyword getFunctionKeyword() {
      assert children != null;
      return (Keyword) children.get(identifierOffset);
    }

    public Identifier getIdentifier() {
      assert children != null;
      return (Identifier) children.get(identifierOffset + 1);
    }

    public @Nullable TypeParameterList getTypeParameterList() {
      assert children != null;
      return (TypeParameterList) children.get(identifierOffset + 2);
    }

    public ParameterList getParamList() {
      assert children != null;
      return (ParameterList) children.get(identifierOffset + 3);
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      assert children != null;
      return (TypeAnnotation) children.get(identifierOffset + 4);
    }

    public Expr getExpr() {
      assert children != null;
      return (Expr) children.get(identifierOffset + 5);
    }

    @SuppressWarnings("DuplicatedCode")
    public Span headerSpan() {
      Span start = null;
      assert children != null;
      for (var child : children) {
        if (child != null) {
          start = child.span();
          break;
        }
      }
      Span end;
      var typeAnnotation = children.get(identifierOffset + 4);
      if (typeAnnotation == null) {
        end = children.get(identifierOffset + 3).span();
      } else {
        end = typeAnnotation.span();
      }
      assert start != null;
      return start.endWith(end);
    }
  }

  public static final class MemberPredicate extends ObjectMember {
    public MemberPredicate(List<Node> nodes, Span span) {
      super(span, nodes);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitMemberPredicate(this);
    }

    public Expr getPred() {
      assert children != null;
      return (Expr) children.get(0);
    }

    public @Nullable Expr getExpr() {
      assert children != null;
      return (Expr) children.get(1);
    }

    @SuppressWarnings("unchecked")
    public List<ObjectBody> getBodyList() {
      assert children != null;
      return (List<ObjectBody>) children.subList(2, children.size());
    }
  }

  public static final class ObjectEntry extends ObjectMember {
    public ObjectEntry(List<Node> nodes, Span span) {
      super(span, nodes);
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectEntry(this);
    }

    public Expr getKey() {
      assert children != null;
      return (Expr) children.get(0);
    }

    public @Nullable Expr getValue() {
      assert children != null;
      return (Expr) children.get(1);
    }

    @SuppressWarnings("unchecked")
    public List<ObjectBody> getBodyList() {
      assert children != null;
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
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectSpread(this);
    }

    public Expr getExpr() {
      assert children != null;
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
      ObjectSpread that = (ObjectSpread) o;
      return isNullable == that.isNullable;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), isNullable);
    }
  }

  public static final class WhenGenerator extends ObjectMember {
    public WhenGenerator(
        Expr predicate, ObjectBody thenClause, @Nullable ObjectBody elseClause, Span span) {
      super(span, Arrays.asList(predicate, thenClause, elseClause));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitWhenGenerator(this);
    }

    public Expr getPredicate() {
      assert children != null;
      return (Expr) children.get(0);
    }

    public ObjectBody getThenClause() {
      assert children != null;
      return (ObjectBody) children.get(1);
    }

    public @Nullable ObjectBody getElseClause() {
      assert children != null;
      return (ObjectBody) children.get(2);
    }
  }

  public static final class ForGenerator extends ObjectMember {
    public ForGenerator(
        Parameter p1, @Nullable Parameter p2, Expr expr, ObjectBody body, Span span) {
      super(span, Arrays.asList(p1, p2, expr, body));
    }

    @Override
    public <T> T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitForGenerator(this);
    }

    public Parameter getP1() {
      assert children != null;
      return (Parameter) children.get(0);
    }

    public @Nullable Parameter getP2() {
      assert children != null;
      return (Parameter) children.get(1);
    }

    public Expr getExpr() {
      assert children != null;
      return (Expr) children.get(2);
    }

    public ObjectBody getBody() {
      assert children != null;
      return (ObjectBody) children.get(3);
    }

    public Span forSpan() {
      return new Span(span.charIndex(), 3);
    }
  }
}
