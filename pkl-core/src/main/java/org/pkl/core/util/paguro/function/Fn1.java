/*
 * Copyright © 2013-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.collections.UnmodIterable;
import org.pkl.core.util.paguro.oneOf.Option;
import org.pkl.core.util.paguro.oneOf.Or;
import org.pkl.core.util.paguro.xform.Transformable;
import org.pkl.core.util.paguro.xform.Xform;

/**
 * This is like Java 8's java.util.function.Function, but retrofitted to turn checked exceptions
 * into unchecked ones.
 */
@FunctionalInterface
public interface Fn1<T, U> extends Function<T, U>, Consumer<T> {
  // ========================================== Static ==========================================

  /** Constant functions that take an Object and return an Object */
  enum ConstObjObj implements Fn1<Object, Object> {
    /** The Identity function */
    IDENTITY {
      @Override
      public Object applyEx(Object t) throws Exception {
        return t;
      }

      @SuppressWarnings({"unchecked", "TypeParameterExplicitlyExtendsObject"})
      @Override
      public <S> Fn1<S, Object> compose(Fn1<? super S, ? extends Object> f) {
        // Composing any function with the identity function has no effect on the original
        // function (by definition of identity) - just return it.
        return (Fn1<S, Object>) f;
      }
    }
  }

  /** Constant functions that take an Object and return a Boolean */
  enum ConstObjBool implements Fn1<Object, Boolean> {
    /**
     * A predicate that always returns true. Use {@link #accept()} for a type-safe version of this
     * predicate.
     */
    ACCEPT {
      @Override
      public Boolean applyEx(Object ignored) throws Exception {
        return Boolean.TRUE;
      }
    },

    /**
     * A predicate that always returns false. Use {@link #reject()} for a type-safe version of this
     * predicate.
     */
    REJECT {
      @Override
      public Boolean applyEx(Object ignored) throws Exception {
        return Boolean.FALSE;
      }
    }
  }

  @SuppressWarnings("unchecked")
  static <V> Fn1<V, V> identity() {
    return (Fn1<V, V>) ConstObjObj.IDENTITY;
  }

  static <S> Fn1<S, Boolean> or(Fn1<S, Boolean> a, Fn1<S, Boolean> b) {
    // Composition is not necessary in every case:
    return a == ConstObjBool.ACCEPT
        ? a
        : // If any are true, all are true.
        a == ConstObjBool.REJECT
            ? b
            : // return whatever b is.
            b == ConstObjBool.ACCEPT
                ? b
                : // If any are true, all are true.
                b == ConstObjBool.REJECT
                    ? a
                    : // Just amounts to if a else false.
                    (S s) ->
                        (a.apply(s) == Boolean.TRUE) || (b.apply(s) == Boolean.TRUE); // compose
  }

  static <S> Fn1<S, Boolean> and(Fn1<S, Boolean> a, Fn1<S, Boolean> b) {
    // Composition is not necessary in every case:
    return a == ConstObjBool.ACCEPT
        ? b
        : // return whatever b is.
        a == ConstObjBool.REJECT
            ? a
            : // if any are false, all are false.
            b == ConstObjBool.ACCEPT
                ? a
                : // Just amounts to if a else false.
                b == ConstObjBool.REJECT
                    ? b
                    : // If any are false, all are false.
                    (S s) ->
                        (a.apply(s) == Boolean.TRUE) && (b.apply(s) == Boolean.TRUE); // compose
  }

  static <S> Fn1<S, Boolean> negate(Fn1<? super S, Boolean> a) {
    return a == ConstObjBool.ACCEPT
        ? reject()
        : a == ConstObjBool.REJECT
            ? accept()
            : (S s) -> (a.apply(s) == Boolean.TRUE) ? Boolean.FALSE : Boolean.TRUE;
  }

  /** Returns a type-safe version of the {@link ConstObjBool#ACCEPT} predicate. */
  @SuppressWarnings("unchecked")
  static <T> Fn1<T, Boolean> accept() {
    return (Fn1<T, Boolean>) ConstObjBool.ACCEPT;
  }

