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
package org.pkl.core.util.paguro.oneOf;

import static org.pkl.core.util.paguro.type.RuntimeTypes.union2Str;

import java.util.Objects;
import org.pkl.core.util.paguro.collections.ImList;
import org.pkl.core.util.paguro.function.Fn1;
import org.pkl.core.util.paguro.type.RuntimeTypes;

/**
 * This is designed to represent a union of 2 types, meaning an object that can be one type, or
 * another. Instead of a get() method, pass 2 functions to match(), one to handle the case where
 * this contains the first thing, the other if it contains the second thing. In theory, this could
 * work with two things of the same type, but Java has polymorphism to handle that more easily.
 * Before using a OneOf2, make sure you don't really need a {@link
 * org.pkl.core.util.paguro.tuple.Tuple2}.
 *
 * <p>OneOf2 is designed to be subclassed to add descriptive names. The safest way to use Union
 * classes is to always call match() because it forces you to think about how to handle each type
 * you could possibly receive.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * thingy.match(fst -> fst.doOneThing(),
 * sec -> sec.doSomethingElse());
 * }</pre>
 *
 * Sometimes it's a programming error to pass one type or another, and you may want to throw an
 * exception.
 *
 * <pre>{@code
 * oneOf.match(fst -> fst.doOneThing(),
 * sec -> { throw new IllegalStateException("Asked for a 2nd; only had a 1st."); });
 * }</pre>
 *
 * For the shortest syntax and best names, define your own subclass. This is similar to sub-classing
 * Tuples.
 *
 * <pre>{@code
 * static class String_Integer extends OneOf2<String,Integer> {
 *
 * // Private Constructor because the o parameter is not type safe.
 * private String_Integer(Object o, int n) { super(o, String.class, Integer.class, n); }
 *
 * // Static factory methods ensure type-safe construction.
 * public static String_Integer ofStr(String o) { return new String_Integer(o, 0); }
 * public static String_Integer ofInt(Integer o) { return new String_Integer(o, 1); }
 * }
 * }</pre>
 *
 * equals(), hashcode(), and toString() are all taken care of for you.
 *
 * <p>Now you use descriptive and extremely brief syntax:
 *
 * <pre>{@code
 * // Type-safe switching - always works at runtime.
 * x.match(s -> (s == null) ? null : s.lowerCase(),
 * n -> "This is the number " + n);
 *
 * // If not a String at runtime throws "Expected a(n) String but found a(n) Integer"
 * x.str().contains("goody!");
 *
 * // If not an Integer at runtime throws "Expected a(n) Integer but found a(n) String"
 * 3 + x.integer();
 * }</pre>
 *
 * Instead of putting a Null object in here, either return a null OneOf2 or wrap OneOf2 in an {@link
 * Option}
 */
public class OneOf2<A, B> {
  protected final Object item;
  private final int sel;

  @SuppressWarnings("rawtypes")
  private final ImList<Class> types;

  /**
   * Protected constructor for subclassing. Be extremely careful to pass the correct index!
   *
   * @param o the item
   * @param aClass class 0
   * @param bClass class 1
   * @param index 0 means this represents an A, 1 represents a B
   */
  protected OneOf2(Object o, Class<A> aClass, Class<B> bClass, int index) {
    types = RuntimeTypes.registerClasses(aClass, bClass);
    sel = index;
    item = o;
    if (index < 0) {
      throw new IllegalArgumentException("Selected item index must be 0-1");
    } else if (index > 1) {
      throw new IllegalArgumentException("Selected item index must be 0-1");
    }
    if (!types.get(index).isInstance(o)) {
      // Made this "indicating a " instead of "indicating a(n) " because a(n) looks like a function.
      // Poor grammar, in this case, is less confusing.
      throw new ClassCastException(
          "You specified index "
              + index
              + ", indicating a "
              + types.get(index).getCanonicalName()
              + ","
              + " but passed a "
              + o.getClass().getCanonicalName());
    }
  }

  /**
   * Languages that have union types built in have a match statement that works like this method.
   * Exactly one of these functions will be executed - determined by which type of item this object
   * holds.
   *
   * @param fa applied iff this stores the first type.
   * @param fb applied iff this stores the second type.
   * @return the return value of whichever function is executed.
   */
  // We only store one item and its type is erased, so we have to cast it at runtime.
  // If sel is managed correctly, this ensures that cast is accurate.
  @SuppressWarnings("unchecked")
  public <R> R match(Fn1<A, R> fa, Fn1<B, R> fb) {
    if (sel == 0) {
      return fa.apply((A) item);
    }
    return fb.apply((B) item);
  }

  public int hashCode() {
    // Simplest way to make the two items different.
    return Objects.hashCode(item) + sel;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof OneOf2)) {
      return false;
    }

    @SuppressWarnings("rawtypes")
    OneOf2 that = (OneOf2) other;
    return (sel == that.sel) && Objects.equals(item, that.item);
  }

  @Override
  public String toString() {
    return union2Str(item, types);
  }
}
