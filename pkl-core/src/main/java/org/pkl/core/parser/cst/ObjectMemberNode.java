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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.pkl.core.parser.ParserVisitor;
import org.pkl.core.parser.Span;
import org.pkl.core.util.Nullable;

public sealed interface ObjectMemberNode extends Node {

  final class ObjectElement implements ObjectMemberNode {
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ObjectElement(Expr expr, Span span) {
      this.expr = expr;
      this.span = span;

      expr.setParent(this);
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
      return List.of(expr);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectElement(this);
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "ObjectElement{" + "expr=" + expr + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ObjectElement that = (ObjectElement) o;
      return Objects.equals(expr, that.expr) && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, span);
    }
  }

  final class ObjectProperty implements ObjectMemberNode {
    private final List<Modifier> modifiers;
    private final Identifier identifier;
    private final @Nullable TypeAnnotation typeAnnotation;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ObjectProperty(
        List<Modifier> modifiers,
        Identifier identifier,
        @Nullable TypeAnnotation typeAnnotation,
        Expr expr,
        Span span) {
      this.modifiers = modifiers;
      this.identifier = identifier;
      this.typeAnnotation = typeAnnotation;
      this.expr = expr;
      this.span = span;

      for (var mod : modifiers) {
        mod.setParent(this);
      }
      identifier.setParent(this);
      if (typeAnnotation != null) {
        typeAnnotation.setParent(this);
      }
      expr.setParent(this);
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
      var children = new ArrayList<Node>(modifiers);
      children.add(identifier);
      if (typeAnnotation != null) {
        children.add(typeAnnotation);
      }
      children.add(expr);
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectProperty(this);
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Identifier getIdentifier() {
      return identifier;
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      return typeAnnotation;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "ObjectProperty{"
          + "modifiers="
          + modifiers
          + ", identifier="
          + identifier
          + ", typeAnnotation="
          + typeAnnotation
          + ", expr="
          + expr
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
      ObjectProperty that = (ObjectProperty) o;
      return Objects.equals(modifiers, that.modifiers)
          && Objects.equals(identifier, that.identifier)
          && Objects.equals(typeAnnotation, that.typeAnnotation)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(modifiers, identifier, typeAnnotation, expr, span);
    }
  }

  final class ObjectBodyProperty implements ObjectMemberNode {
    private final List<Modifier> modifiers;
    private final Identifier identifier;
    private final List<ObjectBody> bodyList;
    private final Span span;
    private Node parent;

    public ObjectBodyProperty(
        List<Modifier> modifiers, Identifier identifier, List<ObjectBody> bodyList, Span span) {
      this.modifiers = modifiers;
      this.identifier = identifier;
      this.bodyList = bodyList;
      this.span = span;

      for (var mod : modifiers) {
        mod.setParent(this);
      }
      identifier.setParent(this);
      for (var body : bodyList) {
        body.setParent(this);
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
      var children = new ArrayList<Node>(modifiers);
      children.add(identifier);
      children.addAll(bodyList);
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectBodyProperty(this);
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Identifier getIdentifier() {
      return identifier;
    }

    public List<ObjectBody> getBodyList() {
      return bodyList;
    }

    @Override
    public String toString() {
      return "ObjectBodyProperty{"
          + "modifiers="
          + modifiers
          + ", identifier="
          + identifier
          + ", bodyList="
          + bodyList
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
      ObjectBodyProperty that = (ObjectBodyProperty) o;
      return Objects.equals(modifiers, that.modifiers)
          && Objects.equals(identifier, that.identifier)
          && Objects.equals(bodyList, that.bodyList)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(modifiers, identifier, bodyList, span);
    }
  }

  final class ObjectMethod implements ObjectMemberNode {
    private final List<Modifier> modifiers;
    private final Identifier identifier;
    private final @Nullable TypeParameterList typeParameterList;
    private final ParameterList paramList;
    private final @Nullable TypeAnnotation typeAnnotation;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ObjectMethod(
        List<Modifier> modifiers,
        Identifier identifier,
        @Nullable TypeParameterList typeParameterList,
        ParameterList paramList,
        @Nullable TypeAnnotation typeAnnotation,
        Expr expr,
        Span span) {
      this.modifiers = modifiers;
      this.identifier = identifier;
      this.typeParameterList = typeParameterList;
      this.paramList = paramList;
      this.typeAnnotation = typeAnnotation;
      this.expr = expr;
      this.span = span;

      for (var mod : modifiers) {
        mod.setParent(this);
      }
      identifier.setParent(this);
      if (typeParameterList != null) {
        typeParameterList.setParent(this);
      }
      paramList.setParent(this);
      if (typeAnnotation != null) {
        typeAnnotation.setParent(this);
      }
      expr.setParent(this);
    }

    @Override
    public Span span() {
      return span;
    }

    public Node parent() {
      return parent;
    }

    @Override
    public void setParent(Node parent) {
      this.parent = parent;
    }

    @Override
    public List<Node> children() {
      var children = new ArrayList<Node>(modifiers);
      children.add(identifier);
      if (typeParameterList != null) {
        children.add(typeParameterList);
      }
      children.add(paramList);
      if (typeAnnotation != null) {
        children.add(typeAnnotation);
      }
      children.add(expr);
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectMethod(this);
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Identifier getIdentifier() {
      return identifier;
    }

    public @Nullable TypeParameterList getTypeParameterList() {
      return typeParameterList;
    }

    public ParameterList getParamList() {
      return paramList;
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      return typeAnnotation;
    }

    public Expr getExpr() {
      return expr;
    }

    public Span headerSpan() {
      var end = span;
      if (typeAnnotation != null) {
        end = typeAnnotation.span();
      } else {
        end = paramList.span();
      }
      return span.endWith(end);
    }

    @Override
    public String toString() {
      return "ObjectMethod{"
          + "modifiers="
          + modifiers
          + ", identifier="
          + identifier
          + ", typeParameterList="
          + typeParameterList
          + ", paramList="
          + paramList
          + ", typeAnnotation="
          + typeAnnotation
          + ", expr="
          + expr
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
      ObjectMethod that = (ObjectMethod) o;
      return Objects.equals(modifiers, that.modifiers)
          && Objects.equals(identifier, that.identifier)
          && Objects.equals(typeParameterList, that.typeParameterList)
          && Objects.equals(paramList, that.paramList)
          && Objects.equals(typeAnnotation, that.typeAnnotation)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          modifiers, identifier, typeParameterList, paramList, typeAnnotation, expr, span);
    }
  }

  final class MemberPredicate implements ObjectMemberNode {
    private final Expr pred;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public MemberPredicate(Expr pred, Expr expr, Span span) {
      this.pred = pred;
      this.expr = expr;
      this.span = span;

      pred.setParent(this);
      expr.setParent(this);
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
      return List.of(pred, expr);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitMemberPredicate(this);
    }

    public Expr getPred() {
      return pred;
    }

    public Expr getExpr() {
      return expr;
    }

    @Override
    public String toString() {
      return "MemberPredicate{" + "pred=" + pred + ", expr=" + expr + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MemberPredicate that = (MemberPredicate) o;
      return Objects.equals(pred, that.pred)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(pred, expr, span);
    }
  }

  final class MemberPredicateBody implements ObjectMemberNode {
    private final Expr key;
    private final List<ObjectBody> bodyList;
    private final Span span;
    private Node parent;

    public MemberPredicateBody(Expr key, List<ObjectBody> bodyList, Span span) {
      this.key = key;
      this.bodyList = bodyList;
      this.span = span;

      key.setParent(this);
      for (var body : bodyList) {
        body.setParent(this);
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
      var children = new ArrayList<Node>(bodyList.size() + 1);
      children.add(key);
      children.addAll(bodyList);
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitMemberPredicateBody(this);
    }

    public Expr getKey() {
      return key;
    }

    public List<ObjectBody> getBodyList() {
      return bodyList;
    }

    @Override
    public String toString() {
      return "MemberPredicateBody{"
          + "key="
          + key
          + ", bodyList="
          + bodyList
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
      MemberPredicateBody that = (MemberPredicateBody) o;
      return Objects.equals(key, that.key)
          && Objects.equals(bodyList, that.bodyList)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, bodyList, span);
    }
  }

  final class ObjectEntry implements ObjectMemberNode {
    private final Expr key;
    private final Expr value;
    private final Span span;
    private Node parent;

    public ObjectEntry(Expr key, Expr value, Span span) {
      this.key = key;
      this.value = value;
      this.span = span;

      key.setParent(this);
      value.setParent(this);
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
      return List.of(key, value);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectEntry(this);
    }

    public Expr getKey() {
      return key;
    }

    public Expr getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "ObjectEntry{" + "key=" + key + ", value=" + value + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ObjectEntry that = (ObjectEntry) o;
      return Objects.equals(key, that.key)
          && Objects.equals(value, that.value)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value, span);
    }
  }

  final class ObjectEntryBody implements ObjectMemberNode {
    private final Expr key;
    private final List<ObjectBody> bodyList;
    private final Span span;
    private Node parent;

    public ObjectEntryBody(Expr key, List<ObjectBody> bodyList, Span span) {
      this.key = key;
      this.bodyList = bodyList;
      this.span = span;

      key.setParent(this);
      for (var body : bodyList) {
        body.setParent(this);
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
      var children = new ArrayList<Node>(bodyList.size() + 1);
      children.add(key);
      children.addAll(bodyList);
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectEntryBody(this);
    }

    public Expr getKey() {
      return key;
    }

    public List<ObjectBody> getBodyList() {
      return bodyList;
    }

    @Override
    public String toString() {
      return "ObjectEntryBody{" + "key=" + key + ", bodyList=" + bodyList + ", span=" + span + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ObjectEntryBody that = (ObjectEntryBody) o;
      return Objects.equals(key, that.key)
          && Objects.equals(bodyList, that.bodyList)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, bodyList, span);
    }
  }

  final class ObjectSpread implements ObjectMemberNode {
    private final Expr expr;
    private final boolean isNullable;
    private final Span span;
    private Node parent;

    public ObjectSpread(Expr expr, boolean isNullable, Span span) {
      this.expr = expr;
      this.isNullable = isNullable;
      this.span = span;

      expr.setParent(this);
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
      return List.of(expr);
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitObjectSpread(this);
    }

    public Expr getExpr() {
      return expr;
    }

    public boolean isNullable() {
      return isNullable;
    }

    @Override
    public String toString() {
      return "ObjectSpread{"
          + "expr="
          + expr
          + ", isNullable="
          + isNullable
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
      ObjectSpread that = (ObjectSpread) o;
      return isNullable == that.isNullable
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(expr, isNullable, span);
    }
  }

  final class WhenGenerator implements ObjectMemberNode {
    private final Expr cond;
    private final ObjectBody body;
    private final @Nullable ObjectBody elseClause;
    private final Span span;
    private Node parent;

    public WhenGenerator(Expr cond, ObjectBody body, @Nullable ObjectBody elseClause, Span span) {
      this.cond = cond;
      this.body = body;
      this.elseClause = elseClause;
      this.span = span;

      cond.setParent(this);
      body.setParent(this);
      if (elseClause != null) {
        elseClause.setParent(this);
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
      var children = new ArrayList<Node>();
      children.add(cond);
      children.add(body);
      if (elseClause != null) {
        children.add(elseClause);
      }
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitWhenGenerator(this);
    }

    public Expr getCond() {
      return cond;
    }

    public ObjectBody getBody() {
      return body;
    }

    public @Nullable ObjectBody getElseClause() {
      return elseClause;
    }

    @Override
    public String toString() {
      return "WhenGenerator{"
          + "cond="
          + cond
          + ", body="
          + body
          + ", elseClause="
          + elseClause
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
      WhenGenerator that = (WhenGenerator) o;
      return Objects.equals(cond, that.cond)
          && Objects.equals(body, that.body)
          && Objects.equals(elseClause, that.elseClause)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(cond, body, elseClause, span);
    }
  }

  final class ForGenerator implements ObjectMemberNode {
    private final Parameter p1;
    private final @Nullable Parameter p2;
    private final Expr expr;
    private final ObjectBody body;
    private final Span span;
    private Node parent;

    public ForGenerator(
        Parameter p1, @Nullable Parameter p2, Expr expr, ObjectBody body, Span span) {
      this.p1 = p1;
      this.p2 = p2;
      this.expr = expr;
      this.body = body;
      this.span = span;

      p1.setParent(this);
      if (p2 != null) {
        p2.setParent(this);
      }
      expr.setParent(this);
      body.setParent(this);
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
      var children = new ArrayList<Node>();
      children.add(p1);
      if (p2 != null) {
        children.add(p2);
      }
      children.add(expr);
      children.add(body);
      return children;
    }

    @Override
    public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
      return visitor.visitForGenerator(this);
    }

    public Parameter getP1() {
      return p1;
    }

    public @Nullable Parameter getP2() {
      return p2;
    }

    public Expr getExpr() {
      return expr;
    }

    public ObjectBody getBody() {
      return body;
    }

    public Span forSpan() {
      return new Span(span.charIndex(), 3);
    }

    @Override
    public String toString() {
      return "ForGenerator{"
          + "p1="
          + p1
          + ", p2="
          + p2
          + ", expr="
          + expr
          + ", body="
          + body
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
      ForGenerator that = (ForGenerator) o;
      return Objects.equals(p1, that.p1)
          && Objects.equals(p2, that.p2)
          && Objects.equals(expr, that.expr)
          && Objects.equals(body, that.body)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(p1, p2, expr, body, span);
    }
  }
}
