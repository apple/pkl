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
package org.pkl.core.util;

import static org.organicdesign.fp.collections.Cowry.*;
import static org.organicdesign.fp.indent.IndentUtils.arrayString;
import static org.organicdesign.fp.indent.IndentUtils.indentSpace;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import org.organicdesign.fp.collections.BaseList;
import org.organicdesign.fp.collections.ImList;
import org.organicdesign.fp.collections.MutList;
import org.organicdesign.fp.collections.PersistentVector;
import org.organicdesign.fp.collections.UnmodIterable;
import org.organicdesign.fp.collections.UnmodSortedIterable;
import org.organicdesign.fp.collections.UnmodSortedIterator;
import org.organicdesign.fp.function.Fn0;
import org.organicdesign.fp.indent.Indented;
import org.organicdesign.fp.oneOf.Option;
import org.organicdesign.fp.tuple.Tuple2;
import org.organicdesign.fp.tuple.Tuple4;

// This file is adapted from https://github.com/GlenKPeterson/Paguro:
//
// Copyright 2016-05-28 PlanBase Inc. & Glen Peterson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * An RRB Tree is an immutable List (like Clojure's PersistentVector) that also supports random
 * inserts, deletes, and can be split and joined back together in logarithmic time. This is based on
 * the paper, "RRB-Trees: Efficient Immutable Vectors" by Phil Bagwell and Tiark Rompf, with the
 * following differences:
 *
 * <ul>
 *   <li>The Relaxed nodes can be sized between n/3 and 2n/3 (Bagwell/Rompf specify n and n-1)
 *   <li>The Join operation sticks the shorter tree unaltered into the larger tree (except for very
 *       small trees which just get concatenated).
 * </ul>
 *
 * <p>Details were filled in from the Cormen, Leiserson, Rivest &amp; Stein Algorithms book entry on
 * B-Trees. Also with an awareness of the Clojure PersistentVector by Rich Hickey. All errors are by
 * Glen Peterson.
 *
 * <h4>History (what little I know):</h4>
 *
 * 1972: B-Tree: Rudolf Bayer and Ed McCreight<br>
 * 1998: Purely Functional Data Structures: Chris Okasaki<br>
 * 2007: Clojure's Persistent Vector (and HashMap) implementations: Rich Hickey<br>
 * 2012: RRB-Tree: Phil Bagwell and Tiark Rompf<br>
 *
 * <p>Compared to other collections (timings summary from 2017-06-11):
 *
 * <ul>
 *   <li>append() - {@link ImRrbt} varies between 90% and 100% of the speed of {@link
 *       PersistentVector} (biggest difference above 100K). {@link MutRrbt} varies between 45% and
 *       80% of the speed of {@link PersistentVector.MutVector} (biggest difference from 100 to 1M).
 *   <li>get() - varies between 50% and 150% of the speed of PersistentVector (PV wins above 1K) if
 *       you build RRB using append(). If you build rrb using random inserts (worst case), it goes
 *       from 90% at 10 items down to 15% of the speed of the PV at 1M items.
 *   <li>iterate() - is about the same speed as PersistentVector
 *   <li>insert(0, item) - beats ArrayList above 1K items (worse than ArrayList below 100 items).
 *   <li>insert(random, item) - beats ArrayList above 10K items (worse than ArrayList until then).
 *   <li>O(log n) split(), join(), and remove() (not timed yet).
 * </ul>
 *
 * <p>Latest detailed timing results are <a target="_blank"
 * href="https://docs.google.com/spreadsheets/d/1D0bjfsHpmK7aJzyE2WwArlioI6w69YhZ2f-x0yM6_Z0/edit?usp=sharing">here</a>.
 */
@SuppressWarnings("WeakerAccess")
public abstract class RrbTree<E> implements BaseList<E>, Indented {

  private static final Object[] EMPTY_ARRAY = new Object[0];

  private static <T> T[] singleElementArray(T elem) {
    return (T[]) new Object[] {elem};
  }

  // Focus is like the tail in Rich Hickey's Persistent Vector, but named after the structure
  // in Scala's implementation.  Tail and focus are both designed to allow repeated appends or
  // inserts to the same area of a vector to be done in constant time.  Tail only handles appends
  // but this can handle repeated inserts to any area of a vector.

  /** Mutable version of an {@link RrbTree}. Timing information is available there. */
  public static class MutRrbt<E> extends RrbTree<E> implements MutList<E> {
    private E[] focus;
    private int focusStartIndex;
    private int focusLength;
    private Node<E> root;
    private int size;

    MutRrbt(E[] f, int fi, int fl, Node<E> r, int s) {
      focus = f;
      focusStartIndex = fi;
      focusLength = fl;
      root = r;
      size = s;
    }

