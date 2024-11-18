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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.*;
import java.util.function.Consumer;

public final class Iterators {
  private Iterators() {}

  @SuppressWarnings("unchecked")
  public static <T> Iterator<T> emptyTruffleIterator() {
    return EMPTY_TRUFFLE_ITERATOR;
  }

  /** An empty iterator that performs all work behind Truffle boundaries. */
  @SuppressWarnings("rawtypes")
  private static final Iterator EMPTY_TRUFFLE_ITERATOR =
      new Iterator() {
        @TruffleBoundary
        @Override
        public boolean hasNext() {
          return false;
        }

        @TruffleBoundary
        @Override
        public Object next() {
          throw new NoSuchElementException();
        }

        @TruffleBoundary
        @Override
        public void remove() {
          throw new IllegalStateException();
        }

        @TruffleBoundary
        @Override
        public void forEachRemaining(Consumer action) {
          throw new UnsupportedOperationException("forEachRemaining");
        }
      };

  /** An iterator for iterables that performs all work behind Truffle boundaries. */
  public static final class TruffleIterator<T> implements Iterator<T> {
    private final Iterator<? extends T> delegate;

    // accepting Iterable instead of Iterator puts Iterable.iterator() behind a Truffle boundary
    @TruffleBoundary
    public TruffleIterator(Iterable<? extends T> iterable) {
      delegate = iterable.iterator();
    }

    @Override
    @TruffleBoundary
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    @TruffleBoundary
    public T next() {
      return delegate.next();
    }

    @Override
    @TruffleBoundary
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }

    @Override
    @TruffleBoundary
    public void forEachRemaining(Consumer<? super T> action) {
      throw new UnsupportedOperationException("forEachRemaining");
    }
  }

  /** A reverse iterator for lists that performs all work behind Truffle boundaries. */
  public static final class ReverseTruffleIterator<T> implements Iterator<T> {
    private final ListIterator<? extends T> delegate;

    // accepting List instead of ListIterator puts List.listIterator() behind a Truffle boundary
    @TruffleBoundary
    public ReverseTruffleIterator(List<? extends T> list) {
      delegate = list.listIterator(list.size());
    }

    @Override
    @TruffleBoundary
    public boolean hasNext() {
      return delegate.hasPrevious();
    }

    @Override
    @TruffleBoundary
    public T next() {
      return delegate.previous();
    }

    @Override
    @TruffleBoundary
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }

    @Override
    @TruffleBoundary
    public void forEachRemaining(Consumer<? super T> action) {
      throw new UnsupportedOperationException("forEachRemaining");
    }
  }

  /** A reverse iterator for arrays. */
  public static final class ReverseArrayIterator implements Iterator<Object> {
    private final Object[] array;

    private int nextIndex;

    public ReverseArrayIterator(Object[] array) {
      this.array = array;
      nextIndex = array.length - 1;
    }

    @Override
    public boolean hasNext() {
      return nextIndex >= 0;
    }

    @Override
    public Object next() {
      return array[nextIndex--];
    }
  }
}
