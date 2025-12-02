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

import static org.pkl.core.util.paguro.FunctionUtils.stringify;

import java.util.Iterator;
import java.util.Objects;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.function.Fn1;
import org.pkl.core.util.paguro.function.Fn2;
import org.pkl.core.util.paguro.oneOf.Option;
import org.pkl.core.util.paguro.oneOf.Or;
import org.pkl.core.util.paguro.xform.Transformable;
import org.pkl.core.util.paguro.xform.Xform;

/** An unmodifiable Iterable, without any guarantee about order. */
public interface UnmodIterable<T> extends Iterable<T>, Transformable<T> {
  // ========================================== Static ==========================================

  @SuppressWarnings("rawtypes") // Need raw types here.
  enum UnIterable implements UnmodIterable {
    EMPTY {
      @Override
      public UnmodIterator iterator() {
        return UnmodIterator.emptyUnmodIterator();
      }
    }
  }

  /** We only ever need one empty iterable in memory. */
  @SuppressWarnings("unchecked")
  static <T> UnmodIterable<T> emptyUnmodIterable() {
    return (UnmodIterable<T>) UnIterable.EMPTY;
  }

  //    /**
  // Caution: this is a convenient optimization for immutable data structures and a nightmare
  // waiting to happen to mutable ones.  Don't go slapping this on immutable wrappers for mutable
  // data.  If all the underlying data is truly immutable, this allows you to compute the hashCode
  // the first time it is needed, then return that same code without re-computing it again.  It's
  // the internal version of a memoizer.  Also, use this only for decent sized collections.  If you
  // only have 2 or 3 fields, this isn't buying you anything.
  //     */
  //    static Lazy.Int lazyHashCode(UnmodIterable iter) {
  //        if (iter == null) { throw new IllegalArgumentException("Can't have a null iterable."); }
  //        return Lazy.Int.of(() -> UnmodIterable.hashCode(iter));
  //    }
  //
  //    /**
  //     Caution: this is a convenient optimization for immutable data structures and a nightmare
  //     waiting to happen to mutable ones.  Don't go slapping this on immutable wrappers for
  // mutable
  //     data structures.  If all the underlying data is truly immutable, this allows you to compute
  // a
  //     reasonable toString() the first time it is needed, then return that same String without
  //     re-computing it again.  It's the internal version of a memoizer.
  //     */
  //    static LazyRef<String> lazyToString(String name, UnmodIterable iter) {
  //        if (name == null) { throw new IllegalArgumentException("Can't have a null name."); }
  //        if (iter == null) { throw new IllegalArgumentException("Can't have a null iterable."); }
  //        return LazyRef.of(() -> UnmodIterable.toString(name, iter));
  //    }

  //    /** Lets underlying compareTo method handle comparing nulls to non-null values. */
  //    static <E extends Comparable<E>> int compareHelper(E e1, E e2) {
  //        if (e1 == e2) { return 0; }
  //        if (e1 == null) {
  //            //noinspection ConstantConditions
  //            return -e2.compareTo(e1);
  //        }
  //        return e1.compareTo(e2);
  //    }
  //
  //    /** A default comparator for UnIterables comparable */
  //    static <F extends Comparable<F>,E extends UnmodIterable<F>> Comparator<E>
  //    iterableComparator() {
  //        return new Comparator<E>() {
  //            @Override
  //            public int compare(E o1, E o2) {
  //                if (o1 == null) {
  //                    if (o2 == null) {
  //                        return 0;
  //                    } else {
  //                        //noinspection ConstantConditions
  //                        return -compare(o2, o1);
  //                    }
  //                }
  //                UnmodIterator<F> as = o1.iterator();
  //                UnmodIterator<F> bs = o2.iterator();
  //                while (as.hasNext() && bs.hasNext()) {
  //                    int ret = compareHelper(as.next(), bs.next());
  //                    if (ret != 0) {
  //                        return ret;
  //                    }
  //                }
  //                // If we run out of items in one, the longer one is considered greater, just
  // like
  //                // ordering words in a dictionary.
  //                if (as.hasNext()) { return -1; }
  //                if (bs.hasNext()) { return 1; }
  //                // All items compare 0 and same number of items - these are sorted the same (and
  //                // probably equal)
  //                return 0;
  //            }
  //        };
  //    }

  /**
   * This is correct, but O(n). It also works regardless of the order of the items because a + b = b
   * + a, even when an overflow occurs.
   */
  static int hash(Iterable<?> is) {
    //        System.out.println("hashCode for: " + is);
    int ret = 0;
    for (Object t : is) {
      if (t != null) {
        //                System.out.println("\tt: " + t + " hashCode: " + t.hashCode());
        ret = ret + t.hashCode();
      }
    }
    return ret;
  }

  /** Computes a reasonable to-string. */
  static String toString(String name, Iterable<?> iterable) {
    StringBuilder sB = new StringBuilder();
    sB.append(name).append("(");
    int i = 0;
    Iterator<?> iter = iterable.iterator();
    while (iter.hasNext()) {
      if (i > 0) {
        sB.append(",");
      }
      //            if (i > 4) { break; }
      Object item = iter.next();
      sB.append(stringify(item));
      i++;
    }
    //        if (iter.hasNext()) {
    //            sB.append("...");
    //        }
    return sB.append(")").toString();
  }

  // ================================== Inherited from Iterable ==================================
  /**
   * A one-time use, mutable, not-thread-safe way to get each value of the underling collection in
   * turn. I experimented with various thread-safe alternatives, but the JVM is optimized around
   * iterators so this is the lowest common denominator of collection iteration, even though
   * iterators are inherently mutable.
   */
  @Override
  UnmodIterator<T> iterator();

  // =============================== Inherited from Transformable ===============================

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> concat(@Nullable Iterable<? extends T> list) {
    return Xform.of(this).concat(list);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> precat(@Nullable Iterable<? extends T> list) {
    return Xform.of(this).precat(list);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> drop(long n) {
    return Xform.of(this).drop(n);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> dropWhile(Fn1<? super T, Boolean> predicate) {
    return Xform.of(this).dropWhile(predicate);
  }

  /** {@inheritDoc} */
  @Override
  default <B> B fold(B ident, Fn2<? super B, ? super T, B> reducer) {
    return Xform.of(this).fold(ident, reducer);
  }

  /** {@inheritDoc} */
  @Override
  default <G, B> Or<G, B> foldUntil(
      G accum,
      @Nullable Fn2<? super G, ? super T, B> terminator,
      Fn2<? super G, ? super T, G> reducer) {

    return Xform.of(this).foldUntil(accum, terminator, reducer);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> filter(Fn1<? super T, Boolean> f) {
    return Xform.of(this).filter(f);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> whereNonNull() {
    return Xform.of(this).filter(Objects::nonNull);
  }

  /** {@inheritDoc} */
  @Override
  default <B> UnmodIterable<B> flatMap(Fn1<? super T, Iterable<B>> f) {
    return Xform.of(this).flatMap(f);
  }

  /** {@inheritDoc} */
  @Override
  default <B> UnmodIterable<B> map(Fn1<? super T, ? extends B> f) {
    return Xform.of(this).map(f);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> take(long numItems) {
    return Xform.of(this).take(numItems);
  }

  /** {@inheritDoc} */
  @Override
  default UnmodIterable<T> takeWhile(Fn1<? super T, Boolean> f) {
    return Xform.of(this).takeWhile(f);
  }

  /** The first item in this iterable. */
  @Override
  default Option<T> head() {
    Iterator<T> iter = iterator();
    return iter.hasNext() ? Option.some(iter.next()) : Option.none();
  }
}
