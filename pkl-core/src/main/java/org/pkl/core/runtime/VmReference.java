/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.pkl.core.Composite;
import org.pkl.core.PClass;
import org.pkl.core.PClassInfo;
import org.pkl.core.PType;
import org.pkl.core.Reference;
import org.pkl.core.TypeAlias;
import org.pkl.core.util.paguro.RrbTree;
import org.pkl.core.util.paguro.RrbTree.ImRrbt;

public final class VmReference extends VmValue {

  private final VmTyped domain;
  private final Object data;
  private final ImRrbt<VmTyped> path;
  // candidate types can only be: PType.Class, PType.Alias (only preservedAliasTypes),
  // PType.StringLiteral, or PType.UNKNOWN
  private final PType referentType;

  private boolean forced = false;

  private static VmTyped newAccess(@Nullable String property, @Nullable Object key) {
    return new VmObjectBuilder()
        .addProperty(Identifier.PROPERTY, property == null ? VmNull.withoutDefault() : property)
        .addProperty(Identifier.KEY, key == null ? VmNull.withoutDefault() : key)
        .toTyped(RefModule.getAccessClass());
  }

  @TruffleBoundary
  public VmReference(VmTyped domain, VmClass clazz, Object data) {
    this(
        domain,
        data,
        RrbTree.empty(),
        normalizeTypes(new PType.Class(clazz.export()), clazz.getModule().getVmClass().export()));
  }

  public VmReference(VmTyped domain, Object data, ImRrbt<VmTyped> path, PType referentType) {
    this.domain = domain;
    this.data = data;
    this.referentType = referentType;
    this.path = path;
  }

  public VmTyped getDomain() {
    return domain;
  }

  public Object getData() {
    return data;
  }

  public List<VmTyped> getPath() {
    return path;
  }

  public PType getReferentType() {
    return referentType;
  }

  // simplifies a type by:
  // * erasing constraints
  // * transforming T? into T|Null
  // * dereferencing aliases (except for well-known stdlib alias types)
  // * flattening unions
  // * when moduleClass is supplied, replace PType.MODULE with appropriate PType.Class
  // * drop PType.NOTHING, PType.Function, and PType.TypeVariable
  private static PType normalizeTypes(PType type, PClass moduleClass) {
    var types = new HashSet<PType>();
    normalizeTypes(type, moduleClass, types);
    return minimizeTypes(types);
  }

  private static PType minimizeTypes(Set<PType> types) {
    if (types.size() == 1) return types.iterator().next();
    // optimization: unknown allows all references, erase all candidates to only unknown
    if (types.contains(PType.UNKNOWN)) return PType.UNKNOWN;
    // optimization: All allows all references, erase all candidates to only All
    if (containsClass(types, BaseModule.getAnyClass().export()))
      return new PType.Class(BaseModule.getAnyClass().export());
    var typesList = new ArrayList<>(types);
    typesList.sort(Comparator.comparing(Object::toString));
    return new PType.Union(typesList);
  }

  private static void normalizeTypes(PType type, PClass moduleClass, Set<PType> result) {
    if (type == PType.UNKNOWN || type == PType.NOTHING || type instanceof PType.StringLiteral) {
      result.add(type);
    } else if (type instanceof PType.Class clazz) {
      if (clazz.getTypeArguments().isEmpty()) {
        // if a generic type is used without type arguments, it needs to be normalized so all args
        // are unknown; i.e. with bare List/Map/etc. type annotations (via FinalClassTypeNode).
        var typeParameterCount = clazz.getPClass().getTypeParameters().size();
        result.add(
            typeParameterCount == 0
                ? clazz
                : new PType.Class(
                    clazz.getPClass(), Collections.nCopies(typeParameterCount, PType.UNKNOWN)));
      } else {
        var typeArgs = new ArrayList<PType>(clazz.getTypeArguments().size());
        for (var arg : clazz.getTypeArguments()) {
          typeArgs.add(normalizeTypes(arg, moduleClass));
        }
        result.add(new PType.Class(clazz.getPClass(), typeArgs));
      }
    } else if (type instanceof PType.Nullable nullable) {
      normalizeTypes(nullable.getBaseType(), moduleClass, result);
      result.add(new PType.Class(BaseModule.getNullClass().export()));
    } else if (type instanceof PType.Constrained constrained) {
      normalizeTypes(constrained.getBaseType(), moduleClass, result);
    } else if (type instanceof PType.Alias alias) {
      if (isPreservedTypeAlias(alias.getTypeAlias())) {
        result.add(alias);
      } else {
        normalizeTypes(alias.getAliasedType(), alias.getTypeAlias().getModuleClass(), result);
      }
    } else if (type instanceof PType.Union union) {
      for (var t : union.getElementTypes()) {
        normalizeTypes(t, moduleClass, result);
      }
    } else if (type == PType.MODULE) {
      result.add(new PType.Class(moduleClass));
    }
  }