    @Override
    protected MutRrbt<E> makeNew(E[] f, int fi, int fl, Node<E> r, int s) {
      return new MutRrbt<>(f, fi, fl, r, s);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public MutRrbt<E> append(E val) {
      // If our focus isn't set up for appends or if it's full, insert it into the data structure
      // where it belongs.  Then make a new focus
      if ((focusLength >= STRICT_NODE_LENGTH)
          || ((focusLength > 0) && (focusStartIndex < (size - focusLength)))) {
        root = root.pushFocus(focusStartIndex, arrayCopy(focus, focusLength, null));
        focus = (E[]) new Object[STRICT_NODE_LENGTH];
        focus[0] = val;
        focusStartIndex = size;
        focusLength = 1;
        size++;
        return this;
      }

      // TODO: 3. Make the root the first argument to RrbTree, MutRrbt and ImRrbt.

      if (focus.length <= focusLength) {
        focus = arrayCopy(focus, STRICT_NODE_LENGTH, null);
      }
      focus[focusLength] = val;
      focusLength++;
      size++;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public MutRrbt<E> appendSome(Fn0<? extends Option<E>> supplier) {
      return supplier.apply().match((it) -> append(it), () -> this);
    }

    /** {@inheritDoc} */
    @Override
    public MutRrbt<E> concat(@Nullable Iterable<? extends E> es) {
      return (MutRrbt<E>) MutList.super.concat(es);
    }

    /** {@inheritDoc} */
    @Override
    public MutRrbt<E> precat(@Nullable Iterable<? extends E> es) {
      // We could check if es is sized and has a size >= MIN_NODE_LENGTH
      // and if so, do something clever with writing chunks to the underlying tree.
      // Until then, this will handle everything almost as well.
      //
      // If we insert(0, item) in order, they will end up reversed, so we have to
      // add one to the insertion point for each item added.
      int idx = 0;
      if (es != null) {
        for (E e : es) {
          insert(idx, e);
          idx++;
        }
      }
      return this;
    }

    void debugValidate() {
      if (focusLength > STRICT_NODE_LENGTH) {
        throw new IllegalStateException(
            "focus len:"
                + focusLength
                + " gt STRICT_NODE_LENGTH:"
                + STRICT_NODE_LENGTH
                + "\n"
                + this.indentedStr(0));
      }
      int sz = root.debugValidate();
      if (sz != size - focusLength) {
        throw new IllegalStateException(
            "Size incorrect.  Root size: "
                + root.size()
                + " RrbSize: "
                + size
                + " focusLen: "
                + focusLength
                + "\n"
                + this.indentedStr(0));
      }
      if ((focusStartIndex < 0) || (focusStartIndex > size)) {
        throw new IllegalStateException("focusStartIndex out of bounds!\n" + this.indentedStr(0));
      }
      if (!root.equals(eliminateUnnecessaryAncestors(root))) {
        throw new IllegalStateException("Unnecessary ancestors!\n" + this.indentedStr(0));
      }
    }

    /** {@inheritDoc} */
    @Override
    public E get(int i) {
      if ((i < 0) || (i > size)) {
        throw new IndexOutOfBoundsException("Index: " + i + " size: " + size);
      }

      // This is a debugging assertion - can't be covered by a test.
      //        if ( (focusStartIndex < 0) || (focusStartIndex > size) ) {
      //            throw new IllegalStateException("focusStartIndex: " + focusStartIndex +
      //                                            " size: " + size);
      //        }

      if (i >= focusStartIndex) {
        int focusOffset = i - focusStartIndex;
        if (focusOffset < focusLength) {
          return focus[focusOffset];
        }
        i -= focusLength;
      }
      return root.get(i);
    }

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> immutable() {
      return new ImRrbt<>(arrayCopy(focus, focusLength, null), focusStartIndex, root, size);
    }

    /** {@inheritDoc} */
    @Override
    public String indentedStr(int indent) {
      return "RrbTree(size="
          + size
          + " fsi="
          + focusStartIndex
          + " focus="
          + arrayString(focus)
          + "\n"
          + indentSpace(indent + 8)
          + "root="
          + root.indentedStr(indent + 13)
          + ")";
    }

    /** {@inheritDoc} */
    @Override
    public MutRrbt<E> insert(int idx, E element) {
      // If the focus is full, push it into the tree and make a new one with the new element.
      if (focusLength >= STRICT_NODE_LENGTH) {
        root = root.pushFocus(focusStartIndex, arrayCopy(focus, focusLength, null));
        focus = singleElementArray(element);
        focusStartIndex = idx;
        focusLength = 1;
        size++;
        return this;
      }

      // If we have no focus, add a new one at the ideal spot.
      // TODO: Make sure Immutable does this too.
      if (focusLength == 0) {
        focus = singleElementArray(element);
        focusStartIndex = idx;
        focusLength = 1;
        size++;
        return this;
      }

      // If the index is within the focus, add the item there.
      int diff = idx - focusStartIndex;

      if ((diff >= 0) && (diff <= focusLength)) {
        // Here focus length cannot be zero!
        // We want to double the length each time up to STRICT_NODE_LENGTH
        // because there is no guarantee that the next insert will be in the same
        // place, so this hedges our bets.
        if (focus.length <= focusLength) {
          int newLen =
              (focusLength >= HALF_STRICT_NODE_LENGTH)
                  ? STRICT_NODE_LENGTH
                  : focusLength << 1; // double size.
          focus = arrayCopy(focus, newLen, null);
        }
        // Shift existing items past insertion index to the right
        int numItemsToShift = focusLength - diff;
        //                   src, srcPos, dest, destPos,  length
        if (numItemsToShift > 0) {
          System.arraycopy(focus, diff, focus, diff + 1, numItemsToShift);
        }
        // Put new item into the focus.
        focus[diff] = element;
        focusLength++;
        size++;
        return this;
      }

      // Here we are left with an insert somewhere else than the current focus.
      // Here the mutable version has a focus that's longer than the number of items used,
      // So we need to shorten it before pushing it into the tree.
      if (focusLength > 0) {
        root = root.pushFocus(focusStartIndex, arrayCopy(focus, focusLength, null));
      }
      focus = singleElementArray(element);
      focusStartIndex = idx;
      focusLength = 1;
      size++;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public UnmodSortedIterator<E> iterator() {
      return new Iter(pushFocus());
    }

    /** {@inheritDoc} */
    @Override
    Node<E> pushFocus() {
      return (focusLength == 0)
          ? root
          : root.pushFocus(focusStartIndex, arrayCopy(focus, focusLength, null));
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return UnmodIterable.toString("MutRrbt", this);
    }

    /**
     * Joins the given tree to the right side of this tree (or this to the left side of that one) in
     * something like O(log n) time.
     */
    public MutRrbt<E> join(RrbTree<E> that) {
      return (MutRrbt<E>) super.join(that);
    }

    /** {@inheritDoc} */
    @Override
    public MutRrbt<E> replace(int index, E item) {
      if ((index < 0) || (index > size)) {
        throw new IndexOutOfBoundsException("Index: " + index + " size: " + size);
      }
      if (index >= focusStartIndex) {
        int focusOffset = index - focusStartIndex;
        if (focusOffset < focusLength) {
          focus[focusOffset] = item;
          return this;
        }
        index -= focusLength;
      }
      // About to do replace with maybe-adjusted index
      root = root.replace(index, item);
      return this;
    }

    /** {@inheritDoc} */
    public MutRrbt<E> without(int index) {
      return (MutRrbt<E>) super.without(index);
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    protected MutRrbt<E> mt() {
      return emptyMutable();
    }

    /**
     * Divides this RRB-Tree such that every index less-than the given index ends up in the
     * left-hand tree and the indexed item and all subsequent ones end up in the right-hand tree.
     *
     * @param splitIndex the split point (excluded from the left-tree, included in the right one)
     * @return two new sub-trees as determined by the split point. If the point is 0 or this.size()
     *     one tree will be empty (but never null).
     */
    @SuppressWarnings("unchecked")
    @Override
    public Tuple2<MutRrbt<E>, MutRrbt<E>> split(int splitIndex) {
      return (Tuple2<MutRrbt<E>, MutRrbt<E>>) super.split(splitIndex);
    }
  }

  /** Immutable version of an {@link RrbTree}. Timing information is available there. */
  public static class ImRrbt<E> extends RrbTree<E> implements ImList<E>, Serializable {
    private final E[] focus;
    private final int focusStartIndex;
    private final transient Node<E> root;
    private final int size;

    ImRrbt(E[] f, int fi, Node<E> r, int s) {
      focus = f;
      focusStartIndex = fi;
      root = r;
      size = s;
    }

    @Override
    protected ImRrbt<E> makeNew(E[] f, int fi, int fl, Node<E> r, int s) {
      return new ImRrbt<>(f, fi, r, s);
    }

    // ===================================== Serialization =====================================
    // This class has a custom serialized form designed to be as small as possible.  It does not
    // have the same internal structure as an instance of this class.

    // For serializable.  Make sure to change whenever internal data format changes.
    private static final long serialVersionUID = 20170625165600L;

    // Check out Josh Bloch Item 78, p. 312 for an explanation of what's going on here.
    private static class SerializationProxy<E> implements Serializable {
      // For serializable.  Make sure to change whenever internal data format changes.
      private static final long serialVersionUID = 20160904155600L;

      private final int size;
      private transient RrbTree<E> rrbTree;

      SerializationProxy(RrbTree<E> v) {
        size = v.size();
        rrbTree = v;
      }

      // Taken from Josh Bloch Item 75, p. 298
      private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        // Write out all elements in the proper order
        for (E entry : rrbTree) {
          s.writeObject(entry);
        }
      }

      @SuppressWarnings("unchecked")
      private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        MutRrbt<E> temp = emptyMutable();
        for (int i = 0; i < size; i++) {
          temp.append((E) s.readObject());
        }
        rrbTree = temp.immutable();
      }

      private Object readResolve() {
        return rrbTree;
      }
    }

    private Object writeReplace() {
      return new SerializationProxy<>(this);
    }

    private void readObject(java.io.ObjectInputStream in)
        throws IOException, ClassNotFoundException {
      throw new InvalidObjectException("Proxy required");
    }

    // =================================== Instance Methods ===================================

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> append(E val) {
      // If our focus isn't set up for appends or if it's full, insert it into the data
      // structure where it belongs.  Then make a new focus
      if ((focus.length >= STRICT_NODE_LENGTH)
          || ((focus.length > 0) && (focusStartIndex < (size - focus.length)))) {
        Node<E> newRoot = root.pushFocus(focusStartIndex, focus);
        return new ImRrbt<>(singleElementArray(val), size, newRoot, size + 1);
      }
      return new ImRrbt<>(
          insertIntoArrayAt(val, focus, focus.length, null), focusStartIndex, root, size + 1);
    }

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> appendSome(Fn0<? extends Option<E>> supplier) {
      return supplier.apply().match((it) -> append(it), () -> this);
    }

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> concat(@Nullable Iterable<? extends E> es) {
      return this.mutable().concat(es).immutable();
    }

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> precat(@Nullable Iterable<? extends E> es) {
      return this.mutable().precat(es).immutable();
    }

    void debugValidate() {
      if (focus.length > STRICT_NODE_LENGTH) {
        throw new IllegalStateException(
            "focus len:"
                + focus.length
                + " gt STRICT_NODE_LENGTH:"
                + STRICT_NODE_LENGTH
                + "\n"
                + this.indentedStr(0));
      }
      int sz = root.debugValidate();
      if (sz != size - focus.length) {
        throw new IllegalStateException(
            "Size incorrect.  Root size: "
                + root.size()
                + " RrbSize: "
                + size
                + " focusLen: "
                + focus.length
                + "\n"
                + this.indentedStr(0));
      }
      if ((focusStartIndex < 0) || (focusStartIndex > size)) {
        throw new IllegalStateException("focusStartIndex out of bounds!\n" + this.indentedStr(0));
      }
      if (!root.equals(eliminateUnnecessaryAncestors(root))) {
        throw new IllegalStateException("Unnecessary ancestors!\n" + this.indentedStr(0));
      }
    }

    /** {@inheritDoc} */
    @Override
    public E get(int i) {
      if ((i < 0) || (i > size)) {
        throw new IndexOutOfBoundsException("Index: " + i + " size: " + size);
      }

      // This is a debugging assertion - can't be covered by a test.
      //        if ( (focusStartIndex < 0) || (focusStartIndex > size) ) {
      //            throw new IllegalStateException("focusStartIndex: " + focusStartIndex +
      //                                            " size: " + size);
      //        }

      if (i >= focusStartIndex) {
        int focusOffset = i - focusStartIndex;
        if (focusOffset < focus.length) {
          return focus[focusOffset];
        }
        i -= focus.length;
      }
      return root.get(i);
    }

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> insert(int idx, E element) {
      // If the focus is full, push it into the tree and make a new one with the new element.
      if (focus.length >= STRICT_NODE_LENGTH) {
        Node<E> newRoot = root.pushFocus(focusStartIndex, focus);
        E[] newFocus = singleElementArray(element);
        return new ImRrbt<>(newFocus, idx, newRoot, size + 1);
      }

      // If the index is within the focus, add the item there.
      int diff = idx - focusStartIndex;

      if ((diff >= 0) && (diff <= focus.length)) {
        // new focus
        E[] newFocus = insertIntoArrayAt(element, focus, diff, null);
        return new ImRrbt<>(newFocus, focusStartIndex, root, size + 1);
      }

      // Here we are left with an insert somewhere else than the current focus.
      Node<E> newRoot = focus.length > 0 ? root.pushFocus(focusStartIndex, focus) : root;
      E[] newFocus = singleElementArray(element);
      return new ImRrbt<>(newFocus, idx, newRoot, size + 1);
    }

    /** {@inheritDoc} */
    @Override
    public MutRrbt<E> mutable() {
      // TODO: Should we defensively copy the root as well?
      return new MutRrbt<>(
          arrayCopy(focus, focus.length, null), focusStartIndex, focus.length, root, size);
    }

    /** {@inheritDoc} */
    @Override
    public UnmodSortedIterator<E> iterator() {
      return new Iter(pushFocus());
    }

    /** {@inheritDoc} */
    @Override
    Node<E> pushFocus() {
      return (focus.length == 0) ? root : root.pushFocus(focusStartIndex, focus);
    }

    /**
     * Joins the given tree to the right side of this tree (or this to the left side of that one) in
     * something like O(log n) time.
     */
    public ImRrbt<E> join(RrbTree<E> that) {
      return (ImRrbt<E>) super.join(that);
    }

    /** {@inheritDoc} */
    @Override
    public ImRrbt<E> replace(int index, E item) {
      if ((index < 0) || (index > size)) {
        throw new IndexOutOfBoundsException("Index: " + index + " size: " + size);
      }
      if (index >= focusStartIndex) {
        int focusOffset = index - focusStartIndex;
        if (focusOffset < focus.length) {
          return new ImRrbt<>(
              replaceInArrayAt(item, focus, focusOffset, null), focusStartIndex, root, size);
        }
        index -= focus.length;
      }
      // About to do replace with maybe-adjusted index
      return new ImRrbt<>(focus, focusStartIndex, root.replace(index, item), size);
    }

    /** {@inheritDoc} */
    public ImRrbt<E> without(int index) {
      return (ImRrbt<E>) super.without(index);
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    protected ImRrbt<E> mt() {
      return empty();
    }

    /**
     * Divides this RRB-Tree such that every index less-than the given index ends up in the
     * left-hand tree and the indexed item and all subsequent ones end up in the right-hand tree.
     *
     * @param splitIndex the split point (excluded from the left-tree, included in the right one)
     * @return two new sub-trees as determined by the split point. If the point is 0 or this.size()
     *     one tree will be empty (but never null).
     */
    @SuppressWarnings("unchecked")
    @Override
    public Tuple2<ImRrbt<E>, ImRrbt<E>> split(int splitIndex) {
      return (Tuple2<ImRrbt<E>, ImRrbt<E>>) super.split(splitIndex);
    }

    /** {@inheritDoc} */
    @Override
    public String indentedStr(int indent) {
      return "RrbTree(size="
          + size
          + " fsi="
          + focusStartIndex
          + " focus="
          + arrayString(focus)
          + "\n"
          + indentSpace(indent + 8)
          + "root="
          + root.indentedStr(indent + 13)
          + ")";
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return UnmodIterable.toString("ImRrbt", this);
    }

    @SuppressWarnings("rawtypes")
    private static final ImRrbt EMPTY_IM_RRBT = new ImRrbt<>(emptyArray(), 0, emptyLeaf(), 0);
  }

  /** Returns the empty, immutable RRB-Tree (there is only one) */
  @SuppressWarnings("unchecked")
  public static <T> ImRrbt<T> empty() {
    return (ImRrbt<T>) ImRrbt.EMPTY_IM_RRBT;
  }

  /** Returns the empty, mutable RRB-Tree (there is only one) */
  @SuppressWarnings("unchecked")
  public static <T> MutRrbt<T> emptyMutable() {
    return (MutRrbt<T>) empty().mutable();
  }

  // ===================================== Instance Methods =====================================

  /** {@inheritDoc} */
  @Override
  public abstract RrbTree<E> append(E t);

  /** {@inheritDoc} */
  @Override
  public abstract RrbTree<E> appendSome(Fn0<? extends Option<E>> supplier);

  /** Internal validation method for testing. */
  abstract void debugValidate();

  /** {@inheritDoc} */
  @Override
  public abstract E get(int i);

  /**
   * Inserts an item in the RRB tree pushing the current element at that index and all subsequent
   * elements to the right.
   *
   * @param idx the insertion point
   * @param element the item to insert
   * @return a new RRB-Tree with the item inserted.
   */
  @SuppressWarnings("WeakerAccess")
  public abstract RrbTree<E> insert(int idx, E element);

  /** {@inheritDoc} */
  @Override
  public abstract UnmodSortedIterator<E> iterator();

  /*
  I'm implementing something like the [Bagwell/Rompf RRB-Tree][1] and I'm a little unsatisfied with
  the details of the join/merge algorithm.  I wonder if there's a standard way to do this that they
  assume that I know (I don't), or if someone has come up with a better way to do this.

  My basic approach was to fit the shorter tree into the left-most or right-most leg of the taller
  tree at the correct height.  For `a.join(b)`, if `b` is taller, fit `a` into `b`'s left-most leg at
  the right height, otherwise fit `b` into `a`'s right-most leg at the right height

  Overview:

  1. Push the focus of both trees, so we don't have to worry about it.

  2. Find the height of each tree.

  3. Stick the shorter tree into the proper level of the larger tree (on the left or right as
  appropriate).  If the leftmost/rightmost node at the proper level of the larger tree is full,
  add a "skinny leg" (a new root with a single child) to the short tree and stick it on the left or
  right one level up in the large one.  If the large tree is packed, several skinny-leg insertion
  attempts may be required, or even a new root added to the large tree with 2 children: the old
  large tree and the smaller tree on the appropriate side of it.

  Optimization: If one tree is really small, we could do an append or prepend.

  I don't see the advantage of zipping nodes together at every level the very complicated
  way it seems to do in the paper.  Even after all that work, it still has nodes of varying sizes and
  involves changing more nodes than maybe necessary.

    [1]: https://infoscience.epfl.ch/record/169879/files/RMTrees.pdf
  */

  /**
   * Joins the given tree to the right side of this tree (or this to the left side of that one) in
   * something like O(log n) time.
   */
  public RrbTree<E> join(RrbTree<E> that) {
    // We don't want to wonder below if we're inserting leaves or branch-nodes.
    // Also, it leaves the tree cleaner to just smash leaves onto the bigger tree.
    // Ultimately, we might want to see if we can grab the tail and stick it where it
    // belongs but for now, this should be alright.
    if (that.size() <= MAX_NODE_LENGTH) {
      return (RrbTree<E>) concat(that);
    }
    if (this.size() <= MAX_NODE_LENGTH) {
      for (int i = 0; i < size(); i++) {
        that = that.insert(i, this.get(i));
      }
      return that;
    }

    // OK, here we've eliminated the case of merging a leaf into a tree.  We only have to
    // deal with tree-into-tree merges below.
    //
    // Note that if the right-hand tree is bigger, we'll effectively add this tree to the
    // left-hand side of that one.  It's logically the same as adding that tree to the right
    // of this, but the mechanism by which it happens is a little different.
    Node<E> leftRoot = pushFocus();
    Node<E> rightRoot = that.pushFocus();

    //        if (leftRoot != eliminateUnnecessaryAncestors(leftRoot)) {
    //            throw new IllegalStateException("Left had unnecessary ancestors!");
    //        }

    //        if (rightRoot != eliminateUnnecessaryAncestors(rightRoot)) {
    //            throw new IllegalStateException("Right had unnecessary ancestors!");
    //        }

    // Whether to add the right tree to the left one (true) or vice-versa (false).
    // True also means left is taller, false: right is taller.
    boolean leftIntoRight = leftRoot.height() < rightRoot.height();
    Node<E> taller = leftIntoRight ? rightRoot : leftRoot;
    Node<E> shorter = leftIntoRight ? leftRoot : rightRoot;

    // Most compact: Descend the taller tree to shorter.height and find room for all
    //     shorter children as children of that node.
    //
    // Next: add the shorter node, unchanged, as a child to the taller tree at
    //       shorter.height + 1
    //
    // If that level of the taller tree is full, add an ancestor to the shorter node and try to
    // fit at the next level up in the taller tree.
    //
    // If this brings us to the top of the taller tree (both trees are the same height), add a
    // new parent node with leftRoot and rightRoot as children

    // Walk down the taller tree to one below the shorter, remembering ancestors.
    Node<E> n = taller;

    // This is the maximum we can descend into the taller tree (before running out of tree)
    //        int maxDescent = taller.height() - 1;

    // Actual amount we're going to descend.
    int descentDepth = taller.height() - shorter.height();
    //        if ( (descentDepth < 0) || (descentDepth >= taller.height()) ) {
    //            throw new IllegalStateException("Illegal descent depth: " + descentDepth);
    //        }
    Node<E>[] ancestors = genericNodeArray(descentDepth);
    int i = 0;
    for (; i < ancestors.length; i++) {
      // Add an ancestor to array
      ancestors[i] = n;
      //            if (n instanceof Leaf) {
      //                throw new IllegalStateException("Somehow found a leaf node");
      //            }
      n = n.endChild(leftIntoRight);
    }
    // i is incremented before leaving the loop, so decrement it here to make it point
    // to ancestors.length - 1;
    i--;

    //        if (n.height() != shorter.height()) {
    //            throw new IllegalStateException("Didn't get to proper height");
    //        }

    // Most compact: Descend the taller tree to shorter.height and find room for all
    //     shorter children as children of that node.
    if (n.thisNodeHasRelaxedCapacity(shorter.numChildren())) {
      // Adding kids of shorter to proper level of taller...
      Node<E>[] kids;
      kids = ((Relaxed<E>) shorter).nodes;
      n = n.addEndChildren(leftIntoRight, kids);
      //                System.out.println("n0=" + n.indentedStr(3));
    }

    if (i >= 0) {
      // Go back up one after lowest check.
      n = ancestors[i];
      //                System.out.println("n1=" + n.indentedStr(3));
      i--;
      //            if (n.height() != shorter.height() + 1) {
      //                throw new IllegalStateException("Didn't go back up enough");
      //            }
    }

    // TODO: Is this used?
    // While nodes in the taller are full, add a parent to the shorter and try the next level
    // up.
    while (!n.thisNodeHasRelaxedCapacity(1) && (i >= 0)) {

      // no room for short at this level (n has too many kids)
      n = ancestors[i];
      i--;

      shorter = addAncestor(shorter);
      //            shorter.debugValidate();

      // Sometimes we care about which is shorter and sometimes about left and right.
      // Since we fixed the shorter tree, we have to update the left/right
      // pointer to point to the new shorter.
      if (leftIntoRight) {
        leftRoot = shorter;
      } else {
        rightRoot = shorter;
      }
    }

    // Here we either have 2 trees of equal height, or
    // we have room in n for the shorter as a child.

    if (shorter.height() == (n.height() - 1)) {
      //            if (!n.thisNodeHasRelaxedCapacity(1)) {
      //                throw new IllegalStateException("somehow got here without relaxed
      // capacity...");
      //            }
      // Shorter one level below n and there's room
      // Trees are not equal height and there's room somewhere.
      n = n.addEndChild(leftIntoRight, shorter);
      //                System.out.println("n2=" + n.indentedStr(3));
      //            n.debugValidate();
    } else if (i < 0) {
      // 2 trees of equal height: make a new parent
      //            if (shorter.height() != n.height()) {
      //                throw new IllegalStateException("Expected trees of equal height");
      //            }

      @SuppressWarnings("unchecked") // Need raw types here.
      Node<E>[] newRootArray = new Node[] {leftRoot, rightRoot};
      int leftSize = leftRoot.size();
      Node<E> newRoot =
          new Relaxed<>(new int[] {leftSize, leftSize + rightRoot.size()}, newRootArray);
      //            newRoot.debugValidate();
      return makeNew(emptyArray(), 0, 0, newRoot, newRoot.size());
    } else {
      throw new IllegalStateException("How did we get here?");
    }

    // We've merged the nodes.  Now see if we need to create new parents
    // to hold the changed sub-nodes...
    while (i >= 0) {
      Node<E> anc = ancestors[i];
      // By definition, I think that if we need a new root node, then we aren't dealing with

      // leaf nodes, but I could be wrong.
      // I also think we should get rid of relaxed nodes and everything will be much
      // easier.
      Relaxed<E> rel = (Relaxed<E>) anc;

      int repIdx = leftIntoRight ? 0 : rel.numChildren() - 1;
      n =
          Relaxed.replaceInRelaxedAt(
              rel.cumulativeSizes, rel.nodes, n, repIdx, n.size() - rel.nodes[repIdx].size());
      i--;
    }

    //        n.debugValidate();
    return makeNew(emptyArray(), 0, 0, n, n.size());
  }

  /**
   * Allows removing duplicated code by letting super-class produce new members of subclass types.
   */
  protected abstract RrbTree<E> makeNew(E[] f, int fi, int fl, Node<E> r, int s);

  /** Creates a new empty ("M-T") tree of the appropriate (mutable/immutable) type. */
  protected abstract RrbTree<E> mt();

  /**
   * {@inheritDoc} Precat is implemented here because it is a very cheap operation on an RRB-Tree.
   * It's not implemented on PersistentVector because it is very expensive there.
   */
  @Override
  public abstract RrbTree<E> precat(@Nullable Iterable<? extends E> es);

  /** Internal method - do not use. */
  abstract Node<E> pushFocus();

  /** {@inheritDoc} */
  @Override
  public abstract RrbTree<E> replace(int index, E item);

  /** {@inheritDoc} */
  @Override
  public abstract int size();

  /**
   * Divides this RRB-Tree such that every index less-than the given index ends up in the left-hand
   * tree and the indexed item and all subsequent ones end up in the right-hand tree.
   *
   * @param splitIndex the split point (excluded from the left-tree, included in the right one)
   * @return two new sub-trees as determined by the split point. If the point is 0 or this.size()
   *     one tree will be empty (but never null).
   */
  public Tuple2<? extends RrbTree<E>, ? extends RrbTree<E>> split(int splitIndex) {
    if (splitIndex < 1) {
      if (splitIndex == 0) {
        return Tuple2.of(mt(), this);
      } else {
        throw new IndexOutOfBoundsException("Constraint violation failed: 1 <= splitIndex <= size");
      }
    } else if (splitIndex >= size()) {
      if (splitIndex == size()) {
        return Tuple2.of(this, mt());
      } else {
        throw new IndexOutOfBoundsException("Constraint violation failed: 1 <= splitIndex <= size");
      }
    }
    // Push the focus before splitting.
    Node<E> newRoot = pushFocus();

    // If a leaf-node is split, the fragments become the new focus for each side of the split.
    // Otherwise, the focus can be left empty, or the last node of each side can be made into
    // the focus.

    SplitNode<E> split = newRoot.splitAt(splitIndex);

    //        split.left().debugValidate();
    //        split.right().debugValidate();

    E[] lFocus = split.leftFocus();
    Node<E> left = eliminateUnnecessaryAncestors(split.left());

    E[] rFocus = split.rightFocus();
    Node<E> right = eliminateUnnecessaryAncestors(split.right());

    // These branches are identical, just different classes.
    return Tuple2.of(
        makeNew(lFocus, left.size(), lFocus.length, left, left.size() + lFocus.length),
        makeNew(rFocus, 0, rFocus.length, right, right.size() + rFocus.length));
  }

  /**
   * Returns a new RrbTree minus the given item (all items to the right are shifted left one) This
   * is O(log n).
   */
  public RrbTree<E> without(int index) {
    if ((index > 0) && (index < size() - 1)) {
      Tuple2<? extends RrbTree<E>, ? extends RrbTree<E>> s1 = split(index);
      Tuple2<? extends RrbTree<E>, ? extends RrbTree<E>> s2 = s1._2().split(1);
      return s1._1().join(s2._2());
    } else if (index == 0) {
      return split(1)._2();
    } else if (index == size() - 1) {
      return split(size() - 1)._1();
    } else {
      throw new IndexOutOfBoundsException("Failed test: 0 <= index < size");
    }
  }

  private static <E> Node<E> eliminateUnnecessaryAncestors(Node<E> n) {
    while (!(n instanceof Leaf) && (n.numChildren() == 1)) {
      n = n.child(0);
    }
    return n;
  }

  @SuppressWarnings("unchecked")
  private static <E> Node<E> addAncestor(Node<E> n) {
    return new Relaxed<>(new int[] {n.size()}, (Node<E>[]) new Node[] {n});
  }

  // ================================== Standard Object Methods ==================================

  /** {@inheritDoc} */
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof List)) {
      return false;
    }
    List<? extends E> that = (List<? extends E>) other;
    return (this.size() == that.size())
        && UnmodSortedIterable.equal(this, UnmodSortedIterable.castFromList(that));
  }

