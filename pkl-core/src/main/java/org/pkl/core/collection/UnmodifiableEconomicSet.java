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

/**
 * Unmodifiable memory efficient set data structure.
 *
 * @since 19.0
 */
public interface UnmodifiableEconomicSet<E> extends Iterable<E> {

  /**
   * Returns {@code true} if this set contains a mapping for the {@code element}.
   *
   * @since 19.0
   */
  boolean contains(E element);

  /**
   * Returns the number of elements in this set.
   *
   * @since 19.0
   */
  int size();

  /**
   * Returns {@code true} if this set contains no elements.
   *
   * @since 19.0
   */
  boolean isEmpty();

  /**
   * Stores all of the elements in this set into {@code target}. An {@link
   * UnsupportedOperationException} will be thrown if the length of {@code target} does not match
   * the size of this set.
   *
   * @return an array containing all the elements in this set.
   * @throws UnsupportedOperationException if the length of {@code target} does not equal the size
   *     of this set.
   * @since 19.0
   */
  default E[] toArray(E[] target) {
    if (target.length != size()) {
      throw new UnsupportedOperationException(
          "Length of target array must equal the size of the set.");
    }

    int index = 0;
    for (E element : this) {
      target[index++] = element;
    }

    return target;
  }
}
