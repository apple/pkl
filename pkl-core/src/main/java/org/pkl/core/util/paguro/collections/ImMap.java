/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.collections;

import java.util.Map;

/** An immutable map with no guarantees about its ordering. */
public interface ImMap<K, V> extends BaseUnsortedMap<K, V> {

  /** Returns a new map with the given key/value added */
  @Override
  ImMap<K, V> assoc(K key, V val);

  /** Returns a new map with an immutable copy of the given entry added */
  @Override
  default ImMap<K, V> assoc(Map.Entry<K, V> entry) {
    return assoc(entry.getKey(), entry.getValue());
  }

  /** Returns a new map with the given key/value removed */
  @Override
  ImMap<K, V> without(K key);

  /**
   * Returns a view of the mappings contained in this map. The set should actually contain
   * UnmodMap.Entry items, but that return signature is illegal in Java, so you'll just have to
   * remember.
   */
  @Override
  default ImSet<Map.Entry<K, V>> entrySet() {
    return map(e -> (Map.Entry<K, V>) e).toImSet();
  }

  /** Returns an immutable view of the keys contained in this map. */
  @Override
  default ImSet<K> keySet() {
    return mutable().keySet().immutable();
  }

  /** Returns a mutable version of this mutable map. */
  MutMap<K, V> mutable();
}
