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
package org.pkl.config.java;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import org.pkl.config.java.mapper.Types;
import org.pkl.core.Pair;
import org.pkl.core.util.Nullable;

/**
 * Runtime representation of a possibly parameterized Java type. Factory methods are provided to
 * ease construction of commonly used Java standard library types. For example, a {@code JavaType}
 * for {@code List<String>} can be constructed using {@code JavaType.listOf(String.class)}.
 *
 * <p>Parameterizations of other types can be constructed using the <em>super type token</em> idiom:
 *
 * <p>
 *
 * <pre>{@code
 * class Mapping<T> {} // some user-defined type
 * Config config = ...
 *
 * Mapping<String> container = config.as(
 *   // construct super type token for Mapping<String>
 *   new JavaType<Mapping<String>>() {}
 * );
 * }</pre>
 *
 * @param <T> the type reified by this {@code JavaType} instance
 */
@SuppressWarnings("unused")
public class JavaType<T> {
  private final Type type;

  protected JavaType() {
    var superclass = getClass().getGenericSuperclass();
    if (superclass instanceof Class) {
      throw new IllegalStateException("JavaType token must be parameterized.");
    }
    type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
  }

  private JavaType(Type type) {
    this.type = type;
  }

  /** Creates a {@code JavaType} for the given {@code Class}. */
  public static <T> JavaType<T> of(Class<T> type) {
    return new JavaType<>(type);
  }

  /**
   * Creates a {@code JavaType} for the given {@code Type}.
   *
   * <p>Note: This method is not type safe, and should be used with care.
   */
  public static <T> JavaType<T> of(Type type) {
    return new JavaType<>(type);
  }

  /** Creates an {@link Optional} type with the given element type. */
  public static <E> JavaType<Optional<E>> optionalOf(Class<E> elementType) {
    return JavaType.of(Types.optionalOf(elementType));
  }

  /** Creates an {@link Optional} type with the given element type. */
  public static <E> JavaType<Optional<E>> optionalOf(JavaType<E> elementType) {
    return JavaType.of(Types.optionalOf(elementType.type));
  }

  /** Creates a {@link Pair} type with the given first and second element types. */
  public static <F, S> JavaType<Pair<F, S>> pairOf(Class<F> firstType, Class<S> secondType) {
    return JavaType.of(Types.pairOf(firstType, secondType));
  }

  /** Creates a {@link Pair} type with the given first and second element types. */
  public static <F, S> JavaType<Pair<F, S>> pairOf(JavaType<F> firstType, JavaType<S> secondType) {
    return JavaType.of(Types.pairOf(firstType.type, secondType.type));
  }

  /** Creates an array type with the given element type. */
  public static <E> JavaType<E[]> arrayOf(Class<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType));
  }

  /** Creates an array type with the given element type. */
  public static <E> JavaType<E[]> arrayOf(JavaType<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType.type));
  }

  /** Creates an {@link Iterable} type with the given element type. */
  public static <E> JavaType<Iterable<E>> iterableOf(Class<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType));
  }

  /** Creates an {@link Iterable} type with the given element type. */
  public static <E> JavaType<Iterable<E>> iterableOf(JavaType<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType.type));
  }

  /** Creates a {@link Collection} type with the given element type. */
  public static <E> JavaType<Collection<E>> collectionOf(Class<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType));
  }

  /** Creates a {@link Collection} type with the given element type. */
  public static <E> JavaType<Collection<E>> collectionOf(JavaType<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType.type));
  }

  /** Creates a {@link List} type with the given element type. */
  public static <E> JavaType<List<E>> listOf(Class<E> elementType) {
    return JavaType.of(Types.listOf(elementType));
  }

  /** Creates a {@link List} type with the given element type. */
  public static <E> JavaType<List<E>> listOf(JavaType<E> elementType) {
    return JavaType.of(Types.listOf(elementType.type));
  }

  /** Creates a {@link Set} type with the given element type. */
  public static <E> JavaType<Set<E>> setOf(Class<E> elementType) {
    return JavaType.of(Types.setOf(elementType));
  }

  /** Creates a {@link Set} type with the given element type. */
  public static <E> JavaType<Set<E>> setOf(JavaType<E> elementType) {
    return JavaType.of(Types.setOf(elementType.type));
  }

  /** Creates a {@link Map} type with the given key and value types. */
  public static <K, V> JavaType<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
    return JavaType.of(Types.mapOf(keyType, valueType));
  }

  /** Creates a {@link Map} type with the given key and value types. */
  public static <K, V> JavaType<Map<K, V>> mapOf(JavaType<K> keyType, JavaType<V> valueType) {
    return JavaType.of(Types.mapOf(keyType.type, valueType.type));
  }

  /** Returns the underlying {@link Type}. */
  public Type getType() {
    return type;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof JavaType)) return false;

    var other = (JavaType<?>) obj;
    return type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return type.toString();
  }
}
