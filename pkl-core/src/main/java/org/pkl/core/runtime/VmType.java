/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.pkl.core.PType;
import org.pkl.core.PType.Alias;
import org.pkl.core.PType.Class;
import org.pkl.core.PType.Constrained;
import org.pkl.core.PType.StringLiteral;
import org.pkl.core.PType.TypeVariable;
import org.pkl.core.PType.Union;
import org.pkl.core.TypeParameter;
import org.pkl.core.ValueFormatter;

public abstract class VmType {

  public PType export() {
    var alias = getVmTypeAlias();
    // needs to come before `clazz != null` check
    if (alias != null) {
      return new Alias(alias.export());
    }
    var clazz = getVmClass();
    if (clazz != null) {
      return new Class(clazz.export());
    }
    CompilerDirectives.transferToInterpreter();
    throw new VmExceptionBuilder()
        .bug("`%s` must override method `doExport()`.", getClass().getTypeName())
        .build();
  }

  public boolean isParametric() {
    return false;
  }

  public @Nullable VmClass getVmClass() {
    return null;
  }

  public @Nullable VmTypeAlias getVmTypeAlias() {
    return null;
  }

  /** Tells if this type is the same typecheck as the other type. */
  @Override
  public boolean equals(Object value) {
    return this == value || value instanceof VmType other && doIsEquivalentTo(other);
  }

  protected abstract boolean doIsEquivalentTo(VmType other);

  public final boolean isSubtypeOf(VmType other) {
    return other.isSupertypeOf(this);
  }

  public final boolean isSupertypeOf(VmType other) {
    return equals(other)
        || doIsSupertypeOf(other)
        || other == UNKNOWN // all types are supertypes/subtypes of unknown
        || other instanceof TypeVariableType
        || (other instanceof UnionType ut && ut.allElementsMatch(this::isSupertypeOf))
        || (other instanceof AliasType at && isSupertypeOf(at.aliasedType))
        || (other instanceof ConstrainedType ct && isSupertypeOf(ct.baseType));
  }

  protected abstract boolean doIsSupertypeOf(VmType other);

  public static VmType UNKNOWN =
      new VmType() {
        @Override
        protected boolean doIsEquivalentTo(VmType other) {
          // equality checked by equals()
          return false;
        }

        @Override
        protected boolean doIsSupertypeOf(VmType other) {
          // unknown is supertype of everything!
          return true;
        }

        @Override
        public PType export() {
          return PType.UNKNOWN;
        }

        @Override
        public String toString() {
          return "unknown";
        }
      };

  public static VmType NOTHING =
      new VmType() {
        @Override
        protected boolean doIsEquivalentTo(VmType other) {
          // equality checked by equals()
          return false;
        }

        @Override
        protected boolean doIsSupertypeOf(VmType other) {
          // nothing is a supertype of nothing (except itself and unknown, handled by callers)
          return false;
        }

        @Override
        public PType export() {
          return PType.NOTHING;
        }

        @Override
        public String toString() {
          return "nothing";
        }
      };

  public abstract static class SelfType extends VmType {
    protected final VmClass clazz;

    protected SelfType(VmClass clazz) {
      this.clazz = clazz;
    }

    @Override
    public @Nullable VmClass getVmClass() {
      return clazz;
    }

    @Override
    public int hashCode() {
      return clazz.hashCode();
    }
  }

  public static final class ModuleType extends SelfType {
    public ModuleType(VmClass clazz) {
      super(clazz);
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      return other instanceof ModuleType t && clazz == t.clazz;
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      return other instanceof ModuleType mt && clazz.isSuperclassOf(mt.clazz);
    }

    @Override
    public PType export() {
      return PType.MODULE;
    }

    @Override
    public String toString() {
      return "module";
    }
  }

  public static final class ThisType extends SelfType {
    public ThisType(VmClass clazz) {
      super(clazz);
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      return other instanceof ThisType t && clazz == t.clazz;
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      return other instanceof ThisType mt && clazz.isSuperclassOf(mt.clazz);
    }

    @Override
    public PType export() {
      return PType.THIS;
    }

    @Override
    public String toString() {
      return "this";
    }
  }

