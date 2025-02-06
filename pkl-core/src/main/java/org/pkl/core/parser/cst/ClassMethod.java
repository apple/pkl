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

public class ClassMethod implements Node {
  private final @Nullable DocComment docComment;
  private final List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final Identifier name;
  private final @Nullable TypeParameterList typeParameterList;
  private final ParameterList parameterList;
  private final @Nullable TypeAnnotation typeAnnotation;
  private final @Nullable Expr expr;
  private final Span headerSpan;
  private final Span span;
  private Node parent;

  public ClassMethod(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Identifier name,
      @Nullable TypeParameterList typeParameterList,
      ParameterList parameterList,
      @Nullable TypeAnnotation typeAnnotation,
      @Nullable Expr expr,
      Span headerSpan,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.typeParameterList = typeParameterList;
    this.parameterList = parameterList;
    this.typeAnnotation = typeAnnotation;
    this.expr = expr;
    this.headerSpan = headerSpan;
    this.span = span;

    if (docComment != null) {
      docComment.setParent(this);
    }
    for (var ann : annotations) {
      ann.setParent(this);
    }
    for (var mod : modifiers) {
      mod.setParent(this);
    }
    name.setParent(this);
    if (typeParameterList != null) {
      typeParameterList.setParent(this);
    }
    parameterList.setParent(this);
    if (typeAnnotation != null) {
      typeAnnotation.setParent(this);
    }
    if (expr != null) {
      expr.setParent(this);
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

  @SuppressWarnings("DuplicatedCode")
  @Override
  public List<Node> children() {
    var children = new ArrayList<Node>();
    if (docComment != null) {
      children.add(docComment);
    }
    children.addAll(annotations);
    children.addAll(modifiers);
    children.add(name);
    if (typeParameterList != null) {
      children.add(typeParameterList);
    }
    children.add(parameterList);
    if (typeAnnotation != null) {
      children.add(typeAnnotation);
    }
    if (expr != null) {
      children.add(expr);
    }
    return children;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClassMethod(this);
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

  public @Nullable TypeParameterList getTypeParameterList() {
    return typeParameterList;
  }

  public ParameterList getParameterList() {
    return parameterList;
  }

  public @Nullable TypeAnnotation getTypeAnnotation() {
    return typeAnnotation;
  }

  public @Nullable Expr getExpr() {
    return expr;
  }

  public Span getHeaderSpan() {
    return headerSpan;
  }

  @Override
  public String toString() {
    return "ClassMethod{"
        + "docComment="
        + docComment
        + ", annotations="
        + annotations
        + ", modifiers="
        + modifiers
        + ", name="
        + name
        + ", typeParameterList="
        + typeParameterList
        + ", parameterList="
        + parameterList
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
    ClassMethod that = (ClassMethod) o;
    return Objects.equals(docComment, that.docComment)
        && Objects.equals(annotations, that.annotations)
        && Objects.equals(modifiers, that.modifiers)
        && Objects.equals(name, that.name)
        && Objects.equals(typeParameterList, that.typeParameterList)
        && Objects.equals(parameterList, that.parameterList)
        && Objects.equals(typeAnnotation, that.typeAnnotation)
        && Objects.equals(expr, that.expr)
        && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        docComment,
        annotations,
        modifiers,
        name,
        typeParameterList,
        parameterList,
        typeAnnotation,
        expr,
        span);
  }
}
