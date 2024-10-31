/*
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.SourceSection;
import java.util.*;
import java.util.function.*;
import javax.annotation.concurrent.GuardedBy;
import org.graalvm.collections.*;
import org.pkl.core.Member.SourceLocation;
import org.pkl.core.PClass;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;
import org.pkl.core.TypeParameter;
import org.pkl.core.ast.*;
import org.pkl.core.ast.member.*;
import org.pkl.core.ast.type.TypeNode;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

// Most stdlib modules and their members are initialized in static initializers
// and reused across Truffle contexts.
// As a consequence, VmObject/VmClass instances of stdlib modules must be thread-safe.
// The currently implemented (and likely insufficient) solution is to
// * deeply force standard library modules at initialization time.
// * ensure that any further mutation (e.g., lazy initialization in VmClass) is thread-safe.
public final class VmClass extends VmValue {
  private final SourceSection sourceSection;
  private final SourceSection headerSection;
  private final @Nullable SourceSection docComment;
  private final List<VmTyped> annotations;
  private final int modifiers;
  private final PClassInfo<?> classInfo;
  private final List<TypeParameter> typeParameters;
  private final VmTyped prototype;

  private final EconomicMap<Identifier, ClassProperty> declaredProperties = EconomicMaps.create();
  private final EconomicMap<Identifier, ClassMethod> declaredMethods = EconomicMaps.create();

  // initialized to non-null value by `initSupertype()` for all classes but `pkl.base#Any`
  @CompilationFinal private @Nullable TypeNode supertypeNode;
  @CompilationFinal private @Nullable VmClass superclass;

  @LateInit
  @GuardedBy("allPropertiesLock")
  private UnmodifiableEconomicMap<Identifier, ClassProperty> __allProperties;

  private final Object allPropertiesLock = new Object();

  @LateInit
  @GuardedBy("allMethodsLock")
  private UnmodifiableEconomicMap<Identifier, ClassMethod> __allMethods;

  private final Object allMethodsLock = new Object();

  // Element type is `Object` rather than `Identifier` to enable `contains(Object)` tests
  // (see signature of `UnmodifiableEconomicSet.contains()`).
  @LateInit
  @GuardedBy("allRegularPropertyNamesLock")
  private UnmodifiableEconomicSet<Object> __allRegularPropertyNames;

  private final Object allRegularPropertyNamesLock = new Object();

  // Element type is `Object` rather than `Identifier` to enable `contains(Object)` tests
  // (see signature of `UnmodifiableEconomicSet.contains()`).
  @LateInit
  @GuardedBy("allHiddenPropertyNamesLock")
  private UnmodifiableEconomicSet<Object> __allHiddenPropertyNames;

  private final Object allHiddenPropertyNamesLock = new Object();

  // Helps to overcome recursive initialization issues
  // between classes and annotations in pkl.base.
  @CompilationFinal private volatile boolean isInitialized;

  // PClass must be cached for correctness (identity is equality)
  @LateInit
  @GuardedBy("pClassLock")
  private PClass __pClass;

  private final Object pClassLock = new Object();

  @LateInit
  @GuardedBy("mirrorLock")
  private VmTyped __mirror;

  private final Object mirrorLock = new Object();

  @LateInit
  @GuardedBy("typedToDynamicMembersLock")
  private EconomicMap<Object, ObjectMember> __typedToDynamicMembers;

  private final Object typedToDynamicMembersLock = new Object();

  @LateInit
  @GuardedBy("dynamicToTypedMembersLock")
  private EconomicMap<Object, ObjectMember> __dynamicToTypedMembers;

  private final Object dynamicToTypedMembersLock = new Object();

  @LateInit
  @GuardedBy("mapToTypedMembersLock")
  private EconomicMap<Object, ObjectMember> __mapToTypedMembers;

  private final Object mapToTypedMembersLock = new Object();

  public VmClass(
      SourceSection sourceSection,
      SourceSection headerSection,
      @Nullable SourceSection docComment,
      List<VmTyped> annotations,
      int modifiers,
      PClassInfo<?> classInfo,
      List<TypeParameter> typeParameters,
      VmTyped prototype) {

    this.sourceSection = sourceSection;
    this.headerSection = headerSection;
    this.docComment = docComment;
    this.annotations = annotations;
    this.modifiers = modifiers;
    this.classInfo = classInfo;
    this.typeParameters = typeParameters;

    this.prototype = prototype;
    prototype.lateInitVmClass(this);
  }

  public void initSupertype(TypeNode supertypeNode, VmClass superclass) {
    assert this.supertypeNode == null;
    assert this.superclass == null;

    this.supertypeNode = supertypeNode;
    this.superclass = superclass;
    prototype.lateInitParent(superclass.getPrototype());
  }

  @TruffleBoundary
  public void addProperty(ClassProperty property) {
    prototype.addProperty(property.getInitializer());
    EconomicMaps.put(declaredProperties, property.getName(), property);

    if (!property.isLocal()) {
      __allProperties = null;
      __allHiddenPropertyNames = null;
    }
  }

  @TruffleBoundary
  public void addProperties(Iterable<ClassProperty> properties) {
    for (var property : properties) {
      addProperty(property);
    }
  }

  @TruffleBoundary
  public void addMethod(ClassMethod method) {
    EconomicMaps.put(declaredMethods, method.getName(), method);

    if (!method.isLocal()) {
      __allMethods = null;
    }
  }

  @TruffleBoundary
  public void addMethods(Iterable<ClassMethod> methods) {
    for (var method : methods) {
      addMethod(method);
    }
  }

  // Note: Superclasses may not have finished their initialization when this method is called.
  public void notifyInitialized() {
    isInitialized = true;
  }

  public int getTypeParameterCount() {
    return typeParameters.size();
  }

  /**
   * Returns the property with the given name declared in this class, or {@code null} if no such
   * property was found. Does return local properties.
   */
  public @Nullable ClassProperty getDeclaredProperty(Identifier name) {
    return EconomicMaps.get(declaredProperties, name);
  }

  /** Returns all properties declared in this class. Does include local properties. */
  public Iterable<ClassProperty> getDeclaredProperties() {
    return EconomicMaps.getValues(declaredProperties);
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getClassClass();
  }

  public SourceSection getSourceSection() {
    return sourceSection;
  }

  public SourceSection getHeaderSection() {
    return headerSection;
  }

  public @Nullable SourceSection getDocComment() {
    return docComment;
  }

  public List<VmTyped> getAnnotations() {
    return annotations;
  }

  public String getModuleName() {
    return classInfo.getModuleName();
  }

  /**
   * Returns the module that this class is declared in. For a module class, returns the
   * corresponding module.
   */
  public VmTyped getModule() {
    //noinspection ConstantConditions
    return classInfo.isModuleClass() ? prototype : (VmTyped) prototype.getEnclosingOwner();
  }

  public VmTyped getModuleMirror() {
    return getModule().getModuleInfo().getMirror(getModule());
  }

  public String getSimpleName() {
    return classInfo.getSimpleName();
  }

  /**
   * Returns the qualified name of this class, `<moduleName>#<simpleName>`. Note that a qualified
   * class name isn't guaranteed to be unique, especially if the module name is not declared but
   * inferred.
   */
  public String getQualifiedName() {
    return classInfo.getQualifiedName();
  }

  public String getDisplayName() {
    return classInfo.getDisplayName();
  }

  public PClassInfo<?> getPClassInfo() {
    return classInfo;
  }

  @TruffleBoundary
  public boolean isHiddenProperty(Object key) {
    return getAllHiddenPropertyNames().contains(key);
  }

  /**
   * Returns the property with the given name declared in this class or a superclass, or {@code
   * null} if no such property was found. Does not return local properties.
   */
  public @Nullable ClassProperty getProperty(Identifier name) {
    return EconomicMaps.get(getAllProperties(), name);
  }

  /** Shorthand for {@code getProperty(name) != null}. */
  public boolean hasProperty(Identifier name) {
    return !isInitialized || EconomicMaps.containsKey(getAllProperties(), name);
  }

  /**
   * Returns the names of all non-hidden non-local non-external properties defined in this class and
   * its superclasses.
   */
  public UnmodifiableEconomicSet<Object> getAllRegularPropertyNames() {
    synchronized (allRegularPropertyNamesLock) {
      if (__allRegularPropertyNames == null) {
        __allRegularPropertyNames = collectAllRegularPropertyNames();
      }
      return __allRegularPropertyNames;
    }
  }

  /** Returns the names of all hidden properties defined in this class and its superclasses. */
  public UnmodifiableEconomicSet<Object> getAllHiddenPropertyNames() {
    synchronized (allHiddenPropertyNamesLock) {
      if (__allHiddenPropertyNames == null) {
        __allHiddenPropertyNames = collectAllHiddenPropertyNames();
      }
      return __allHiddenPropertyNames;
    }
  }

  /** Includes local methods. */
  public boolean hasDeclaredMethod(Identifier name) {
    return EconomicMaps.containsKey(declaredMethods, name);
  }

  /** Does return local methods. */
  public @Nullable ClassMethod getDeclaredMethod(Identifier name) {
    return EconomicMaps.get(declaredMethods, name);
  }

  /** Includes local methods. */
  public Iterable<ClassMethod> getDeclaredMethods() {
    return EconomicMaps.getValues(declaredMethods);
  }

  /** Does not return local methods. */
  public @Nullable ClassMethod getMethod(Identifier name) {
    return EconomicMaps.get(getAllMethods(), name);
  }

  /** Does not include local methods. */
  public Iterable<ClassMethod> getMethods() {
    return EconomicMaps.getValues(getAllMethods());
  }

  public @Nullable VmClass getSuperclass() {
    return superclass;
  }

  @Override
  public VmTyped getPrototype() {
    return prototype;
  }

  @Idempotent
  public boolean isAbstract() {
    return VmModifier.isAbstract(modifiers);
  }

  @Idempotent
  public boolean isExternal() {
    return VmModifier.isExternal(modifiers);
  }

  @Idempotent
  public boolean isOpen() {
    return VmModifier.isOpen(modifiers);
  }

  @Idempotent
  public boolean isClosed() {
    return VmModifier.isClosed(modifiers);
  }

  @Idempotent
  public boolean isInstantiable() {
    return VmModifier.isInstantiable(modifiers);
  }

  @Idempotent
  public boolean isNullClass() {
    return isClass(BaseModule.getNullClass(), "pkl.base#Null");
  }

  @Idempotent
  public boolean isCollectionClass() {
    return isClass(BaseModule.getCollectionClass(), "pkl.base#Collection");
  }

  @Idempotent
  public boolean isListClass() {
    return isClass(BaseModule.getListClass(), "pkl.base#List");
  }

  @Idempotent
  public boolean isSetClass() {
    return isClass(BaseModule.getSetClass(), "pkl.base#Set");
  }

  @Idempotent
  public boolean isMapClass() {
    return isClass(BaseModule.getMapClass(), "pkl.base#Map");
  }

  @Idempotent
  public boolean isListingClass() {
    return isClass(BaseModule.getListingClass(), "pkl.base#Listing");
  }

  @Idempotent
  public boolean isMappingClass() {
    return isClass(BaseModule.getMappingClass(), "pkl.base#Mapping");
  }

  @Idempotent
  public boolean isDynamicClass() {
    return isClass(BaseModule.getDynamicClass(), "pkl.base#Dynamic");
  }

  @Idempotent
  public boolean isPairClass() {
    return isClass(BaseModule.getPairClass(), "pkl.base#Pair");
  }

  @Idempotent
  public boolean isFunctionClass() {
    return isClass(BaseModule.getFunctionClass(), "pkl.base#Function");
  }

  @Idempotent
  public boolean isFunctionNClass() {
    return superclass != null
        && superclass.isClass(BaseModule.getFunctionClass(), "pkl.base#Function");
  }

  @Idempotent
  public boolean isModuleClass() {
    return isClass(BaseModule.getModuleClass(), "pkl.base#Module");
  }

  @Idempotent
  public boolean isClassClass() {
    return isClass(BaseModule.getClassClass(), "pkl.base#Class");
  }

  @Idempotent
  public boolean isVarArgsClass() {
    return isClass(BaseModule.getVarArgsClass(), "pkl.base#VarArgs");
  }

  private boolean isClass(@Nullable VmClass clazz, String qualifiedClassName) {
    // may be null during evaluation of base module
    return clazz != null ? this == clazz : getQualifiedName().equals(qualifiedClassName);
  }

  public boolean isSuperclassOf(VmClass other) {
    if (isClosed()) return this == other;

    for (var clazz = other; clazz != null; clazz = clazz.getSuperclass()) {
      if (clazz == this) return true;
    }
    return false;
  }

  public boolean isSubclassOf(VmClass other) {
    return other.isSuperclassOf(this);
  }

  public void visitMethodDefsTopDown(Consumer<ClassMethod> visitor) {
    if (superclass != null) {
      superclass.visitMethodDefsTopDown(visitor);
    }
    EconomicMaps.getValues(declaredMethods).forEach(visitor);
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  public VmTyped getMirror() {
    synchronized (mirrorLock) {
      if (__mirror == null) {
        __mirror = MirrorFactories.classFactory.create(this);
      }
      return __mirror;
    }
  }

  @TruffleBoundary
  public EconomicMap<Object, ObjectMember> getTypedToDynamicMembers() {
    synchronized (typedToDynamicMembersLock) {
      if (__typedToDynamicMembers == null) {
        __typedToDynamicMembers =
            createDelegatingMembers(
                (member) ->
                    new UntypedObjectMemberNode(
                        null, new FrameDescriptor(), member, new DelegateToExtraStorageObjNode()));
      }
      return __typedToDynamicMembers;
    }
  }

  @TruffleBoundary
  public EconomicMap<Object, ObjectMember> getDynamicToTypedMembers() {
    synchronized (dynamicToTypedMembersLock) {
      if (__dynamicToTypedMembers == null) {
        __dynamicToTypedMembers =
            createDelegatingMembers(
                (member) ->
                    TypeCheckedPropertyNodeGen.create(
                        null,
                        new FrameDescriptor(),
                        member,
                        new DelegateToExtraStorageObjOrParentNode()));
      }
      return __dynamicToTypedMembers;
    }
  }

  @TruffleBoundary
  public EconomicMap<Object, ObjectMember> getMapToTypedMembers() {
    synchronized (mapToTypedMembersLock) {
      if (__mapToTypedMembers == null) {
        __mapToTypedMembers =
            createDelegatingMembers(
                (member) ->
                    TypeCheckedPropertyNodeGen.create(
                        null,
                        new FrameDescriptor(),
                        member,
                        new DelegateToExtraStorageMapOrParentNode()));
      }
      return __mapToTypedMembers;
    }
  }

  private EconomicMap<Object, ObjectMember> createDelegatingMembers(
      Function<ObjectMember, MemberNode> memberNodeFactory) {
    var result = EconomicMaps.<Object, ObjectMember>create();
    for (var cursor = getAllProperties().getEntries(); cursor.advance(); ) {
      var property = cursor.getValue();
      // Typed->Dynamic conversion: Dynamic objects cannot currently have hidden members.
      // Dynamic/Map->Typed conversion: Overall it seems more useful for the typed object
      // to inherit its prototype's value for the hidden property (e.g., Module.output).
      if (property.isHidden()) continue;

      var name = cursor.getKey();
      var member =
          new ObjectMember(
              VmUtils.unavailableSourceSection(),
              VmUtils.unavailableSourceSection(),
              VmModifier.NONE,
              name,
              name.toString());
      member.initMemberNode(memberNodeFactory.apply(member));
      result.put(name, member);
    }
    return result;
  }

  public VmSet getModifierMirrors() {
    return VmModifier.getMirrors(modifiers, true);
  }

  public VmList getTypeParameterMirrors() {
    var builder = VmList.EMPTY.builder();
    for (var typeParameter : typeParameters) {
      builder.add(MirrorFactories.typeParameterFactory.create(typeParameter));
    }
    return builder.build();
  }

  public VmValue getSuperclassMirror() {
    return superclass == null ? VmNull.withoutDefault() : superclass.getMirror();
  }

  public VmValue getSupertypeMirror() {
    return supertypeNode == null ? VmNull.withoutDefault() : supertypeNode.getMirror();
  }

  public VmMap getPropertyMirrors() {
    var builder = VmMap.builder();
    for (var property : declaredProperties.getValues()) {
      if (property.isLocal()) continue;
      builder.add(property.getName().toString(), property.getMirror());
    }
    return builder.build();
  }

  public VmMap getMethodMirrors() {
    var builder = VmMap.builder();
    for (var method : declaredMethods.getValues()) {
      if (method.isLocal()) continue;
      builder.add(method.getName().toString(), method.getMirror());
    }
    return builder.build();
  }

  @Override
  @TruffleBoundary
  public PClass export() {
    synchronized (pClassLock) {
      if (__pClass == null) {
        var exportedAnnotations = new ArrayList<PObject>();
        var properties =
            CollectionUtils.<String, PClass.Property>newLinkedHashMap(
                EconomicMaps.size(declaredProperties));
        var methods =
            CollectionUtils.<String, PClass.Method>newLinkedHashMap(
                EconomicMaps.size(declaredMethods));

        // set pClass before exporting class members to prevent
        // infinite recursion in case of cyclic references
        __pClass =
            new PClass(
                VmUtils.exportDocComment(docComment),
                new SourceLocation(headerSection.getStartLine(), sourceSection.getEndLine()),
                VmModifier.export(modifiers, true),
                exportedAnnotations,
                classInfo,
                typeParameters,
                properties,
                methods);

        for (var parameter : typeParameters) {
          parameter.initOwner(__pClass);
        }

        if (supertypeNode != null) {
          assert superclass != null;
          __pClass.initSupertype(TypeNode.export(supertypeNode), superclass.export());
        }

        VmUtils.exportAnnotations(annotations, exportedAnnotations);

        for (var property : EconomicMaps.getValues(declaredProperties)) {
          if (isClassPropertyDefinition(property)) {
            properties.put(property.getName().toString(), property.export(__pClass));
          }
        }

        for (var method : EconomicMaps.getValues(declaredMethods)) {
          if (method.isLocal()) continue;
          methods.put(method.getName().toString(), method.export(__pClass));
        }
      }

      return __pClass;
    }
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitClass(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertClass(this, path);
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  private UnmodifiableEconomicMap<Identifier, ClassProperty> getAllProperties() {
    synchronized (allPropertiesLock) {
      if (__allProperties == null) {
        // can't do this in ClassNode because it requires a fully initialized inheritance hierarchy
        // (which may not yet exist when ClassNode runs due to circular class dependencies)
        __allProperties = collectAllProperties();
      }
      return __allProperties;
    }
  }

  private UnmodifiableEconomicMap<Identifier, ClassMethod> getAllMethods() {
    synchronized (allMethodsLock) {
      if (__allMethods == null) {
        // can't do this in ClassNode because it requires a fully initialized inheritance hierarchy
        // (which may not yet exist when ClassNode runs due to circular class dependencies)
        __allMethods = collectAllMethods();
      }
      return __allMethods;
    }
  }

  /**
   * Tells if the given property defines a member of this class. Requires a fully initialized
   * inheritance hierarchy.
   */
  private boolean isClassPropertyDefinition(ClassProperty declaredProperty) {
    if (declaredProperty.isLocal() || declaredProperty.isClass() || declaredProperty.isTypeAlias())
      return false;
    return getProperty(declaredProperty.getName()) == declaredProperty;
  }

  @TruffleBoundary
  private UnmodifiableEconomicMap<Identifier, ClassProperty> collectAllProperties() {
    if (EconomicMaps.isEmpty(declaredProperties)) {
      return superclass == null ? EconomicMaps.create() : superclass.getAllProperties();
    }

    var size =
        EconomicMaps.size(declaredProperties)
            + (superclass == null ? 0 : EconomicMaps.size(superclass.getAllProperties()));
    var result = EconomicMaps.<Identifier, ClassProperty>create(size);

    if (superclass != null) {
      EconomicMaps.putAll(result, superclass.getAllProperties());
    }

    for (var property : EconomicMaps.getValues(declaredProperties)) {
      if (property.isLocal()) continue;

      // A property is considered a class property definition
      // if it has a type annotation or has no superdefinition (ad-hoc case).
      // Otherwise, it is considered an object property definition,
      // which means it affects the class prototype but not the class itself.
      // An example for the latter is when `Module.output` is overridden with `output { ... }`.
      if (property.getTypeNode() != null || !EconomicMaps.containsKey(result, property.getName())) {
        EconomicMaps.put(result, property.getName(), property);
      }
    }

    return result;
  }

  @TruffleBoundary
  private UnmodifiableEconomicMap<Identifier, ClassMethod> collectAllMethods() {
    if (EconomicMaps.isEmpty(declaredMethods)) {
      return superclass == null ? EconomicMaps.create() : superclass.getAllMethods();
    }

    var size =
        EconomicMaps.size(declaredMethods)
            + (superclass == null ? 0 : EconomicMaps.size(superclass.getAllMethods()));
    var result = EconomicMaps.<Identifier, ClassMethod>create(size);

    if (superclass != null) {
      EconomicMaps.putAll(result, superclass.getAllMethods());
    }

    for (var method : EconomicMaps.getValues(declaredMethods)) {
      if (method.isLocal()) continue;

      EconomicMaps.put(result, method.getName(), method);
    }

    return result;
  }

  @TruffleBoundary
  private UnmodifiableEconomicSet<Object> collectAllRegularPropertyNames() {
    if (EconomicMaps.isEmpty(declaredProperties)) {
      return superclass == null ? EconomicSet.create() : superclass.getAllRegularPropertyNames();
    }

    var size = superclass == null ? 0 : superclass.getAllRegularPropertyNames().size();
    var result = EconomicSet.create(size);
    for (var property : EconomicMaps.getValues(declaredProperties)) {
      if (!(property.isLocal() || isHiddenProperty(property.getName()) || property.isExternal())) {
        result.add(property.getName());
      }
    }

    if (superclass == null) {
      return result;
    }

    if (result.isEmpty()) {
      return superclass.getAllRegularPropertyNames();
    }

    result.addAll(superclass.getAllRegularPropertyNames());
    return result;
  }

  @TruffleBoundary
  private UnmodifiableEconomicSet<Object> collectAllHiddenPropertyNames() {
    if (EconomicMaps.isEmpty(declaredProperties)) {
      return superclass == null ? EconomicSet.create() : superclass.getAllHiddenPropertyNames();
    }

    var size = superclass == null ? 0 : superclass.getAllHiddenPropertyNames().size();
    var result = EconomicSet.create(size);
    for (var property : EconomicMaps.getValues(declaredProperties)) {
      if (property.isHidden()) {
        result.add(property.getName());
      }
    }

    if (superclass == null) {
      return result;
    }

    if (result.isEmpty()) {
      return superclass.getAllHiddenPropertyNames();
    }

    result.addAll(superclass.getAllHiddenPropertyNames());
    return result;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    // each class is represented by a unique instance of VmClass
    return this == obj;
  }

  @Override
  public int hashCode() {
    // use a more deterministic hash code than System.identityHashCode()
    return classInfo.hashCode();
  }
}
