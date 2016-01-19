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

import static java.util.Map.*;

import java.io.Serializable;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.util.Nullable;

/** Information about a Pkl class and its Java representation. */
@SuppressWarnings("rawtypes")
public final class PClassInfo<T> implements Serializable {

  private static final long serialVersionUID = 0L;

  // Simple name of a module's class.
  // User-facing via `module.getClass()` and error messages.
  // "Module" would result in a name clash between
  // the module class of `pkl.base` and class `Module` defined in `pkl.base`.
  public static final String MODULE_CLASS_NAME = "ModuleClass";

  public static final URI pklBaseUri = URI.create("pkl:base");
  public static final URI pklSemverUri = URI.create("pkl:semver");
  public static final URI pklProjectUri = URI.create("pkl:Project");

  public static final PClassInfo<Void> Any = pklBaseClassInfo("Any", Void.class);
  public static final PClassInfo<PNull> Null = pklBaseClassInfo("Null", PNull.class);
  public static final PClassInfo<String> String = pklBaseClassInfo("String", String.class);
  public static final PClassInfo<Boolean> Boolean = pklBaseClassInfo("Boolean", boolean.class);
  public static final PClassInfo<Void> Number = pklBaseClassInfo("Number", Void.class);
  public static final PClassInfo<Long> Int = pklBaseClassInfo("Int", long.class);
  public static final PClassInfo<Double> Float = pklBaseClassInfo("Float", double.class);
  public static final PClassInfo<Duration> Duration = pklBaseClassInfo("Duration", Duration.class);
  public static final PClassInfo<DataSize> DataSize = pklBaseClassInfo("DataSize", DataSize.class);
  public static final PClassInfo<Pair> Pair = pklBaseClassInfo("Pair", Pair.class);
  public static final PClassInfo<Void> Collection = pklBaseClassInfo("Collection", Void.class);
  public static final PClassInfo<ArrayList> List = pklBaseClassInfo("List", ArrayList.class);
  public static final PClassInfo<LinkedHashSet> Set = pklBaseClassInfo("Set", LinkedHashSet.class);
  public static final PClassInfo<LinkedHashMap> Map = pklBaseClassInfo("Map", LinkedHashMap.class);
  public static final PClassInfo<PObject> Object = pklBaseClassInfo("Object", PObject.class);
  public static final PClassInfo<PObject> Dynamic = pklBaseClassInfo("Dynamic", PObject.class);
  public static final PClassInfo<PObject> Typed = pklBaseClassInfo("Typed", PObject.class);
  public static final PClassInfo<ArrayList> Listing = pklBaseClassInfo("Listing", ArrayList.class);
  public static final PClassInfo<LinkedHashMap> Mapping =
      pklBaseClassInfo("Mapping", LinkedHashMap.class);
  public static final PClassInfo<PModule> Module = pklBaseClassInfo("Module", PModule.class);
  public static final PClassInfo<PClass> Class = pklBaseClassInfo("Class", PClass.class);
  public static final PClassInfo<TypeAlias> TypeAlias =
      pklBaseClassInfo("TypeAlias", TypeAlias.class);
  public static final PClassInfo<Pattern> Regex = pklBaseClassInfo("Regex", Pattern.class);
  public static final PClassInfo<PObject> Deprecated =
      pklBaseClassInfo("Deprecated", PObject.class);
  public static final PClassInfo<PObject> AlsoKnownAs =
      pklBaseClassInfo("AlsoKnownAs", PObject.class);
  public static final PClassInfo<PObject> Unlisted = pklBaseClassInfo("Unlisted", PObject.class);
  public static final PClassInfo<PObject> DocExample =
      pklBaseClassInfo("DocExample", PObject.class);
  public static final PClassInfo<PObject> PcfRenderDirective =
      pklBaseClassInfo("PcfRenderDirective", PObject.class);
  public static final PClassInfo<PObject> ModuleInfo =
      pklBaseClassInfo("ModuleInfo", PObject.class);
  public static final PClassInfo<PObject> Version =
      new PClassInfo<>("pkl.semver", "Version", PObject.class, pklSemverUri);
  public static final PClassInfo<PObject> Project =
      new PClassInfo<>("pkl.Project", "ModuleClass", PObject.class, pklProjectUri);

  public static final PClassInfo<Object> Unavailable =
      new PClassInfo<>("unavailable", "unavailable", Object.class, URI.create("pkl:unavailable"));

  /** Returns the class info for the class with the given module and class name. */
  public static PClassInfo<?> get(String moduleName, String className, URI moduleUri) {
    if (moduleName.equals("pkl.base")) {
      var classInfo = pooledPklBaseClassInfos.get(className);
      if (classInfo != null) return classInfo;
    }
    return new PClassInfo<>(moduleName, className, PObject.class, moduleUri);
  }

  /** Returns the class info for the module class with the given module name. */
  public static PClassInfo<?> forModuleClass(String moduleName, URI moduleUri) {
    return get(moduleName, MODULE_CLASS_NAME, moduleUri);
  }