  private static Iterable<PType> iterateTypes(PType t) {
    if (t instanceof PType.Union union) return union.getElementTypes();
    return Collections.singleton(t);
  }

  public @Nullable VmReference withPropertyAccess(Identifier property) {
    var propString = property.toString();
    return withAccess(
        (t, candidates) -> getCandidatePropertyType(t, propString, candidates),
        () -> newAccess(property.toString(), null));
  }

  public @Nullable VmReference withSubscriptAccess(Object key) {
    return withAccess(
        (t, candidates) -> getCandidateSubscriptType(t, key, candidates),
        () -> newAccess(null, key));
  }

  @TruffleBoundary
  private @Nullable VmReference withAccess(
      BiConsumer<PType, Set<PType>> checkCandidate, Supplier<VmTyped> makeAccess) {
    Set<PType> candidates = new HashSet<>();
    for (var t : iterateTypes(referentType)) {
      checkCandidate.accept(t, candidates);
    }
    if (candidates.isEmpty()) {
      return null; // no valid access found
    }
    return new VmReference(domain, data, path.append(makeAccess.get()), minimizeTypes(candidates));
  }

  @SuppressWarnings("DuplicatedCode")
  private static void getCandidatePropertyType(PType type, String property, Set<PType> result) {
    if (type == PType.UNKNOWN) {
      result.add(type);
      return;
    }
    // restriction: only class types can have their properties referenced
    if (!(type instanceof PType.Class clazz)) return;
    // restriction: cannot reference properties of external classes
    if (clazz.getPClass().isExternal()) return;
    if (clazz.getPClass().getInfo() == PClassInfo.Dynamic) {
      // restriction: cannot reference Dynamic.default
      if (!property.equals("default")) result.add(PType.UNKNOWN);
      return;
    }
    // restriction: cannot reference Listing/Mapping.default
    if (clazz.getPClass().getInfo() == PClassInfo.Listing
        || clazz.getPClass().getInfo() == PClassInfo.Mapping) {
      return;
    }
    // restriction: cannot reference Module.output.
    //   generalized: properties originally defined in external classes; the only extant example.
    // This is implemented specifically because this is the only case where an external class
    //   containing a property can be subclassed.
    // And this can't check prop.getOwner().isExternal() because fully overriding the property with
    //   a new type annotation means the owner isn't Module.
    if (clazz.getPClass().isSubclassOf(BaseModule.getModuleClass().export())
        && property.equals("output")) return;

    var prop = clazz.getPClass().getAllProperties().get(property);
    // restriction: cannot reference external properties
    if (prop == null || prop.isExternal()) {
      return;
    }
    normalizeTypes(prop.getType(), clazz.getPClass().getModuleClass(), result);
  }

