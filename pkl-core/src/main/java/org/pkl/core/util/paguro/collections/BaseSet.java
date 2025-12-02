/*
 * Copyright © 2017-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

/**
 * Adds copy-on-write, "fluent interface" methods to {@link UnmodSet}. Lowest common ancestor of
 * {@link MutSet}, {@link ImSet}, and {@link ImSortedSet}.
 */
public interface BaseSet<E> extends UnmodSet<E> {
  /**
   * Adds an element. If the element already exists in this set, the new value overwrites the old
   * one. If the new element is the same as an old element (based on the address of that item in
   * memory, not an equals test), the old set may be returned unchanged.
   *
   * @param e the element to add to this set
   * @return a new set with the element added (see note above about adding duplicate elements).
   */
  BaseSet<E> put(E e);

  /** Returns a new set containing all the items. */
  BaseSet<E> union(Iterable<? extends E> iter);

  //    {
  //        return concat(iter).toImSet();
  //    }

  /** Removes this key from the set */
  BaseSet<E> without(E key);
}
