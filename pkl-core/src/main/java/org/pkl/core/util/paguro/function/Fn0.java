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

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * This is like Java 8's java.util.function.Supplier, but retrofitted to turn checked exceptions
 * into unchecked ones. It's also called a thunk when used to delay evaluation.
 */
@FunctionalInterface
public interface Fn0<U> extends Supplier<U>, Callable<U> {
  /** Implement this one method and you don't have to worry about checked exceptions. */
  U applyEx() throws Exception;

  /**
   * The class that takes a consumer as an argument uses this convenience method so that it doesn't
   * have to worry about checked exceptions either.
   */
  default U apply() {
    try {
      return applyEx();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  default U get() {
    return apply();
  }

  /** {@inheritDoc} */
  @Override
  default U call() throws Exception {
    return applyEx();
  }

  // ========================================== Static ==========================================
  // Enums are serializable.  Anonymous classes and lambdas are not.

  // Dear Glen,
  // You have implemented this twice now.  There are 3 reasons to leave it commented out:
  // 1. `() -> none` is brief, clear, and beautiful.
  // 2. You hate the method reference syntax (it's ambiguous).
  // 3. Encourage using Option over null.
  //    /** One-of-a-kind Fn0's. */
  //    enum ConstObj implements Fn0<Option<Object>> {
  //        NULL {
  //            @Override
  //            public  Option<Object> applyEx() throws Exception { return Option.none(); }
  //        }
  //    }
  //
  //    /** Returns a type-safe version of the {@link ConstObj#NULL} thunk. */
  //    @SuppressWarnings("unchecked")
  //    static <S>  Fn0<S> nullSupplier() { return (Fn0<S>) ConstObj.NULL; }

  //    /**
  //     Wraps a value in a constant function.  If you need to "memoize" some really expensive
  //     operation, use a {@link org.pkl.core.util.paguro.function.LazyRef}.
  //     */
  //    class Constant<K> implements Fn0<K>, Serializable {
  //        private static final long serialVersionUID = 201608281356L;
  //        private final K k;
  //        Constant(K theK) { k = theK; }
  //        @Override public K applyEx() { return k; }
  //        @Override public int hashCode() { return (k == null) ? 0 : k.hashCode(); }
  //        @Override public boolean equals(Object o) {
  //            if (this == o) { return true; }
  //            if ( (o == null) || !(o instanceof Constant) ) { return false; }
  //            return k.equals(((Constant) o).get());
  //        }
  //        @Override public String toString() { return "() -> " + k; };
  //    }
  //
  //    static <K> Fn0<K> constantFunction(final K k) { return new Constant<>(k); }
}
