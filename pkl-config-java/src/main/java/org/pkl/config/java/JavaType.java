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
 * Represents a Java type, including fully parameterized types.
 *
 * <p>This class captures complete type information that cannot be expressed with {@link Class}, for
 * example {@code List<String>} or {@code Result<String, @Nullable Integer>}. It is often used with
 * {@link Config#as(JavaType)}.
 *
 * <p>A {@code JavaType} always represents a non-null top-level type, but its type arguments may be
 * nullable. For example, {@code listOfNullable(String.class)} represents {@code List<@Nullable
 * String>}.
 *
 * <p>To construct a non-parameterized type, use {@link #of(Class)}.
 *
 * <p>To construct common parameterized types, use one of:
 *
 * <ul>
 *   <li>{@link #optionalOf(Class)}
 *   <li>{@link #arrayOf(Class)}
 *   <li>{@link #collectionOf(Class)}
 *   <li>{@link #iterableOf(Class)}
 *   <li>{@link #listOf(Class)}
 *   <li>{@link #setOf(Class)}
 *   <li>{@link #mapOf(Class, Class)}
 *   <li>{@link #pairOf(Class, Class)}
 * </ul>
 *
 * <p>Apart from {@code optionalOf()}, the above methods have nullable variants:
 *
 * <ul>
 *   <li>{@link #arrayOfNullable(Class)}
 *   <li>{@link #collectionOfNullable(Class)}
 *   <li>{@link #iterableOfNullable(Class)}
 *   <li>{@link #listOfNullable(Class)}
 *   <li>{@link #setOfNullable(Class)}
 *   <li>{@link #mapOfNullableKeys(Class, Class)}
 *   <li>{@link #mapOfNullableValues(Class, Class)}
 *   <li>{@link #mapOfNullableKeysAndValues(Class, Class)}
 *   <li>{@link #pairOfNullableFirst(Class, Class)}
 *   <li>{@link #pairOfNullableSecond(Class, Class)}
 *   <li>{@link #pairOfNullableFirstAndSecond(Class, Class)}
 * </ul>
 *
 * <p>These methods can be nested. For example, {@code optionalOf(listOfNullable(String.class))}
 * represents {@code Optional<List<@Nullable String>>}.
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
 * @param <T> the non-null type represented by this {@code JavaType}
 */
public class JavaType<T> {
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

  /** Creates a {@code JavaType} for the given type. */
  public static <T> JavaType<T> of(Class<T> type) {
    return new JavaType<>(type);
  }

  /**
   * Creates a {@code JavaType} for the given {@link Type}.
   *
   * <p>Use this method when the target type is already available as a {@link Type}; otherwise,
   * prefer {@link #of(Class)}.
   */
  public static <T> JavaType<T> of(Type type) {
    return new JavaType<>(type);
  }

  /**
   * Creates an {@link Optional} type with the given non-null element type.
   *
   * <p>For a parameterized element type, use {@link #optionalOf(JavaType)}.
   */
  public static <E> JavaType<Optional<E>> optionalOf(Class<E> elementType) {
    return JavaType.of(Types.optionalOf(elementType));
  }

  /** Creates an {@link Optional} type with the given non-null element type. */
  public static <E> JavaType<Optional<E>> optionalOf(JavaType<E> elementType) {
    return JavaType.of(Types.optionalOf(elementType.type));
  }

  /**
   * Creates a {@link Pair} type with the given non-null first and non-null second element types.
   *
   * <p>For nullable first and/or second element types, use one of the {@code pairOfNullable*}
   * methods.
   *
   * <p>For parameterized element types, use {@link #pairOf(JavaType, JavaType)}.
   */
  public static <F, S> JavaType<Pair<F, S>> pairOf(Class<F> firstType, Class<S> secondType) {
    return JavaType.of(Types.pairOf(firstType, secondType));
  }

  /**
   * Creates a {@link Pair} type with the given non-null first and non-null second element types.
   *
   * <p>For nullable first and/or second element types, use one of the {@code pairOfNullable*}
   * methods.
   */
  public static <F, S> JavaType<Pair<F, S>> pairOf(JavaType<F> firstType, JavaType<S> secondType) {
    return JavaType.of(Types.pairOf(firstType.type, secondType.type));
  }

  /**
   * Creates a {@link Pair} type with the given nullable first and non-null second element types.
   *
   * <p>For parameterized element types, use {@link #pairOfNullableFirst(JavaType, JavaType)}.
   */
  public static <F, S> JavaType<Pair<@Nullable F, S>> pairOfNullableFirst(
      Class<F> firstType, Class<S> secondType) {
    return JavaType.of(Types.pairOf(firstType, secondType));
  }

  /**
   * Creates a {@link Pair} type with the given nullable first and non-null second element types.
   */
  public static <F, S> JavaType<Pair<@Nullable F, S>> pairOfNullableFirst(
      JavaType<F> firstType, JavaType<S> secondType) {
    return JavaType.of(Types.pairOf(firstType.type, secondType.type));
  }

  /**
   * Creates a {@link Pair} type with the given non-null first and nullable second element types.
   *
   * <p>For parameterized element types, use {@link #pairOfNullableSecond(JavaType, JavaType)}.
   */
  public static <F, S> JavaType<Pair<F, @Nullable S>> pairOfNullableSecond(
      Class<F> firstType, Class<S> secondType) {
    return JavaType.of(Types.pairOf(firstType, secondType));
  }

  /**
   * Creates a {@link Pair} type with the given non-null first and nullable second element types.
   */
  public static <F, S> JavaType<Pair<F, @Nullable S>> pairOfNullableSecond(
      JavaType<F> firstType, JavaType<S> secondType) {
    return JavaType.of(Types.pairOf(firstType.type, secondType.type));
  }

  /**
   * Creates a {@link Pair} type with the given nullable first and nullable second element types.
   *
   * <p>For parameterized element types, use {@link #pairOfNullableFirstAndSecond(JavaType,
   * JavaType)}.
   */
  public static <F, S> JavaType<Pair<@Nullable F, @Nullable S>> pairOfNullableFirstAndSecond(
      Class<F> firstType, Class<S> secondType) {
    return JavaType.of(Types.pairOf(firstType, secondType));
  }

  /**
   * Creates a {@link Pair} type with the given nullable first and nullable second element types.
   */
  public static <F, S> JavaType<Pair<@Nullable F, @Nullable S>> pairOfNullableFirstAndSecond(
      JavaType<F> firstType, JavaType<S> secondType) {
    return JavaType.of(Types.pairOf(firstType.type, secondType.type));
  }

  /**
   * Creates an array type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #arrayOfNullable(Class)}.
   *
   * <p>For a parameterized element type, use {@link #arrayOf(JavaType)}.
   */
  public static <E> JavaType<E[]> arrayOf(Class<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType));
  }

  /**
   * Creates an array type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #arrayOfNullable(JavaType)}.
   */
  public static <E> JavaType<E[]> arrayOf(JavaType<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType.type));
  }

  /**
   * Creates an array type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #arrayOf(Class)}.
   *
   * <p>For a parameterized element type, use {@link #arrayOfNullable(JavaType)}.
   */
  public static <E> JavaType<@Nullable E[]> arrayOfNullable(Class<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType));
  }

  /**
   * Creates an array type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #arrayOf(JavaType)}.
   */
  public static <E> JavaType<@Nullable E[]> arrayOfNullable(JavaType<E> elementType) {
    return JavaType.of(Types.arrayOf(elementType.type));
  }

  /**
   * Creates an {@link Iterable} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #iterableOfNullable(Class)}.
   *
   * <p>For a parameterized element type, use {@link #iterableOf(JavaType)}.
   */
  public static <E> JavaType<Iterable<E>> iterableOf(Class<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType));
  }

  /**
   * Creates an {@link Iterable} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #iterableOfNullable(JavaType)}.
   */
  public static <E> JavaType<Iterable<E>> iterableOf(JavaType<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType.type));
  }

  /**
   * Creates an {@link Iterable} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #iterableOf(Class)}.
   *
   * <p>For a parameterized element type, use {@link #iterableOfNullable(JavaType)}.
   */
  public static <E> JavaType<Iterable<@Nullable E>> iterableOfNullable(Class<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType));
  }

  /**
   * Creates an {@link Iterable} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #iterableOf(JavaType)}.
   */
  public static <E> JavaType<Iterable<@Nullable E>> iterableOfNullable(JavaType<E> elementType) {
    return JavaType.of(Types.iterableOf(elementType.type));
  }

  /**
   * Creates a {@link Collection} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #collectionOfNullable(Class)}.
   *
   * <p>For a parameterized element type, use {@link #collectionOf(JavaType)}.
   */
  public static <E> JavaType<Collection<E>> collectionOf(Class<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType));
  }

  /**
   * Creates a {@link Collection} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #collectionOfNullable(JavaType)}.
   */
  public static <E> JavaType<Collection<E>> collectionOf(JavaType<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType.type));
  }

  /**
   * Creates a {@link Collection} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #collectionOf(Class)}.
   *
   * <p>For a parameterized element type, use {@link #collectionOfNullable(JavaType)}.
   */
  public static <E> JavaType<Collection<@Nullable E>> collectionOfNullable(Class<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType));
  }

  /**
   * Creates a {@link Collection} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #collectionOf(JavaType)}.
   */
  public static <E> JavaType<Collection<@Nullable E>> collectionOfNullable(
      JavaType<E> elementType) {
    return JavaType.of(Types.collectionOf(elementType.type));
  }

  /**
   * Creates a {@link List} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #listOfNullable(Class)}.
   *
   * <p>For a parameterized element type, use {@link #listOf(JavaType)}.
   */
  public static <E> JavaType<List<E>> listOf(Class<E> elementType) {
    return JavaType.of(Types.listOf(elementType));
  }

  /**
   * Creates a {@link List} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #listOfNullable(JavaType)}.
   */
  public static <E> JavaType<List<E>> listOf(JavaType<E> elementType) {
    return JavaType.of(Types.listOf(elementType.type));
  }

  /**
   * Creates a {@link List} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #listOf(Class)}.
   *
   * <p>For a parameterized element type, use {@link #listOfNullable(JavaType)}.
   */
  public static <E> JavaType<List<@Nullable E>> listOfNullable(Class<E> elementType) {
    return JavaType.of(Types.listOf(elementType));
  }

  /**
   * Creates a {@link List} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #listOf(JavaType)}.
   */
  public static <E> JavaType<List<@Nullable E>> listOfNullable(JavaType<E> elementType) {
    return JavaType.of(Types.listOf(elementType.type));
  }

  /**
   * Creates a {@link Set} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #setOfNullable(Class)}.
   *
   * <p>For a parameterized element type, use {@link #setOf(JavaType)}.
   */
  public static <E> JavaType<Set<E>> setOf(Class<E> elementType) {
    return JavaType.of(Types.setOf(elementType));
  }

  /**
   * Creates a {@link Set} type with the given non-null element type.
   *
   * <p>For a nullable element type, use {@link #setOfNullable(JavaType)}.
   */
  public static <E> JavaType<Set<E>> setOf(JavaType<E> elementType) {
    return JavaType.of(Types.setOf(elementType.type));
  }

  /**
   * Creates a {@link Set} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #setOf(Class)}.
   *
   * <p>For a parameterized element type, use {@link #setOfNullable(JavaType)}.
   */
  public static <E> JavaType<Set<@Nullable E>> setOfNullable(Class<E> elementType) {
    return JavaType.of(Types.setOf(elementType));
  }

  /**
   * Creates a {@link Set} type whose element type is nullable.
   *
   * <p>For a non-null element type, use {@link #setOf(JavaType)}.
   */
  public static <E> JavaType<Set<@Nullable E>> setOfNullable(JavaType<E> elementType) {
    return JavaType.of(Types.setOf(elementType.type));
  }

  /**
   * Creates a {@link Map} type with the given non-null key and non-null value types.
   *
   * <p>For nullable keys and/or values, use one of the {@code mapOfNullable*} methods.
   *
   * <p>For parameterized key and value types, use {@link #mapOf(JavaType, JavaType)}.
   */
  public static <K, V> JavaType<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
    return JavaType.of(Types.mapOf(keyType, valueType));
  }

  /**
   * Creates a {@link Map} type with the given non-null key and non-null value types.
   *
   * <p>For nullable keys and/or values, use one of the {@code mapOfNullable*} methods.
   */
  public static <K, V> JavaType<Map<K, V>> mapOf(JavaType<K> keyType, JavaType<V> valueType) {
    return JavaType.of(Types.mapOf(keyType.type, valueType.type));
  }

  /**
   * Creates a {@link Map} type with the given nullable key and non-null value types.
   *
   * <p>For parameterized key and value types, use {@link #mapOfNullableKeys(JavaType, JavaType)}.
   */
  public static <K, V> JavaType<Map<@Nullable K, V>> mapOfNullableKeys(
      Class<K> keyType, Class<V> valueType) {
    return JavaType.of(Types.mapOf(keyType, valueType));
  }

  /** Creates a {@link Map} type with the given nullable key and non-null value types. */
  public static <K, V> JavaType<Map<@Nullable K, V>> mapOfNullableKeys(
      JavaType<K> keyType, JavaType<V> valueType) {
    return JavaType.of(Types.mapOf(keyType.type, valueType.type));
  }

  /**
   * Creates a {@link Map} type with the given non-null key and nullable value types.
   *
   * <p>For parameterized key and value types, use {@link #mapOfNullableValues(JavaType, JavaType)}.
   */
  public static <K, V> JavaType<Map<K, @Nullable V>> mapOfNullableValues(
      Class<K> keyType, Class<V> valueType) {
    return JavaType.of(Types.mapOf(keyType, valueType));
  }

  /** Creates a {@link Map} type with the given non-null key and nullable value types. */
  public static <K, V> JavaType<Map<K, @Nullable V>> mapOfNullableValues(
      JavaType<K> keyType, JavaType<V> valueType) {
    return JavaType.of(Types.mapOf(keyType.type, valueType.type));
  }

  /**
   * Creates a {@link Map} type with the given nullable key and nullable value types.
   *
   * <p>For parameterized key and value types, use {@link #mapOfNullableKeysAndValues(JavaType,
   * JavaType)}.
   */
  public static <K, V> JavaType<Map<@Nullable K, @Nullable V>> mapOfNullableKeysAndValues(
      Class<K> keyType, Class<V> valueType) {
    return JavaType.of(Types.mapOf(keyType, valueType));
  }

  /** Creates a {@link Map} type with the given nullable key and nullable value types. */
  public static <K, V> JavaType<Map<@Nullable K, @Nullable V>> mapOfNullableKeysAndValues(
      JavaType<K> keyType, JavaType<V> valueType) {
    return JavaType.of(Types.mapOf(keyType.type, valueType.type));
  }

  /** Returns the underlying {@link Type}. */
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
