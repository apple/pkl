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

/** Interface for mutable (hash) set builder. */
public interface MutSet<E> extends BaseSet<E> {

  /** Returns an immutable version of this immutable set. */
  ImSet<E> immutable();

  /** {@inheritDoc} */
  @Override
  MutSet<E> put(E val);

  /** {@inheritDoc} */
  @Override
  default MutSet<E> union(Iterable<? extends E> iter) {
    if (iter != null) {
      for (E item : iter) {
        put(item);
      }
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  MutSet<E> without(E key);
}
