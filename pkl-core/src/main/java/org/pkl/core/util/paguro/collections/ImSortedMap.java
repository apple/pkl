/*
 * Copyright © 2015-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.pkl.core.util.paguro.oneOf.Option;

/** An immutable sorted map. */
public interface ImSortedMap<K, V> extends UnmodSortedMap<K, V>, BaseMap<K, V> {

  Option<UnmodMap.UnEntry<K, V>> entry(K key);

  //    /**
  //     Returns a view of the mappings contained in this map.  The set should actually contain
  //     UnmodMap.Entry items, but that return signature is illegal in Java, so you'll just have to
  //     remember.
  //     */
  //    @Override ImSortedSet<Entry<K,V>> entrySet();

  // public  K	firstKey()

  @SuppressWarnings("unchecked")
  @Override
  default boolean containsKey(Object key) {
    return entry((K) key).isSome();
  }

  /** {@inheritDoc} */
  @Override
  ImSortedSet<Entry<K, V>> entrySet();

  @SuppressWarnings("unchecked")
  @Override
  default V get(Object key) {
    Option<UnEntry<K, V>> entry = entry((K) key);
    return entry.isSome() ? entry.get().getValue() : null;
  }

  default V getOrElse(K key, V notFound) {
    Option<UnEntry<K, V>> entry = entry(key);
    return entry.isSome() ? entry.get().getValue() : notFound;
  }

  /** Return the elements in this map up (but excluding) to the given element */
  @Override
  default ImSortedMap<K, V> headMap(K toKey) {
    return subMap(firstKey(), toKey);
  }

  /**
   * Returns an iterator over the UnEntries of this map in order.
   *
   * @return an Iterator.
   */
  @Override
  UnmodSortedIterator<UnEntry<K, V>> iterator();

  /** Returns a view of the keys contained in this map. */
  @Override
  default ImSortedSet<K> keySet() {
    return PersistentTreeSet.ofMap(this);
  }

  // public  K	lastKey()

  /**
   * Return the elements in this map from the start element (inclusive) to the end element
   * (exclusive)
   */
  @Override
  ImSortedMap<K, V> subMap(K fromKey, K toKey);

  /** Return the elements in this from the given element to the end */
  @Override
  ImSortedMap<K, V> tailMap(K fromKey);

  //    /** {@inheritDoc} */
  //    @Override default UnmodSortedCollection<V> values() {
  //        // We need values, but still ordered by their keys.
  //        final ImSortedMap<K,V> parent = this;
  //        return new UnmodSortedCollection<V>() {
  //            @Override public UnmodSortedIterator<V> iterator() {
  //                return new UnmodListIterator<V>() {}
  //                return UnmodSortedIterable.castFromTypedList(parent.entrySet()
  //                                                                   .map(e -> e.getValue())
  //                                                                   .toMutList())
  //                                          .iterator();
  //            }
  //            @Override public int size() { return parent.size(); }
  //
  //            @SuppressWarnings("SuspiciousMethodCalls")
  //            @Override public boolean contains(Object o) { return parent.containsValue(o); }
  //
  //            @Override public int hashCode() { return UnmodIterable.hashCode(this); }
  //
  //            @Override public boolean equals(Object o) {
  //                if (this == o) { return true; }
  //                if ( !(o instanceof Collection) ) { return false; }
  //                Collection that = (Collection) o;
  //                if (this.size() != that.size()) { return false; }
  //                return containsAll(that);
  //            }
  //
  //            @Override public String toString() {
  //                return UnmodIterable.toString("ImMapOrd.values.UnCollectionOrd", this);
  //            }
  //        };
  //    }

  /**
   * Returns a new map with the given key/value added. If the key exists in this map, the new value
   * overwrites the old one. If the key exists with the same value (based on the address of that
   * value in memory, not an equals test), the old map is returned unchanged.
   *
   * @param key the key used to look up the value. In the case of a duplicate key, later values
   *     overwrite the earlier ones. The resulting map can contain zero or one null key (if your
   *     comparator knows how to sort nulls) and any number of null values.
   * @param val the value to store in this key.
   * @return a new PersistentTreeMap of the specified comparator and the given key/value pairs
   */
  ImSortedMap<K, V> assoc(K key, V val);

  /** Returns a new map with an immutable copy of the given entry added */
  default ImSortedMap<K, V> assoc(Map.Entry<K, V> entry) {
    return assoc(entry.getKey(), entry.getValue());
  }

  /** Returns a new map with the given key/value removed */
  ImSortedMap<K, V> without(K key);
}
