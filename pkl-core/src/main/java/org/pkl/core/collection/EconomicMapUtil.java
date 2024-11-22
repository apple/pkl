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
import java.util.Comparator;
import java.util.Objects;
import org.pkl.core.util.Nullable;

/**
 * Utility methods for the {@link EconomicMap}.
 *
 * @since 23.0
 */
public final class EconomicMapUtil {
  /**
   * @since 23.0
   */
  private EconomicMapUtil() {}

  /**
   * Compares maps for equality. The maps are equal iff they share the same {@link Equivalence
   * equivalence strategy}, their keys are equal with respect to the strategy and the values are
   * equal as determined by the {@link Objects#equals(Object, Object) equals} method.
   *
   * @param lhs the first map to be compared
   * @param rhs the second map to be compared
   * @return {@code true} iff the maps are equal
   * @since 23.0
   */
  @TruffleBoundary
  public static <K, V> boolean equals(
      @Nullable UnmodifiableEconomicMap<K, V> lhs, @Nullable UnmodifiableEconomicMap<K, V> rhs) {
    if (lhs == rhs) {
      return true;
    }
    if (lhs == null
        || rhs == null
        || lhs.size() != rhs.size()
        || !Objects.equals(lhs.getEquivalenceStrategy(), rhs.getEquivalenceStrategy())) {
      return false;
    }
    UnmodifiableMapCursor<K, V> cursor = rhs.getEntries();
    while (cursor.advance()) {
      if (!lhs.containsKey(cursor.getKey())
          || !Objects.equals(lhs.get(cursor.getKey()), cursor.getValue())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes an order-independent hash code for an {@link EconomicMap}.
   *
   * @param map the input map or {@code null}
   * @return the hash code of the map
   * @since 23.0
   */
  @TruffleBoundary
  public static <K, V> int hashCode(@Nullable UnmodifiableEconomicMap<K, V> map) {
    if (map == null) {
      return -1;
    }
    int keyHash = 0;
    int valueHash = 0;
    UnmodifiableMapCursor<K, V> cursor = map.getEntries();
    while (cursor.advance()) {
      keyHash ^= cursor.getKey().hashCode();
      if (cursor.getValue() != null) {
        valueHash ^= cursor.getValue().hashCode();
      }
    }
    return keyHash + 31 * valueHash;
  }

  /**
   * Returns an {@link EconomicSet} of the keys contained in a map.
   *
   * @param map the input map
   * @return an {@link EconomicSet} of the keys contained in a map
   * @since 23.0
   */
  @TruffleBoundary
  public static <K, V> EconomicSet<K> keySet(EconomicMap<K, V> map) {
    EconomicSet<K> set = EconomicSet.create(map.size());
    for (K key : map.getKeys()) {
      set.add(key);
    }
    return set;
  }

  /**
   * Creates a lexicographical map comparator using the provided key and value comparators. The maps
   * are treated as if they were lists with the structure {@code {key1, value1, key2, value2, ...}}.
   * The comparison starts by comparing their {@code key1} and if they are equal, it goes on to
   * compare {@code value1}, then {@code key2}, {@code value2} and so on. If one of the maps is
   * shorter, the comparators are called with {@code null} values in place of the missing
   * keys/values.
   *
   * @param keyComparator a comparator to compare keys
   * @param valueComparator a comparator to compare values
   * @return a lexicographical map comparator
   * @since 23.0
   */
  @TruffleBoundary
  public static <K, V> Comparator<UnmodifiableEconomicMap<K, V>> lexicographicalComparator(
      Comparator<K> keyComparator, Comparator<V> valueComparator) {
    return new Comparator<>() {
      @Override
      public int compare(UnmodifiableEconomicMap<K, V> map1, UnmodifiableEconomicMap<K, V> map2) {
        if (map2.size() > map1.size()) {
          return -compare(map2, map1);
        }
        assert map1.size() >= map2.size();
        UnmodifiableMapCursor<K, V> cursor1 = map1.getEntries();
        UnmodifiableMapCursor<K, V> cursor2 = map2.getEntries();
        while (cursor1.advance()) {
          K key2 = null;
          V value2 = null;
          if (cursor2.advance()) {
            key2 = cursor2.getKey();
            value2 = cursor2.getValue();
          }
          int order = keyComparator.compare(cursor1.getKey(), key2);
          if (order != 0) {
            return order;
          }
          order = valueComparator.compare(cursor1.getValue(), value2);
          if (order != 0) {
            return order;
          }
        }
        return 0;
      }
    };
  }
}