  public static final class StringLiteralType extends VmType {
    private final String literal;

    public StringLiteralType(String literal) {
      super();
      this.literal = literal;
    }

    public String getLiteral() {
      return literal;
    }

    @Override
    public PType export() {
      return new StringLiteral(literal);
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      return other instanceof StringLiteralType t && literal.equals(t.literal);
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      return other instanceof StringLiteralType slt && literal.equals(slt.literal);
    }

    @Override
    public String toString() {
      return ValueFormatter.basic().formatStringValue(literal, "");
    }

    @Override
    public int hashCode() {
      return literal.hashCode();
    }
  }

  public static final class ClassType extends VmType {
    private final VmClass clazz;
    private final VmType[] typeArguments;

    public ClassType(VmClass clazz) {
      this.clazz = clazz;
      typeArguments = new VmType[0];
    }

    public ClassType(VmClass clazz, VmType typeArgument) {
      this.clazz = clazz;
      typeArguments = new VmType[] {typeArgument};
    }

    public ClassType(VmClass clazz, VmType typeArgument1, VmType typeArgument2) {
      this.clazz = clazz;
      typeArguments = new VmType[] {typeArgument1, typeArgument2};
    }

    public ClassType(VmClass clazz, VmType[] typeArguments) {
      this.clazz = clazz;
      this.typeArguments = typeArguments;
    }

    public VmType[] getTypeArguments() {
      return typeArguments;
    }

    public VmType.ClassType withTypeArguments(VmType[] typeArguments) {
      return new VmType.ClassType(clazz, typeArguments);
    }

    @Override
    public VmClass getVmClass() {
      return clazz;
    }

    @Override
    public boolean isParametric() {
      return typeArguments.length > 0;
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      if (!(other instanceof ClassType t)) return false;
      if (clazz != t.clazz) return false;
      return typesEquals(typeArguments, t.typeArguments);
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      // special case: Any is a supertype of everything
      if (clazz == BaseModule.getAnyClass()) return true;
      // special case: String is a supertype of all string literals and also Char
      if (clazz == BaseModule.getStringClass()
          && (other instanceof StringLiteralType
              || (other instanceof AliasType at && at.typeAlias == BaseModule.getCharTypeAlias())))
        return true;
      // special case: Int is a supertype of range-constrained aliases
      if (clazz == BaseModule.getIntClass()
          && other instanceof AliasType at
          && (at.typeAlias == BaseModule.getInt8TypeAlias()
              || at.typeAlias == BaseModule.getInt16TypeAlias()
              || at.typeAlias == BaseModule.getInt32TypeAlias()
              || at.typeAlias == BaseModule.getUIntTypeAlias()
              || at.typeAlias == BaseModule.getUInt8TypeAlias()
              || at.typeAlias == BaseModule.getUInt16TypeAlias()
              || at.typeAlias == BaseModule.getUInt32TypeAlias())) return true;
      // special case: FunctionN can be a subtype of a FunctionType
      if (clazz.isFunctionNClass() && other instanceof FunctionType ft) {
        if (typeArguments.length - 1 != ft.parameterTypes.length) return false;
        return functionIsSupertype(
            ft.parameterTypes.length,
            typeArguments,
            typeArguments[ft.parameterTypes.length],
            ft.parameterTypes,
            ft.returnType);
      }
      // special case: FunctionType is a subtype of class Function if return type is contravariant
      if (clazz.isFunctionClass() && other instanceof FunctionType ft) {
        if (typeArguments.length == 0) return true;
        return typeArguments[0].isSupertypeOf(ft.returnType);
      }
      ;

      // standard case: other is a ClassType
      if (!(other instanceof ClassType ct)) return false;
      // if clazz isn't a superclass of other's we're not a supertype
      if (!clazz.isSuperclassOf(ct.clazz)) return false;
      // if our class has no type params, we are a supertype
      if (clazz.getTypeParameterCount() == 0) return true;

      // check generic type args: walk ct.clazz to clazz, substituting type arguments as we go
      // handles arbitrary generics, even though the stdlib only contains List<T>/Set<T> ->
      // Collection<T>

      var goalState =
          typeArguments.length > 0 ? typeArguments : nUnknowns(clazz.getTypeParameterCount());
      var state =
          ct.typeArguments.length > 0
              ? ct.typeArguments
              : nUnknowns(ct.clazz.getTypeParameterCount());
      for (var c = ct.clazz; c != clazz; c = c.getSuperclass()) {
        assert c != null; // we know walking parents reaches clazz before null
        var cSuperclass = c.getSuperclass();
        assert cSuperclass != null; // we know c has a superclass
        var cSupertype = (ClassType) c.getSupertype();
        assert cSupertype != null; // we know c has a supertype

        if (cSupertype.typeArguments.length == 0) {
          // supertype args could be omitted, e.g. class MyList<T> extends List
          state = nUnknowns(cSuperclass.getTypeParameterCount());
          continue;
        } else if (state.length == 0) {
          // subclass may not have type args, e.g. class A extends B<Int>
          state = cSupertype.typeArguments;
          continue;
        }

        // otherwise, clone supertype args and substitute any type vars
        var newState = cSupertype.typeArguments.clone();
        for (var i = 0; i < newState.length; i++) {
          if (!(newState[i] instanceof TypeVariableType tvt)) continue;
          newState[i] = state[tvt.typeParameter.getIndex()];
        }
        state = newState;
      }

      // do variance checking
      assert goalState.length == state.length;
      var params = clazz.getTypeParameters();
      for (var i = 0; i < goalState.length; i++) {
        if (!switch (params.get(i).getVariance()) {
          case INVARIANT -> state[i].equals(goalState[i]);
          case COVARIANT -> state[i].isSubtypeOf(goalState[i]);
          case CONTRAVARIANT -> state[i].isSupertypeOf(goalState[i]);
        }) return false;
      }
      return true;
    }