  /** This implementation is correct and compatible with java.util.AbstractList, but O(n). */
  @Override
  public int hashCode() {
    int ret = 1;
    for (E item : this) {
      ret *= 31;
      if (item != null) {
        ret += item.hashCode();
      }
    }
    return ret;
  }

  /** {@inheritDoc} */
  @Override
  public abstract String indentedStr(int indent);

  // ================================== Implementation Details ==================================

  // Definitions:
  // Relaxed: short for "Relaxed Radix."   Relaxed nodes are of somewhat varying sizes, ranging
  //          from MIN_NODE_LENGTH (Cormen et al. calls this "Minimum Degree") to MAX_NODE_LENGTH.
  //          This requires linear interpolation, a bit of searching, and subtraction to find an
  //          index into a sub-node, but supports inserts, split, and combine (with another
  //          RrbTree)

  // There's bit shifting going on here because it's a very fast operation.
  // Shifting right by 5 is eons faster than dividing by 32.
  private static final int NODE_LENGTH_POW_2 = 5; // 2 for testing, 5 for real

  // 0b00000000000000000000000000100000 = 0x20 = 32
  static final int STRICT_NODE_LENGTH = 1 << NODE_LENGTH_POW_2;

  private static final int HALF_STRICT_NODE_LENGTH = STRICT_NODE_LENGTH >> 1;