  /** Returns a type-safe version of the {@link ConstObjBool#REJECT} predicate. */
  @SuppressWarnings("unchecked")
  static <T> Fn1<T, Boolean> reject() {
    return (Fn1<T, Boolean>) ConstObjBool.REJECT;
  }

  /**
   * Composes multiple functions into a single function to potentially minimize trips through the
   * source data. The resultant function will loop through the functions for each item in the
   * source. For a few functions and many source items, that takes less memory. Considers no
   * function to mean the IDENTITY function. This decision is based on the way filters work and may
   * or may not prove useful in practice. Please use the identity()/IDENTITY sentinel value in this
   * abstract class since function comparison is done by reference.
   *
   * <p>LIMITATION: You could have a function that maps from T to U then the next from U to V, the
   * next from V to W and so on. So long as the output type of one matches up to the input type of
   * the next, you're golden. But type safety curls up and dies when you try to detect the IDENTITY
   * function at some point in the chain.
   *
   * <p>For arbitrary chaining, it's best to roll your own. The following example shows how simple
   * it is to chain two functions with an intermediate type into a single composite function:
   *
   * <pre><code>
   * public static &lt;A,B,C&gt; Fn1&lt;A,C&gt; chain2(final Fn1&lt;A,B&gt; f1,
   * final Fn1&lt;B,C&gt; f2) {
   * return new Fn1&lt;A,C&gt;() {
   * &#64;Override
   * public C applyEx(A a) throws Exception {
   * return f2.applyEx(f1.applyEx(a));
   * }
   * };
   * }</code></pre>
   *
   * Even with 2 arguments, there are several signatures that would work: imagine where A=B, B=C, or
   * A=C. I just don't see the value to providing a bunch of chain2(), chain3() etc. functions that
   * will ultimately not be type-safe and cannot perform optimizations for you, when you can roll
   * your own type safe versions as you need them. Only the simplest case seems worth providing,
   * along the lines of the and() helper function in Filter()
   *
   * @param in the functions to applyEx in order. Nulls and IDENTITY functions are ignored. No
   *     functions: returns IDENTITY.
   * @param <V> the type of object to chain functions on
   * @return a function which applies all the given functions in order.
   */
  static <V> Fn1<V, V> compose(@Nullable Iterable<@Nullable Fn1<V, V>> in) {
    if (in == null) {
      return identity();
    }
    final List<Fn1<V, V>> out = new ArrayList<>();
    for (Fn1<V, V> f : in) {
      if ((f == null) || (f == ConstObjObj.IDENTITY)) {
        continue;
      }
      out.add(f);
    }
    if (out.size() < 1) {
      return identity(); // No functions: return the original item
    } else if (out.size() == 1) {
      return out.get(0);
    } else {
      return v -> {
        V ret = v;
        for (Fn1<V, V> f : out) {
          ret = f.applyEx(ret);
        }
        return ret;
      };
    }
  }

  /**
   * Composes multiple predicates into a single predicate to potentially minimize trips through the
   * source data. The resultant predicate will loop through the predicates for each item in the
   * source, but for few predicates and many source items, that takes less memory. Considers no
   * predicate to mean "accept all." Use only accept()/ACCEPT and reject()/REJECT since function
   * comparison is done by reference.
   *
   * @param in the predicates to test in order. Nulls and ACCEPT predicates are ignored. Any REJECT
   *     predicate will cause this entire method to return a single REJECT predicate. No predicates:
   *     returns ACCEPT.
   * @param <T> the type of object to predicate on.
   * @return a predicate which returns true if all input predicates return true, false otherwise.
   */
  static <T> Fn1<T, Boolean> and(@Nullable Iterable<@Nullable Fn1<T, Boolean>> in) {
    if (in == null) {
      return accept();
    }

    Transformable<Fn1<T, Boolean>> v =
        (in instanceof UnmodIterable) ? (UnmodIterable<Fn1<T, Boolean>>) in : Xform.of(in);

    Or<Fn1<T, Boolean>, Fn1<T, Boolean>> ret =
        v.filter(p -> (p != null) && (p != ConstObjBool.ACCEPT))
            .foldUntil(
                accept(),
                (accum, p) -> (p == reject()) ? p : null,
                Fn1::and); // (accum, p) -> and(accum, p)
    // We don't care whether it returns early or not.  Just return whatever is in the or.
    return ret.match(g -> g, b -> b);
  }