    @Override
    public PType export() {
      return new Class(clazz.export(), exportTypes(typeArguments));
    }

    @Override
    public String toString() {
      var result = clazz.getDisplayName();
      if (typeArguments.length > 0) {
        result +=
            "<"
                + Arrays.stream(typeArguments)
                    .map(Object::toString)
                    .collect(Collectors.joining(", "))
                + ">";
      }
      return result;
    }

    @Override
    public int hashCode() {
      return 31 * clazz.hashCode() + Arrays.hashCode(typeArguments);
    }
  }

  public static final class NullableType extends VmType {
    private final VmType elementType;

    public NullableType(VmType elementType) {
      this.elementType = elementType;
    }

    public VmType getElementType() {
      return elementType;
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      return other instanceof NullableType t && elementType.equals(t.elementType);
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      if (other instanceof NullableType nt) return elementType.isSupertypeOf(nt.elementType);
      return elementType.isSupertypeOf(other);
    }

    @Override
    public PType export() {
      return new PType.Nullable(elementType.export());
    }

    @Override
    public String toString() {
      return elementType instanceof FunctionType || elementType instanceof UnionType
          ? "(" + elementType + ")?"
          : elementType + "?";
    }

    @Override
    public int hashCode() {
      return elementType.hashCode();
    }
  }

  public static final class ConstrainedType extends VmType {
    private final VmType baseType;
    private final String[] constraints;

    public ConstrainedType(VmType baseType, String[] constraints) {
      this.baseType = baseType;
      this.constraints = constraints;
    }

    public VmType getBaseType() {
      return baseType;
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      // consider constrained types as always different
      return false;
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      // constrained types can never supertypes
      return false;
    }

    @Override
    public PType export() {
      return new Constrained(baseType.export(), Arrays.asList(constraints));
    }

    @Override
    public String toString() {
      return (baseType instanceof FunctionType || baseType instanceof UnionType
              ? "(" + baseType + ")"
              : baseType)
          + "("
          + String.join(", ", constraints)
          + ")";
    }

    @Override
    public int hashCode() {
      return 31 * baseType.hashCode() + Arrays.hashCode(constraints);
    }
  }

  public static final class AliasType extends VmType {
    private final VmTypeAlias typeAlias;
    private final @Nullable VmClass clazz;
    private final VmType[] typeArguments;
    private final VmType aliasedType;