  // (MIN_NODE_LENGTH + MAX_NODE_LENGTH) / 2 should equal STRICT_NODE_LENGTH so that they have
  // roughly the
  // same average node size to make the index interpolation easier.
  private static final int MIN_NODE_LENGTH = (STRICT_NODE_LENGTH + 1) * 2 / 3;
  // Always check if less-than this.  Never if less-than-or-equal.  Cormen adds a -1 here and tests
  // for <= (I think!).
  private static final int MAX_NODE_LENGTH = ((STRICT_NODE_LENGTH + 1) * 4 / 3);

  @SuppressWarnings("rawtypes")
  private static final Leaf EMPTY_LEAF = new Leaf<>(EMPTY_ARRAY);

  @SuppressWarnings("unchecked")
  private static <T> Leaf<T> emptyLeaf() {
    return (Leaf<T>) EMPTY_LEAF;
  }

  // ================================ Node private inner classes ================================

  private interface Node<T> extends Indented {
    /** Returns the immediate child node at the given index. */
    Node<T> child(int childIdx);

    int debugValidate();

    /** Returns the leftMost (first) or right-most (last) child */
    Node<T> endChild(boolean leftMost);

    /** Adds a node as the first/leftmost or last/rightmost child */
    Node<T> addEndChild(boolean leftMost, Node<T> shorter);

    /** Adds kids as leftmost or rightmost of current children */
    Node<T> addEndChildren(boolean leftMost, Node<T>[] newKids);

    /** Return the item at the given index */
    T get(int i);

    /** Returns the maximum depth below this node. Leaf nodes are height 1. */
    int height();

    //        /** Try to add all sub-nodes to this one. */
    //        Node<T> join(Node<T> that);

    /** Number of items stored in this node */
    int size();

    //        /** Returns true if this node's array is not full */
    //        boolean thisNodeHasCapacity();

    /** Can this node take the specified number of children? */
    boolean thisNodeHasRelaxedCapacity(int numItems);

    /**
     * Can we put focus at the given index without reshuffling nodes?
     *
     * @param index the index we want to insert at
     * @param size the number of items to insert. Must be size < MAX_NODE_LENGTH
     * @return true if we can do so without otherwise adjusting the tree.
     */
    boolean hasRelaxedCapacity(int index, int size);

    // Splitting a strict node yields an invalid Relaxed node (too short).
    // We don't yet split Leaf nodes.
    // So this needs to only be implemented on Relaxed for now.
    //        Relaxed<T>[] split();

    /** Returns the number of immediate children of this node, not all descendants. */
    int numChildren();

    // Because we want to append/insert into the focus as much as possible, we will treat
    // the insert or append of a single item as a degenerate case.  Instead, the primary way
    // to add to the internal data structure will be to push the entire focus array into it
    Node<T> pushFocus(int index, T[] oldFocus);

    Node<T> replace(int idx, T t);

    SplitNode<T> splitAt(int splitIndex);
  }

  //    private interface BranchNode<T> extends Node<T> {
  //    }

  //    /** For calcCumulativeSizes */
  //    private static final class CumulativeSizes {
  //        int szSoFar; // Size so far (of all things to left)
  //        int srcOffset; // offset in source array
  //        int[] destArray; // the destination array
  //        int destPos; // offset in destArray to copy to
  //        int length; // number of items to copy.
  //    }

  /** This class is the return type when splitting a node. */
  private static class SplitNode<T> extends Tuple4<Node<T>, T[], Node<T>, T[]> implements Indented {
    /**
     * Constructor.
     *
     * @param ln Left-hand whole-node
     * @param lf Left-focus (leftover items from left node)
     * @param rn Right-hand whole-node
     * @param rf Right-focus (leftover items from right node)
     */
    SplitNode(Node<T> ln, T[] lf, Node<T> rn, T[] rf) {
      super(ln, lf, rn, rf);
      //            if (lf.length > STRICT_NODE_LENGTH) {
      //                throw new IllegalStateException("Left focus too long: " + arrayString(lf));
      //            }
      //            if (rf.length > STRICT_NODE_LENGTH) {
      //                throw new IllegalStateException("Right focus too long: " + arrayString(rf));
      //            }
    }

