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

import java.util.Comparator;
import java.util.Iterator;
import org.pkl.core.util.Nullable;

/**
 * Represents a context for comparison because sometimes you order the same things differently. For
 * instance, a class of students might be sorted by height for the yearbook picture (shortest in
 * front), alphabetically for role call, and by GPA at honors ceremonies. Sometimes you need to sort
 * non-compatible classes together for some reason. If you didn't define those classes, this
 * provides an external means of ordering them.
 *
 * <p>A Comparison Context represents both ordering and equality, since the two often need to be
 * defined compatibly. Implement compare() and hash() and you get a compatible eq() for free! If you
 * don't want ordering, use {@link Equator} instead.
 *
 * <p>Typical implementations of {@link #compare(Object, Object)} throw an IllegalArgumentExceptions
 * if one argument is null because most objects cannot be meaningfully be orderd with respect to
 * null. It's also OK if you want to return 0 when both arguments are null because null == null.
 * Default implementations of eq(), gte(), and lte() check for nulls first, before calling compare()
 * so they will work either way you choose to implement compare().
 *
 * <p>A common mistake is to implement a ComparisonContext, Equator, or Comparator as an anonymous
 * class or lambda, then be surprised when it can't be serialized, or is deserialized as null. These
 * one-off classes are often singletons, which are easiest to serialize as enums. If your
 * implementation requires generic type parameters, look at how {@link #defCompCtx()} tricks the
 * type system into using generic type parameters (correctly) with an enum.
 */
public interface ComparisonContext<T> extends Equator<T>, Comparator<T> {
  /** Returns true if the first object is less than the second. */
  default boolean lt(T o1, T o2) {
    return compare(o1, o2) < 0;
  }

  /** Returns true if the first object is less than or equal to the second. */
  default boolean lte(T o1, T o2) {
    if ((o1 == null) || (o2 == null)) {
      return (o1 == o2);
    }
    return compare(o1, o2) <= 0;
  }

  /** Returns true if the first object is greater than the second. */
  default boolean gt(T o1, T o2) {
    return compare(o1, o2) > 0;
  }

  /** Returns true if the first object is greater than or equal to the second. */
  default boolean gte(T o1, T o2) {
    if ((o1 == null) || (o2 == null)) {
      return (o1 == o2);
    }
    return compare(o1, o2) >= 0;
  }

  /**
   * The default implementation of this method returns false if only one parameter is null then
   * checks if compare() returns zero.
   */
  @Override
  default boolean eq(T o1, T o2) {
    if ((o1 == null) || (o2 == null)) {
      return (o1 == o2);
    }

    // Now they are equal if compare returns zero.
    return compare(o1, o2) == 0;
  }

  /**
   * Returns the minimum (as defined by this Comparison Context). Nulls are skipped. If there are
   * duplicate minimum values, the first one is returned.
   */
  default T min(Iterable<T> items) {
    // Note: following code is identical to max() except for lt() vs. gt()
    Iterator<T> iter = items.iterator();
    T ret = null;
    while ((ret == null) && iter.hasNext()) {
      ret = iter.next();
    }
    while (iter.hasNext()) {
      T next = iter.next();
      if ((next != null) && lt(next, ret)) {
        ret = next;
      }
    }
    return ret; // could be null if all items are null.
  }

  /**
   * Returns the maximum (as defined by this Comparison Context). Nulls are skipped. If there are
   * duplicate maximum values, the first one is returned.
   */
  default T max(Iterable<T> items) {
    // Note: following code is identical to min() except for lt() vs. gt()
    Iterator<T> iter = items.iterator();
    T ret = null;
    while ((ret == null) && iter.hasNext()) {
      ret = iter.next();
    }
    while (iter.hasNext()) {
      T next = iter.next();
      if ((next != null) && gt(next, ret)) {
        ret = next;
      }
    }
    return ret; // could be null if all items are null.
  }

  /**
   * Please access this type-safely through {@link #defCompCtx()} instead of calling directly. This
   * exists because Enums are serializable and lambdas are not. Enums also make ideal singletons.
   */
  enum CompCtx implements ComparisonContext<Comparable<Object>> {
    DEFAULT {
      @Override
      public int hash(@Nullable Comparable<Object> o) {
        return (o == null) ? 0 : o.hashCode();
      }

      @Override
      public int compare(Comparable<Object> o1, Comparable<Object> o2) {
        return Equator.doCompare(o1, o2);
      }
    }
  }

  /**
   * Returns a typed, serializable ComparisonContext that works on any class that implements {@link
   * Comparable}.
   */
  @SuppressWarnings("unchecked")
  static <T> ComparisonContext<T> defCompCtx() {
    return (ComparisonContext<T>) CompCtx.DEFAULT;
  }
}
