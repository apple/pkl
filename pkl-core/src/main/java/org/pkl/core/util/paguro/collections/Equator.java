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

import java.util.Comparator;
import org.pkl.core.util.Nullable;

/**
 * An Equator represents an equality context in a way that is analgous to the java.util.Comparator
 * interface. <a
 * href="http://glenpeterson.blogspot.com/2013/09/object-equality-is-context-relative.html"
 * target="_blank">Comparing Objects is Relative</a> This will need to be passed to Hash-based
 * collections the way a Comparator is passed to tree-based ones.
 *
 * <p>The method names hash() and eq() are intentionally elisions of hashCode() and equals() so that
 * your IDE will suggest the shorter name as you start typing which is almost always what you want.
 * You want the hash() and eq() methods because that's how Equators compare things. You don't want
 * an equator's .hashCode() or .equals() methods because those are for comparing *Equators* and are
 * inherited from java.lang.Object. I'd deprecate those methods, but you can't do that on an
 * interface.
 *
 * <p>A common mistake is to implement an Equator, ComparisonContext, or Comparator as an anonymous
 * class or lambda, then be surprised when it is can't be serialized, or is deserialized as null.
 * These one-off classes are often singletons, which are easiest to serialize as enums. If your
 * implementation requires generic type parameters, observe how {@link #defaultEquator()} tricks the
 * type system into using generic type parameters (correctly) with an enum.
 */
public interface Equator<T> {

  // ============================================= Static ========================================

  // Enums are serializable and lambdas are not.  Therefore enums make better singletons.
  enum Equat implements Equator<Object> {
    /**
     * Assumes the object is not an array. Default array.equals() is == comparison which is probably
     * not what you want.
     */
    DEFAULT {
      @Override
      public int hash(@Nullable Object o) {
        return (o == null) ? 0 : o.hashCode();
      }

      @Override
      public boolean eq(@Nullable Object o1, @Nullable Object o2) {
        if (o1 == null) {
          return (o2 == null);
        }
        return o1.equals(o2);
      }
      //        },
      //        ARRAY {
      //            @Override public int hash(Object o) {
      //                return Arrays.hashCode( (Object[]) o);
      //            }
      //
      //            @Override public boolean eq(Object o1, Object o2) {
      //                try {
      //                    return Arrays.equals((Object[]) o1, (Object[]) o2);
      //                } catch (Exception e) {
      //                    return false;
      //                }
      //            }
    }
  }

  @SuppressWarnings("unchecked")
  static <T> Equator<T> defaultEquator() {
    return (Equator<T>) Equat.DEFAULT;
  }

  // Enums are serializable and lambdas are not.  Therefore, enums make better singletons.
  enum Comp implements Comparator<Comparable<Object>> {
    DEFAULT {
      @Override
      public int compare(Comparable<Object> o1, Comparable<Object> o2) {
        return doCompare(o1, o2);
      }
    }
  }

  @SuppressWarnings("unchecked")
  static <T> Comparator<T> defaultComparator() {
    return (Comparator<T>) Comp.DEFAULT;
  }

  /** This is the guts of building a comparator from Comparables. */
  static int doCompare(Comparable<Object> o1, Comparable<Object> o2) {
    if (o1 == o2) {
      return 0;
    }
    if (o1 == null) {
      // FindBugs Hates this for at least the following reasons:
      // 1. Method call passes null for known non-null value.
      // 2. Load of known null value
      // 3. Negating the result of compareTo()/compare()
      // Negating Integer.MIN_VALUE won't negate the sign of the result.
      //
      // Generally, you shouldn't be comparing something to null.
      // But if your Comparable class is defined for comparing against null, and if you pass a null,
      // this should work.  We're trying to be compatible with anything you do that even might make
      // sense.
      //
      // I'd love to have someone explain to me why this can never work.  I'm unable to prove to
      // myself that
      // this could never be useful.  In fact, I think I rely on it to sort some lists of stuff that
      // can
      // contain nulls somewhere, like a list of selectOptions including the null option to display
      // to the user.
      // The selectOption is defined so that compareTo(null) always returns 1.
      return -Integer.signum(o2.compareTo(o1));
    }
    return o1.compareTo(o2);
  }

  // ========================================= Instance =========================================
  /**
   * An integer digest used for very quick "can-equal" testing. This method MUST return equal hash
   * codes for equal objects. It should USUALLY return unequal hash codes for unequal objects. You
   * should not change mutable objects while you rely on their hash codes. That said, if a mutable
   * object's internal state changes, the hash code generally must change to reflect the new state.
   * The name of this method is short so that auto-complete can offer it before hashCode().
   */
  int hash(T t);

  /**
   * Determines whether two objects are equal. The name of this method is short so that
   * auto-complete can offer it before equals().
   *
   * <p>You can do anything you want here, but consider having null == null but null != anything
   * else.
   *
   * @return true if this Equator considers the two objects to be equal.
   */
  boolean eq(T o1, T o2);

  /**
   * Returns true if two objects are NOT equal. By default, just delegates to {@link #eq(Object,
   * Object)} and reverses the result.
   *
   * @return true if this Equator considers the two objects to NOT be equal.
   */
  default boolean neq(T o1, T o2) {
    return !eq(o1, o2);
  }
}
