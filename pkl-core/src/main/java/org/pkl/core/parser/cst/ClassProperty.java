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

@SuppressWarnings("DuplicatedCode")
public final class ClassProperty implements Node {
  private final @Nullable DocComment docComment;
  private final List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final Identifier name;
  private final @Nullable TypeAnnotation typeAnnotation;
  private final @Nullable Expr expr;
  private final @Nullable List<ObjectBody> bodyList;
  private final Span span;
  private Node parent;

  public ClassProperty(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Identifier name,
      @Nullable TypeAnnotation typeAnnotation,
      @Nullable Expr expr,
      @Nullable List<ObjectBody> bodyList,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.typeAnnotation = typeAnnotation;
    this.expr = expr;
    this.bodyList = bodyList;
    this.span = span;

    if (docComment != null) {
      docComment.setParent(this);
    }
    for (var annotation : annotations) {
      annotation.setParent(this);
    }
    for (var modifier : modifiers) {
      modifier.setParent(this);
    }
    name.setParent(this);
    if (typeAnnotation != null) {
      typeAnnotation.setParent(this);
    }
    if (expr != null) {
      expr.setParent(this);
    }
    if (bodyList != null) {
      for (var body : bodyList) {
        body.setParent(this);
      }
    }
  }

  @Override
  public Span span() {
    return span;
  }

  @Override
  public @Nullable Node parent() {
    return parent;
  }

  @Override
  public void setParent(Node parent) {
    this.parent = parent;
  }

  @Override
  public List<Node> children() {
    var children = new ArrayList<Node>();
    if (docComment != null) {
      children.add(docComment);
    }
    children.addAll(annotations);
    children.addAll(modifiers);
    if (typeAnnotation != null) {
      children.add(typeAnnotation);
    }
    if (expr != null) {
      children.add(expr);
    }
    if (bodyList != null) {
      children.addAll(bodyList);
    }
    return children;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClassProperty(this);
  }

  public @Nullable DocComment getDocComment() {
    return docComment;
  }

  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public List<Modifier> getModifiers() {
    return modifiers;
  }

  public Identifier getName() {
    return name;
  }

  public @Nullable TypeAnnotation getTypeAnnotation() {
    return typeAnnotation;
  }

  public @Nullable Expr getExpr() {
    return expr;
  }

  public @Nullable List<ObjectBody> getBodyList() {
    return bodyList;
  }

  @Override
  public String toString() {
    return "ClassProperty{"
        + "docComment="
        + docComment
        + ", annotations="
        + annotations
        + ", modifiers="
        + modifiers
        + ", name="
        + name
        + ", typeAnnotation="
        + typeAnnotation
        + ", expr="
        + expr
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
    ClassProperty that = (ClassProperty) o;
    return Objects.equals(docComment, that.docComment)
        && Objects.equals(annotations, that.annotations)
        && Objects.equals(modifiers, that.modifiers)
        && Objects.equals(name, that.name)
        && Objects.equals(typeAnnotation, that.typeAnnotation)
        && Objects.equals(expr, that.expr)
        && Objects.equals(bodyList, that.bodyList)
        && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        docComment, annotations, modifiers, name, typeAnnotation, expr, bodyList, span);
  }
}
