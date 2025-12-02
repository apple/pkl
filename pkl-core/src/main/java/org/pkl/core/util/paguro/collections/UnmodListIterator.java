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

import java.util.ListIterator;

/** An unmodifiable ListIterator */
public interface UnmodListIterator<E> extends ListIterator<E>, UnmodSortedIterator<E> {

  // ========================================= Instance =========================================
  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default void add(E element) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  // boolean	hasNext()
  // boolean	hasPrevious()
  // E	next()
  // int	nextIndex()
  // E	previous()

  // I think this is the only valid implementation of this method. You can override it if you
  // think otherwise.
  /** {@inheritDoc} */
  @Override
  default int previousIndex() {
    return nextIndex() - 1;
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @SuppressWarnings("deprecation")
  @Override
  @Deprecated
  default void remove() {
    throw new UnsupportedOperationException("Modification attempted");
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default void set(E element) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  // Methods inherited from interface java.util.Iterator
  // forEachRemaining
}
