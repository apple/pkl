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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.ImRrbt;
import org.pkl.core.Composite;
import org.pkl.core.PClass;
import org.pkl.core.PClassInfo;
import org.pkl.core.PType;
import org.pkl.core.Reference;
import org.pkl.core.TypeAlias;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.MemberLookupMode;
import org.pkl.core.ast.expression.member.InvokeMethodVirtualNodeGen;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.Nullable;

public final class VmReference extends VmValue {

  private final VmTyped domain;
  private final Object data;
  private final ImRrbt<VmTyped> path;
  // candidate types can only be: PType.Class, PType.Alias (only preservedAliasTypes),
  // PType.StringLiteral, or PType.UNKNOWN
  private final Set<PType> candidateTypes;

  private boolean forced = false;

  private static final PType nullType = new PType.Class(BaseModule.getNullClass().export());
  private static final Set<TypeAlias> intAliasTypes = new HashSet<>();
  private static final Set<TypeAlias> preservedAliasTypes = new HashSet<>();

  static {
    for (var t : BaseModule.getIntTypeAliases()) {
      intAliasTypes.add(t.export());
      preservedAliasTypes.add(t.export());
    }
  }

  private record Access(@Nullable String property, @Nullable Object key) {}

  private static final VmObjectFactory<Access> accessFactory =
      new VmObjectFactory<>(RefModule::getAccessClass);

  static {
    accessFactory
        .addProperty(
            "property",
            access -> access.property == null ? VmNull.withoutDefault() : access.property)
        .addProperty("key", access -> access.key == null ? VmNull.withoutDefault() : access.key);
  }

  public VmReference(VmTyped domain, VmClass clazz, Object data) {
    this(domain, data, RrbTree.empty(), Set.of(new PType.Class(clazz.export())));
  }

  public VmReference(VmTyped domain, Object data, ImRrbt<VmTyped> path, Set<PType> candidateTypes) {
    this.domain = domain;
    this.data = data;
    this.candidateTypes = candidateTypes;
    this.path = path;
  }

  public Set<PType> getCandidateTypes() {
    return candidateTypes;
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

  // simplifies a type by:
  // * erasing constraints
  // * transforming T? into T|Null
  // * dereferencing aliases (except for well-known stdlib alias types)
  // * flattening unions
  // * when moduleClass is supplied, replace PType.MODULE with appropriate PType.Class
  // * drop PType.NOTHING, PType.Function, and PType.TypeVariable
  private static Set<PType> normalizeTypes(PType type, @Nullable PClass moduleClass) {
    var types = new HashSet<PType>();
    normalizeTypes(type, moduleClass, types);
    return types;
  }

  private static void normalizeTypes(PType type, @Nullable PClass moduleClass, Set<PType> result) {
    if (type == PType.UNKNOWN || type instanceof PType.StringLiteral) {
      result.add(type);
    } else if (type instanceof PType.Class clazz) {
      if (clazz.getTypeArguments().isEmpty()) {
        result.add(clazz);
      } else {
        var typeArgs = new ArrayList<PType>(clazz.getTypeArguments().size());
        for (var arg : clazz.getTypeArguments()) {
          var tt = new ArrayList<>(normalizeTypes(arg, moduleClass));
          typeArgs.add(tt.size() == 1 ? tt.get(0) : new PType.Union(tt));
        }
        result.add(new PType.Class(clazz.getPClass(), typeArgs));
      }
    } else if (type instanceof PType.Nullable nullable) {
      normalizeTypes(nullable.getBaseType(), moduleClass, result);
      result.add(nullType);
    } else if (type instanceof PType.Constrained constrained) {
      normalizeTypes(constrained.getBaseType(), moduleClass, result);
    } else if (type instanceof PType.Alias alias) {
      if (preservedAliasTypes.contains(alias.getTypeAlias())) {
        result.add(alias);
      } else {
        normalizeTypes(alias.getAliasedType(), alias.getTypeAlias().getModuleClass(), result);
      }
    } else if (type instanceof PType.Union union) {
      for (var t : union.getElementTypes()) {
        normalizeTypes(t, moduleClass, result);
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
        domain,
        data,
        path.append(accessFactory.create(new Access(property.toString(), null))),
        candidates);
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
    return new VmReference(
        domain, data, path.append(accessFactory.create(new Access(null, key))), candidates);
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
    if (clazz.getPClass().isModuleClass() && property.equals("output")) return;

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
      for (var kt : keyTypes) {
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

  /** Checks type against ref's candidateTypes. Does not check domain. */
  public boolean checkType(PType type, @Nullable PClass moduleClass) {
    // fast path: if this could be unknown, any type is accepted
    if (candidateTypes.contains(PType.UNKNOWN)) {
      return true;
    }

    var checkTypes = normalizeTypes(type, moduleClass);

    // all candidate types must be subtypes of at least one target type
    candidate:
    for (var c : candidateTypes) {
      for (var t : checkTypes) {
        if (isSubtype(c, t)) break candidate;
      }
      return false;
    }
    return true;
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
    if (a == b) return true;

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

    return new Reference(domain.export(), VmValue.export(data), pathList, exportReferentType());
  }

  public PType exportReferentType() {
    if (candidateTypes.size() == 1) return candidateTypes.iterator().next();
    var types = new ArrayList<>(candidateTypes);
    // sort multiple candidate types to ensure stable output
    types.sort(Comparator.comparing(Object::toString));
    return new PType.Union(types);
  }

  public PType exportType() {
    return new PType.Class(
        RefModule.getReferenceClass().export(),
        new PType.Class(domain.getVmClass().export()),
        exportReferentType());
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
        && candidateTypes.equals(that.candidateTypes);
  }

  @Override
  public int hashCode() {
    int result = domain.hashCode();
    result = 31 * result + data.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + candidateTypes.hashCode();
    return result;
  }

  public String toPklString(@Nullable VirtualFrame frame, @Nullable SourceSection sourceSection) {
    if (frame == null) {
      frame = VmUtils.createEmptyMaterializedFrame();
    }
    if (sourceSection == null) {
      sourceSection = VmUtils.unavailableSourceSection();
    }

    return (String)
        InvokeMethodVirtualNodeGen.create(
                sourceSection,
                Identifier.TO_STRING,
                new ExpressionNode[] {},
                MemberLookupMode.EXPLICIT_RECEIVER,
                null,
                null)
            .executeWith(frame, this, getVmClass());
  }
}
