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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.pkl.core.newparser.Span;
import org.pkl.core.util.Nullable;

public final class ModuleDecl implements Node {
  private final @Nullable DocComment docComment;
  private final List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final @Nullable QualifiedIdent name;
  private final @Nullable ExtendsOrAmendsDecl extendsOrAmendsDecl;
  private final Span span;
  private Node parent;

  public ModuleDecl(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      @Nullable QualifiedIdent name,
      @Nullable ExtendsOrAmendsDecl extendsOrAmendsDecl,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.extendsOrAmendsDecl = extendsOrAmendsDecl;
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
    if (name != null) {
      name.setParent(this);
    }
    if (extendsOrAmendsDecl != null) {
      extendsOrAmendsDecl.setParent(this);
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
    if (name != null) {
      children.add(name);
    }
    if (extendsOrAmendsDecl != null) {
      children.add(extendsOrAmendsDecl);
    }
    return children;
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

  public @Nullable QualifiedIdent getName() {
    return name;
  }

  public @Nullable ExtendsOrAmendsDecl getExtendsOrAmendsDecl() {
    return extendsOrAmendsDecl;
  }

  public Span headerSpan() {
    if (!modifiers.isEmpty()) {
      return modifiers.get(0).span().endWith(span);
    }
    if (name != null) {
      return name.span().endWith(span);
    }
    if (extendsOrAmendsDecl != null) {
      return extendsOrAmendsDecl.span().endWith(span);
    }
    return span;
  }

  @Override
  public String toString() {
    return "ModuleDecl{"
        + "docComment="
        + docComment
        + ", annotations="
        + annotations
        + ", modifiers="
        + modifiers
        + ", name="
        + name
        + ", extendsOrAmendsDecl="
        + extendsOrAmendsDecl
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
    ModuleDecl that = (ModuleDecl) o;
    return Objects.equals(docComment, that.docComment)
        && Objects.equals(annotations, that.annotations)
        && Objects.equals(modifiers, that.modifiers)
        && Objects.equals(name, that.name)
        && Objects.equals(extendsOrAmendsDecl, that.extendsOrAmendsDecl)
        && Objects.equals(span, that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(docComment, annotations, modifiers, name, extendsOrAmendsDecl, span);
  }
}