    public Node<T> left() {
      return _1;
    }

    public T[] leftFocus() {
      return _2;
    }

    public Node<T> right() {
      return _3;
    }

    public T[] rightFocus() {
      return _4;
    }

    public int size() {
      return _1.size() + _2.length + _3.size() + _4.length;
    }

    @Override
    public String indentedStr(int indent) {
      StringBuilder sB =
          new StringBuilder() // indentSpace(indent)
              .append("SplitNode(");
      int nextIndent = indent + sB.length();
      String nextIndentStr = indentSpace(nextIndent).toString();
      return sB.append("left=")
          .append(left().indentedStr(nextIndent + 5))
          .append(",\n")
          .append(nextIndentStr)
          .append("leftFocus=")
          .append(arrayString(leftFocus()))
          .append(",\n")
          .append(nextIndentStr)
          .append("right=")
          .append(right().indentedStr(nextIndent + 6))
          .append(",\n")
          .append(nextIndentStr)
          .append("rightFocus=")
          .append(arrayString(rightFocus()))
          .append(")")
          .toString();
    }

    @Override
    public String toString() {
      return indentedStr(0);
    }
  }

  private static class Leaf<T> implements Node<T> {
    final T[] items;

    Leaf(T[] ts) {
      items = ts;
    }

    @Override
    public Node<T> child(int childIdx) {
      throw new UnsupportedOperationException("Don't call this on a leaf");
    }

    @Override
    public int debugValidate() {
      if (items.length == 0) {
        return 0;
      }
      if (items.length < MIN_NODE_LENGTH) {
        throw new IllegalStateException("Leaf too short!\n" + this.indentedStr(0));
      } else if (items.length >= MAX_NODE_LENGTH) {
        throw new IllegalStateException("Leaf too long!\n" + this.indentedStr(0));
      }
      return items.length;
    }

    /** Returns the leftMost (first) or right-most (last) child */
    @Override
    public Node<T> endChild(boolean leftMost) {
      throw new UnsupportedOperationException("Don't call this on a leaf");
    }

    /** Adds a node as the first/leftmost or last/rightmost child */
    @Override
    public Node<T> addEndChild(boolean leftMost, Node<T> shorter) {
      throw new UnsupportedOperationException("Don't call this on a leaf");
    }

    /** Adds kids as leftmost or rightmost of current children */
    @Override
    public Node<T> addEndChildren(boolean leftMost, Node<T>[] newKids) {
      throw new UnsupportedOperationException("Don't call this on a leaf");
    }

    @Override
    public T get(int i) {
      return items[i];
    }

    @Override
    public int height() {
      return 1;
    }

    @Override
    public int size() {
      return items.length;
    }

    // If we want to add one more to an existing leaf node, it must already be part of a
    // relaxed tree.
    //        public boolean thisNodeHasCapacity() { return items.length < MAX_NODE_LENGTH; }

    @Override
    public boolean hasRelaxedCapacity(int index, int size) {
      // Appends and prepends need to be a good size, but random inserts do not.
      //            if ( (size < 1) || (size >= MAX_NODE_LENGTH) ) {
      //                throw new IllegalArgumentException("Bad size: " + size);
      //              // + " MIN_NODE_LENGTH=" + MIN_NODE_LENGTH + " MAX_NODE_LENGTH=" +
      // MAX_NODE_LENGTH);
      //            }
      return (items.length + size) < MAX_NODE_LENGTH;
    }

    @Override
    public SplitNode<T> splitAt(int splitIndex) {
      //            if (splitIndex < 0) {
      //                throw new IllegalArgumentException("Called splitAt when splitIndex < 0");
      //            }
      //            if (splitIndex > items.length - 1) {
      //                throw new IllegalArgumentException("Called splitAt when splitIndex >
      // orig.length - 1");
      //            }

      // Should we just ensure that the split is between 1 and items.length (exclusive)?
      if (splitIndex == 0) {
        return new SplitNode<>(emptyLeaf(), emptyArray(), emptyLeaf(), items);
      }
      if (splitIndex == items.length) {
        return new SplitNode<>(emptyLeaf(), items, emptyLeaf(), emptyArray());
      }
      Tuple2<T[], T[]> split = splitArray(items, splitIndex);
      T[] splitL = split._1();
      T[] splitR = split._2();
      Leaf<T> leafL = emptyLeaf();
      Leaf<T> leafR = emptyLeaf();
      if (splitL.length > STRICT_NODE_LENGTH) {
        leafL = new Leaf<>(splitL);
        splitL = emptyArray();
      }
      if (splitR.length > STRICT_NODE_LENGTH) {
        leafR = new Leaf<>(splitR);
        splitR = emptyArray();
      }
      return new SplitNode<>(leafL, splitL, leafR, splitR);
    }

    @SuppressWarnings("unchecked")
    private Leaf<T>[] spliceAndSplit(T[] oldFocus, int splitIndex) {
      // Consider optimizing:
      T[] newItems = spliceIntoArrayAt(oldFocus, items, splitIndex, null);
      //                                             (Class<T>) items[0].getClass());

      // Shift right one is divide-by 2.
      Tuple2<T[], T[]> split = splitArray(newItems, newItems.length >> 1);

      return new Leaf[] {new Leaf<>(split._1()), new Leaf<>(split._2())};
    }

    @Override
    public int numChildren() {
      return size();
    }

    // I think this can only be called when the root node is a leaf.
    @Override
    public Node<T> pushFocus(int index, T[] oldFocus) {
      //            if (oldFocus.length == 0) {
      //                throw new IllegalStateException("Never call this with an empty focus!");
      //            }
      // We put the empty Leaf as the root of the empty vector.  It stays there
      // until the first call to this method, at which point, the oldFocus becomes the
      // new root.
      if (items.length == 0) {
        return new Leaf<>(oldFocus);
      }

      if ((items.length + oldFocus.length) < MAX_NODE_LENGTH) {
        return new Leaf<>(spliceIntoArrayAt(oldFocus, items, index, null));
        //                                                    (Class<T>) items[0].getClass()));
      }

      // We should only get here when the root node is a leaf.
      // Maybe we should be more circumspect with our array creation, but for now, just
      // jam it into one big array, then split it up for simplicity
      Leaf<T>[] res = spliceAndSplit(oldFocus, index);
      Leaf<T> leftLeaf = res[0];
      Leaf<T> rightLeaf = res[1];
      int leftSize = leftLeaf.size();
      return new Relaxed<>(new int[] {leftSize, leftSize + rightLeaf.size()}, res);
    }

    @Override
    public Node<T> replace(int idx, T t) {
      //            if (idx >= size()) {
      //                throw new IllegalArgumentException("Invalid index " + idx + " >= " +
      // size());
      //            }
      return new Leaf<>(replaceInArrayAt(t, items, idx, null));
    }

    @Override
    public boolean thisNodeHasRelaxedCapacity(int numItems) {
      //            if ( (numItems < 1) || (numItems >= MAX_NODE_LENGTH) ) {
      //                throw new IllegalArgumentException("Bad size: " + numItems);
      //            }
      return items.length + numItems < MAX_NODE_LENGTH;
    }

    @Override
    public String toString() {
      //            return "Leaf("+ arrayString(items) + ")";
      return arrayString(items);
    }

    @Override
    public String indentedStr(int indent) {
      return arrayString(items);
    }
  } // end class Leaf

  // Contains a relaxed tree of nodes that average around 32 items each.
  private static class Relaxed<T> implements Node<T> {

    // Holds the size of each sub-node and plus all nodes to its left.  You could think of this
    // as maxIndex + 1. This is a separate array so that it can be retrieved in a single memory
    // fetch.  Note that this is a 1-based count, not a zero-based index.
    final int[] cumulativeSizes;
    // The sub nodes
    final Node<T>[] nodes;

    // Constructor
    Relaxed(int[] szs, Node<T>[] ns) {
      cumulativeSizes = szs;
      nodes = ns;

      // Consider removing constraint validations before shipping for performance
      //            if (cumulativeSizes.length < 1) {
      //                throw new IllegalArgumentException("cumulativeSizes.length < 1");
      //            }
      //            if (nodes.length < 1) {
      //                throw new IllegalArgumentException("nodes.length < 1");
      //            }
      //            if (cumulativeSizes.length != nodes.length) {
      //                throw new IllegalArgumentException(
      //                        "cumulativeSizes.length:" + cumulativeSizes.length +
      //                        " != nodes.length:" + nodes.length);
      //            }

      //            int cumulativeSize = 0;
      //            for (int i = 0; i < nodes.length; i++) {
      //                cumulativeSize += nodes[i].size();
      //                if (cumulativeSize != cumulativeSizes[i]) {
      //                    throw new IllegalArgumentException(
      //                            "nodes[" + i + "].size() was " +
      //                            nodes[i].size() +
      //                            " which is not compatible with cumulativeSizes[" +
      //                            i + "] which was " + cumulativeSizes[i] +
      //                            "\n\tcumulativeSizes=" + arrayString(cumulativeSizes) +
      //                            "\n\tnodes=" + arrayString(nodes));
      //                }
      //            }
    }

    @Override
    public Node<T> child(int childIdx) {
      return nodes[childIdx];
    }

    @Override
    public int debugValidate() {
      int sz = 0;
      int height = height() - 1;
      if (nodes.length != cumulativeSizes.length) {
        throw new IllegalStateException("Unequal size of nodes and sizes!\n" + this.indentedStr(0));
      }
      for (int i = 0; i < nodes.length; i++) {
        Node<T> n = nodes[i];
        if (n.height() != height) {
          throw new IllegalStateException("Unequal height!\n" + this.indentedStr(0));
        }
        sz += n.size();
        if (sz != cumulativeSizes[i]) {
          throw new IllegalStateException("Cumulative Sizes are wrong!\n" + this.indentedStr(0));
        }
      }
      return sz;
    }