  /**
   * Composes multiple predicates into a single predicate to potentially minimize trips through the
   * source data. The resultant predicate will loop through the predicates for each item in the
   * source, but for few predicates and many source items, that takes less memory. Considers no
   * predicate to mean "reject all." Use only {@link #accept()} and {@link #reject()} since function
   * comparison is done by reference.
   *
   * @param in the predicates to test in order. Nulls and REJECT predicates are ignored. Any ACCEPT
   *     predicate will cause this entire method to return the ACCEPT predicate. No predicates:
   *     returns REJECT.
   * @param <T> the type of object to predicate on.
   * @return a predicate which returns true if any of the input predicates return true, false
   *     otherwise.
   */
  static <T> Fn1<T, Boolean> or(@Nullable Iterable<@Nullable Fn1<T, Boolean>> in) {
    if (in == null) {
      return reject();
    }

    Transformable<Fn1<T, Boolean>> v =
        (in instanceof UnmodIterable) ? (UnmodIterable<Fn1<T, Boolean>>) in : Xform.of(in);

    Or<Fn1<T, Boolean>, Fn1<T, Boolean>> ret =
        v.filter(p -> (p != null) && (p != ConstObjBool.REJECT))
            .foldUntil(
                reject(),
                (accum, p) -> (p == ConstObjBool.ACCEPT) ? p : null,
                Fn1::or); // (accum, p) -> or(accum, p)
    // We don't care whether it returns early or not.  Just return whatever is in the or.
    return ret.match(g -> g, b -> b);
  }

  enum BooleanCombiner {
    AND {
      @Override
      public <T> Fn1<T, Boolean> combine(Iterable<Fn1<T, Boolean>> in) {
        return and(in);
      }
    },
    OR {
      @Override
      public <T> Fn1<T, Boolean> combine(Iterable<Fn1<T, Boolean>> in) {
        return or(in);
      }
    };

    public abstract <T> Fn1<T, Boolean> combine(Iterable<Fn1<T, Boolean>> in);
  }

  /**
   * Use only on pure functions with no side effects. Wrap an expensive function with this and for
   * each input value, the output will only be computed once. Subsequent calls with the same input
   * will return identical output very quickly. Please note that the return values from f need to
   * implement equals() and hashCode() correctly for this to work correctly and quickly.
   */
  static <A, B> Fn1<A, B> memoize(Fn1<A, B> f) {
    return new Fn1<>() {
      private final Map<A, Option<B>> memo = new HashMap<>();

      @Override
      public synchronized B applyEx(A a) throws Exception {
        Option<B> val = memo.get(a);
        if ((val != null) && val.isSome()) {
          return val.get();
        }
        B ret = f.applyEx(a);
        memo.put(a, Option.some(ret));
        return ret;
      }
    };
  }

  // ========================================= Instance =========================================

  /** Implement this one method, and you don't have to worry about checked exceptions. */
  U applyEx(T t) throws Exception;

  /** Call this convenience method so that you don't have to worry about checked exceptions. */
  @Override
  default U apply(T t) {
    try {
      return applyEx(t);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** For compatibility with java.util.function.Consumer. Just a wrapper around apply(). */
  @Override
  default void accept(T t) {
    apply(t);
  }

  @SuppressWarnings("unchecked")
  default <S> Fn1<S, U> compose(final Fn1<? super S, ? extends T> f) {
    if (f == ConstObjObj.IDENTITY) {
      // This violates type safety, but makes sense - composing any function with the
      // identity function should return the original function unchanged.  If you mess up the
      // types, then that's your problem.  With generics and type erasure this may be the
      // best you can do.
      return (Fn1<S, U>) this;
    }
    final Fn1<T, U> parent = this;
    return s -> parent.applyEx(f.applyEx(s));
  }
}
