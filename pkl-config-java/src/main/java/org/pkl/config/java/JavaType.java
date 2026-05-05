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
package org.pkl.config.java;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.pkl.config.java.mapper.Types;
import org.pkl.core.Pair;

/**
 * Represents a fully specified Java type.
 *
 * <p>This class captures type information that cannot be expressed with {@link Class}, for example
 * {@code List<String>} or {@code Result<String, @Nullable Integer>}. It is often used with {@link
 * Config#as(JavaType)}.
 *
 * <p>To construct a non-parameterized type, use {@link #of(Class)} or {@link #ofNullable(Class)}.
 *
 * <p>To construct common parameterized types, use one of:
 *
 * <ul>
 *   <li>{@link #arrayOf(Class)}
 *   <li>{@link #collectionOf(Class)}
 *   <li>{@link #iterableOf(Class)}
 *   <li>{@link #listOf(Class)}
 *   <li>{@link #setOf(Class)}
 *   <li>{@link #mapOf(Class, Class)}
 *   <li>{@link #optionalOf(Class)}
 * </ul>
 *
 * <p>Most of the above methods have nullable variants:
 *
 * <ul>
 *   <li>{@link #arrayOfNullable(Class)}
 *   <li>{@link #collectionOfNullable(Class)}
 *   <li>{@link #iterableOfNullable(Class)}
 *   <li>{@link #listOfNullable(Class)}
 *   <li>{@link #setOfNullable(Class)}
 * </ul>
 *
 * <p>To construct arbitrary parameterized types, use the <em>super-type token</em> idiom:
 *
 * <pre>{@code
 * class Result<T, U> {} // library or user-defined type
 *
 * Config config = ...
 *
 * Result<String, @Nullable Integer> result =
 *     config.as(new JavaType<Result<String, @Nullable Integer>>() {});
 * }</pre>
 *
 * @param <T> the type represented by this {@code JavaType}
 */
@SuppressWarnings("unused") // for type parameter T
public class JavaType<T extends @Nullable Object> {
  private final Type type;

  /**
   * Constructs a {@code JavaType} using the super-type token idiom.
   *
   * <p>Subclasses must be parameterized with the desired type, for example:
   *
   * <pre>{@code
   * new JavaType<List<@Nullable String>>() {}
   * }</pre>
   *
   * @throws IllegalStateException if this instance is not parameterized
   */
  protected JavaType() {
    var superclass = getClass().getGenericSuperclass();
    if (!(superclass instanceof ParameterizedType parameterizedType)) {
      throw new IllegalStateException("JavaType token must be parameterized.");
    }
    type = parameterizedType.getActualTypeArguments()[0];
  }

  private JavaType(Type type) {
    this.type = type;
  }

  /**
   * Creates a {@code JavaType} for the given type.
   *
   * <p>For a nullable type, use {@link #ofNullable(Class)}.
   */
  public static <T> JavaType<T> of(Class<T> type) {
    return new JavaType<>(type);
  }

  /**
   * Creates a nullable {@code JavaType} for the given type.
   *
   * <p>For a non-nullable type, use {@link #of(Class)}.
   */
  public static <T> JavaType<@Nullable T> ofNullable(Class<T> type) {
    return new JavaType<>(type);
  }

  /**
   * Creates a {@code JavaType} for the given {@link Type}.
   *
   * <p>For a nullable type, use {@link #ofNullable(Type)}.
   *
   * <p>Use this method when the target type is already available as a {@link Type}; otherwise,
   * prefer {@link #of(Class)}.
   */
  public static <T> JavaType<T> of(Type type) {
    return new JavaType<>(type);
  }

  /**
   * Creates a nullable {@code JavaType} for the given {@link Type}.
   *
   * <p>For a non-nullable type, use {@link #of(Type)}.
   *
   * <p>Use this method when the target type is already available as a {@link Type}; otherwise,
   * prefer {@link #ofNullable(Class)}.
   */
  public static <T> JavaType<@Nullable T> ofNullable(Type type) {
    return new JavaType<>(type);
  }

  /**
   * Creates an {@link Optional} type with the given element type.
   *
   * <p>For a fully specified element type, use {@link #optionalOf(JavaType)}.
   */
  public static <E> JavaType<Optional<E>> optionalOf(Class<E> elementType) {
    return JavaType.of(Types.optionalOf(elementType));
  }

  /** Creates an {@link Optional} type with a fully specified element type. */
  public static <E extends @Nullable Object> JavaType<Optional<E>> optionalOf(
      JavaType<E> elementType) {
    return JavaType.of(Types.optionalOf(elementType.type));
  }

  /**
   * Creates a {@link Pair} type with the given first and second element types.
   *
   * <p>For fully specified first and second element types, use {@link #pairOf(JavaType, JavaType)}.
   */
  public static <F, S> JavaType<Pair<F, S>> pairOf(Class<F> firstType, Class<S> secondType) {
    return JavaType.of(Types.pairOf(firstType, secondType));
  }

  /** Creates a {@link Pair} type with fully specified first and second element types. */
  public static <F extends @Nullable Object, S extends @Nullable Object>
      JavaType<Pair<F, S>> pairOf(JavaType<F> firstType, JavaType<S> secondType) {
    return JavaType.of(Types.pairOf(firstType.type, secondType.type));
  }

