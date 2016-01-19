/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.pkl.core.util.Nullable;

/** Java representation of a {@code pkl.base#Pair} value. */
public final class Pair<F, S> implements Value, Iterable<Object> {
  private static final long serialVersionUID = 0L;

  private final F first;
  private final S second;

  /** Constructs a pair with the given elements. */
  public Pair(F first, S second) {
    this.first = first;
    this.second = second;
  }

  /** Returns the first element of this pair. */
  public F getFirst() {
    return first;
  }

  /** Returns the second element of this pair. */
  public S getSecond() {
    return second;
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<>() {
      int pos = 0;

      @Override
      public boolean hasNext() {
        return pos < 2;
      }

      @Override
      public Object next() {
        switch (pos++) {
          case 0:
            return first;
          case 1:
            return second;
          default:
            throw new NoSuchElementException("Pair only has two elements.");
        }
      }
    };
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitPair(this);
  }

  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertPair(this);
  }

  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.Pair;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Pair)) return false;

    var other = (Pair<?, ?>) obj;
    return first.equals(other.first) && second.equals(other.second);
  }

  @Override
  public int hashCode() {
    return first.hashCode() * 31 + second.hashCode();
  }

  @Override
  public String toString() {
    return "Pair(" + first + ", " + second + ")";
  }
}