    /** Returns the leftMost (first) or right-most (last) child */
    @Override
    public Node<T> endChild(boolean leftMost) {
      return nodes[leftMost ? 0 : nodes.length - 1];
    }

    /** Adds a node as the first/leftmost or last/rightmost child */
    @Override
    public Node<T> addEndChild(boolean leftMost, Node<T> shorter) {
      return insertInRelaxedAt(cumulativeSizes, nodes, shorter, leftMost ? 0 : nodes.length);
    }

    /** Adds kids as leftmost or rightmost of current children */
    @Override
    public Node<T> addEndChildren(boolean leftMost, Node<T>[] newKids) {
      //            if (!thisNodeHasRelaxedCapacity(newKids.length)) {
      //                throw new IllegalStateException("Can't add enough kids");
      //            }
      //            if (nodes[0].height() != newKids[0].height()) {
      //                throw new IllegalStateException("Kids not same height");
      //            }
      @SuppressWarnings("unchecked")
      Node<T>[] res = spliceIntoArrayAt(newKids, nodes, leftMost ? 0 : nodes.length, Node.class);
      // TODO: Figure out which side we inserted on and do the math to adjust counts instead
      // of looking them up.
      return new Relaxed<>(makeSizeArray(res), res);
    }

    @Override
    public int height() {
      return nodes[0].height() + 1;
    }

    @Override
    public int size() {
      return cumulativeSizes[cumulativeSizes.length - 1];
    }

    /**
     * Converts the index of an item into the index of the sub-node containing that item.
     *
     * @param treeIndex The index of the item in the tree
     * @return The index of the immediate child of this node that the desired node resides in.
     */
    private int subNodeIndex(int treeIndex) {
      // For radix=4 this is actually faster, or at least as fast...
      //            for (int i = 0; i < cumulativeSizes.length; i++) {
      //                if (treeIndex < cumulativeSizes[i]) {
      //                    return i;
      //                }
      //            }
      //            if (treeIndex == size()) {
      //                return cumulativeSizes.length - 1;
      //            }

      // treeSize = cumulativeSizes[cumulativeSizes.length - 1]
      // range of sub-node indices: 0 to cumulativeSizes.length - 1
      // range of tree indices: 0 to treeSize
      // liner interpolation:
      //     treeIndex / treeSize ~= subNodeIndex / cumulativeSizes.length
      // solve for endIdxSlot
      //     cumulativeSizes.length * (treeIndex / treeSize) =  subNodeIndex
      // Put division last
      //     subNodeIndex = cumulativeSizes.length * treeIndex / treeSize
      //
      // Now guess the sub-node index (quickly).

      var guessLong = ((long) cumulativeSizes.length * treeIndex) / size();
      if (guessLong >= cumulativeSizes.length) {
        // Guessed beyond end length - returning last item.
        return cumulativeSizes.length - 1;
      }
      var guess = (int) guessLong;
      int guessedCumSize = cumulativeSizes[guess];

      // Now we must check the guess.  The cumulativeSizes we store are slightly misnamed because
      // the max valid treeIndex for a node is its size - 1.  If our guessedCumSize is
      //  - less than the treeIndex
      //         Increment guess and check result again until greater, then return
      //         that guess
      //  - greater than (treeIndex + MIN_NODE_SIZE)
      //         Decrement guess and check result again until less, then return PREVIOUS guess
      //  - equal to the treeIndex (see note about size)
      //         If treeIndex == size Return guess
      //         Else return guess + 1

      // guessedCumSize less than the treeIndex
      //         Increment guess and check result again until greater, then return
      //         that guess
      if (guessedCumSize < treeIndex) {
        while (guess < (cumulativeSizes.length - 1)) {
          //                    System.out.println("    Too low.  Check higher...");
          guessedCumSize = cumulativeSizes[++guess];
          if (guessedCumSize >= treeIndex) {
            //                        System.out.println("    RIGHT!");
            // See note in equal case below...
            return (guessedCumSize == treeIndex) ? guess + 1 : guess;
          }
          //                    System.out.println("    ==== Guess higher...");
        }
        throw new IllegalStateException("Can we get here?  If so, how?");
      } else if (guessedCumSize > (treeIndex + MIN_NODE_LENGTH)) {

        // guessedCumSize greater than (treeIndex + MIN_NODE_LENGTH)
        //         Decrement guess and check result again until less, then return PREVIOUS guess
        while (guess > 0) {
          //                    System.out.println("    Maybe too high.  Check lower...");
          int nextGuess = guess - 1;
          guessedCumSize = cumulativeSizes[nextGuess];

          if (guessedCumSize <= treeIndex) {
            //                        System.out.println("    RIGHT!");
            return guess;
          }
          //                    System.out.println("    ======= Guess lower...");
          guess = nextGuess;
        }
        //                System.out.println("    Returning lower: " + guess);
        return guess;
      } else if (guessedCumSize == treeIndex) {
        // guessedCumSize equal to the treeIndex (see note about size)
        //         If treeIndex == size Return guess
        //         Else return guess + 1
        //                System.out.println("    Equal, so should be simple...");
        // For an append just one element beyond the end of the existing data structure,
        // just try to add it to the last node.  This might seem overly permissive to accept
        // these as inserts or appends without differentiating between the two, but it flows
        // naturally with this data structure and I think makes it easier to use without
        // encouraging user programming errors.
        // Hopefully this still leads to a relatively balanced tree...
        return (treeIndex == size()) ? guess : guess + 1;
      } else {
        //                System.out.println("    First guess: " + guess);
        return guess;
      }
    }

    /**
     * Converts the index of an item into the index to pass to the sub-node containing that item.
     *
     * @param index The index of the item in the entire tree
     * @param subNodeIndex the index into this node's array of sub-nodes.
     * @return The index to pass to the sub-branch the item resides in
     */
    // Better name might be: nextLevelIndex?  subNodeSubIndex?
    private int subNodeAdjustedIndex(int index, int subNodeIndex) {
      return (subNodeIndex == 0) ? index : index - cumulativeSizes[subNodeIndex - 1];
    }

    @Override
    public T get(int index) {
      int subNodeIndex = subNodeIndex(index);
      return nodes[subNodeIndex].get(subNodeAdjustedIndex(index, subNodeIndex));
    }

    @Override
    public boolean thisNodeHasRelaxedCapacity(int numNodes) {
      return nodes.length + numNodes < MAX_NODE_LENGTH;
    }

    @Override
    public boolean hasRelaxedCapacity(int index, int size) {
      // I think we can add any number of items (less than MAX_NODE_LENGTH)
      //            if ( (size < MIN_NODE_LENGTH) || (size > MAX_NODE_LENGTH) ) {
      //            if ( (size < 1) || (size > MAX_NODE_LENGTH) ) {
      //                throw new IllegalArgumentException("Bad size: " + size);
      //            }
      if (thisNodeHasRelaxedCapacity(1)) {
        return true;
      }
      int subNodeIndex = subNodeIndex(index);
      return nodes[subNodeIndex].hasRelaxedCapacity(
          subNodeAdjustedIndex(index, subNodeIndex), size);
    }

    @SuppressWarnings("unchecked")
    Relaxed<T>[] split() {
      int midpoint = nodes.length >> 1; // Shift-right one is the same as dividing by 2.
      Relaxed<T> left =
          new Relaxed<>(Arrays.copyOf(cumulativeSizes, midpoint), Arrays.copyOf(nodes, midpoint));
      int[] rightCumSizes = new int[nodes.length - midpoint];
      int leftCumSizes = cumulativeSizes[midpoint - 1];
      for (int j = 0; j < rightCumSizes.length; j++) {
        rightCumSizes[j] = cumulativeSizes[midpoint + j] - leftCumSizes;
      }
      // I checked this at javaRepl and indeed this starts from the correct item.
      Relaxed<T> right =
          new Relaxed<>(rightCumSizes, Arrays.copyOfRange(nodes, midpoint, nodes.length));
      return new Relaxed[] {left, right};
    }

