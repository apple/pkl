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
package org.pkl.core.util.paguro.function;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.pkl.core.util.paguro.oneOf.Option;
import org.pkl.core.util.paguro.tuple.Tuple2;

/**
 * This is like Java 8's java.util.function.BiFunction, but retrofitted to turn checked exceptions
 * into unchecked ones.
 */
@FunctionalInterface
public interface Fn2<A, B, R> extends BiFunction<A, B, R> {
  /** Implement this one method, and you don't have to worry about checked exceptions. */
  R applyEx(A a, B b) throws Exception;

  /**
   * The class that takes a consumer as an argument uses this convenience method so that it doesn't
   * have to worry about checked exceptions either.
   */
  @Override
  default R apply(A a, B b) {
    try {
      return applyEx(a, b);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Use only on pure functions with no side effects. Wrap an expensive function with this and for
   * each input value, the output will only be computed once. Subsequent calls with the same input
   * will return identical output very quickly. Please note that the parameters to f need to
   * implement equals() and hashCode() correctly for this to work correctly and quickly.
   */
  static <A, B, Z> Fn2<A, B, Z> memoize(Fn2<A, B, Z> f) {
    return new Fn2<>() {
      private final Map<Tuple2<A, B>, Option<Z>> map = new HashMap<>();

      @Override
      public synchronized Z applyEx(A a, B b) throws Exception {
        Tuple2<A, B> t = Tuple2.of(a, b);
        Option<Z> val = map.get(t);
        if (val != null) {
          return val.get();
        }
        Z ret = f.applyEx(a, b);
        map.put(t, Option.some(ret));
        return ret;
      }
    };
  }

  enum Singletons implements Fn2 {
    /**
     * A static function that always returns the first argument it is given. For type safety, please
     * use {@link Fn2#first()} instead of accessing this directly.
     */
    FIRST {
      @Override
      public Object applyEx(Object a, Object b) throws Exception {
        return a;
      }
    },
    /**
     * A static function that always returns the second argument it is given. For type safety,
     * please use {@link Fn2#second()} instead of accessing this directly.
     */
    SECOND {
      @Override
      public Object applyEx(Object a, Object b) throws Exception {
        return b;
      }
    },
  }

  /**
   * Returns a static function that always returns the first argument it is given.
   *
   * @return the first argument, unmodified.
   */
  @SuppressWarnings("unchecked")
  static <A1, B1> Fn2<A1, ? super B1, A1> first() {
    return Singletons.FIRST;
  }

  /**
   * Returns a static function that always returns the second argument it is given.
   *
   * @return the second argument, unmodified.
   */
  @SuppressWarnings("unchecked")
  static <A1, B1> Fn2<A1, ? super B1, B1> second() {
    return Singletons.SECOND;
  }
}
