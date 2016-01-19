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

import java.util.List;
import java.util.Set;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

/** Java representation of a {@code pkl.base#TypeAlias} value. */
public final class TypeAlias extends Member implements Value {
  private static final long serialVersionUID = 0L;

  private final String moduleName;
  private final String qualifiedName;
  private final List<TypeParameter> typeParameters;

  @LateInit private PType aliasedType;

  public TypeAlias(
      @Nullable String docComment,
      SourceLocation sourceLocation,
      Set<Modifier> modifiers,
      List<PObject> annotations,
      String simpleName,
      String moduleName,
      String qualifiedName,
      List<TypeParameter> typeParameters) {
    super(docComment, sourceLocation, modifiers, annotations, simpleName);
    this.moduleName = moduleName;
    this.qualifiedName = qualifiedName;
    this.typeParameters = typeParameters;
  }

  public void initAliasedType(PType type) {
    assert aliasedType == null;
    aliasedType = type;
  }

  /**
   * Returns the name of the module that this type alias is declared in. Note that a module name is
   * not guaranteed to be unique, especially if it not declared but inferred from the module URI.
   */
  public String getModuleName() {
    return moduleName;
  }

  /**
   * Returns the qualified name of this type alias, `moduleName#typeAliasName`. Note that a
   * qualified type alias name is not guaranteed to be unique, especially if the module name is not
   * declared but inferred from the module URI.
   */
  public String getQualifiedName() {
    return qualifiedName;
  }

  /** Returns the name of this type alias for use in user-facing messages. */
  public String getDisplayName() {
    // display `String` rather than `pkl.base#String`, etc.
    return moduleName.equals("pkl.base") ? getSimpleName() : qualifiedName;
  }

  public List<TypeParameter> getTypeParameters() {
    return typeParameters;
  }

  /** Returns the type that this type alias stands for. */
  public PType getAliasedType() {
    assert aliasedType != null;
    return aliasedType;
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitTypeAlias(this);
  }

  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertTypeAlias(this);
  }

  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.TypeAlias;
  }

  public String toString() {
    return getDisplayName();
  }
}
