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

import java.io.Serializable;
import java.util.*;

/**
 * An unmodifiable Iterable, with guaranteed order. The signature of this interface is nearly
 * identical to UnmodIterable, but implementing this interface represents a contract to always
 * return iterators that have the same ordering.
 */
public interface UnmodSortedIterable<T> extends UnmodIterable<T> {
  // ========================================== Static ==========================================

  /** This is correct, but O(n). This only works with an ordered iterable. */
  @SuppressWarnings("rawtypes")
  static boolean equal(UnmodSortedIterable a, UnmodSortedIterable b) {
    // Cheapest operation first...
    if (a == b) {
      return true;
    }

    if ((a == null) || (b == null)) {
      return false;
    }
    Iterator as = a.iterator();
    Iterator bs = b.iterator();
    while (as.hasNext() && bs.hasNext()) {
      if (!Objects.equals(as.next(), bs.next())) {
        return false;
      }
    }
    return !as.hasNext() && !bs.hasNext();
  }

  //    static <E> UnmodSortedIterable<E> castFromSortedSet(SortedSet<E> ss) {
  //        return () -> new UnmodSortedIterator<E>() {
  //            Iterator<E> iter = ss.iterator();
  //            @Override public boolean hasNext() { return iter.hasNext(); }
  //            @Override public E next() { return iter.next(); }
  //        };
  //    }

  static <E> UnmodSortedIterable<E> castFromSortedSet(final SortedSet<E> s) {
    class Implementation<S> implements UnmodSortedIterable<S>, Serializable {
      // For serializable.  Make sure to change whenever internal data format changes.
      private static final long serialVersionUID = 20160903174100L;

      private final SortedSet<S> ss;

      private Implementation(SortedSet<S> s) {
        ss = s;
      }

      /** Returns items in a guaranteed order. */
      @Override
      public UnmodSortedIterator<S> iterator() {
        return new UnmodSortedIterator.Wrapper<>(ss.iterator());
      }
    }
    return new Implementation<>(s);
  }

  //    static <E> UnmodSortedIterable<E> castFromList(List<E> ss) {
  //        return () -> new UnmodSortedIterator<E>() {
  //            Iterator<E> iter = ss.iterator();
  //            @Override public boolean hasNext() { return iter.hasNext(); }
  //            @Override public E next() { return iter.next(); }
  //        };
  //    }

  static <E> UnmodSortedIterable<E> castFromList(List<E> s) {
    class Implementation<S> implements UnmodSortedIterable<S>, Serializable {
      // For serializable.  Make sure to change whenever internal data format changes.
      private static final long serialVersionUID = 20160903174100L;

      private final List<S> ss;

      private Implementation(List<S> s) {
        ss = s;
      }

      /** Returns items in a guaranteed order. */
      @Override
      public UnmodSortedIterator<S> iterator() {
        return new UnmodSortedIterator.Wrapper<>(ss.iterator());
      }
    }
    return new Implementation<>(s);
  }

  //    static <U> UnmodSortedIterable<U> castFromTypedList(List<U> ss) {
  //        return () -> new UnmodSortedIterator<U>() {
  //            Iterator<U> iter = ss.iterator();
  //            @Override public boolean hasNext() { return iter.hasNext(); }
  //            @Override public U next() { return iter.next(); }
  //        };
  //    }
  //
  //    static <U> UnmodSortedIterable<U> castFromCollection(Collection<U> ss) {
  //        return () -> new UnmodSortedIterator<U>() {
  //            Iterator<U> iter = ss.iterator();
  //            @Override public boolean hasNext() { return iter.hasNext(); }
  //            @Override public U next() { return iter.next(); }
  //        };
  //    }

  //    static <K,V> UnmodSortedIterable<Map.Entry<K,V>> castFromSortedMap(SortedMap<K,V> sm) {
  //        return () -> new UnmodSortedIterator<Map.Entry<K,V>>() {
  //            Iterator<Map.Entry<K,V>> iter = sm.entrySet().iterator();
  //            @Override public boolean hasNext() { return iter.hasNext(); }
  //            @Override public Map.Entry<K,V> next() { return iter.next(); }
  //        };
  //    }

  static <K, V> UnmodSortedIterable<UnmodMap.UnEntry<K, V>> castFromSortedMap(SortedMap<K, V> sm) {
    if (sm instanceof UnmodSortedMap) {
      return (UnmodSortedMap<K, V>) sm;
    }

    class Implementation<K1, V1>
        implements UnmodSortedIterable<UnmodMap.UnEntry<K1, V1>>, Serializable {
      // For serializable.  Make sure to change whenever internal data format changes.
      private static final long serialVersionUID = 20160903174100L;

      private final SortedMap<K1, V1> m;

      private Implementation(SortedMap<K1, V1> s) {
        m = s;
      }

      /** Returns items in a guaranteed order. */
      @Override
      public UnmodSortedIterator<UnmodMap.UnEntry<K1, V1>> iterator() {
        return new UnmodMap.UnEntry.EntryToUnEntrySortedIter<>(m.entrySet().iterator());
      }
    }
    return new Implementation<>(sm);
  }

  // ========================================= Instance =========================================
  /** Returns items in a guaranteed order. */
  @Override
  UnmodSortedIterator<T> iterator();
}
