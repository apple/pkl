/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.*;
import org.pkl.core.util.Nullable;

/** Java representation of a {@code pkl.base#Class} value. */
public final class PClass extends Member implements Value {
  private static final long serialVersionUID = 0L;

  private final PClassInfo<?> classInfo;
  private final List<TypeParameter> typeParameters;
  private final Map<String, Property> properties;
  private final Map<String, Method> methods;

  private @Nullable PType supertype;
  private @Nullable PClass superclass;

  private @Nullable Map<String, Property> allProperties;
  private @Nullable Map<String, Method> allMethods;

  public PClass(
      @Nullable String docComment,
      SourceLocation sourceLocation,
      Set<Modifier> modifiers,
      List<PObject> annotations,
      PClassInfo<?> classInfo,
      List<TypeParameter> typeParameters,
      Map<String, Property> properties,
      Map<String, Method> methods) {
    super(docComment, sourceLocation, modifiers, annotations, classInfo.getSimpleName());
    this.classInfo = classInfo;
    this.typeParameters = typeParameters;
    this.properties = properties;
    this.methods = methods;
  }

  public void initSupertype(PType supertype, PClass superclass) {
    this.supertype = supertype;
    this.superclass = superclass;
  }

  /**
   * Returns the name of the module that this class is declared in. Note that a module name is not
   * guaranteed to be unique, especially if it not declared but inferred from the module URI.
   */
  public String getModuleName() {
    return classInfo.getModuleName();
  }

  /**
   * Returns the qualified name of this class, `moduleName#className`. Note that a qualified class
   * name is not guaranteed to be unique, especially if the module name is not declared but inferred
   * from the module URI.
   */
  public String getQualifiedName() {
    return classInfo.getQualifiedName();
  }

  public String getDisplayName() {
    return classInfo.getDisplayName();
  }

  public PClassInfo<?> getInfo() {
    return classInfo;
  }

  /** Tells if this class is the class of a module. */
  public boolean isModuleClass() {
    return getInfo().isModuleClass();
  }

  public List<TypeParameter> getTypeParameters() {
    return typeParameters;
  }

  public @Nullable PType getSupertype() {
    return supertype;
  }

  public @Nullable PClass getSuperclass() {
    return superclass;
  }

  public Map<String, Property> getProperties() {
    return properties;
  }

  public Map<String, Method> getMethods() {
    return methods;
  }

  public Map<String, Property> getAllProperties() {
    if (allProperties == null) {
      allProperties = collectAllProperties(this, new LinkedHashMap<>());
    }
    return allProperties;
  }

  public Map<String, Method> getAllMethods() {
    if (allMethods == null) {
      allMethods = collectAllMethods(this, new LinkedHashMap<>());
    }
    return allMethods;
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitClass(this);
  }

  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertClass(this);
  }

  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.Class;
  }

  public String toString() {
    return getDisplayName();
  }

  public abstract static class ClassMember extends Member {

    private static final long serialVersionUID = 0L;

    private final PClass owner;

    public ClassMember(
        @Nullable String docComment,
        SourceLocation sourceLocation,
        Set<Modifier> modifiers,
        List<PObject> annotations,
        String simpleName,
        PClass owner) {
      super(docComment, sourceLocation, modifiers, annotations, simpleName);
      this.owner = owner;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleName() {
      return owner.getInfo().getModuleName();
    }

    /** Returns the class declaring this member. */
    public PClass getOwner() {
      return owner;
    }

    /**
     * Returns the documentation comment of this member. If this member does not have a
     * documentation comment, returns the documentation comment of the nearest documented ancestor,
     * if any.
     */
    public abstract @Nullable String getInheritedDocComment();
  }

  public static final class Property extends ClassMember {

    private static final long serialVersionUID = 0L;

    private final PType type;

    public Property(
        PClass owner,
        @Nullable String docComment,
        SourceLocation sourceLocation,
        Set<Modifier> modifiers,
        List<PObject> annotations,
        String simpleName,
        PType type) {
      super(docComment, sourceLocation, modifiers, annotations, simpleName, owner);
      this.type = type;
    }

    public PType getType() {
      return type;
    }

    @Override
    public @Nullable String getInheritedDocComment() {
      if (getDocComment() != null) return getDocComment();

      for (var clazz = getOwner().getSuperclass(); clazz != null; clazz = clazz.getSuperclass()) {
        var property = clazz.getProperties().get(getSimpleName());
        if (property != null && property.getDocComment() != null) {
          return property.getDocComment();
        }
      }

      return null;
    }
  }

  public static final class Method extends ClassMember {

    private static final long serialVersionUID = 0L;

    private final List<TypeParameter> typeParameters;
    private final Map<String, PType> parameters;
    private final PType returnType;

    public Method(
        PClass owner,
        @Nullable String docComment,
        SourceLocation sourceLocation,
        Set<Modifier> modifiers,
        List<PObject> annotations,
        String simpleName,
        List<TypeParameter> typeParameters,
        Map<String, PType> parameters,
        PType returnType) {
      super(docComment, sourceLocation, modifiers, annotations, simpleName, owner);
      this.typeParameters = typeParameters;
      this.parameters = parameters;
      this.returnType = returnType;
    }

    public List<TypeParameter> getTypeParameters() {
      return typeParameters;
    }

    public Map<String, PType> getParameters() {
      return parameters;
    }

    public PType getReturnType() {
      return returnType;
    }

    @Override
    public @Nullable String getInheritedDocComment() {
      if (getDocComment() != null) return getDocComment();

      for (var clazz = getOwner().getSuperclass(); clazz != null; clazz = clazz.getSuperclass()) {
        var method = clazz.getMethods().get(getSimpleName());
        if (method != null && method.getDocComment() != null) {
          return method.getDocComment();
        }
      }

      return null;
    }
  }

  private Map<String, Property> collectAllProperties(
      PClass clazz, Map<String, Property> collector) {
    if (clazz.superclass != null) {
      collectAllProperties(clazz.superclass, collector);
    }
    collector.putAll(clazz.properties);
    return collector;
  }

  private Map<String, Method> collectAllMethods(PClass clazz, Map<String, Method> collector) {
    if (clazz.superclass != null) {
      collectAllMethods(clazz.superclass, collector);
    }
    collector.putAll(clazz.methods);
    return collector;
  }
}
