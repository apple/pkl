/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.net.URI;
import java.util.*;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

/** Describes the property, method and class members of a module. */
public final class ModuleSchema {
  private final URI moduleUri;
  private final String moduleName;
  private final boolean isAmend;
  private final @Nullable ModuleSchema supermodule;
  private final PClass moduleClass;
  private final @Nullable String docComment;
  private final List<PObject> annotations;
  private final Map<String, PClass> classes;
  private final Map<String, TypeAlias> typeAliases;
  private final Map<String, URI> imports;

  @LateInit private Map<String, PClass> __allClasses;
  @LateInit private Map<String, TypeAlias> __allTypeAliases;

  /** Constructs a {@code ModuleSchema} instance. */
  public ModuleSchema(
      URI moduleUri,
      String moduleName,
      boolean isAmend,
      @Nullable ModuleSchema supermodule,
      PClass moduleClass,
      @Nullable String docComment,
      List<PObject> annotations,
      Map<String, PClass> classes,
      Map<String, TypeAlias> typeAliases,
      Map<String, URI> imports) {
    this.moduleUri = moduleUri;
    this.moduleName = moduleName;
    this.isAmend = isAmend;
    this.supermodule = supermodule;
    this.moduleClass = moduleClass;
    this.docComment = docComment;
    this.annotations = annotations;
    this.classes = classes;
    this.typeAliases = typeAliases;
    this.imports = imports;
  }

  /** Returns the absolute URI from which this module was first loaded. */
  public URI getModuleUri() {
    return moduleUri;
  }

  /**
   * Returns the name of this module.
   *
   * <p>Note that module names are not guaranteed to be unique, especially if they are not declared
   * but inferred from the module URI.
   */
  public String getModuleName() {
    return moduleName;
  }

  /**
   * Returns the last name part of a dot-separated {@link #getModuleName}, or the entire {@link
   * #getModuleName} if it is not dot-separated.
   */
  public String getShortModuleName() {
    var index = moduleName.lastIndexOf('.');
    return moduleName.substring(index + 1);
  }

  /**
   * Returns this module's supermodule, or {@code null} if this module does not amend or extend
   * another module.
   */
  public @Nullable ModuleSchema getSupermodule() {
    return supermodule;
  }

  /** Tells if this module amends a module (namely {@link #getSupermodule()}). */
  public boolean isAmend() {
    return isAmend;
  }

  /** Tells if this module extends a module (namely {@link #getSupermodule()}). */
  public boolean isExtend() {
    return supermodule != null && !isAmend;
  }

  /** Returns the doc comment of this module (if any). */
  public @Nullable String getDocComment() {
    return docComment;
  }

  /** Returns the annotations of this module. */
  public List<PObject> getAnnotations() {
    return annotations;
  }

  /**
   * Returns the class of this module, which describes the properties and methods defined in this
   * module.
   */
  public PClass getModuleClass() {
    return moduleClass;
  }

  /**
   * Returns the imports declared in this module.
   *
   * <p>Map keys are the identifiers by which imports are accessed within this module. Map values
   * are the URIs of the imported modules.
   *
   * <p>Does not cover import expressions.
   */
  public Map<String, URI> getImports() {
    return imports;
  }

  /** Returns the classes defined in this module in declaration order. */
  public Map<String, PClass> getClasses() {
    return classes;
  }

  /**
   * Returns all classes defined in this module and its supermodules in declaration order.
   * Supermodule classes are ordered before submodule classes.
   */
  public Map<String, PClass> getAllClasses() {
    if (__allClasses == null) {
      if (supermodule == null) {
        __allClasses = classes;
      } else if (classes.isEmpty()) {
        __allClasses = supermodule.getAllClasses();
      } else {
        __allClasses = new LinkedHashMap<>();
        __allClasses.putAll(supermodule.getAllClasses());
        __allClasses.putAll(classes);
      }
    }
    return __allClasses;
  }

  /** Returns the type aliases defined in this module in declaration order. */
  public Map<String, TypeAlias> getTypeAliases() {
    return typeAliases;
  }

  /**
   * Returns all type aliases defined in this module and its supermodules in declaration order.
   * Supermodule type aliases are ordered before submodule type aliases.
   */
  public Map<String, TypeAlias> getAllTypeAliases() {
    if (__allTypeAliases == null) {
      if (supermodule == null) {
        __allTypeAliases = typeAliases;
      } else if (typeAliases.isEmpty()) {
        __allTypeAliases = supermodule.getAllTypeAliases();
      } else {
        __allTypeAliases = new LinkedHashMap<>();
        __allTypeAliases.putAll(supermodule.getAllTypeAliases());
        __allTypeAliases.putAll(typeAliases);
      }
    }
    return __allTypeAliases;
  }
}
