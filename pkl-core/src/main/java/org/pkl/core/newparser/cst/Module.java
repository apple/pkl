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

public final class Module implements Node {
  private final @Nullable ModuleDecl decl;
  private final List<Import> imports;
  private final List<Clazz> classes;
  private final List<TypeAlias> typeAliases;
  private final List<ClassPropertyEntry> properties;
  private final List<ClassMethod> methods;
  private final Span span;
  private Node parent;

  public Module(
      @Nullable ModuleDecl decl,
      List<Import> imports,
      List<Clazz> classes,
      List<TypeAlias> typeAliases,
      List<ClassPropertyEntry> properties,
      List<ClassMethod> methods,
      Span span) {
    this.decl = decl;
    this.imports = imports;
    this.classes = classes;
    this.typeAliases = typeAliases;
    this.properties = properties;
    this.methods = methods;
    this.span = span;

    if (decl != null) {
      decl.setParent(this);
    }
    for (var imp : imports) {
      imp.setParent(this);
    }
    for (var clazz : classes) {
      clazz.setParent(this);
    }
    for (var typeAlias : typeAliases) {
      typeAlias.setParent(this);
    }
    for (var prop : properties) {
      prop.setParent(this);
    }
    for (var method : methods) {
      method.setParent(this);
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
    if (decl != null) {
      children.add(decl);
    }
    children.addAll(imports);
    children.addAll(classes);
    children.addAll(typeAliases);
    children.addAll(properties);
    children.addAll(methods);
    return children;
  }

  public @Nullable ModuleDecl getDecl() {
    return decl;
  }

  public List<Import> getImports() {
    return imports;
  }

  public List<Clazz> getClasses() {
    return classes;
  }

  public List<TypeAlias> getTypeAliases() {
    return typeAliases;
  }

  public List<ClassPropertyEntry> getProperties() {
    return properties;
  }

  public List<ClassMethod> getMethods() {
    return methods;
  }

  @Override
  public String toString() {
    return "Module{"
        + "decl="
        + decl
        + ", imports="
        + imports
        + ", classes="
        + classes
        + ", typeAliases="
        + typeAliases
        + ", properties="
        + properties
        + ", methods="
        + methods
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
    Module module = (Module) o;
    return Objects.equals(decl, module.decl)
        && Objects.equals(imports, module.imports)
        && Objects.equals(classes, module.classes)
        && Objects.equals(typeAliases, module.typeAliases)
        && Objects.equals(properties, module.properties)
        && Objects.equals(methods, module.methods)
        && Objects.equals(span, module.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(decl, imports, classes, typeAliases, properties, methods, span);
  }
}
