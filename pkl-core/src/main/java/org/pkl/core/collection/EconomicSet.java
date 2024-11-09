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
import java.util.Iterator;

/**
 * Memory efficient set data structure.
 *
 * @since 19.0
 */
public interface EconomicSet<E> extends UnmodifiableEconomicSet<E> {

  /**
   * Adds {@code element} to this set if it is not already present.
   *
   * @return {@code true} if this set did not already contain {@code element}.
   * @since 19.0
   */
  boolean add(E element);

  /**
   * Removes {@code element} from this set if it is present. This set will not contain {@code
   * element} once the call returns.
   *
   * @since 19.0
   */
  void remove(E element);

  /**
   * Removes all of the elements from this set. The set will be empty after this call returns.
   *
   * @since 19.0
   */
  void clear();

  /**
   * Adds all of the elements in {@code other} to this set if they're not already present.
   *
   * @since 19.0
   */
  default void addAll(EconomicSet<E> other) {
    addAll(other.iterator());
  }

  /**
   * Adds all of the elements in {@code values} to this set if they're not already present.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void addAll(Iterable<E> values) {
    addAll(values.iterator());
  }

  /**
   * Adds all of the elements enumerated by {@code iterator} to this set if they're not already
   * present.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void addAll(Iterator<E> iterator) {
    while (iterator.hasNext()) {
      add(iterator.next());
    }
  }

  /**
   * Removes from this set all of its elements that are contained in {@code other}.
   *
   * @since 19.0
   */
  default void removeAll(EconomicSet<E> other) {
    removeAll(other.iterator());
  }

  /**
   * Removes from this set all of its elements that are contained in {@code values}.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void removeAll(Iterable<E> values) {
    removeAll(values.iterator());
  }

  /**
   * Removes from this set all of its elements that are enumerated by {@code iterator}.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void removeAll(Iterator<E> iterator) {
    while (iterator.hasNext()) {
      remove(iterator.next());
    }
  }

  /**
   * Removes from this set all of its elements that are not contained in {@code other}.
   *
   * @since 19.0
   */
  @TruffleBoundary
  default void retainAll(EconomicSet<E> other) {
    Iterator<E> iterator = iterator();
    while (iterator.hasNext()) {
      E key = iterator.next();
      if (!other.contains(key)) {
        iterator.remove();
      }
    }
  }

  /**
   * Creates a new set guaranteeing insertion order when iterating over its elements with the
   * default {@link Equivalence#DEFAULT} comparison strategy.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <E> EconomicSet<E> create() {
    return EconomicSet.create(Equivalence.DEFAULT);
  }

  /**
   * Creates a new set guaranteeing insertion order when iterating over its elements.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <E> EconomicSet<E> create(Equivalence strategy) {
    return EconomicMapImpl.create(strategy, true);
  }

  /**
   * Creates a new set guaranteeing insertion order when iterating over its elements with the
   * default {@link Equivalence#DEFAULT} comparison strategy and inserts all elements of the
   * specified collection.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <E> EconomicSet<E> create(int initialCapacity) {
    return EconomicSet.create(Equivalence.DEFAULT, initialCapacity);
  }

  /**
   * Creates a new set guaranteeing insertion order when iterating over its elements with the
   * default {@link Equivalence#DEFAULT} comparison strategy and inserts all elements of the
   * specified collection.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <E> EconomicSet<E> create(UnmodifiableEconomicSet<E> c) {
    return EconomicSet.create(Equivalence.DEFAULT, c);
  }

  /**
   * Creates a new set guaranteeing insertion order when iterating over its elements and initializes
   * with the given capacity.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <E> EconomicSet<E> create(Equivalence strategy, int initialCapacity) {
    return EconomicMapImpl.create(strategy, initialCapacity, true);
  }

  /**
   * Creates a new set guaranteeing insertion order when iterating over its elements and inserts all
   * elements of the specified collection.
   *
   * @since 19.0
   */
  @TruffleBoundary
  static <E> EconomicSet<E> create(Equivalence strategy, UnmodifiableEconomicSet<E> c) {
    return EconomicMapImpl.create(strategy, c, true);
  }
}
