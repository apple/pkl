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

/** An immutable sorted set interface */
public interface ImSortedSet<E> extends BaseSet<E>, UnmodSortedSet<E> {
  /** {@inheritDoc} */
  @Override
  ImSortedSet<E> put(E e);

  /** {@inheritDoc} */
  @Override
  ImSortedSet<E> without(E key);

  /** {@inheritDoc} */
  @SuppressWarnings("unchecked")
  @Override
  default ImSortedSet<E> headSet(E toElement) {
    return (ImSortedSet<E>) UnmodSortedSet.super.headSet(toElement);
  }

  /** Iterates over contents in a guaranteed order. {@inheritDoc} */
  @Override
  UnmodSortedIterator<E> iterator();

  /**
   * Return the elements in this set from the start element (inclusive) to the end element
   * (exclusive)
   */
  @Override
  ImSortedSet<E> subSet(E fromElement, E toElement);

  /** {@inheritDoc} */
  // Note: there is no simple default implementation because subSet() is exclusive of the given
  // end element and there is no way to reliably find an element exactly larger than last().
  // Otherwise we could just return subSet(fromElement, last());

  @Override
  ImSortedSet<E> tailSet(E fromElement);

  @Override
  default ImSortedSet<E> union(Iterable<? extends E> iter) {
    ImSortedSet<E> ret = this;
    for (E e : iter) {
      if (!ret.contains(e)) {
        ret = ret.put(e);
      }
    }
    return ret;
  }
}
