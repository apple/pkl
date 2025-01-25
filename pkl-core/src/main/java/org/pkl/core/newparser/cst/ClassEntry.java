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
public sealed interface ClassEntry extends ModuleEntry {

  final class ClassProperty implements ClassEntry {
    private final @Nullable DocComment docComment;
    private final List<Annotation> annotations;
    private final List<Modifier> modifiers;
    private final Ident name;
    private final Type type;
    private final Span span;
    private Node parent;

    public ClassProperty(
        @Nullable DocComment docComment,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        Ident name,
        Type type,
        Span span) {
      this.docComment = docComment;
      this.annotations = annotations;
      this.modifiers = modifiers;
      this.name = name;
      this.type = type;
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
      type.setParent(this);
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

    public @Nullable DocComment getDocComment() {
      return docComment;
    }

    public List<Annotation> getAnnotations() {
      return annotations;
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Ident getName() {
      return name;
    }

    public Type getType() {
      return type;
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
          + ", type="
          + type
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
          && Objects.equals(type, that.type)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(docComment, annotations, modifiers, name, type, span);
    }
  }

  final class ClassPropertyExpr implements ClassEntry {
    private final @Nullable DocComment docComment;
    private final List<Annotation> annotations;
    private final List<Modifier> modifiers;
    private final Ident name;
    private final @Nullable Type type;
    private final Expr expr;
    private final Span span;
    private Node parent;

    public ClassPropertyExpr(
        @Nullable DocComment docComment,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        Ident name,
        @Nullable Type type,
        Expr expr,
        Span span) {
      this.docComment = docComment;
      this.annotations = annotations;
      this.modifiers = modifiers;
      this.name = name;
      this.type = type;
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
      if (type != null) {
        type.setParent(this);
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

    public @Nullable DocComment getDocComment() {
      return docComment;
    }

    public List<Annotation> getAnnotations() {
      return annotations;
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Ident getName() {
      return name;
    }

    public @Nullable Type getType() {
      return type;
    }

    public Expr getExpr() {
      return expr;
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
          + ", type="
          + type
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
          && Objects.equals(type, that.type)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(docComment, annotations, modifiers, name, type, expr, span);
    }
  }

  final class ClassPropertyBody implements ClassEntry {
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

    public @Nullable DocComment getDocComment() {
      return docComment;
    }

    public List<Annotation> getAnnotations() {
      return annotations;
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Ident getName() {
      return name;
    }

    public List<ObjectBody> getBodyList() {
      return bodyList;
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

  final class ClassMethod implements ClassEntry {
    private final @Nullable DocComment docComment;
    private final List<Annotation> annotations;
    private final List<Modifier> modifiers;
    private final Ident name;
    private final List<TypeParameter> typePars;
    private final List<Parameter> args;
    private final @Nullable Type returnType;
    private final @Nullable Expr expr;
    private final Span span;
    private Node parent;

    public ClassMethod(
        @Nullable DocComment docComment,
        List<Annotation> annotations,
        List<Modifier> modifiers,
        Ident name,
        List<TypeParameter> typePars,
        List<Parameter> args,
        @Nullable Type returnType,
        @Nullable Expr expr,
        Span span) {
      this.docComment = docComment;
      this.annotations = annotations;
      this.modifiers = modifiers;
      this.name = name;
      this.typePars = typePars;
      this.args = args;
      this.returnType = returnType;
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
      for (var tpar : typePars) {
        tpar.setParent(this);
      }
      for (var arg : args) {
        arg.setParent(this);
      }
      if (returnType != null) {
        returnType.setParent(this);
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

    public @Nullable DocComment getDocComment() {
      return docComment;
    }

    public List<Annotation> getAnnotations() {
      return annotations;
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public Ident getName() {
      return name;
    }

    public List<TypeParameter> getTypePars() {
      return typePars;
    }

    public List<Parameter> getArgs() {
      return args;
    }

    public @Nullable Type getReturnType() {
      return returnType;
    }

    public @Nullable Expr getExpr() {
      return expr;
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
          + ", typePars="
          + typePars
          + ", args="
          + args
          + ", returnType="
          + returnType
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
          && Objects.equals(typePars, that.typePars)
          && Objects.equals(args, that.args)
          && Objects.equals(returnType, that.returnType)
          && Objects.equals(expr, that.expr)
          && Objects.equals(span, that.span);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          docComment, annotations, modifiers, name, typePars, args, returnType, expr, span);
    }
  }
}
