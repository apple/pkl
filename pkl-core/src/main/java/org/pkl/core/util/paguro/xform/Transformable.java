/*
 * Copyright © 2014-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.xform;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.collections.ImList;
import org.pkl.core.util.paguro.collections.ImMap;
import org.pkl.core.util.paguro.collections.ImSet;
import org.pkl.core.util.paguro.collections.ImSortedMap;
import org.pkl.core.util.paguro.collections.ImSortedSet;
import org.pkl.core.util.paguro.collections.MutList;
import org.pkl.core.util.paguro.collections.MutMap;
import org.pkl.core.util.paguro.collections.MutSet;
import org.pkl.core.util.paguro.collections.PersistentHashMap;
import org.pkl.core.util.paguro.collections.PersistentHashSet;
import org.pkl.core.util.paguro.collections.PersistentTreeMap;
import org.pkl.core.util.paguro.collections.PersistentTreeSet;
import org.pkl.core.util.paguro.collections.PersistentVector;
import org.pkl.core.util.paguro.collections.RrbTree;
import org.pkl.core.util.paguro.collections.RrbTree.ImRrbt;
import org.pkl.core.util.paguro.collections.RrbTree.MutRrbt;
import org.pkl.core.util.paguro.function.Fn1;
import org.pkl.core.util.paguro.function.Fn2;
import org.pkl.core.util.paguro.oneOf.Option;
import org.pkl.core.util.paguro.oneOf.Or;

/**
 * Represents transformations to be carried out on a collection. The to___() methods were formerly
 * defined by a separate Realizable interface that extended this one, but that never proved useful
 * and it complicated things slightly, so I just combined Realizable into Transformable.
 *
 * @param <T> The input type to the current stage of transformation. Some transforms produce a
 *     different output type.
 */
public interface Transformable<T> {
  /**
   * Return only the items for which the given predicate returns true.
   *
   * @return a Transformable of only the filtered items.
   * @param predicate a function that returns true for items to keep, false for items to drop
   */
  default boolean any(Fn1<? super T, Boolean> predicate) {
    return filter(predicate).head().isSome();
  }

  /**
   * Add items to the end of this Transformable (precat() adds to the beginning)
   *
   * @param list the items to add
   * @return a new Transformable with the items added.
   */
  Transformable<T> concat(@Nullable Iterable<? extends T> list);

  /**
   * Ignore the first n items and return only those that come after. The Xform API is designed to
   * allow dropping items with a single pointer addition if the data source is a List, but that
   * feature is not implemented yet. For best results, drop as early in your chain of functions as
   * practical.
   *
   * @param numItems the number of items at the beginning of this Transformable to ignore
   * @return a Transformable with the specified number of items ignored.
   */
  Transformable<T> drop(long numItems);

  /**
   * Ignore leading items until the given predicate returns false.
   *
   * @param predicate the predicate (test function)
   * @return a Transformable with the matching leading items ignored.
   */
  Transformable<T> dropWhile(Fn1<? super T, Boolean> predicate);

  /**
   * Return only the items for which the given predicate returns true.
   *
   * @return a Transformable of only the filtered items.
   * @param predicate a function that returns true for items to keep, false for items to drop
   */
  Transformable<T> filter(Fn1<? super T, Boolean> predicate);

  /**
   * Return only the items which are non-null
   *
   * @return a Transformable of only the non-null items.
   */
  Transformable<T> whereNonNull();

  /**
   * Returns the first item produced by this transform. If the source is unordered, there is no
   * guarantee about which item will make it through the transform first.
   *
   * <p>This was going to be called first(), but that conflicts with SortedSet.first() which is used
   * by SortedMap.entrySet(). The contract for that is to return the first item or null, so that if
   * it returns null, you don't know whether that means the set is empty or that the first item is
   * null. I guess your comparator would have to understand nulls, but it could happen.
   *
   * @return an eagerly evaluated result which is a single item.
   */
  default Option<T> head() {
    return foldUntil(Option.none(), (accum, item) -> Option.someOrNullNoneOf(item), Fn2.first())
        .match(g -> Option.none(), b -> b);
  }

  /**
   * Transform each item into zero or more new items using the given function. One of the two
   * higher-order functions that can produce more output items than input items. fold is the other,
   * but flatMap is lazy while fold is eager.
   *
   * @return a lazily evaluated collection which is expected to be larger than the input collection.
   *     For a collection that's the same size, map() is more efficient. If the expected return is
   *     smaller, use filter followed by map if possible, or vice versa if not.
   * @param f yields a Transformable of 0 or more results for each input item.
   */
  <U> Transformable<U> flatMap(Fn1<? super T, Iterable<U>> f);