  /**
   * Creates an array type with the given element type.
   *
   * <p>For a nullable element type, use {@link #arrayOfNullable(Class)}.
   *
   * <p>For a fully specified element type, use {@link #arrayOf(JavaType)}.
   */
  public static <E> JavaType<E[]> arrayOf(Class<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType));
  }

  /** Creates an array type with a fully specified element type. */
  public static <E extends @Nullable Object> JavaType<E[]> arrayOf(JavaType<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType.type));
  }

  /**
   * Creates an array type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #arrayOf(Class)}.
   *
   * <p>For a fully specified element type, use {@link #arrayOf(JavaType)}.
   */
  public static <E> JavaType<@Nullable E[]> arrayOfNullable(Class<E> elementType) {
    return JavaType.ofNullable(Types.arrayOf(elementType));
  }

  /**
   * Creates an {@link Iterable} type with the given element type.
   *
   * <p>For a nullable element type, use {@link #iterableOfNullable(Class)}.
   *
   * <p>For a fully specified element type, use {@link #iterableOf(JavaType)}.
   */
  public static <E> JavaType<Iterable<E>> iterableOf(Class<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType));
  }

  /**
   * Creates an {@link Iterable} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #iterableOf(Class)}.
   *
   * <p>For a fully specified element type, use {@link #iterableOf(JavaType)}.
   */
  public static <E> JavaType<Iterable<@Nullable E>> iterableOfNullable(Class<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType));
  }

  /** Creates an {@link Iterable} type with a fully specified element type. */
  public static <E extends @Nullable Object> JavaType<Iterable<E>> iterableOf(
      JavaType<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType.type));
  }

  /**
   * Creates a {@link Collection} type with the given element type.
   *
   * <p>For a nullable element type, use {@link #collectionOfNullable(Class)}.
   *
   * <p>For a fully specified element type, use {@link #collectionOf(JavaType)}.
   */
  public static <E> JavaType<Collection<E>> collectionOf(Class<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType));
  }

  /**
   * Creates a {@link Collection} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #collectionOf(Class)}.
   *
   * <p>For a fully specified element type, use {@link #collectionOf(JavaType)}.
   */
  public static <E> JavaType<Collection<@Nullable E>> collectionOfNullable(Class<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType));
  }

  /** Creates a {@link Collection} type with a fully specified element type. */
  public static <E extends @Nullable Object> JavaType<Collection<E>> collectionOf(
      JavaType<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType.type));
  }

  /**
   * Creates a {@link List} type with the given element type.
   *
   * <p>For a nullable element type, use {@link #listOfNullable(Class)}.
   *
   * <p>For a fully specified element type, use {@link #listOf(JavaType)}.
   */
  public static <E> JavaType<List<E>> listOf(Class<E> elementType) {
    return JavaType.of(Types.listOf(elementType));
  }

  /**
   * Creates a {@link List} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #listOf(Class)}.
   *
   * <p>For a fully specified element type, use {@link #listOf(JavaType)}.
   */
  public static <E> JavaType<List<@Nullable E>> listOfNullable(Class<E> elementType) {
    return JavaType.of(Types.listOf(elementType));
  }

  /** Creates a {@link List} type with a fully specified element type. */
  public static <E extends @Nullable Object> JavaType<List<E>> listOf(JavaType<E> elementType) {
    return JavaType.of(Types.listOf(elementType.type));
  }

  /**
   * Creates a {@link Set} type with the given element type.
   *
   * <p>For a nullable element type, use {@link #setOfNullable(Class)}.
   *
   * <p>For a fully specified element type, use {@link #setOf(JavaType)}.
   */
  public static <E> JavaType<Set<E>> setOf(Class<E> elementType) {
    return JavaType.of(Types.setOf(elementType));
  }

  /**
   * Creates a {@link Set} type whose element type is non-nullable.
   *
   * <p>For a non-null element type, use {@link #setOf(Class)}.
   *
   * <p>For a fully specified element type, use {@link #setOf(JavaType)}.
   */
  public static <E> JavaType<Set<@Nullable E>> setOfNullable(Class<E> elementType) {
    return JavaType.of(Types.setOf(elementType));
  }

  /** Creates a {@link Set} type with a fully specified element type. */
  public static <E extends @Nullable Object> JavaType<Set<E>> setOf(JavaType<E> elementType) {
    return JavaType.of(Types.setOf(elementType.type));
  }

  /**
   * Creates a {@link Map} type with the given key and value types.
   *
   * <p>For fully specified key and value types, use {@link #mapOf(JavaType, JavaType)}.
   */
  public static <K, V> JavaType<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
    return JavaType.of(Types.mapOf(keyType, valueType));
  }

  /** Creates a {@link Map} type with fully specified key and value types. */
  public static <K extends @Nullable Object, V extends @Nullable Object> JavaType<Map<K, V>> mapOf(
      JavaType<K> keyType, JavaType<V> valueType) {
    return JavaType.of(Types.mapOf(keyType.type, valueType.type));
  }

  public Type getType() {
    return type;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof JavaType<?> other)) return false;
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
