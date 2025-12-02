/*
 * Copyright © 2016-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

/** Interface for mutable (hash) map builder. */
public interface MutMap<K, V> extends BaseUnsortedMap<K, V> {
  /** {@inheritDoc} */
  @Override
  MutMap<K, V> assoc(K key, V val);

  /** {@inheritDoc} */
  @Override
  default MutMap<K, V> assoc(Map.Entry<K, V> entry) {
    return assoc(entry.getKey(), entry.getValue());
  }

  @Override
  default MutSet<Entry<K, V>> entrySet() {
    return map(e -> (Map.Entry<K, V>) e).toMutSet();
  }

  /** Returns a mutable view of the keys contained in this map. */
  @Override
  default MutSet<K> keySet() {
    return map(e -> ((Map.Entry<K, V>) e).getKey()).toMutSet();
  }

  /** Returns an immutable version of this mutable map. */
  ImMap<K, V> immutable();

  /** {@inheritDoc} */
  @Override
  MutMap<K, V> without(K key);
}