  /**
   * Apply the function to each item, accumulating the result in u. Other transformations can be
   * implemented with just this one function, but it is clearer (and allows lazy evaluation) to use
   * the most specific transformations that meet your needs. Still, sometimes you need the
   * flexibility fold provides. This is techincally a fold-left because it processes items in order*
   * unless those items are a linked list.
   *
   * <p>Fold is one of the two higher-order functions that can produce more output items than input
   * items (when u is a collection). FlatMap is the other, but fold is eager while flatMap is lazy.
   * Fold can also produce a single (scalar) value. In that form, it is often called reduce().
   *
   * @param accum the accumulator and starting value. This will be passed to the function on the
   *     first iteration to be combined with the first member of the underlying data source. For
   *     some operations you'll need to pass an identity, e.g. for a sum, pass 0, for a product,
   *     pass 1 as this parameter.
   * @param reducer combines each value in the list with the result so far. The initial result is u.
   * @return an eagerly evaluated result which could be a single value like a sum, or a collection.
   */
  <U> U fold(U accum, Fn2<? super U, ? super T, U> reducer);

  /**
   * Normally you want to terminate by doing a take(), drop(), or takeWhile() before you get to the
   * fold, but if you need to terminate based on the complete result so far, you can provide your
   * own termination condition to this version of fold().
   *
   * <p>This function can do anything a loop can do. One use case is to accumulate a map and stop if
   * it finds a duplicate key, before overwriting that element in the map. It could then return the
   * map so far, an error, or whatever you like.
   *
   * @param accum the accumulator and starting value. This will be passed to the function on the
   *     first iteration to be combined with the first member of the underlying data source. For
   *     some operations you'll need to pass an identity, e.g. for a sum, pass 0, for a product,
   *     pass 1 as this parameter.
   * @param terminator return null to continue processing. Return non-null to terminate the
   *     foldUntil and return Or.bad of this value. This function is called at the beginning of each
   *     "loop", thus it's first called with the original value of accum and the first item to
   *     process. Returning non-null immediately will prevent the reducer from ever being called.
   * @param reducer combines each value in the list with the result so far. The initial result is u.
   * @return an {@link Or} where the {@link Or#good()} is an eagerly evaluated result and {@link
   *     Or#bad()} is whatever terminateWhen returned.
   */
  <G, B> Or<G, B> foldUntil(
      G accum,
      @Nullable Fn2<? super G, ? super T, B> terminator,
      Fn2<? super G, ? super T, G> reducer);

  /**
   * Transform each item into exactly one new item using the given function.
   *
   * @param func a function that returns a new value for any value in the input
   * @return a Transformable of the same size as the input (may contain duplicates) containing the
   *     return values of the given function in the same order as the input values.
   */
  <U> Transformable<U> map(Fn1<? super T, ? extends U> func);

  /**
   * Add items to the beginning of this Transformable ("precat" is a PREpending version of conCAT).
   *
   * @param list the items to add
   * @return a new Transformable with the items added.
   */
  Transformable<T> precat(@Nullable Iterable<? extends T> list);

  /**
   * Return only the first n items.
   *
   * @param numItems the maximum number of items in the returned view.
   * @return a Transformable containing no more than the specified number of items.
   */
  Transformable<T> take(long numItems);

  /**
   * Return items from the beginning until the given predicate returns false.
   *
   * @param predicate the predicate (test function)
   * @return a lazy transformable containing the longest un-interrupted run of items, from the
   *     beginning of the transformable, that satisfy the given predicate. This could be 0 items to
   *     the entire transformable.
   */
  Transformable<T> takeWhile(Fn1<? super T, Boolean> predicate);

  //    /**
  //     Returns an Object[] for backward compatibility
  //     */
  //    @SuppressWarnings("unchecked")
  //    default Object[] toArray() {
  //        return toMutList().toArray();
  ////        return al.toArray((T[]) new Object[al.size()]);
  //    }

  /** Realize a thread-safe immutable list to access items quickly O(log32 n) by index. */
  default ImList<T> toImList() {
    return toMutList().immutable();
  }

  /** Realize a thread-safe immutable RRB-Tree to access items quickly O(log32 n) by index. */
  default ImRrbt<T> toImRrbt() {
    return toMutRrbt().immutable();
  }

  /**
   * Realize an unordered immutable hash map to very quickly O(1) look up values by key, but don't
   * care about ordering. In the case of a duplicate key, later values from this transform will
   * overwrite the earlier ones. The resulting map can contain zero or one null key and any number
   * of null values.
   *
   * @param f1 Maps each item in this collection to a key/value pair. If the collection is composed
   *     of Map.Entries (or Tuple2's), you can pass Fn1.identity() here. This function must never
   *     return null (filter out nulls in an earlier step of the transform if necessary).
   * @return An immutable map
   */
  default <K, V> ImMap<K, V> toImMap(Fn1<? super T, Entry<K, V>> f1) {
    return toMutMap(f1).immutable();
  }

