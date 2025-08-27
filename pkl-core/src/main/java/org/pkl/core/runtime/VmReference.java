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
package org.pkl.core.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.ImRrbt;
import org.pkl.core.Composite;
import org.pkl.core.Modifier;
import org.pkl.core.PClass;
import org.pkl.core.PClassInfo;
import org.pkl.core.PNull;
import org.pkl.core.PObject;
import org.pkl.core.PType;
import org.pkl.core.TypeAlias;
import org.pkl.core.util.Nullable;

public final class VmReference extends VmValue {

  // candidate types can only be: PType.Class, PType.Alias (only preservedAliasTypes),
  // PType.StringLiteral, or PType.UNKNOWN
  private final Set<PType> candidateTypes;
  // TODO figure out what to do with constraints
  //    maybe: start w/ errors and open up to analyzable constraints later
  private final VmValue rootValue;
  private final ImRrbt<Access> path;
  private boolean forced = false;

  private static final PType nullType = new PType.Class(BaseModule.getNullClass().export());
  private static final Set<TypeAlias> intAliasTypes = getIntAliasTypes();
  private static final Set<TypeAlias> preservedAliasTypes = intAliasTypes;

  private static Set<TypeAlias> getIntAliasTypes() {
    var types = new HashSet<TypeAlias>();
    for (var t : BaseModule.getIntTypeAliases()) {
      types.add(t.export());
    }
    return types;
  }

  public VmReference(VmValue rootValue) {
    this(Set.of(new PType.Class(rootValue.getVmClass().export())), rootValue, RrbTree.empty());
  }

  public VmReference(Set<PType> candidateTypes, VmValue rootValue, ImRrbt<Access> path) {
    this.candidateTypes = candidateTypes;
    this.rootValue = rootValue;
    this.path = path;
  }

  public Set<PType> getCandidateTypes() {
    return candidateTypes;
  }

  public VmValue getRootValue() {
    return rootValue;
  }

  public List<Access> getPath() {
    return path;
  }

  // simplifies a type by:
  // * erasing constraints
  // * transforming T? into T|Null
  // * dereferencing aliases (except for well-known stdlib alias types)
  // * flattening unions
  // * when moduleClass is supplied, replace PType.MODULE with appropriate PType.Class
  // * drop PType.NOTHING, PType.Function, and PType.TypeVariable
  private static Set<PType> simplifyType(PType type, @Nullable PClass moduleClass) {
    var types = new HashSet<PType>();
    simplifyType(type, moduleClass, types);
    return types;
  }

  private static void simplifyType(PType type, @Nullable PClass moduleClass, Set<PType> result) {
    if (type == PType.UNKNOWN || type instanceof PType.StringLiteral) {
      result.add(type);
    } else if (type instanceof PType.Class klass) {
      if (klass.getTypeArguments().isEmpty()) {
        result.add(klass);
      } else {
        var typeArgs = new ArrayList<PType>(klass.getTypeArguments().size());
        for (var arg : klass.getTypeArguments()) {
          var tt = new ArrayList<>(simplifyType(arg, moduleClass));
          typeArgs.add(tt.size() == 1 ? tt.get(0) : new PType.Union(tt));
        }
        result.add(new PType.Class(klass.getPClass(), typeArgs));
      }
    } else if (type instanceof PType.Nullable nullable) {
      simplifyType(nullable.getBaseType(), moduleClass, result);
      result.add(nullType);
    } else if (type instanceof PType.Constrained constrained) {
      simplifyType(constrained.getBaseType(), moduleClass, result);
    } else if (type instanceof PType.Alias alias) {
      if (preservedAliasTypes.contains(alias.getTypeAlias())) {
        result.add(alias);
      } else {
        simplifyType(alias.getAliasedType(), alias.getTypeAlias().getModuleClass(), result);
      }
    } else if (type instanceof PType.Union union) {
      for (var t : union.getElementTypes()) {
        simplifyType(t, moduleClass, result);
      }
    } else if (type == PType.MODULE && moduleClass != null) {
      result.add(new PType.Class(moduleClass));
    }
  }

  public @Nullable VmReference withPropertyAccess(Identifier property) {
    Set<PType> candidates = new HashSet<>();
    for (var t : candidateTypes) {
      getCandidatePropertyType(t, property.toString(), candidates);
    }
    if (candidates.isEmpty()) {
      return null; // no valid property found
    } else if (candidates.contains(PType.UNKNOWN)) {
      // optimization: unknown allows all references, erase all candidates to only unknown
      candidates = Set.of(PType.UNKNOWN);
    }
    return new VmReference(
        candidates, rootValue, path.append(Access.property(property.toString())));
  }

  public @Nullable VmReference withSubscriptAccess(Object key) {
    Set<PType> candidates = new HashSet<>();
    for (var t : candidateTypes) {
      getCandidateSubscriptType(t, key, candidates);
    }
    if (candidates.isEmpty()) {
      return null; // no valid subscript found
    } else if (candidates.contains(PType.UNKNOWN)) {
      // optimization: unknown allows all references, erase all candidates to only unknown
      candidates = Set.of(PType.UNKNOWN);
    }
    return new VmReference(candidates, rootValue, path.append(Access.subscript(key)));
  }

