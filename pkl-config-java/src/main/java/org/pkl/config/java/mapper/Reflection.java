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
package org.pkl.config.java.mapper;

import static java.util.Arrays.stream;

import io.leangen.geantyref.CaptureType;
import io.leangen.geantyref.GenericTypeReflector;
import java.lang.reflect.*;
import org.pkl.core.util.Nullable;

/**
 * Reflection utilities for implementing {@link ConverterFactory}s. Mostly covers introspection of
 * parameterized types, which is not covered by the {@code java.util.reflect} API.
 *
 * <p>The heavy lifting under the covers is done by the excellent ge(a)ntyref library.
 */
public final class Reflection {
  private Reflection() {}

  /**
   * Returns the class with the given fully qualified name, or {@code null} if a class with the
   * given name cannot be found.
   */
  public static @Nullable Class<?> tryLoadClass(String qualifiedName) {
    try {
      // use Class.forName() rather than ClassLoader.loadClass()
      // because Class.getClassLoader() is not supported by AOT
      return Class.forName(qualifiedName);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  public static boolean isMissingTypeArguments(Type type) {
    if (type instanceof WildcardType) {
      var wildcardType = (WildcardType) type;
      var baseType =
          wildcardType.getLowerBounds().length > 0
              ? wildcardType.getLowerBounds()[0]
              : wildcardType.getUpperBounds()[0];
      return isMissingTypeArguments(baseType);
    }
    return GenericTypeReflector.isMissingTypeParameters(type);
  }

  /**
   * Returns the normalized form of the given type. A normalized type is concrete (no wildcards) and
   * instantiable (not an interface or abstract class).
   */
  public static Type normalize(Type type) {
    if (type instanceof WildcardType) {
      var wcType = (WildcardType) type;
      var bounds = wcType.getLowerBounds();
      if (bounds.length > 0) return bounds[0];
      bounds = wcType.getUpperBounds();
      if (bounds.length > 0) return bounds[0];
    }
    return getExactSupertype(type, toRawType(type));
  }

  /**
   * Returns the raw (erased) type for the given parameterized type, type bound for the given
   * wildcard type, or the given type otherwise.
   */
  public static Class<?> toRawType(Type type) {
    return GenericTypeReflector.erase(type);
  }

  /**
   * Returns the wrapper type for the given primitive type. If the given type is not a primitive
   * type, returns the given type.
   */
  @SuppressWarnings("unchecked")
  // casts are safe as (say) boolean.class and Boolean.class are both of type Class<Boolean>
  public static <T> Class<T> toWrapperType(Class<T> type) {
    if (type == boolean.class) return (Class<T>) Boolean.class;
    if (type == char.class) return (Class<T>) Character.class;
    if (type == long.class) return (Class<T>) Long.class;
    if (type == int.class) return (Class<T>) Integer.class;
    if (type == short.class) return (Class<T>) Short.class;
    if (type == byte.class) return (Class<T>) Byte.class;
    if (type == double.class) return (Class<T>) Double.class;
    if (type == float.class) return (Class<T>) Float.class;

    return type;
  }

  /** Returns the (possibly parameterized) element type for the given array type. */
  public static Type getArrayElementType(Type type) {
    return GenericTypeReflector.getArrayComponentType(type);
  }

  /**
   * Returns a parameterization of the given raw supertype, taking into account type arguments of
   * the given subtype. For example, @{code getExactSupertype(listOf(String.class),
   * Collection.class)} will return @{code collectionOf(String.class)}. If the given subtype is not
   * a parameterized type, returns the given raw supertype. If the given types have no inheritance
   * relationship, returns {@code null}.
   */
  // call sites typically know that the given types have an inheritance relationship
  // annotating the return type with @Nullable would lead to annoying IDE
  // nullability warnings in these cases
  public static Type getExactSupertype(Type type, Class<?> rawSupertype) {
    return uncapture(GenericTypeReflector.getExactSuperType(type, rawSupertype));
  }

  /**
   * Returns a parameterization of the given raw subtype, taking into account type arguments of the
   * given supertype. For example, @{code getExactSubtype(collectionOf(String.class), List.class)}
   * will return @{code listOf(String.class)}. If the given supertype is not a parameterized type,
   * returns the given raw subtype. If the given types have no inheritance relationship, returns
   * {@code null}.
   */
  // call sites typically know that the given types have an inheritance relationship
  // annotating the return type with @Nullable would lead to annoying IDE
  // nullability warnings in these cases
  public static Type getExactSubtype(Type type, Class<?> rawSubtype) {
    return uncapture(GenericTypeReflector.getExactSubType(type, rawSubtype));
  }

  /**
   * Returns the exact parameter types of the given method or constructor, taking into account type
   * arguments of the given declaring type. For example, {@code
   * getExactParameterTypes(List.class.getDeclaredMethod("get"), listOf(optionalOf(String.class)}
   * will return {@code optionalOf(String.class)}. Throws {@link IllegalArgumentException} if the
   * given method or constructor is not declared by the given type.
   */
  public static Type[] getExactParameterTypes(Executable m, Type declaringType) {
    return stream(
            GenericTypeReflector.getExactParameterTypes(
                m, GenericTypeReflector.annotate(declaringType)))
        .map(annType -> uncapture(annType.getType()))
        .toArray(Type[]::new);
  }

  /**
   * Undoes the capture of a wildcard type, or returns the given type otherwise. Unlike wildcard
   * types, capture types are not represented in the Java reflection API, but may be returned by
   * geantyref's getExactXXX methods. This leads to problems, which is why our getExactXXX methods
   * eliminate them.
   */
  private static Type uncapture(Type type) {
    if (type instanceof CaptureType) {
      return ((CaptureType) type).getWildcardType();
    }
    return type;
  }
}
