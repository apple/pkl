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
package org.pkl.core.collection;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Map;
import java.util.function.BiFunction;
import org.pkl.core.util.Nullable;

/**
 * Memory efficient map data structure that dynamically changes its representation depending on the
 * number of entries and is specially optimized for small number of entries. It keeps elements in a
 * linear list without any hashing when the number of entries is small. Should an actual hash data
 * structure be necessary, it tries to fit the hash value into as few bytes as possible. In contrast
 * to {@link java.util.HashMap}, it avoids allocating an extra node object per entry and rather
 * keeps values always in a plain array. See {@link EconomicMapImpl} for implementation details and
 * exact thresholds when its representation changes.
 *
 * <p>It supports a {@code null} value, but it does not support adding or looking up a {@code null}
 * key. Operations {@code get} and {@code put} provide constant-time performance on average if
 * repeatedly performed. They can however trigger an operation growing or compressing the data
 * structure, which is linear in the number of elements. Iteration is also linear in the number of
 * elements.
 *
 * <p>The implementation is not synchronized. If multiple threads want to access the data structure,
 * it requires manual synchronization, for example using {@link
 * java.util.Collections#synchronizedMap}. There is also no extra precaution to detect concurrent
 * modification while iterating.
 *
 * <p>Different strategies for the equality comparison can be configured by providing a {@link
 * Equivalence} configuration object.
 *
 * @since 19.0
 */
public interface EconomicMap<K, V> extends UnmodifiableEconomicMap<K, V> {

  /**
   * Associates {@code value} with {@code key} in this map. If the map previously contained a
   * mapping for {@code key}, the old value is replaced by {@code value}. While the {@code value}
   * may be {@code null}, the {@code key} must not be {code null}.
   *
   * @return the previous value associated with {@code key}, or {@code null} if there was no mapping
   *     for {@code key}.
   * @since 19.0
   */
  @Nullable
  V put(K key, @Nullable V value);

  /**
   * If the specified key is not already associated with a value (or is mapped to {@code null})
   * associates it with the given value and returns {@code null}, else returns the current value.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return the previous value associated with the specified key, or {@code null} if there was no
   *     mapping for the key. (A {@code null} return can also indicate that the map previously
   *     associated {@code null} with the key, if the implementation supports null values.)
   * @since 20.2
   */
  @TruffleBoundary
  default @Nullable V putIfAbsent(K key, @Nullable V value) {
    V v = get(key);
    if (v == null) {
      v = put(key, value);
    }

    return v;
  }

  /**
   * Copies all of the mappings from {@code other} to this map.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void putAll(EconomicMap<K, V> other) {
    MapCursor<K, V> e = other.getEntries();
    while (e.advance()) {
      put(e.getKey(), e.getValue());
    }
  }

  /**
   * Copies all of the mappings from {@code other} to this map.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void putAll(UnmodifiableEconomicMap<? extends K, ? extends V> other) {
    UnmodifiableMapCursor<? extends K, ? extends V> entry = other.getEntries();
    while (entry.advance()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Removes all of the mappings from this map. The map will be empty after this call returns.
   *
   * @since 19.0
   */
  @TruffleBoundary
  void clear();

  /**
   * Removes the mapping for {@code key} from this map if it is present. The map will not contain a
   * mapping for {@code key} once the call returns. The {@code key} must not be {@code null}.
   *
   * @return the previous value associated with {@code key}, or {@code null} if there was no mapping
   *     for {@code key}.
   * @since 19.0
   */
  @TruffleBoundary
  @Nullable
  V removeKey(K key);

  /**
   * Returns a {@link MapCursor} view of the mappings contained in this map.
   *
   * @since 19.0
   */
  @Override
  @TruffleBoundary
  MapCursor<K, V> getEntries();

  /**
   * Replaces each entry's value with the result of invoking {@code function} on that entry until
   * all entries have been processed or the function throws an exception. Exceptions thrown by the
   * function are relayed to the caller.
   *
   * @since 19.0
   */
  void replaceAll(BiFunction<? super K, ? super V, ? extends V> function);

  /**
   * Creates a new map that guarantees insertion order on the key set with the default {@link
   * Equivalence#DEFAULT} comparison strategy for keys.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> create() {
    return EconomicMap.create(Equivalence.DEFAULT);
  }

  /**
   * Creates a new map that guarantees insertion order on the key set with the default {@link
   * Equivalence#DEFAULT} comparison strategy for keys and initializes with a specified capacity.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> create(int initialCapacity) {
    return EconomicMap.create(Equivalence.DEFAULT, initialCapacity);
  }

  /**
   * Creates a new map that guarantees insertion order on the key set with the given comparison
   * strategy for keys.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> create(Equivalence strategy) {
    return EconomicMapImpl.create(strategy, false);
  }

  /**
   * Creates a new map that guarantees insertion order on the key set with the default {@link
   * Equivalence#DEFAULT} comparison strategy for keys and copies all elements from the specified
   * existing map.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> create(UnmodifiableEconomicMap<K, V> m) {
    return EconomicMap.create(Equivalence.DEFAULT, m);
  }

  /**
   * Creates a new map that guarantees insertion order on the key set and copies all elements from
   * the specified existing map.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> create(Equivalence strategy, UnmodifiableEconomicMap<K, V> m) {
    return EconomicMapImpl.create(strategy, m, false);
  }

  /**
   * Creates a new map that guarantees insertion order on the key set and initializes with a
   * specified capacity.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> create(Equivalence strategy, int initialCapacity) {
    return EconomicMapImpl.create(strategy, initialCapacity, false);
  }

  /**
   * Wraps an existing {@link Map} as an {@link EconomicMap}.
   *
   * @since 19.0
   */
  static <K, V> EconomicMap<K, V> wrapMap(Map<K, V> map) {
    return new EconomicMapWrap<>(map);
  }

  /**
   * Return an empty {@link MapCursor}.
   *
   * @since 22.0
   */
  @SuppressWarnings("unchecked")
  static <K, V> MapCursor<K, V> emptyCursor() {
    return (MapCursor<K, V>) EmptyMap.EMPTY_CURSOR;
  }

  /**
   * Return an empty, unmodifiable {@link EconomicMap}.
   *
   * @since 22.2
   */
  @SuppressWarnings("unchecked")
  static <K, V> EconomicMap<K, V> emptyMap() {
    return (EconomicMap<K, V>) EmptyMap.EMPTY_MAP;
  }

  /**
   * Creates an {@link EconomicMap} with one mapping.
   *
   * @param key1 the key of the first mapping
   * @param value1 the value of the first mapping
   * @return a map with the mapping
   * @since 23.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> of(K key1, V value1) {
    EconomicMap<K, V> map = EconomicMap.create(1);
    map.put(key1, value1);
    return map;
  }

  /**
   * Creates an {@link EconomicMap} with two mappings.
   *
   * @param key1 the key of the first mapping
   * @param value1 the value of the first mapping
   * @param key2 the key of the second mapping
   * @param value2 the value of the second mapping
   * @return a map with two mappings
   * @since 23.0
   */
  @TruffleBoundary
  static <K, V> EconomicMap<K, V> of(K key1, V value1, K key2, V value2) {
    EconomicMap<K, V> map = EconomicMap.create(2);
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }
}
