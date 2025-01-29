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
package org.pkl.core.newparser.cst;

import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

@SuppressWarnings("DuplicatedCode")
public sealed interface ClassPropertyEntry extends Node {

  @Nullable
  DocComment docComment();

  List<Annotation> annotations();

  List<Modifier> modifiers();

  Ident name();

  @Nullable
  TypeAnnotation typeAnnotation();

  @Nullable
  Expr expr();

  @Nullable
  List<ObjectBody> bodyList();

  final class ClassProperty implements ClassPropertyEntry {
    private final @Nullable DocComment docComment;
    private final List<Annotation> annotations;
    private final List<Modifier> modifiers;
    private final Ident name;
    private final TypeAnnotation typeAnnotation;
    private final Span span;
    private Node parent;

    public ClassProperty(
        @Nullable DocComment docComment,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        Ident name,
        TypeAnnotation typeAnnotation,
        Span span) {
      this.docComment = docComment;
      this.annotations = annotations;
      this.modifiers = modifiers;
      this.name = name;
      this.typeAnnotation = typeAnnotation;
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
      typeAnnotation.setParent(this);
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
    public @Nullable DocComment docComment() {
      return docComment;
    }

    @Override
    public List<Annotation> annotations() {
      return annotations;
    }

    @Override
    public List<Modifier> modifiers() {
      return modifiers;
    }

    @Override
    public Ident name() {
      return name;
    }

    @Override
    public TypeAnnotation typeAnnotation() {
      return typeAnnotation;
    }

    @Override
    public @Nullable Expr expr() {
      return null;
    }

    @Override
    public @Nullable List<ObjectBody> bodyList() {
      return null;
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
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(docComment, annotations, modifiers, name, typeAnnotation, span);
    }
  }

  final class ClassPropertyExpr implements ClassPropertyEntry {
    private final @Nullable DocComment docComment;
    private final List<Annotation> annotations;
    private final List<Modifier> modifiers;
    private final Ident name;
    private final @Nullable TypeAnnotation typeAnnotation;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ClassPropertyExpr(
        @Nullable DocComment docComment,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        Ident name,
        @Nullable TypeAnnotation typeAnnotation,
        Expr expr,
        Span span) {
      this.docComment = docComment;
      this.annotations = annotations;
      this.modifiers = modifiers;
      this.name = name;
      this.typeAnnotation = typeAnnotation;
      this.expr = expr;
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
    public @Nullable DocComment docComment() {
      return docComment;
    }

    @Override
    public List<Annotation> annotations() {
      return annotations;
    }

    @Override
    public List<Modifier> modifiers() {
      return modifiers;
    }

    @Override
    public Ident name() {
      return name;
    }

    public @Nullable TypeAnnotation getTypeAnnotation() {
      return typeAnnotation;
    }

    @Override
    public Expr expr() {
      return expr;
    }

    @Override
    public @Nullable TypeAnnotation typeAnnotation() {
      return typeAnnotation;
    }

    @Override
    public @Nullable List<ObjectBody> bodyList() {
      return null;
    }

    @Override
    public String toString() {
      return "ClassPropertyExpr{"
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
      ClassPropertyExpr that = (ClassPropertyExpr) o;
      return Objects.equals(docComment, that.docComment)
          && Objects.equals(annotations, that.annotations)
          && Objects.equals(modifiers, that.modifiers)
          && Objects.equals(name, that.name)
          && Objects.equals(typeAnnotation, that.typeAnnotation)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(docComment, annotations, modifiers, name, typeAnnotation, expr, span);
    }
  }

  final class ClassPropertyBody implements ClassPropertyEntry {
    private final @Nullable DocComment docComment;
    private final List<Annotation> annotations;
    private final List<Modifier> modifiers;
    private final Ident name;
    private final List<ObjectBody> bodyList;
    private final Span span;
    private Node parent;

    public ClassPropertyBody(
        @Nullable DocComment docComment,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        Ident name,
        List<ObjectBody> bodyList,
        Span span) {
      this.docComment = docComment;
      this.annotations = annotations;
      this.modifiers = modifiers;
      this.name = name;
      this.bodyList = bodyList;
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
    public @Nullable DocComment docComment() {
      return docComment;
    }

    @Override
    public List<Annotation> annotations() {
      return annotations;
    }

    @Override
    public List<Modifier> modifiers() {
      return modifiers;
    }

    @Override
    public Ident name() {
      return name;
    }

    @Override
    public List<ObjectBody> bodyList() {
      return bodyList;
    }

    @Override
    public @Nullable TypeAnnotation typeAnnotation() {
      return null;
    }

    @Override
    public @Nullable Expr expr() {
      return null;
    }

    @Override
    public String toString() {
      return "ClassPropertyBody{"
          + "docComment="
          + docComment
          + ", annotations="
          + annotations
          + ", modifiers="
          + modifiers
          + ", name="
          + name
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
      ClassPropertyBody that = (ClassPropertyBody) o;
      return Objects.equals(docComment, that.docComment)
          && Objects.equals(annotations, that.annotations)
          && Objects.equals(modifiers, that.modifiers)
          && Objects.equals(name, that.name)
          && Objects.equals(bodyList, that.bodyList)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(docComment, annotations, modifiers, name, bodyList, span);
    }
  }
}
