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

public final class Clazz implements Node {
  private final @Nullable DocComment docComment;
  private final List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final Identifier name;
  private final @Nullable TypeParameterList typeParameterList;
  private final @Nullable Type superClass;
  private final @Nullable ClassBody body;
  private final Span span;
  private Node parent;

  public Clazz(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Identifier name,
      @Nullable TypeParameterList typeParameterList,
      @Nullable Type superClass,
      @Nullable ClassBody body,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.typeParameterList = typeParameterList;
    this.superClass = superClass;
    this.body = body;
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
    if (superClass != null) {
      superClass.setParent(this);
    }
    if (body != null) {
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
    if (superClass != null) {
      children.add(superClass);
    }
    if (body != null) {
      children.add(body);
    }
    return children;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitClass(this);
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

  public @Nullable Type getSuperClass() {
    return superClass;
  }

  public @Nullable ClassBody getBody() {
    return body;
  }

  public Span getHeaderSpan() {
    Span end;
    if (superClass != null) {
      end = superClass.span();
    } else if (typeParameterList != null) {
      end = typeParameterList.span();
    } else {
      end = name.span();
    }
    return span.endWith(end);
  }

  @Override
  public String toString() {
    return "Clazz{"
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
        + ", superClass="
        + superClass
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
    Clazz clazz = (Clazz) o;
    return Objects.equals(docComment, clazz.docComment)
        && Objects.equals(annotations, clazz.annotations)
        && Objects.equals(modifiers, clazz.modifiers)
        && Objects.equals(name, clazz.name)
        && Objects.equals(typeParameterList, clazz.typeParameterList)
        && Objects.equals(superClass, clazz.superClass)
        && Objects.equals(body, clazz.body)
        && Objects.equals(span, clazz.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        docComment, annotations, modifiers, name, typeParameterList, superClass, body, span);
  }
}
