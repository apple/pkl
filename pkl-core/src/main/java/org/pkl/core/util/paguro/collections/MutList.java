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

import java.util.Collection;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.function.Fn0;
import org.pkl.core.util.paguro.oneOf.Option;

/**
 * A mutate-in-place interface using the same copy-on-write methods as {@link BaseList} and {@link
 * ImList} so that you can treat mutable and immutable lists the same. You could think of this as a
 * builder for an ImList, or just a stand-alone MutList that behaves similarly (extends {@link
 * org.pkl.core.util.paguro.xform.Transformable}). Being mutable, this is inherently NOT
 * thread-safe.
 */
public interface MutList<E> extends BaseList<E> {
  /**
   * Adds the item to the end of this list (mutating it in place).
   *
   * @param e the value to append
   */
  @Override
  MutList<E> append(E e);

  /**
   * If supplier returns Some, append the additional item to the end of this MutList (modifying it
   * in place). If None, just return this MutList unmodified.
   *
   * @param supplier return {@link org.pkl.core.util.paguro.oneOf.Option.Some} to append, {@link
   *     org.pkl.core.util.paguro.oneOf.None} for a no-op.
   */
  @Override
  default MutList<E> appendSome(Fn0<? extends Option<E>> supplier) {
    return supplier.apply().match((it) -> append(it), () -> this);
  }

  // TODO: Is this a good idea?  Kotlin does this...
  // I'm concerned that we cannot provide good implementations for all these methods.
  // Will implementing some be a benefit?
  // Or just a temptation to use other deprecated methods?
  // I'm going to try this with a few easy methods to see how it goes.
  // These are all technically "optional" operations anyway.
  /**
   * Ensures that this collection contains the specified element (optional operation). Returns true
   * if this collection changed as a result of the call.
   */
  @SuppressWarnings("deprecation")
  @Override
  default boolean add(E val) {
    append(val);
    return true;
  }

  /**
   * Appends all the elements in the specified collection to the end of this list, in the order that
   * they are returned by the specified collection's iterator.
   */
  @SuppressWarnings("deprecation")
  @Override
  default boolean addAll(Collection<? extends E> c) {
    concat(c);
    return true;
  }

  /** Returns an immutable version of this mutable list. */
  ImList<E> immutable();

  /** {@inheritDoc} */
  @Override
  default MutList<E> concat(@Nullable Iterable<? extends E> es) {
    if (es != null) {
      for (E e : es) {
        this.append(e);
      }
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  MutList<E> replace(int idx, E e);

  /** {@inheritDoc} */
  @Override
  default MutList<E> reverse() {
    MutList<E> ret = PersistentVector.emptyMutable();
    UnmodListIterator<E> iter = listIterator(size());
    while (iter.hasPrevious()) {
      ret.append(iter.previous());
    }
    return ret;
  }
}
