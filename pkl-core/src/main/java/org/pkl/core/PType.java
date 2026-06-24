/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/** A Pkl type as used in type annotations. */
public abstract class PType implements Serializable {
  @Serial private static final long serialVersionUID = 0L;

  /** The `unknown` type. Omitting a type annotation is equivalent to stating this type. */
  public static final PType UNKNOWN =
      new PType() {
        @Serial private static final long serialVersionUID = 0L;

        @Override
        public String toString() {
          return "unknown";
        }
      };

  /** The bottom type. */
  public static final PType NOTHING =
      new PType() {
        @Serial private static final long serialVersionUID = 0L;

        @Override
        public String toString() {
          return "nothing";
        }
      };

  /** The type of the enclosing module. */
  public static final PType MODULE =
      new PType() {
        @Serial private static final long serialVersionUID = 0L;

        @Override
        public String toString() {
          return "module";
        }
      };

  /** The type of the enclosing owner. */
  public static final PType THIS =
      new PType() {
        @Serial private static final long serialVersionUID = 0L;

        @Override
        public String toString() {
          return "this";
        }
      };

  private PType() {}

  public List<PType> getTypeArguments() {
    return List.of();
  }

  public static final class StringLiteral extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final String literal;

    public StringLiteral(String literal) {
      this.literal = literal;
    }

    public String getLiteral() {
      return literal;
    }

    @Override
    public String toString() {
      return ValueFormatter.basic().formatStringValue(literal, "");
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof StringLiteral that && literal.equals(that.literal);
    }

    @Override
    public int hashCode() {
      return literal.hashCode();
    }
  }

  public static final class Class extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final PClass pClass;
    private final List<PType> typeArguments;

    public Class(PClass pClass, List<PType> typeArguments) {
      this.pClass = pClass;
      this.typeArguments = typeArguments;
    }

    public Class(PClass pClass) {
      this(pClass, List.of());
    }

    public Class(PClass pClass, PType typeArgument1) {
      this(pClass, List.of(typeArgument1));
    }

    public Class(PClass pClass, PType typeArgument1, PType typeArgument2) {
      this(pClass, List.of(typeArgument1, typeArgument2));
    }

    public PClass getPClass() {
      return pClass;
    }

    @Override
    public List<PType> getTypeArguments() {
      return typeArguments;
    }

    @Override
    public String toString() {
      var result = pClass.getDisplayName();
      if (!typeArguments.isEmpty()) {
        result +=
            "<"
                + typeArguments.stream().map(Object::toString).collect(Collectors.joining(", "))
                + ">";
      }
      return result;
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof Class that
          && pClass.equals(that.pClass)
          && typeArguments.equals(that.typeArguments);
    }

    @Override
    public int hashCode() {
      return 31 * pClass.hashCode() + typeArguments.hashCode();
    }
  }

  public static final class Nullable extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final PType baseType;

    public Nullable(PType baseType) {
      this.baseType = baseType;
    }

    public PType getBaseType() {
      return baseType;
    }

    @Override
    public String toString() {
      return baseType instanceof Function || baseType instanceof Union
          ? "(" + baseType + ")?"
          : baseType + "?";
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof Nullable that && baseType.equals(that.baseType);
    }

    @Override
    public int hashCode() {
      return baseType.hashCode();
    }
  }

  public static final class Constrained extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final PType baseType;
    private final List<String> constraints;

    public Constrained(PType baseType, List<String> constraints) {
      this.baseType = baseType;
      this.constraints = constraints;
    }

    public PType getBaseType() {
      return baseType;
    }

    public List<String> getConstraints() {
      return constraints;
    }

    @Override
    public String toString() {
      return (baseType instanceof Function || baseType instanceof Union
              ? "(" + baseType + ")"
              : baseType)
          + "("
          + String.join(", ", constraints)
          + ")";
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof Constrained that
          && baseType.equals(that.baseType)
          && constraints.equals(that.constraints);
    }

    @Override
    public int hashCode() {
      return 31 * baseType.hashCode() + constraints.hashCode();
    }
  }

  public static final class Alias extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final TypeAlias typeAlias;
    private final List<PType> typeArguments;
    private final PType aliasedType;

    public Alias(TypeAlias typeAlias) {
      this(typeAlias, List.of(), typeAlias.getAliasedType());
    }

    public Alias(TypeAlias typeAlias, List<PType> typeArguments, PType aliasedType) {
      this.typeAlias = typeAlias;
      this.typeArguments = typeArguments;
      this.aliasedType = aliasedType;
    }

    public TypeAlias getTypeAlias() {
      return typeAlias;
    }

    @Override
    public List<PType> getTypeArguments() {
      return typeArguments;
    }

    /**
     * Returns the aliased type, namely {@code getTypeAlias().getAliasedType()} with type arguments
     * substituted for type variables.
     */
    public PType getAliasedType() {
      return aliasedType;
    }

    @Override
    public String toString() {
      var result = typeAlias.getDisplayName();
      if (!typeArguments.isEmpty()) {
        result +=
            "<"
                + typeArguments.stream().map(Object::toString).collect(Collectors.joining(", "))
                + ">";
      }
      return result;
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof Alias that
          && typeAlias.equals(that.typeAlias)
          && typeArguments.equals(that.typeArguments);
    }

    @Override
    public int hashCode() {
      return 31 * typeAlias.hashCode() + typeArguments.hashCode();
    }
  }

  public static final class Function extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final List<PType> parameterTypes;
    private final PType returnType;

    public Function(List<PType> parameterTypes, PType returnType) {
      this.parameterTypes = parameterTypes;
      this.returnType = returnType;
    }

    public List<PType> getParameterTypes() {
      return parameterTypes;
    }

    public PType getReturnType() {
      return returnType;
    }

    @Override
    public String toString() {
      return "("
          + parameterTypes.stream().map(Object::toString).collect(Collectors.joining(", "))
          + ") -> "
          + returnType;
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof Function that
          && parameterTypes.equals(that.parameterTypes)
          && returnType.equals(that.returnType);
    }

    @Override
    public int hashCode() {
      return 31 * parameterTypes.hashCode() + returnType.hashCode();
    }
  }

  public static final class Union extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final List<PType> elementTypes;

    public Union(List<PType> elementTypes) {
      this.elementTypes = elementTypes;
    }

    public List<PType> getElementTypes() {
      return elementTypes;
    }

    @Override
    public String toString() {
      return elementTypes.stream().map(Object::toString).collect(Collectors.joining(" | "));
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof Union that && elementTypes.equals(that.elementTypes);
    }

    @Override
    public int hashCode() {
      return elementTypes.hashCode();
    }
  }

  public static final class TypeVariable extends PType {
    @Serial private static final long serialVersionUID = 0L;

    private final TypeParameter typeParameter;

    public TypeVariable(TypeParameter typeParameter) {
      this.typeParameter = typeParameter;
    }

    public String getName() {
      return typeParameter.getName();
    }

    public TypeParameter getTypeParameter() {
      return typeParameter;
    }

    @Override
    public String toString() {
      return typeParameter.getName();
    }

    @Override
    public boolean equals(@org.jspecify.annotations.Nullable Object obj) {
      if (obj == this) return true;
      return obj instanceof TypeVariable that && typeParameter.equals(that.typeParameter);
    }

    @Override
    public int hashCode() {
      return typeParameter.hashCode();
    }
  }
}