  /** Returns the class info for the given value's class. */
  @SuppressWarnings("unchecked")
  public static <T> PClassInfo<T> forValue(T value) {
    if (value instanceof Value) return (PClassInfo<T>) ((Value) value).getClassInfo();

    if (value instanceof String) return (PClassInfo<T>) String;
    if (value instanceof Boolean) return (PClassInfo<T>) Boolean;
    if (value instanceof Long) return (PClassInfo<T>) Int;
    if (value instanceof Double) return (PClassInfo<T>) Float;
    if (value instanceof List) return (PClassInfo<T>) List;
    if (value instanceof Set) return (PClassInfo<T>) Set;
    if (value instanceof Map) return (PClassInfo<T>) Map;
    if (value instanceof Pattern) return (PClassInfo<T>) Regex;

    throw new IllegalArgumentException("Not a Pkl value: " + value);
  }

  /**
   * Returns the name of the module that this Pkl class is declared in. Note that a module name is
   * not guaranteed to be unique, especially if it not declared but inferred.
   */
  public String getModuleName() {
    return moduleName;
  }

  /** Returns the simple name of this Pkl class. */
  public String getSimpleName() {
    return className;
  }

  /**
   * Returns the qualified name of this Pkl class, `moduleName/className`. Note that a qualified
   * class name is not guaranteed to be unique, especially if the module name is not declared but
   * inferred.
   */
  public String getQualifiedName() {
    return qualifiedName;
  }

  public String getDisplayName() {
    // display `String` rather than `pkl.base#String`, etc.
    return moduleName.equals("pkl.base") ? className : isModuleClass() ? moduleName : qualifiedName;
  }

  public boolean isModuleClass() {
    // should have a better way but this is what we got
    return className.equals(MODULE_CLASS_NAME);
  }

  /**
   * Returns the concrete Java class used to represent values of this Pkl class in Java. Returns
   * {@code Void.class} for abstract Pkl classes.
   */
  public Class<T> getJavaClass() {
    return javaClass;
  }

  /** Tells if this Pkl class is external (built-in). */
  public boolean isExternalClass() {
    return javaClass != PObject.class;
  }

  /** Tells if this class is defined in Pkl's standard library. */
  public boolean isStandardLibraryClass() {
    return moduleName.startsWith("pkl.");
  }

  public boolean isConcreteCollectionClass() {
    return this == PClassInfo.List || this == PClassInfo.Set;
  }

  public boolean isExactClassOf(Object value) {
    var clazz = value.getClass();
    if (clazz != javaClass) return false;
    if (clazz != PObject.class) return true;

    var pObject = (PObject) value;
    return pObject.getClassInfo().equals(this);
  }

  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof PClassInfo)) return false;

    var other = (PClassInfo<?>) obj;
    return qualifiedName.equals(other.qualifiedName);
  }

  public int hashCode() {
    return qualifiedName.hashCode();
  }

  public String toString() {
    return getDisplayName();
  }

  private static final Map<String, PClassInfo<?>> pooledPklBaseClassInfos =
      java.util.Map.ofEntries(
          entry(Any.className, Any),
          entry(Null.className, Null),
          entry(Boolean.className, Boolean),
          entry(String.className, String),
          entry(Number.className, Number),
          entry(Int.className, Int),
          entry(Float.className, Float),
          entry(Duration.className, Duration),
          entry(DataSize.className, DataSize),
          entry(Pair.className, Pair),
          entry(Collection.className, Collection),
          entry(List.className, List),
          entry(Set.className, Set),
          entry(Map.className, Map),
          entry(Object.className, Object),
          entry(Dynamic.className, Dynamic),
          entry(Typed.className, Typed),
          entry(Listing.className, Listing),
          entry(Mapping.className, Mapping),
          entry(Module.className, Module),
          entry(Class.className, Class),
          entry(TypeAlias.className, TypeAlias),
          entry(Regex.className, Regex),
          entry(Deprecated.className, Deprecated),
          entry(AlsoKnownAs.className, AlsoKnownAs),
          entry(Unlisted.className, Unlisted),
          entry(DocExample.className, DocExample),
          entry(PcfRenderDirective.className, PcfRenderDirective));

  private final String moduleName;
  private final String className;
  private final URI moduleUri;
  private final String qualifiedName;
  private final Class<T> javaClass;

  private PClassInfo(String moduleName, String className, Class<T> javaClass, URI moduleUri) {
    this.moduleName = moduleName;
    this.className = className;
    this.moduleUri = moduleUri;
    this.qualifiedName = moduleName + "#" + className;
    this.javaClass = javaClass;
  }

  private static <T> PClassInfo<T> pklBaseClassInfo(String className, Class<T> javaType) {
    return new PClassInfo<>("pkl.base", className, javaType, pklBaseUri);
  }

  public URI getModuleUri() {
    return moduleUri;
  }
}