    /** For intrinsified typealiases with an undetermined class (e.g. NonNull) */
    public AliasType(VmTypeAlias typeAlias) {
      this.typeAlias = typeAlias;
      this.aliasedType = typeAlias.getTypeNode().getType();
      this.typeArguments = new VmType[0];
      this.clazz = aliasedType.getVmClass();
    }

    /** For intrinsified typealiases with an inherent class (e.g. Int aliases) */
    public AliasType(VmTypeAlias typeAlias, VmClass clazz) {
      this.typeAlias = typeAlias;
      this.aliasedType = typeAlias.getTypeNode().getType();
      this.typeArguments = new VmType[0];
      this.clazz = clazz;
    }

    /** For arbitrary typealiases */
    public AliasType(VmTypeAlias typeAlias, VmType[] typeArguments, VmType aliasedType) {
      this.typeAlias = typeAlias;
      this.typeArguments = typeArguments;
      this.clazz = aliasedType.getVmClass();
      this.aliasedType = aliasedType;
    }

    @Override
    public VmTypeAlias getVmTypeAlias() {
      return typeAlias;
    }

    public VmType getAliasedType() {
      return aliasedType;
    }

    @Override
    public @Nullable VmClass getVmClass() {
      return clazz;
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      var o = other;
      while (o instanceof AliasType aliasType) {
        o = aliasType.aliasedType;
      }
      return aliasedType.equals(o);
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      if (other instanceof AliasType at) {
        if (at.typeAlias == BaseModule.getInt8TypeAlias()
            && (typeAlias == BaseModule.getInt16TypeAlias()
                || typeAlias == BaseModule.getInt32TypeAlias())) return true;
        if (at.typeAlias == BaseModule.getInt16TypeAlias()
            && typeAlias == BaseModule.getInt32TypeAlias()) return true;
        if (at.typeAlias == BaseModule.getUInt8TypeAlias()
            && (typeAlias == BaseModule.getInt16TypeAlias()
                || typeAlias == BaseModule.getInt32TypeAlias()
                || typeAlias == BaseModule.getUInt16TypeAlias()
                || typeAlias == BaseModule.getUInt32TypeAlias()
                || typeAlias == BaseModule.getUIntTypeAlias())) return true;
        if (at.typeAlias == BaseModule.getUInt16TypeAlias()
            && (typeAlias == BaseModule.getInt32TypeAlias()
                || typeAlias == BaseModule.getUInt32TypeAlias()
                || typeAlias == BaseModule.getUIntTypeAlias())) return true;
        if (at.typeAlias == BaseModule.getUInt32TypeAlias()
            && typeAlias == BaseModule.getUIntTypeAlias()) return true;
      }

      return aliasedType.isSupertypeOf(other);
    }

    @Override
    public PType export() {
      return new Alias(typeAlias.export(), exportTypes(typeArguments), aliasedType.export());
    }

    @Override
    public boolean isParametric() {
      return typeArguments.length > 0;
    }

    @Override
    public String toString() {
      var result = typeAlias.getDisplayName();
      if (typeArguments.length > 0) {
        result +=
            "<"
                + Arrays.stream(typeArguments)
                    .map(Object::toString)
                    .collect(Collectors.joining(", "))
                + ">";
      }
      return result;
    }

    @Override
    public int hashCode() {
      return 31 * typeAlias.hashCode() + Arrays.hashCode(typeArguments);
    }
  }

  public static final class FunctionType extends VmType {
    private final VmType[] parameterTypes;
    private final VmType returnType;

    public FunctionType(VmType[] parameterTypes, VmType returnType) {
      this.parameterTypes = parameterTypes;
      this.returnType = returnType;
    }

    public VmType[] getParameterTypes() {
      return parameterTypes;
    }

    public VmType getReturnType() {
      return returnType;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getFunctionNClass(parameterTypes.length);
    }

