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
import org.pkl.core.util.Nullable;

/**
 * Unmodifiable memory efficient map. See {@link EconomicMap} for the underlying data structure and
 * its properties.
 *
 * @since 19.0
 */
public interface UnmodifiableEconomicMap<K, V> {

  /**
   * Returns the value to which {@code key} is mapped, or {@code null} if this map contains no
   * mapping for {@code key}. The {@code key} must not be {@code null}.
   *
   * @since 19.0
   */
  @TruffleBoundary
  @Nullable
  V get(K key);

  /**
   * Returns the value to which {@code key} is mapped, or {@code defaultValue} if this map contains
   * no mapping for {@code key}. The {@code key} must not be {@code null}.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default @Nullable V get(K key, V defaultValue) {
    V v = get(key);
    if (v == null) {
      return defaultValue;
    }
    return v;
  }

  /**
   * Returns {@code true} if this map contains a mapping for {@code key}. Always returns {@code
   * false} if the {@code key} is {@code null}.
   *
   * @since 19.0
   */
  @TruffleBoundary
  boolean containsKey(K key);

  /**
   * Returns the number of key-value mappings in this map.
   *
   * @since 19.0
   */
  int size();

  /**
   * Returns {@code true} if this map contains no key-value mappings.
   *
   * @since 19.0
   */
  boolean isEmpty();

  /**
   * Returns a {@link Iterable} view of the values contained in this map.
   *
   * @since 19.0
   */
  Iterable<V> getValues();

  /**
   * Returns a {@link Iterable} view of the keys contained in this map.
   *
   * @since 19.0
   */
  Iterable<K> getKeys();

  /**
   * Returns a {@link UnmodifiableMapCursor} view of the mappings contained in this map.
   *
   * @since 19.0
   */
  UnmodifiableMapCursor<K, V> getEntries();

  /**
   * Returns the strategy used to compare keys.
   *
   * @since 23.0
   */
  default Equivalence getEquivalenceStrategy() {
    return Equivalence.DEFAULT;
  }
}
