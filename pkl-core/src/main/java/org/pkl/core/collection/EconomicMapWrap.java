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

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import org.pkl.core.util.Nullable;

/**
 * Wraps an existing {@link Map} as an {@link EconomicMap}.
 *
 * @since 21.1
 */
public class EconomicMapWrap<K, V> implements EconomicMap<K, V> {

  private final Map<K, V> map;

  /**
   * @since 21.1
   */
  public EconomicMapWrap(Map<K, V> map) {
    this.map = map;
  }

  /**
   * @since 21.1
   */
  @Override
  public V get(K key) {
    V result = map.get(key);
    return result;
  }

  /**
   * @since 21.1
   */
  @Override
  public V put(K key, V value) {
    V result = map.put(key, value);
    return result;
  }

  /**
   * @since 21.1
   */
  @Override
  public @Nullable V putIfAbsent(K key, @Nullable V value) {
    V result = map.putIfAbsent(key, value);
    return result;
  }

  /**
   * @since 21.1
   */
  @Override
  public int size() {
    int result = map.size();
    return result;
  }

  /**
   * @since 21.1
   */
  @Override
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  /**
   * @since 21.1
   */
  @Override
  public void clear() {
    map.clear();
  }

  /**
   * @since 21.1
   */
  @Override
  public V removeKey(K key) {
    V result = map.remove(key);
    return result;
  }

  /**
   * @since 21.1
   */
  @Override
  public Iterable<V> getValues() {
    return map.values();
  }

  /**
   * @since 21.1
   */
  @Override
  public Iterable<K> getKeys() {
    return map.keySet();
  }

  /**
   * @since 21.1
   */
  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * @since 21.1
   */
  @Override
  public MapCursor<K, V> getEntries() {
    Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
    return new MapCursor<>() {

      private Map.Entry<K, V> current;

      @Override
      public boolean advance() {
        boolean result = iterator.hasNext();
        if (result) {
          current = iterator.next();
        }

        return result;
      }

      @Override
      public K getKey() {
        return current.getKey();
      }

      @Override
      public V getValue() {
        return current.getValue();
      }

      @Override
      public void remove() {
        iterator.remove();
      }

      @Override
      public V setValue(V newValue) {
        return current.setValue(newValue);
      }
    };
  }

  /**
   * @since 21.1
   */
  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    map.replaceAll(function);
  }
}
