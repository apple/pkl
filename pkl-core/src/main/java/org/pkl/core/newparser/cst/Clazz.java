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

public final class Clazz implements ModuleEntry {
  private final @Nullable DocComment docComment;
  private final List<Annotation> annotations;
  private final List<Modifier> modifiers;
  private final Ident name;
  private final List<TypeParameter> typePars;
  private final @Nullable QualifiedIdent superClass;
  private final List<ClassEntry> body;
  private final Span span;
  private Node parent;

  public Clazz(
      @Nullable DocComment docComment,
      List<Annotation> annotations,
      List<Modifier> modifiers,
      Ident name,
      List<TypeParameter> typePars,
      @Nullable QualifiedIdent superClass,
      List<ClassEntry> body,
      Span span) {
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.name = name;
    this.typePars = typePars;
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
    for (var tpar : typePars) {
      tpar.setParent(this);
    }
    if (superClass != null) {
      superClass.setParent(this);
    }
    for (var entry : body) {
      entry.setParent(this);
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

  public @Nullable QualifiedIdent getSuperClass() {
    return superClass;
  }

  public List<ClassEntry> getBody() {
    return body;
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
        + ", typePars="
        + typePars
        + ", superClass="
        + superClass
        + ", body="
        + body
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
    Clazz clazz = (Clazz) o;
    return Objects.equals(docComment, clazz.docComment)
        && Objects.equals(annotations, clazz.annotations)
        && Objects.equals(modifiers, clazz.modifiers)
        && Objects.equals(name, clazz.name)
        && Objects.equals(typePars, clazz.typePars)
        && Objects.equals(superClass, clazz.superClass)
        && Objects.equals(body, clazz.body)
        && Objects.equals(span, clazz.span)
        && Objects.equals(parent, clazz.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        docComment, annotations, modifiers, name, typePars, superClass, body, span, parent);
  }
}
