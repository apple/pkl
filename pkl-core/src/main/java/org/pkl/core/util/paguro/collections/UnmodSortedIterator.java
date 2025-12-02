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

import java.util.Iterator;

/** This represents an iterator with a guaranteed ordering. */
public interface UnmodSortedIterator<E> extends UnmodIterator<E> {
  class Wrapper<E> implements UnmodSortedIterator<E> {
    //        , Serializable {
    //        // For serializable.  Make sure to change whenever internal data format changes.
    //        private static final long serialVersionUID = 20160903174100L;

    private final Iterator<E> iter;

    Wrapper(Iterator<E> i) {
      iter = i;
    }

    @Override
    public boolean hasNext() {
      return iter.hasNext();
    }

    @Override
    public E next() {
      return iter.next();
    }
  }
}
