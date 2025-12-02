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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Don't implement this interface directly if you don't have to. A collection is an {@link
 * java.lang.Iterable} with a size (a size() method) and unfortunately a contains() method
 * (deprecated on Lists).
 *
 * <p>Collection defines the return of Map.values() which can have duplicates and may be ordered, or
 * unordered. For this reason, I don't think it's possible to define an equals() method on
 * Collection that works in all circumstances (when comparing it to a List, a Set, or another
 * amorphous Collection). I don't think Map.values() would exist if Generics had existed and Map had
 * implemented Iterable&lt;Entry&gt; from the beginning.
 *
 * <p>UnmodCollection is an unmodifiable version of {@link java.util.Collection} which formalizes
 * the return type of Collections.unmodifiableCollection() and Map.values().
 */
public interface UnmodCollection<E> extends Collection<E>, UnmodIterable<E>, Sized {

  // ========================================== Static ==========================================

  //    /**
  //     Don't use this.  There may not be any way to implement equals() meaningfully on a
  // Collection
  //     because the definition of Collection is too broad.
  //
  //     Implements equals and hashCode() methods to make defining unmod sets easier, especially for
  //     implementing Map.values() and such.
  //     */
  //    abstract class AbstractUnmodCollection<T> implements UnmodCollection<T> {
  //        @SuppressWarnings("unchecked")
  //        @Override public boolean equals(Object other) {
  //            if (this == other) { return true; }
  //            if ( !(other instanceof Collection) ) { return false; }
  //            Collection that = (Collection) other;
  //            if (size() != that.size()) { return false; }
  //
  //            // A set may contain all the elements of a list, plus additional elements, and have
  // the
  //            // same size as a list that contains duplicates.  Equality for lists and
  //            // sets is not the same.  Lists are ordered and have duplicates. Sets have no
  // duplicates
  //            // and may or may not be ordered.
  //            //
  //            // The only place that a Collection is returned and needs to live with an equals
  // method
  //            // is on Map.values().  It can contain duplicates, so it's not a set.  It can be
  // ordered
  //            // (as in SortedMap.values()) which could equal a List, or unordered (just
  // Map.values())
  //            // which can't be a Set because it needs to contain duplicates.  Ugh, that one
  // method
  //            // is an abomination and should not exist.  If only Map had implemented
  // Iterable<Entry>
  //            // none of this would have been necessary.
  //            //
  //            // In order to have reflexive equals, check first for legitimate child interfaces
  //            // and let the other object compare itself to this one.  I don't like the idea of
  //            // saying, "Are we equal?  I don't know.  What do you think?" because another class
  //            // could do the same thing and go into an infinite call loop (trampoline loop?).
  //            // So even though this is maybe unordered, we'll compare the random ordering to the
  //            // list.
  //            //
  //            // Hmm... Maybe there is no java.util.AbstractCollection because there is no
  // sensible
  //            // way to implement it.  Ditto why java.util.Map.values() doesn't implement equals()
  // or
  //            // hashCode().
  //
  //            // I can't imagine why containsAll would ever call equals on the parent collection,
  //            // so this should be safe from infinite call loops.
  //
  //            // Doing containsAll() both ways should ensure that duplicates are checked properly
  //            // without checking order, but it's going to likely be a little slow.  You want
  // fast?
  //            // Implement List or Set instead!
  //            return containsAll(that) && that.containsAll(this);
  //        }
  //
  //        @Override public int hashCode() { return UnmodIterable.hashCode(this); }
  //    }
  // ========================================= Instance =========================================
  // Methods are listed in the same order as the javadocs.

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default boolean add(E e) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default void clear() {
    throw new UnsupportedOperationException("Modification attempted");
  }

  // I don't think that this should be implemented here.  It's a core function so each
  // implementation
  // of the interface should implement it
  //    /**
  //     This is quick for sets O(1) or O(log n), but slow for Lists O(n).
  //
  //     {@inheritDoc}
  //     */
  //    @Override default boolean contains(Object o) {
  //        for (Object item : this) {
  //            if (Objects.equals(item, o)) { return true; }
  //        }
  //        return false;
  //    }

  /**
   * The default implementation of this method has O(this.size() + that.size()) or O(n) performance.
   * So even though contains() is impossible to implement efficiently for Lists, containsAll() has a
   * decent implementation (brute force would be O(this.size() * that.size()) or O(n^2) ).
   *
   * <p>{@inheritDoc}
   */
  @Override
  default boolean containsAll(Collection<?> c) {
    // Faster to create a HashSet and call containsAll on that because it's
    // O(this size PLUS that size), whereas looping through both would be
    // O(this size TIMES that size).

    // We're also going to check for simple cases before constructing a new HashSet...
    if (c.size() < 1) {
      return true;
    } else {
      return (size() >= 1) && new HashSet<>(this).containsAll(c);
    }
  }

  // You can't implement equals correctly for a Collection due to duplicates, ordering, and
  // the fact that List.equals(other) and Set.equals(other) both return false when other is
  // not an instance of List or Set.  This interface just isn't meant to be instantiated.
  // boolean	equals(Object o)
  // int	hashCode()

  /** {@inheritDoc} */
  @Override
  default boolean isEmpty() {
    return size() == 0;
  }

  /** An unmodifiable iterator {@inheritDoc} */
  @Override
  UnmodIterator<E> iterator();

  // default Stream<E> parallelStream()

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default boolean remove(Object o) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default boolean removeIf(Predicate<? super E> filter) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  /** Not allowed - this is supposed to be unmodifiable */
  @Override
  @Deprecated
  default boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("Modification attempted");
  }

  // int	size()
  // default Spliterator<E> spliterator()
  // default Stream<E>	stream()

  /**
   * This method goes against Josh Bloch's Item 25: "Prefer Lists to Arrays", but is provided for
   * backwards compatibility in some performance-critical situations. If you really need an array,
   * consider using the somewhat type-safe version of this method instead, but read the caveats
   * first.
   *
   * <p>{@inheritDoc}
   */
  @Override
  default Object[] toArray() {
    return this.toArray(new Object[size()]);
  }

  /**
   * This method goes against Josh Bloch's Item 25: "Prefer Lists to Arrays", but is provided for
   * backwards compatibility in some performance-critical situations. If you need to create an array
   * (you almost always do) then the best way to use this method is: <code>
   * MyThing[] things = col.toArray(new MyThing[coll.size()]);</code> Calling this method any other
   * way causes unnecessary work to be done - an extra memory allocation and potential garbage
   * collection if the passed array is too small, extra effort to fill the end of the array with
   * nulls if it is too large.
   *
   * <p>{@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  @Override
  default <T> T[] toArray(T[] as) {
    if (as.length < size()) {
      // This produced a class cast exception when the return was put into a
      // variable of type non-Object[] (like String[] or Integer[]).  To see the problem
      // you must.
      // 1. pass a smaller array so that an Object[] is created
      // 2. Force the return type to be a String[] (or other not-exactly Object)
      // Merely running Arrays.AsList() on it is not enough to get the exception.

      // Bad: watch for this in the future!
      //            as = (T[]) new Object[size()];
      as = (T[]) Array.newInstance(as.getClass().getComponentType(), size());
    }
    Iterator<E> iter = iterator();
    for (int i = 0; i < size(); i++) {
      as[i] = (T) iter.next();
    }
    if (size() < as.length) {
      Arrays.fill(as, size(), as.length, null);
    }
    return as;
  }
}