  @SuppressWarnings("DuplicatedCode")
  private static void getCandidateSubscriptType(PType type, Object key, Set<PType> result) {
    if (type == PType.UNKNOWN) {
      result.add(type);
      return;
    }
    if (!(type instanceof PType.Class clazz)) {
      return;
    }
    if (clazz.getPClass().getInfo() == PClassInfo.Dynamic) {
      result.add(PType.UNKNOWN);
      return;
    }
    if (clazz.getPClass().getInfo() == PClassInfo.Listing
        || clazz.getPClass().getInfo() == PClassInfo.List) {
      if (key instanceof Long) {
        normalizeTypes(clazz.getTypeArguments().get(0), clazz.getPClass().getModuleClass(), result);
      }
      return;
    }
    if (clazz.getPClass().getInfo() == PClassInfo.Mapping
        || clazz.getPClass().getInfo() == PClassInfo.Map) {
      var typeArgs = clazz.getTypeArguments();
      var keyTypes = normalizeTypes(typeArgs.get(0), clazz.getPClass().getModuleClass());
      for (var kt : iterateTypes(keyTypes)) {
        if (kt == PType.UNKNOWN
            || (kt instanceof PType.Class klazz
                && klazz.getPClass().getInfo() == PClassInfo.forValue(VmValue.export(key)))
            || (kt instanceof PType.StringLiteral stringLiteral
                && stringLiteral.getLiteral().equals(key))) {
          normalizeTypes(typeArgs.get(1), clazz.getPClass().getModuleClass(), result);
          return;
        }
      }
    }
  }

  /**
   * Tells if this reference's referent type is a subtype of {@code type}. Does not check domain.
   */
  public boolean referentTypeIsSubtypeOf(PType type, PClass moduleClass) {
    // fast path: if referent is unknown it can match any type check
    if (referentType == PType.UNKNOWN) {
      return true;
    }

    var checkType = normalizeTypes(type, moduleClass);
    // fast path: short circuit if any referent is accepted
    if (checkType == PType.UNKNOWN || isClass(checkType, BaseModule.getAnyClass().export())) {
      return true;
    }
    // fast path: short circuit if nothing is accepted
    if (checkType == PType.NOTHING) {
      return false;
    }

    return isSubtype(referentType, checkType);
  }

  private static boolean containsClass(Set<PType> types, PClass pClass) {
    for (var t : types) {
      if (isClass(t, pClass)) return true;
    }
    return false;
  }

  private static boolean isClass(PType t, PClass pClass) {
    return t instanceof PType.Class clazz && clazz.getPClass() == pClass;
  }