  /**
   * Realize an unordered immutable hash set to remove duplicates or very quickly O(1) tell whether
   * the set contains various items, but don't care about ordering. If the input contains duplicate
   * elements, later values overwrite earlier ones.
   *
   * @return An immutable set (with duplicates removed)
   */
  default ImSet<T> toImSet() {
    return toMutSet().immutable();
  }

  /**
   * Realize an immutable, ordered (tree) map to quickly O(log2 n) look up values by key, but still
   * retrieve entries in key order. The keys are sorted according to the comparator you provide.
   *
   * @param comp A comparator (on the keys) that defines the sort order inside the new map. This
   *     becomes a permanent part of the map and all sub-maps or appended maps derived from it. If
   *     you want to use a null key, make sure the comparator treats nulls correctly in all
   *     circumstances!
   * @param f1 Maps each item in this collection to a key/value pair. If the collection is composed
   *     of Map.Entries, you can pass Fn1.identity() here. In the case of a duplicate key, later
   *     values in transform overwrite the earlier ones. The resulting map can contain zero or one
   *     null key (if your comparator knows how to sort nulls) and any number of null values. This
   *     function must never return null (filter out nulls in an earlier step of the transform if
   *     necessary).
   * @return a new PersistentTreeMap of the specified comparator and the given key/value pairs
   */
  default <K, V> ImSortedMap<K, V> toImSortedMap(
      Comparator<? super K> comp, Fn1<? super T, Entry<K, V>> f1) {
    return fold(
        (ImSortedMap<K, V>) PersistentTreeMap.<K, V>empty(comp), (ts, t) -> ts.assoc(f1.apply(t)));
  }

  /**
   * Realize an immutable, sorted (tree) set to quickly O(log2 n) test it contains items, but still
   * retrieve entries in order.
   *
   * @param comparator Determines the ordering. If T implements Comparable, you can pass
   *     Fn2.defaultComparator() here.
   * @return An immutable set (with duplicates removed). Null elements are not allowed.
   */
  default ImSortedSet<T> toImSortedSet(Comparator<? super T> comparator) {
    return fold(PersistentTreeSet.ofComp(comparator), PersistentTreeSet::put);
  }

  /** Realize a mutable list. Use toImList unless you need to modify the list in-place. */
  default MutList<T> toMutList() {
    return fold(PersistentVector.emptyMutable(), MutList<T>::append);
  }

  /** Realize a mutable RRB-Tree. Use toImRrbt unless you need to modify the list in-place. */
  default MutRrbt<T> toMutRrbt() {
    return fold(RrbTree.emptyMutable(), MutRrbt<T>::append);
  }

  /**
   * Realize a mutable hash map. Use toImMap() unless you need to modify the map in-place.
   *
   * @param f1 Maps keys to values. This function must never return null (filter out nulls in an
   *     earlier step of the transform if necessary).
   * @return A map with the keys from the given set, mapped to values using the given function.
   */
  default <K, V> MutMap<K, V> toMutMap(Fn1<? super T, Entry<K, V>> f1) {
    return fold(PersistentHashMap.emptyMutable(), (MutMap<K, V> ts, T t) -> ts.assoc(f1.apply(t)));
  }

  /**
   * Realize a mutable tree map. Use toImSortedMap() unless you need to modify the map in-place.
   *
   * @param comp A comparator (on the keys) that defines the sort order inside the new map. Null
   *     keys are probably not allowed.
   * @param f1 Maps keys to values. This should never return null.
   * @return A map with the keys from the given set, mapped to values using the given function.
   */
  default <K, V> SortedMap<K, V> toMutSortedMap(
      @Nullable Comparator<? super K> comp, Fn1<? super T, Entry<K, V>> f1) {
    return fold(
        new TreeMap<>(comp),
        (ts, t) -> {
          Entry<K, V> entry = f1.apply(t);
          ts.put(entry.getKey(), entry.getValue());
          return ts;
        });
  }

  /**
   * Realize a mutable hash set. Use toImSet() unless you need to modify the set in-place.
   *
   * @return A mutable set (with duplicates removed)
   */
  default MutSet<T> toMutSet() {
    return fold(PersistentHashSet.emptyMutable(), PersistentHashSet.MutHashSet::put);
  }

  /**
   * Returns a mutable tree set. Use toImSortedSet unless you need to modify the set in-place.
   *
   * @param comparator Determines the ordering. If T implements Comparable, you can pass
   *     Fn2.defaultComparator() here.
   * @return A mutable sorted set
   */
  default SortedSet<T> toMutSortedSet(@Nullable Comparator<? super T> comparator) {
    return fold(
        new TreeSet<>(comparator),
        (ts, t) -> {
          ts.add(t);
          return ts;
        });
  }
}
