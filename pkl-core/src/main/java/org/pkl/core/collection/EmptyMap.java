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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/** Singleton instances for empty maps and the corresponding iterators and cursors. */
class EmptyMap {

  static final MapCursor<Object, Object> EMPTY_CURSOR =
      new MapCursor<>() {
        @Override
        public void remove() {
          throw new NoSuchElementException("Empty cursor does not have elements");
        }

        @Override
        public boolean advance() {
          return false;
        }

        @Override
        public Object getKey() {
          throw new NoSuchElementException("Empty cursor does not have elements");
        }

        @Override
        public Object getValue() {
          throw new NoSuchElementException("Empty cursor does not have elements");
        }

        @Override
        public Object setValue(Object newValue) {
          throw new NoSuchElementException("Empty cursor does not have elements");
        }
      };

  static final Iterator<Object> EMPTY_ITERATOR =
      new Iterator<>() {
        @Override
        public boolean hasNext() {
          return false;
        }

        @Override
        public Object next() {
          throw new NoSuchElementException("Empty iterator does not have elements");
        }
      };

  static final Iterable<Object> EMPTY_ITERABLE =
      new Iterable<>() {
        @Override
        public Iterator<Object> iterator() {
          return EMPTY_ITERATOR;
        }
      };

  static final EconomicMap<Object, Object> EMPTY_MAP =
      new EconomicMap<>() {
        @Override
        public Object put(Object key, Object value) {
          throw new IllegalArgumentException("Cannot modify the always-empty map");
        }

        @Override
        public void clear() {
          throw new IllegalArgumentException("Cannot modify the always-empty map");
        }

        @Override
        public Object removeKey(Object key) {
          throw new IllegalArgumentException("Cannot modify the always-empty map");
        }

        @Override
        public Object get(Object key) {
          return null;
        }

        @Override
        public boolean containsKey(Object key) {
          return false;
        }

        @Override
        public int size() {
          return 0;
        }

        @Override
        public boolean isEmpty() {
          return true;
        }

        @Override
        public Iterable<Object> getValues() {
          return EMPTY_ITERABLE;
        }

        @Override
        public Iterable<Object> getKeys() {
          return EMPTY_ITERABLE;
        }

        @Override
        public MapCursor<Object, Object> getEntries() {
          return EMPTY_CURSOR;
        }

        @Override
        public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
          throw new IllegalArgumentException("Cannot modify the always-empty map");
        }
      };
}