  @SuppressWarnings("DuplicatedCode")
  private static void getCandidatePropertyType(PType type, String property, Set<PType> result) {
    if (type == PType.UNKNOWN) {
      result.add(type);
      return;
    }
    if (!(type instanceof PType.Class klass)) {
      return;
    }
    if (klass.getPClass().getInfo() == PClassInfo.Dynamic) {
      result.add(PType.UNKNOWN);
      return;
    }
    if (klass.getPClass().getInfo() == PClassInfo.Listing
        || klass.getPClass().getInfo() == PClassInfo.List
        || klass.getPClass().getInfo() == PClassInfo.Mapping
        || klass.getPClass().getInfo() == PClassInfo.Map) {
      return;
    }
    // Typed
    var prop = klass.getPClass().getAllProperties().get(property);
    if (prop == null || prop.isExternal()) {
      return;
    }
    simplifyType(prop.getType(), klass.getPClass().getModuleClass(), result);
  }

  @SuppressWarnings("DuplicatedCode")
  private static void getCandidateSubscriptType(PType type, Object key, Set<PType> result) {
    if (type == PType.UNKNOWN) {
      result.add(type);
      return;
    }
    if (!(type instanceof PType.Class klass)) {
      return;
    }
    if (klass.getPClass().getInfo() == PClassInfo.Dynamic) {
      result.add(PType.UNKNOWN);
      return;
    }
    if (klass.getPClass().getInfo() == PClassInfo.Listing
        || klass.getPClass().getInfo() == PClassInfo.List) {
      if (key instanceof Long) {
        simplifyType(klass.getTypeArguments().get(0), klass.getPClass().getModuleClass(), result);
      }
      return;
    }
    if (klass.getPClass().getInfo() == PClassInfo.Mapping
        || klass.getPClass().getInfo() == PClassInfo.Map) {
      var typeArgs = klass.getTypeArguments();
      var keyTypes = simplifyType(typeArgs.get(0), klass.getPClass().getModuleClass());
      for (var kt : keyTypes) {
        if (kt == PType.UNKNOWN
            || (kt instanceof PType.Class klazz
                && klazz.getPClass().getInfo() == PClassInfo.forValue(key))
            || (kt instanceof PType.StringLiteral stringLiteral
                && stringLiteral.getLiteral().equals(key))) {
          simplifyType(typeArgs.get(1), klass.getPClass().getModuleClass(), result);
          return;
        }
      }
    }
  }

  public boolean checkType(PType type, @Nullable PClass moduleClass) {
    // fast path: if this could be unknown, any type is accepted
    if (candidateTypes.contains(PType.UNKNOWN)) {
      return true;
    }

    // check if any candidate type is a subtype of and check type
    for (var t : simplifyType(type, moduleClass)) {
      for (var c : candidateTypes) {
        if (!isSubtype(c, t)) return false;
      }
    }
    return true;
  }

  private static boolean isSubtype(PType a, PType b) {
    // checks if A is a subtype of B
    // cases (A -> B)
    // * StringLiteral -> StringLiteral: if literals are the same
    // * StringLiteral -> Class: B is String
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

    if (a instanceof PType.StringLiteral aStr) {
      if (b instanceof PType.StringLiteral bStr) {
        return aStr.getLiteral().equals(bStr.getLiteral());
      } else if (b instanceof PType.Class bClass) {
        return bClass.getPClass() == BaseModule.getStringClass().export();
      }
    } else if (a instanceof PType.Alias aAlias) {
      var aa = aAlias.getTypeAlias();
      if (intAliasTypes.contains(aa)) {
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
    }
    return false;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getReferenceClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    rootValue.force(allowUndefinedValues);
    for (var elem : path) {
      VmValue.force(elem, allowUndefinedValues);
    }
  }

  @Override
  public Composite export() {
    var pathList = new ArrayList<>(path.size());
    for (Access elem : path) {
      pathList.add(elem.export());
    }

    return new PObject(
        getVmClass().getPClassInfo(),
        Map.of(
            "candidateTypes", candidateTypes,
            "rootValue", rootValue.export(),
            "path", pathList));
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
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmReference other)) return false;

    return Objects.equals(candidateTypes, other.getCandidateTypes())
        && rootValue.equals(other.getRootValue())
        && path.equals(other.getPath());
  }

  public static class Access extends VmValue {
    private final @Nullable String property;
    private final @Nullable Object key;

    public static Access property(String property) {
      return new Access(property, null);
    }

    public static Access subscript(Object key) {
      return new Access(null, key);
    }

    private Access(@Nullable String property, @Nullable Object key) {
      this.property = property;
      this.key = key;
    }

    public String getProperty() {
      assert property != null;
      return property;
    }

    public Object getKey() {
      assert key != null;
      return key;
    }

    public boolean isProperty() {
      return property != null;
    }

    public boolean isSubscript() {
      return key != null;
    }

    @Override
    public VmClass getVmClass() {
      return BaseModule.getReferenceAccessClass();
    }

    @Override
    public void force(boolean allowUndefinedValues) {
      if (key != null) {
        VmValue.force(key, allowUndefinedValues);
      }
    }

    @Override
    public Object export() {
      return new PObject(
          getVmClass().getPClassInfo(),
          Map.of(
              "property",
              property == null ? PNull.getInstance() : property,
              "key",
              key == null ? PNull.getInstance() : VmValue.export(key)));
    }

    @Override
    public void accept(VmValueVisitor visitor) {
      visitor.visitReferenceAccess(this);
    }

    @Override
    public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
      return converter.convertReferenceAccess(this, path);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Access other)) return false;
      return Objects.equals(property, other.getProperty()) && Objects.equals(key, other.getKey());
    }
  }
}