  private static boolean isSubtype(PType a, PType b) {
    // checks if A is a subtype of B
    // cases (A -> B)
    // * A == B
    // * StringLiteral -> StringLiteral: if literals are the same
    // * StringLiteral -> Class: B is String
    // * Char Alias -> Char Alias, StringLiteral (known single character)
    // * Int Alias -> Class: B is a subtype of Number (Int|Float|Number)
    // * Int Alias -> Alias
    //   * same alias
    //   * Int8 is Int16|Int32
    //   * Int16 is Int32
    //   * UInt8 is Int16|Int32|Uint16|UInt32|UInt
    //   * UInt16 is Int32|UInt32|UInt
    //   * UInt32 is UInt
    // * Class -> Class: if same class or A is a subclass of B
    //   * if type args are present, must have equal number of them
    //   * for each pair of type args, check variance
    //     * invariant: A_i must be identical to B_i
    //     * covariant: A_i must be a subtype of B_i
    //     * contravariant: B_i must be a subtype of A_i
    // * Union -> Union: Each elem of A must be a subtype of at least one elem of B
    // * Non-union -> Union: A must be a subtype of at least one elem of B
    if (a == b) return true;

    if (a instanceof PType.StringLiteral aStr) {
      if (b instanceof PType.StringLiteral bStr) {
        return aStr.getLiteral().equals(bStr.getLiteral());
      } else if (b instanceof PType.Class bClass) {
        return bClass.getPClass() == BaseModule.getStringClass().export();
      }
    } else if (a instanceof PType.Alias aAlias) {
      var aa = aAlias.getTypeAlias();
      if (isIntTypeAlias(aa)) {
        // special casing for stdlib Int typealiases
        if (b instanceof PType.Class bClass) {
          // A is an int alias, B is a Number (sub)class
          return bClass.getPClass().isSubclassOf(BaseModule.getNumberClass().export());
        } else if (b instanceof PType.Alias bAlias) {
          var bb = bAlias.getTypeAlias();
          if (aa == bb) {
            return true;
          }
          if (aa == BaseModule.getInt8TypeAlias().export()) {
            return bb == BaseModule.getInt16TypeAlias().export()
                || bb == BaseModule.getInt32TypeAlias().export();
          } else if (aa == BaseModule.getInt16TypeAlias().export()) {
            return bb == BaseModule.getInt32TypeAlias().export();
          } else if (aa == BaseModule.getUInt8TypeAlias().export()) {
            return bb == BaseModule.getInt16TypeAlias().export()
                || bb == BaseModule.getInt32TypeAlias().export()
                || bb == BaseModule.getUInt16TypeAlias().export()
                || bb == BaseModule.getUInt32TypeAlias().export()
                || bb == BaseModule.getUIntTypeAlias().export();
          } else if (aa == BaseModule.getUInt16TypeAlias().export()) {
            return bb == BaseModule.getInt32TypeAlias().export()
                || bb == BaseModule.getUInt32TypeAlias().export()
                || bb == BaseModule.getUIntTypeAlias().export();
          } else if (aa == BaseModule.getUInt32TypeAlias().export()) {
            return bb == BaseModule.getUIntTypeAlias().export();
          }
        }
      }
    } else if (a instanceof PType.Class aClass && b instanceof PType.Class bClass) {
      if (!aClass.getPClass().isSubclassOf(bClass.getPClass())) {
        return false;
      }
      var aArgs = aClass.getTypeArguments();
      var bArgs = bClass.getTypeArguments();
      var bParams = bClass.getPClass().getTypeParameters();
      if (aArgs.size() != bArgs.size()) {
        return false;
      }
      // check variance of type args pairwise
      for (var i = 0; i < aArgs.size(); i++) {
        if (!switch (bParams.get(i).getVariance()) {
          case INVARIANT -> aArgs.get(i) == bArgs.get(i);
          case COVARIANT -> isSubtype(aArgs.get(i), bArgs.get(i));
          case CONTRAVARIANT -> isSubtype(bArgs.get(i), aArgs.get(i));
        }) {
          return false;
        }
      }
      return true;
    } else if (b instanceof PType.Union bUnion) {
      if (a instanceof PType.Union aUnion) {
        a:
        for (var aElem : aUnion.getElementTypes()) {
          for (var bElem : bUnion.getElementTypes()) {
            if (isSubtype(aElem, bElem)) continue a;
          }
          return false;
        }
        return true;
      } else {
        for (var bElem : bUnion.getElementTypes()) {
          if (isSubtype(a, bElem)) return true;
        }
      }
    }
    return false;
  }

  private static boolean isIntTypeAlias(TypeAlias t) {
    return t == BaseModule.getInt8TypeAlias().export()
        || t == BaseModule.getInt16TypeAlias().export()
        || t == BaseModule.getInt32TypeAlias().export()
        || t == BaseModule.getUInt8TypeAlias().export()
        || t == BaseModule.getUInt16TypeAlias().export()
        || t == BaseModule.getUInt32TypeAlias().export()
        || t == BaseModule.getUIntTypeAlias().export();
  }

  private static boolean isPreservedTypeAlias(TypeAlias t) {
    return isIntTypeAlias(t);
  }

  @Override
  public VmClass getVmClass() {
    return RefModule.getReferenceClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    domain.force(allowUndefinedValues);
    VmValue.force(data, allowUndefinedValues);
    for (var elem : path) {
      elem.force(allowUndefinedValues);
    }
  }

  @Override
  public Reference export() {
    var pathList = new ArrayList<Composite>(path.size());
    for (var elem : path) {
      pathList.add(elem.export());
    }

    return new Reference(domain.export(), VmValue.export(data), pathList, getReferentType());
  }

  public PType exportType() {
    return new PType.Class(
        RefModule.getReferenceClass().export(),
        new PType.Class(domain.getVmClass().export()),
        getReferentType());
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitReference(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertReference(this, path);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) return true;
    if (!(o instanceof VmReference that)) {
      return false;
    }

    return domain.equals(that.domain)
        && data.equals(that.data)
        && path.equals(that.path)
        && referentType.equals(that.referentType);
  }

  @Override
  public int hashCode() {
    int result = domain.hashCode();
    result = 31 * result + data.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + referentType.hashCode();
    return result;
  }
}