    @Override
    public SplitNode<T> splitAt(int splitIndex) {
      int size = size();
      //            if ( (splitIndex < 0) || (splitIndex > size) ) {
      //                throw new IllegalArgumentException("Bad splitIndex: " + splitIndex);
      //            }
      if (splitIndex == 0) {
        return new SplitNode<>(emptyLeaf(), emptyArray(), emptyLeaf(), emptyArray());
      }
      if (splitIndex == size) {
        return new SplitNode<>(this, emptyArray(), emptyLeaf(), emptyArray());
      }

      int subNodeIndex = subNodeIndex(splitIndex);
      Node<T> subNode = nodes[subNodeIndex];

      if ((subNodeIndex > 0) && (splitIndex == cumulativeSizes[subNodeIndex - 1])) {
        // Falls on an existing node boundary
        Tuple2<Node<T>[], Node<T>[]> splitNodes = splitArray(nodes, subNodeIndex);

        int[][] splitCumSizes = splitArray(cumulativeSizes, subNodeIndex);
        int[] leftCumSizes = splitCumSizes[0];
        int[] rightCumSizes = splitCumSizes[1];
        int bias = leftCumSizes[leftCumSizes.length - 1];
        for (int i = 0; i < rightCumSizes.length; i++) {
          rightCumSizes[i] = rightCumSizes[i] - bias;
        }
        Node<T> left = new Relaxed<>(leftCumSizes, splitNodes._1());
        //                left.debugValidate();
        Node<T> right = new Relaxed<>(rightCumSizes, splitNodes._2());
        //                right.debugValidate();

        return new SplitNode<>(
            left, emptyArray(),
            right, emptyArray());
      }

      int subNodeAdjustedIndex = subNodeAdjustedIndex(splitIndex, subNodeIndex);
      SplitNode<T> split = subNode.splitAt(subNodeAdjustedIndex);

      final Node<T> left;
      Node<T> splitLeft = split.left();
      //            splitLeft.debugValidate();

      if (subNodeIndex == 0) {
        //                debug("If we have a single left node, it doesn't need a parent.");
        left = splitLeft;
      } else {
        boolean haveLeft = (splitLeft.size() > 0);
        int numLeftItems = subNodeIndex + (haveLeft ? 1 : 0);
        int[] leftCumSizes = new int[numLeftItems];
        Node<T>[] leftNodes = genericNodeArray(numLeftItems);
        //                      src, srcPos,    dest,destPos, length
        System.arraycopy(cumulativeSizes, 0, leftCumSizes, 0, numLeftItems);
        if (haveLeft) {
          int cumulativeSize = (numLeftItems > 1) ? leftCumSizes[numLeftItems - 2] : 0;
          leftCumSizes[numLeftItems - 1] = cumulativeSize + splitLeft.size();
        }
        //                    debug("leftCumSizes=" + arrayString(leftCumSizes));
        // Copy one less item if we are going to add the split one in a moment.
        // I could have written:
        //     haveLeft ? numLeftItems - 1
        //              : numLeftItems
        // but that's always equal to subNodeIndex.
        System.arraycopy(nodes, 0, leftNodes, 0, subNodeIndex);
        if (haveLeft) {
          // I don't know why I have to fix this here.
          while (splitLeft.height() < this.height() - 1) {
            splitLeft = addAncestor(splitLeft);
          }
          //                    if ( (leftNodes.length > 0) &&
          //                         (leftNodes[0].height() != splitLeft.height()) ) {
          //                        throw new IllegalStateException("nodesHeight: " +
          // leftNodes[0].height() +
          //                                                        " splitLeftHeight: " +
          // splitLeft.height());
          //                    }
          leftNodes[numLeftItems - 1] = splitLeft;
        }
        left = new Relaxed<>(leftCumSizes, leftNodes);
        //                left.debugValidate();
      }

      final Node<T> right = fixRight(nodes, split.right(), subNodeIndex);
      //            right.debugValidate();

      //            debug("RETURNING=", ret);
      //            if (this.size() != ret.size()) {
      //                throw new IllegalStateException(
      //                        "Split on " + this.size() + " items returned " +
      //                        ret.size() + " items\n" +
      //                        "original=" + this.indentedStr(9) + "\n" +
      //                        "splitIndex=" + splitIndex + "\n" +
      //                        "leftFocus=" + arrayString(split.leftFocus()) + "\n" +
      //                        "left=" + left.indentedStr(5) + "\n" +
      //                        "rightFocus=" + arrayString(split.rightFocus()) + "\n" +
      //                        "right=" + right.indentedStr(6));
      //            }

      return new SplitNode<>(
          left, split.leftFocus(),
          right, split.rightFocus());
    }

    @Override
    public int numChildren() {
      return nodes.length;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Node<T> pushFocus(int index, T[] oldFocus) {
      // TODO: Review this entire method.
      int subNodeIndex = subNodeIndex(index);
      Node<T> subNode = nodes[subNodeIndex];
      int subNodeAdjustedIndex = subNodeAdjustedIndex(index, subNodeIndex);

      // 1st choice: insert into the subNode if it has enough space enough to handle it
      if (subNode.hasRelaxedCapacity(subNodeAdjustedIndex, oldFocus.length)) {
        // Push the focus down to a lower-level node w. capacity.
        Node<T> newNode = subNode.pushFocus(subNodeAdjustedIndex, oldFocus);
        // Make a copy of our nodesArray, replacing the old node at subNodeIndex with the
        // new node
        return replaceInRelaxedAt(cumulativeSizes, nodes, newNode, subNodeIndex, oldFocus.length);
      }

      // I think this is a root node thing.
      if (!thisNodeHasRelaxedCapacity(1)) {
        // For now, split at half of size.
        Relaxed<T>[] split = split();
        int max1 = split[0].size();
        Relaxed<T> newRelaxed = new Relaxed<>(new int[] {max1, max1 + split[1].size()}, split);
        return newRelaxed.pushFocus(index, oldFocus);
      }

      if (subNode instanceof Leaf) {
        // Here we already know:
        //  - the leaf doesn't have capacity
        //  - We don't need to split ourselves
        // Therefore:
        //  - If the focus is big enough to be its own leaf and the index is on a leaf
        //    boundary and , make it one.
        //  - Else, insert into the array and replace one leaf with two.

        final Node<T>[] newNodes;
        final int[] newCumSizes;
        final int numToSkip;

        //  If the focus is big enough to be its own leaf and the index is on a leaf
        // boundary, make it one.
        if ((oldFocus.length >= MIN_NODE_LENGTH)
            && (subNodeAdjustedIndex == 0 || subNodeAdjustedIndex == subNode.size())) {

          // Insert-between
          // Just add a new leaf
          Leaf<T> newNode = new Leaf<>(oldFocus);

          // If we aren't inserting before the existing leaf node, we're inserting after.
          if (subNodeAdjustedIndex != 0) {
            subNodeIndex++;
          }

          newNodes = insertIntoArrayAt(newNode, nodes, subNodeIndex, Node.class);
          // Increment newCumSizes for the changed item and all items to the right.
          newCumSizes = new int[cumulativeSizes.length + 1];
          int cumulativeSize = 0;
          if (subNodeIndex > 0) {
            System.arraycopy(cumulativeSizes, 0, newCumSizes, 0, subNodeIndex);
            cumulativeSize = newCumSizes[subNodeIndex - 1];
          }
          newCumSizes[subNodeIndex] = cumulativeSize + oldFocus.length;
          numToSkip = 1;
          //                    for (int i = subNodeIndex + 1; i < newCumSizes.length; i++) {
          //                        newCumSizes[i] = cumulativeSizes[i - 1] + oldFocus.length;
          //                    }
        } else {
          // Grab the array from the existing leaf node, make the insert, and yield two
          // new leaf nodes.
          // Split-to-insert
          Leaf<T>[] res = ((Leaf<T>) subNode).spliceAndSplit(oldFocus, subNodeAdjustedIndex);
          Leaf<T> leftLeaf = res[0];
          Leaf<T> rightLeaf = res[1];

          newNodes = new Node[nodes.length + 1];

          // Increment newCumSizes for the changed item and all items to the right.
          newCumSizes = new int[cumulativeSizes.length + 1];
          int leftSize = 0;

          // Copy nodes and cumulativeSizes before split
          if (subNodeIndex > 0) {
            //               src,srcPos,dest,destPos,length
            System.arraycopy(nodes, 0, newNodes, 0, subNodeIndex);
            //               src,   srcPos, dest,    destPos, length
            System.arraycopy(cumulativeSizes, 0, newCumSizes, 0, subNodeIndex);

            leftSize = cumulativeSizes[subNodeIndex - 1];
          }

          // Copy split nodes and cumulativeSizes
          newNodes[subNodeIndex] = leftLeaf;
          newNodes[subNodeIndex + 1] = rightLeaf;
          leftSize += leftLeaf.size();
          newCumSizes[subNodeIndex] = leftSize;
          newCumSizes[subNodeIndex + 1] = leftSize + rightLeaf.size();

          if (subNodeIndex < (nodes.length - 1)) {
            //               src,srcPos,dest,destPos,length
            System.arraycopy(
                nodes,
                subNodeIndex + 1,
                newNodes,
                subNodeIndex + 2,
                nodes.length - subNodeIndex - 1);
          }
          numToSkip = 2;
        }
        for (int i = subNodeIndex + numToSkip; i < newCumSizes.length; i++) {
          newCumSizes[i] = cumulativeSizes[i - 1] + oldFocus.length;
        }

        return new Relaxed<>(newCumSizes, newNodes);
        // end if subNode instanceof Leaf
      }

      // Here we have capacity and the full sub-node is not a leaf or strict, so we have to
      // split the appropriate sub-node.

      // For now, split at half of size.
      Relaxed<T>[] newSubNode = ((Relaxed<T>) subNode).split();

      Relaxed<T> node1 = newSubNode[0];
      Relaxed<T> node2 = newSubNode[1];
      Node<T>[] newNodes = genericNodeArray(nodes.length + 1);

      // If we aren't inserting at the first item, array-copy the nodes before the insert
      // point.
      if (subNodeIndex > 0) {
        System.arraycopy(nodes, 0, newNodes, 0, subNodeIndex);
      }

      // Insert the new item.
      newNodes[subNodeIndex] = node1;
      newNodes[subNodeIndex + 1] = node2;

      // If we aren't inserting at the last item, array-copy the nodes after the insert
      // point.
      if (subNodeIndex < nodes.length) {
        System.arraycopy(
            nodes, subNodeIndex + 1, newNodes, subNodeIndex + 2, nodes.length - subNodeIndex - 1);
      }

      int[] newCumSizes = new int[cumulativeSizes.length + 1];
      int cumulativeSize = 0;
      if (subNodeIndex > 0) {
        System.arraycopy(cumulativeSizes, 0, newCumSizes, 0, subNodeIndex);
        cumulativeSize = cumulativeSizes[subNodeIndex - 1];
      }

      for (int i = subNodeIndex; i < newCumSizes.length; i++) {
        // TODO: Calculate instead of loading into memory.  See splitAt calculation above.
        cumulativeSize += newNodes[i].size();
        newCumSizes[i] = cumulativeSize;
      }

      Relaxed<T> newRelaxed = new Relaxed<>(newCumSizes, newNodes);
      //            debug("newRelaxed2:\n" + newRelaxed.indentedStr(0));

      return newRelaxed.pushFocus(index, oldFocus);
      //            debug("Parent after:" + after.indentedStr(0));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Node<T> replace(int index, T t) {
      int subNodeIndex = subNodeIndex(index);
      Node<T> alteredNode =
          nodes[subNodeIndex].replace(subNodeAdjustedIndex(index, subNodeIndex), t);
      Node<T>[] newNodes = replaceInArrayAt(alteredNode, nodes, subNodeIndex, Node.class);
      return new Relaxed<>(cumulativeSizes, newNodes);
    }

    @Override
    public String indentedStr(int indent) {
      StringBuilder sB =
          new StringBuilder() // indentSpace(indent)
              .append("Relaxed(");
      int nextIndent = indent + sB.length();
      sB.append("cumulativeSizes=")
          .append(arrayString(cumulativeSizes))
          .append("\n")
          .append(indentSpace(nextIndent))
          .append("nodes=[");
      // + 6 for "nodes="
      return showSubNodes(sB, nodes, nextIndent + 7).append("])").toString();
    }

    @Override
    public String toString() {
      return indentedStr(0);
    }

    /**
     * This asks each node what size it is, then puts the cumulative sizes into a new array. In
     * theory, it might be faster to figure out what side we added/removed nodes and do some
     * addition/subtraction (in the amount of the added/removed nodes). But I want to optimize other
     * things first and be sure everything is correct before experimenting with that. After all, it
     * might not even be faster!
     *
     * @param newNodes the nodes to take sizes from.
     * @return An array of cumulative sizes of each node in the passed array.
     */
    private static int[] makeSizeArray(@SuppressWarnings("rawtypes") Node[] newNodes) {
      int[] newCumSizes = new int[newNodes.length];
      int cumulativeSize = 0;
      for (int i = 0; i < newCumSizes.length; i++) {
        cumulativeSize += newNodes[i].size();
        newCumSizes[i] = cumulativeSize;
      }
      return newCumSizes;
    }

    // TODO: Search for more opportunities to use this
    /**
     * Replace a node in a relaxed node by recalculating the cumulative sizes and copying all sub
     * nodes.
     *
     * @param is original cumulative sizes
     * @param ns original nodes
     * @param newNode replacement node
     * @param subNodeIndex index to replace in this node's immediate children
     * @param insertSize the difference in size between the original node and the new node.
     * @return a new immutable Relaxed node with the immediate child node replaced.
     */
    static <T> Relaxed<T> replaceInRelaxedAt(
        int[] is, Node<T>[] ns, Node<T> newNode, int subNodeIndex, int insertSize) {
      @SuppressWarnings("unchecked")
      Node<T>[] newNodes = replaceInArrayAt(newNode, ns, subNodeIndex, Node.class);
      // Increment newCumSizes for the changed item and all items to the right.
      int[] newCumSizes = new int[is.length];
      if (subNodeIndex > 0) {
        System.arraycopy(is, 0, newCumSizes, 0, subNodeIndex);
      }
      for (int i = subNodeIndex; i < is.length; i++) {
        newCumSizes[i] = is[i] + insertSize;
      }
      return new Relaxed<>(newCumSizes, newNodes);
    }

    /**
     * Insert a node in a relaxed node by recalculating the cumulative sizes and copying all sub
     * nodes.
     *
     * @param oldCumSizes original cumulative sizes
     * @param ns original nodes
     * @param newNode replacement node
     * @param subNodeIndex index to insert in this node's immediate children
     * @return a new immutable Relaxed node with the immediate child node inserted.
     */
    static <T> Relaxed<T> insertInRelaxedAt(
        int[] oldCumSizes, Node<T>[] ns, Node<T> newNode, int subNodeIndex) {
      @SuppressWarnings("unchecked")
      Node<T>[] newNodes = insertIntoArrayAt(newNode, ns, subNodeIndex, Node.class);

      int oldLen = oldCumSizes.length;
      //            if (subNodeIndex > oldLen) {
      //                throw new IllegalStateException("subNodeIndex > oldCumSizes.length");
      //            }

      int[] newCumSizes = new int[oldLen + 1];
      // Copy unchanged cumulative sizes
      if (subNodeIndex > 0) {
        System.arraycopy(oldCumSizes, 0, newCumSizes, 0, subNodeIndex);
      }
      // insert the cumulative size of the new node
      int newNodeSize = newNode.size();
      // Find cumulative size of previous node
      int prevNodeTotal = (subNodeIndex == 0) ? 0 : oldCumSizes[subNodeIndex - 1];

      newCumSizes[subNodeIndex] = newNodeSize + prevNodeTotal;

      for (int i = subNodeIndex; i < oldCumSizes.length; i++) {
        newCumSizes[i + 1] = oldCumSizes[i] + newNodeSize;
      }
      return new Relaxed<>(newCumSizes, newNodes);
    }

    /**
     * Fixes up nodes on the right-hand side of the split. Might want to explain this better...
     *
     * @param origNodes the immediate children of the node we're splitting
     * @param splitRight the pre-split right node
     * @param subNodeIndex the index to split children at?
     * @return a copy of this node with only the right-hand side of the split.
     */
    @SuppressWarnings("unchecked")
    public static <T> Node<T> fixRight(Node<T>[] origNodes, Node<T> splitRight, int subNodeIndex) {
      //            if ( (splitRight.size() > 0) &&
      //                 (origNodes[0].height() != splitRight.height()) ) {
      //                throw new IllegalStateException("Passed a splitRight node of a different
      // height" +
      //                                                " than the origNodes!");
      //            }
      Node<T> right;
      if (subNodeIndex == (origNodes.length - 1)) {
        //                right = splitRight;
        right = new Relaxed<T>(new int[] {splitRight.size()}, new Node[] {splitRight});
      } else {
        boolean haveRightSubNode = splitRight.size() > 0;
        // If we have a rightSubNode, it's going to need a space in our new node array.
        int numRightNodes = (origNodes.length - subNodeIndex) - (haveRightSubNode ? 0 : 1);
        // Here the first (leftmost) node of the right-hand side was turned into the focus
        // and we have additional right-hand origNodes to adjust the parent for.
        int[] rightCumSizes = new int[numRightNodes];
        Node<T>[] rightNodes = genericNodeArray(numRightNodes);

        int cumulativeSize = 0;
        int destCopyStartIdx = 0;

        if (haveRightSubNode) {
          //                 src,       srcPos,          dest, destPos, length
          System.arraycopy(origNodes, subNodeIndex + 1, rightNodes, 1, numRightNodes - 1);

          rightNodes[0] = splitRight;
          cumulativeSize = splitRight.size();
          rightCumSizes[0] = cumulativeSize;
          destCopyStartIdx = 1;
        } else {
          //                 src,       srcPos,          dest, destPos, length
          System.arraycopy(origNodes, subNodeIndex + 1, rightNodes, 0, numRightNodes);
        }

        // For relaxed nodes, we could calculate from previous cumulativeSizes instead of
        // calling .size() on each one.  For strict, we could just add a strict amount.
        // For now, this works.
        for (int i = destCopyStartIdx; i < numRightNodes; i++) {
          cumulativeSize += rightNodes[i].size();
          rightCumSizes[i] = cumulativeSize;
        }

        right = new Relaxed<>(rightCumSizes, rightNodes);
        //                right.debugValidate();
      }
      return right;
    } // end fixRight()
  } // end class Relaxed

