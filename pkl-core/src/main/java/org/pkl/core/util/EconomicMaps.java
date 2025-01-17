/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;
import java.util.function.BiFunction;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;

/**
 * Puts {@link TruffleBoundary}s on {@link EconomicMap} methods and provides some added
 * functionality.
 */
public final class EconomicMaps {
  private EconomicMaps() {}

  public static <K, V> UnmodifiableEconomicMap<K, V> emptyMap() {
    return EconomicMap.emptyMap();
  }

  @TruffleBoundary
  public static <K, V> EconomicMap<K, V> create() {
    return EconomicMap.create();
  }

  @TruffleBoundary
  public static <K, V> EconomicMap<K, V> create(int initialSize) {
    // for economic maps, size == capacity
    return EconomicMap.create(initialSize);
  }

  @TruffleBoundary
  public static <K, V> EconomicMap<K, V> of(K key, V value) {
    var result = EconomicMap.<K, V>create(1);
    result.put(key, value);
    return result;
  }

  @TruffleBoundary
  public static <K, V> EconomicMap<K, V> of(K key1, V value1, K key2, V value2) {
    var result = EconomicMap.<K, V>create(2);
    result.put(key1, value1);
    result.put(key2, value2);
    return result;
  }

  @TruffleBoundary
  public static <K, V> EconomicMap<K, V> of(K key1, V value1, K key2, V value2, K key3, V value3) {
    var result = EconomicMap.<K, V>create(3);
    result.put(key1, value1);
    result.put(key2, value2);
    result.put(key3, value3);
    return result;
  }

  @TruffleBoundary
  public static <K, V> EconomicMap<K, V> of(
      K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4) {
    var result = EconomicMap.<K, V>create(4);
    result.put(key1, value1);
    result.put(key2, value2);
    result.put(key3, value3);
    result.put(key4, value4);
    return result;
  }

  @TruffleBoundary
  public static <K, V> @Nullable V get(UnmodifiableEconomicMap<K, V> self, K key) {
    return self.get(key);
  }

  @TruffleBoundary
  public static <K, V> @Nullable V get(UnmodifiableEconomicMap<K, V> self, K key, V defaultValue) {
    return self.get(key, defaultValue);
  }

  @TruffleBoundary
  public static <K, V> boolean containsKey(UnmodifiableEconomicMap<K, V> self, K key) {
    return self.containsKey(key);
  }

  @TruffleBoundary
  public static <K, V> int size(UnmodifiableEconomicMap<K, V> self) {
    return self.size();
  }

  @TruffleBoundary
  public static <K, V> boolean isEmpty(UnmodifiableEconomicMap<K, V> self) {
    return self.isEmpty();
  }

  @TruffleBoundary
  public static <K, V> Iterable<V> getValues(UnmodifiableEconomicMap<K, V> self) {
    return self.getValues();
  }

  @TruffleBoundary
  public static <K, V> Iterable<K> getKeys(UnmodifiableEconomicMap<K, V> self) {
    return self.getKeys();
  }

  @TruffleBoundary
  public static <K, V> UnmodifiableMapCursor<K, V> getEntries(UnmodifiableEconomicMap<K, V> self) {
    return self.getEntries();
  }

  @TruffleBoundary
  public static <K, V> @Nullable V put(EconomicMap<K, V> self, K key, @Nullable V value) {
    return self.put(key, value);
  }

  @TruffleBoundary
  public static <K, V> void putAll(EconomicMap<K, V> self, EconomicMap<K, V> other) {
    self.putAll(other);
  }

  @TruffleBoundary
  public static <K, V> void putAll(
      EconomicMap<K, V> self, UnmodifiableEconomicMap<? extends K, ? extends V> other) {

    self.putAll(other);
  }

  @TruffleBoundary
  public static <K, V> void clear(EconomicMap<K, V> self) {
    self.clear();
  }

  @TruffleBoundary
  public static <K, V> V removeKey(EconomicMap<K, V> self, K key) {
    return self.removeKey(key);
  }

  @TruffleBoundary
  public static <K, V> MapCursor<K, V> getEntries(EconomicMap<K, V> self) {
    return self.getEntries();
  }

  @TruffleBoundary
  public static <K, V> void replaceAll(
      EconomicMap<K, V> self, BiFunction<? super K, ? super V, ? extends V> function) {
    self.replaceAll(function);
  }

  // inspired by java.util.AbstractMap#equals
  @TruffleBoundary
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static boolean equals(UnmodifiableEconomicMap map1, UnmodifiableEconomicMap map2) {
    if (map1 == map2) return true;

    if (map1.size() != map2.size()) return false;

    for (var cursor = map1.getEntries(); cursor.advance(); ) {
      var key1 = cursor.getKey();
      var value1 = cursor.getValue();
      var value2 = map2.get(key1);
      if (value2 == null) {
        if (value1 != null || !map2.containsKey(key1)) return false;
      } else {
        if (!value2.equals(value1)) return false;
      }
    }

    return true;
  }

  // inspired by java.util.AbstractMap#hashCode
  @TruffleBoundary
  public static int hashCode(UnmodifiableEconomicMap<?, ?> map) {
    var result = 0;
    for (var cursor = map.getEntries(); cursor.advance(); ) {
      result += (cursor.getKey().hashCode() ^ Objects.hashCode(cursor.getValue()));
    }
    return result;
  }
}