    @Override
    public boolean isParametric() {
      return true;
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      if (!(other instanceof FunctionType t)) return false;
      if (!returnType.equals(t.returnType)) return false;
      return typesEquals(parameterTypes, t.parameterTypes);
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      if (other instanceof FunctionType ft) {
        if (parameterTypes.length != ft.parameterTypes.length) return false;
        return functionIsSupertype(
            parameterTypes.length, parameterTypes, returnType, ft.parameterTypes, ft.returnType);
      }
      if (other instanceof ClassType ct
          && (ct.clazz == BaseModule.getFunctionNClass(parameterTypes.length))) {
        // check against specific FunctionN class avoids need to compare param lengths
        return functionIsSupertype(
            parameterTypes.length,
            parameterTypes,
            returnType,
            ct.typeArguments,
            ct.typeArguments[parameterTypes.length]);
      }
      return false;
    }

    @Override
    public PType export() {
      return new PType.Function(exportTypes(parameterTypes), returnType.export());
    }

    @Override
    public String toString() {
      return "("
          + Arrays.stream(parameterTypes).map(Object::toString).collect(Collectors.joining(", "))
          + ") -> "
          + returnType;
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(parameterTypes) + returnType.hashCode();
    }
  }

  public static final class UnionType extends VmType {
    private final int defaultIndex;
    private final VmType[] elementTypes;

    public UnionType(int defaultIndex, VmType[] elementTypes) {
      this.defaultIndex = defaultIndex;
      this.elementTypes = elementTypes;
    }

    public UnionType(int defaultIndex, String[] stringLiterals) {
      this.defaultIndex = defaultIndex;
      elementTypes = new VmType[stringLiterals.length];
      for (var i = 0; i < elementTypes.length; i++) {
        elementTypes[i] = new StringLiteralType(stringLiterals[i]);
      }
    }

    public int getDefaultIndex() {
      return defaultIndex;
    }

    public VmType[] getElementTypes() {
      return elementTypes;
    }

    @Override
    public PType export() {
      return new Union(exportTypes(elementTypes));
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      if (!(other instanceof UnionType t)) return false;
      return typesEquals(elementTypes, t.elementTypes);
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      for (var member : elementTypes) {
        if (member.isSupertypeOf(other)) return true;
      }
      return false;
    }

    public boolean isUnionOfStringLiterals() {
      return allElementsMatch(it -> it instanceof StringLiteralType);
    }

    @Override
    public String toString() {
      return Arrays.stream(elementTypes).map(Object::toString).collect(Collectors.joining(" | "));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(elementTypes);
    }

    boolean allElementsMatch(Function<VmType, Boolean> predicate) {
      for (var member : elementTypes) {
        if (!predicate.apply(member)) return false;
      }
      return true;
    }
  }

  public static final class TypeVariableType extends VmType {
    private final TypeParameter typeParameter;

    public TypeVariableType(TypeParameter typeParameter) {
      this.typeParameter = typeParameter;
    }

    public TypeParameter getTypeParameter() {
      return typeParameter;
    }

    @Override
    protected boolean doIsEquivalentTo(VmType other) {
      return other instanceof TypeVariableType;
    }

    @Override
    protected boolean doIsSupertypeOf(VmType other) {
      return true;
    }

    @Override
    public PType export() {
      return new TypeVariable(typeParameter);
    }

    @Override
    public String toString() {
      return typeParameter.getName();
    }

    @Override
    public int hashCode() {
      return typeParameter.hashCode();
    }
  }

  private static boolean typesEquals(VmType[] a, VmType[] b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (!a[i].equals(b[i])) return false;
    }
    return true;
  }

  private static List<PType> exportTypes(VmType[] types) {
    return Arrays.stream(types).map(VmType::export).toList();
  }

  private static boolean functionIsSupertype(
      int paramCount,
      VmType[] thisParams,
      VmType thisReturn,
      VmType[] otherParams,
      VmType otherReturn) {
    // param types are contravariant
    for (var i = 0; i < paramCount; i++) {
      if (!thisParams[i].isSubtypeOf(otherParams[i])) return false;
    }
    // return type is covariant
    return thisReturn.isSupertypeOf(otherReturn);
  }

  private static VmType[] nUnknowns(int len) {
    return Collections.nCopies(len, VmType.UNKNOWN).toArray(new VmType[0]);
  }
}