  // =================================== Tree-walking Iterator ==================================

  /** Holds a node and the index of the child node we are currently iterating in. */
  private static final class IdxNode<E> {
    int idx = 0;
    final Node<E> node;

    IdxNode(Node<E> n) {
      node = n;
    }

    public boolean hasNext() {
      return idx < node.numChildren();
    }

    public Node<E> next() {
      return node.child(idx++);
    }
    //        public String toString() { return "IdxNode(" + idx + " " + node + ")"; }
  }

  final class Iter implements UnmodSortedIterator<E> {

    // We want this iterator to walk the node tree.
    private final IdxNode<E>[] stack;
    private int stackMaxIdx = -1;

    private E[] leafArray;
    private int leafArrayIdx;

    // Focus must be pre-pushed so we don't have to ever check the index.
    @SuppressWarnings("unchecked")
    private Iter(Node<E> root) {
      stack = (IdxNode<E>[]) new IdxNode<?>[root.height()];
      leafArray = findLeaf(root);
    }

    // Descent to the leftmost unused leaf node.
    private E[] findLeaf(Node<E> node) {
      // Descent to left-most bottom node.
      while (!(node instanceof Leaf)) {
        IdxNode<E> in = new IdxNode<>(node);
        // Add indexNode to ancestor stack
        stack[++stackMaxIdx] = in;
        node = in.next();
      }
      return ((Leaf<E>) node).items;
    }

    private E[] nextLeafArray() {
      // While nodes are used up, get next node from node one level up.
      while ((stackMaxIdx > -1) && !stack[stackMaxIdx].hasNext()) {
        stackMaxIdx--;
      }

      if (stackMaxIdx < 0) {
        return emptyArray();
      }
      // If node one level up is used up, find a node that isn't used up and descend to its
      // leftmost leaf.
      return findLeaf(stack[stackMaxIdx].next());
    }

    @Override
    public boolean hasNext() {
      if (leafArrayIdx < leafArray.length) {
        return true;
      }
      //            if (leafArray.length == 0) { return false; }
      leafArray = nextLeafArray();
      leafArrayIdx = 0;
      return leafArray.length > 0;
    }

    @Override
    public E next() {
      // If there's no more in this leaf array, get the next one
      if (leafArrayIdx >= leafArray.length) {
        leafArray = nextLeafArray();
        leafArrayIdx = 0;
      }
      // Return the next item in the leaf array and increment index
      return leafArray[leafArrayIdx++];
    }
  }

  // =================================== Array Helper Functions ==================================
  // Helper function to avoid type warnings.

  @SuppressWarnings("unchecked")
  private static <T> Node<T>[] genericNodeArray(int size) {
    return (Node<T>[]) new Node<?>[size];
  }

  // =============================== Debugging and pretty-printing ===============================

  private static StringBuilder showSubNodes(StringBuilder sB, Object[] items, int nextIndent) {
    boolean isFirst = true;
    for (Object n : items) {
      if (isFirst) {
        isFirst = false;
      } else {
        //                sB.append(" ");
        if (items[0] instanceof Leaf) {
          sB.append(" ");
        } else {
          sB.append("\n").append(indentSpace(nextIndent));
        }
      }
      //noinspection rawtypes
      sB.append(((Node) n).indentedStr(nextIndent));
    }
    return sB;
  }
} // end class RrbTree
