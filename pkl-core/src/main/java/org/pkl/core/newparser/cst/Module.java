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

public final class Module implements Node {
  private final @Nullable ModuleDecl decl;
  private final List<Import> imports;
  private final List<ModuleEntry> entries;
  private final Span span;
  private Node parent;

  public Module(
      @Nullable ModuleDecl decl, List<Import> imports, List<ModuleEntry> entries, Span span) {
    this.decl = decl;
    this.imports = imports;
    this.entries = entries;
    this.span = span;

    if (decl != null) {
      decl.setParent(this);
    }
    for (var imp : imports) {
      imp.setParent(this);
    }
    for (var entry : entries) {
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

  public @Nullable ModuleDecl getDecl() {
    return decl;
  }

  public List<Import> getImports() {
    return imports;
  }

  public List<ModuleEntry> getEntries() {
    return entries;
  }

  @Override
  public String toString() {
    return "Module{"
        + "decl="
        + decl
        + ", imports="
        + imports
        + ", entries="
        + entries
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
        && Objects.equals(entries, module.entries)
        && Objects.equals(span, module.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(decl, imports, entries, span);
  }
}
