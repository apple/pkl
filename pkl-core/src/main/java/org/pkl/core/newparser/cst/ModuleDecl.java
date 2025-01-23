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

public final class ModuleDecl implements Node {
  private final @Nullable DocComment docComment;
  private final @Nullable List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final @Nullable QualifiedIdent name;
  private final @Nullable ExtendsDecl extendsUrl;
  private final @Nullable AmendsDecl amendsUrl;
  private final Span span;
  private Node parent;

  public ModuleDecl(
      @Nullable DocComment docComment,
      @Nullable List<Annotation> annotations,
      List<Modifier> modifiers,
      @Nullable QualifiedIdent name,
      @Nullable ExtendsDecl extendsUrl,
      @Nullable AmendsDecl amendsUrl,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.extendsUrl = extendsUrl;
    this.amendsUrl = amendsUrl;
    this.span = span;
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

  public @Nullable DocComment getDocComment() {
    return docComment;
  }

  public @Nullable List<Annotation> getAnnotations() {
    return annotations;
  }

  public List<Modifier> getModifiers() {
    return modifiers;
  }

  public @Nullable QualifiedIdent getName() {
    return name;
  }

  public @Nullable ExtendsDecl getExtendsUrl() {
    return extendsUrl;
  }

  public @Nullable AmendsDecl getAmendsUrl() {
    return amendsUrl;
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
        + ", extendsUrl="
        + extendsUrl
        + ", amendsUrl="
        + amendsUrl
        + ", span="
        + span
        + ", parent="
        + parent
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
        && Objects.equals(extendsUrl, that.extendsUrl)
        && Objects.equals(amendsUrl, that.amendsUrl)
        && Objects.equals(span, that.span)
        && Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        docComment, annotations, modifiers, name, extendsUrl, amendsUrl, span, parent);
  }

  //  public ModuleDecl {
  //    if (docComment != null) {
  //      docComment.parent().set(this);
  //    }
  //    if (annotations != null) {
  //      for (var ann : annotations) {
  //        ann.parent().set(this);
  //      }
  //    }
  //    for (var mod : modifiers) {
  //      mod.parent().set(this);
  //    }
  //    if (name != null) {
  //      name.parent().set(this);
  //    }
  //    if (extendsUrl != null) {
  //      extendsUrl.parent().set(this);
  //    }
  //    if (amendsUrl != null) {
  //      amendsUrl.parent().set(this);
  //    }
  //  }

}
