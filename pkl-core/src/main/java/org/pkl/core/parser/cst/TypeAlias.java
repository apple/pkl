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

public final class TypeAlias implements Node {
  private final @Nullable DocComment docComment;
  private final List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final Identifier name;
  private final @Nullable TypeParameterList typeParameterList;
  private final Type type;
  private final Span span;
  private Node parent;

  public TypeAlias(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Identifier name,
      @Nullable TypeParameterList typeParameterList,
      Type type,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.typeParameterList = typeParameterList;
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
    if (typeParameterList != null) {
      typeParameterList.setParent(this);
    }
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
    children.add(type);
    return children;
  }

  @Override
  public <T> @Nullable T accept(ParserVisitor<? extends T> visitor) {
    return visitor.visitTypeAlias(this);
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

  public Type getType() {
    return type;
  }

  public Span getHeaderSpan() {
    var end = name.span();
    if (typeParameterList != null) {
      end = typeParameterList.span();
    }
    return span.endWith(end);
  }

  @Override
  public String toString() {
    return "TypeAlias{"
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
    TypeAlias typeAlias = (TypeAlias) o;
    return Objects.equals(docComment, typeAlias.docComment)
        && Objects.equals(annotations, typeAlias.annotations)
        && Objects.equals(modifiers, typeAlias.modifiers)
        && Objects.equals(name, typeAlias.name)
        && Objects.equals(typeParameterList, typeAlias.typeParameterList)
        && Objects.equals(type, typeAlias.type)
        && Objects.equals(span, typeAlias.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(docComment, annotations, modifiers, name, typeParameterList, type, span);
  }
}
