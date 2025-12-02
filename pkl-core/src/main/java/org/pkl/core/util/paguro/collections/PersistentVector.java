/*
 * Copyright © 2007-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.function.Fn0;
import org.pkl.core.util.paguro.oneOf.Option;

/**
 * This started out as Rich Hickey's PersistentVector class from Clojure in late 2014. Glen added
 * generic types, tried to make it a little more pure-Java friendly, and removed dependencies on
 * other Clojure stuff.
 *
 * <p>This file is a derivative work based on a Clojure collection licensed under the Eclipse Public
 * License 1.0 Copyright Rich Hickey
 *
 * @author Rich Hickey (Primary author)
 * @author Glen Peterson (Java-centric editor)
 */
public class PersistentVector<E> extends UnmodList.AbstractUnmodList<E>
    implements ImList<E>, Serializable {

  // There's bit shifting going on here because it's a very fast operation.
  // Shifting right by 5 is aeons faster than dividing by 32.
  private static final int NODE_LENGTH_POW_2 = 5;

  // 0b00000000000000000000000000100000 = 0x20 = 32
  private static final int MAX_NODE_LENGTH = 1 << NODE_LENGTH_POW_2;
  // 0b00000000000000000000000000011111 = 0x1f
  private static final int LOW_BITS = MAX_NODE_LENGTH - 1;

  // Java shift operator review:
  // The signed left shift operator "<<" shifts a bit pattern to the left, and
  // the signed right shift operator ">>" shifts a bit pattern to the right.
  // The bit pattern is given by the left-hand operand, and the
  // number of positions to shift by the right-hand operand.
  // The unsigned right shift operator ">>>" shifts a zero into the leftmost position,
  // while the leftmost position after ">>" depends on sign extension.
  //
  // The bitwise & operator performs a bitwise AND operation.
  //
  // The bitwise ^ operator performs a bitwise exclusive OR operation.
  //
  // The bitwise | operator performs a bitwise inclusive OR operation

  private static class Node {
    // The same data structure backs both the mutable and immutable vector.  The immutable
    // one uses copy-on-write for all operations.  The mutable one still uses copy-on-write
    // for the tree, but not for the tail.  Instead of creating a new tail one bigger after
    // each append, it creates a STRICT_NODE_SIZE tail and inserts items into it in place.
    //
    // The reason we need this AtomicReference is that some mutable vector could still have
    // a pointer to the Tail array that's in the tree.  When first mutating, the current thread
    // is placed in here.  After the mutable structure is made immutable, a null is placed in
    // here.  Subsequent attempts to mutate anything check and if they find the null, they
    // throw an exception.
    public final transient AtomicReference<Thread> edit;

    // This is either the data in the node (for a leaf node), or it's pointers to sub-nodes (for
    // a branch node).  We could probably have two separate classes: NodeLeaf and NodeBranch
    // where NodeLeaf has T[] and NodeBranch has Node<T>[].
    public final Object[] array;

    Node(AtomicReference<Thread> edit, Object[] array) {
      this.edit = edit;
      this.array = array;
    }

    Node(AtomicReference<Thread> edit) {
      this.edit = edit;
      this.array = new Object[MAX_NODE_LENGTH];
    }
  }

  private static final AtomicReference<Thread> NOEDIT = new AtomicReference<>(null);

  private static final Node EMPTY_NODE = new Node(NOEDIT, new Object[MAX_NODE_LENGTH]);

  public static final PersistentVector<?> EMPTY =
      new PersistentVector<>(0, NODE_LENGTH_POW_2, EMPTY_NODE, new Object[] {});

  /** Returns the empty ImList (there only needs to be one) */
  @SuppressWarnings("unchecked")
  public static <T> PersistentVector<T> empty() {
    return (PersistentVector<T>) EMPTY;
  }

  /**
   * Returns a new mutable vector. For some reason calling empty().mutable() sometimes requires an
   * explicit type parameter in Java, so this convenience method works around that.
   */
  public static <T> MutVector<T> emptyMutable() {
    PersistentVector<T> e = empty();
    return e.mutable();
  }

  /**
   * Public static factory method to create a vector from an Iterable. A varargs version of this
   * method is: {@link org.pkl.core.util.paguro.StaticImports#vec(Object...)}.
   */
  public static <T> PersistentVector<T> ofIter(Iterable<T> items) {
    MutVector<T> ret = emptyMutable();
    for (T item : items) {
      ret.append(item);
    }
    return ret.immutable();
  }

  // ==================================== Instance Variables ====================================
  // The number of items in this Vector.
  private final int size;
  private final int shift;
  private final transient Node root;
  private final E[] tail;

  // ======================================= Constructor =======================================
  /** Constructor */
  private PersistentVector(int z, int shift, Node root, E[] tail) {
    size = z;
    this.shift = shift;
    this.root = root;
    this.tail = tail;
  }

  // ======================================= Serialization =======================================
  // This class has a custom serialized form designed to be as small as possible.  It does not
  // have the same internal structure as an instance of this class.

  // For serializable.  Make sure to change whenever internal data format changes.
  private static final long serialVersionUID = 20160904160500L;

  // Check out Josh Bloch Item 78, p. 312 for an explanation of what's going on here.
  private static class SerializationProxy<E> implements Serializable {
    // For serializable.  Make sure to change whenever internal data format changes.
    private static final long serialVersionUID = 20160904155600L;

    private final int size;
    private transient ImList<E> vector;

    SerializationProxy(PersistentVector<E> v) {
      size = v.size();
      vector = v;
    }

    // Taken from Josh Bloch Item 75, p. 298
    private void writeObject(ObjectOutputStream s) throws IOException {
      s.defaultWriteObject();
      // Write out all elements in the proper order
      for (E entry : vector) {
        s.writeObject(entry);
      }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
      s.defaultReadObject();
      MutList<E> temp = emptyMutable();
      for (int i = 0; i < size; i++) {
        temp.append((E) s.readObject());
      }
      vector = temp.immutable();
    }

    private Object readResolve() {
      return vector;
    }
  }

  private Object writeReplace() {
    return new SerializationProxy<>(this);
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    throw new InvalidObjectException("Proxy required");
  }

  // ===================================== Instance Methods =====================================

  // IEditableCollection has this return ITransientCollection<E>,
  // not MutVector<E> as this originally returned.
  //    @Override
  // We could make this public some day, maybe.
  @Override
  public MutVector<E> mutable() {
    return new MutVector<>(this);
  }

  // Returns the high (gt 5) bits of the index of the last item.
  // I think this is the index of the start of the last array in the tree.
  private int tailoff() {
    // ((size - 1) / 32) * 32
    // (Size - 1) is an index into an array because size starts counting from 1 and array
    //            indices start from 0.
    // /32 *32 zeroes out the low 5 bits.
    return (size < MAX_NODE_LENGTH) ? 0 : ((size - 1) >>> NODE_LENGTH_POW_2) << NODE_LENGTH_POW_2;
    // Last line can be replaced with (size -1) & HIGH_BITS
  }

  /** Returns the array (of type E) from the leaf node indicated by the given index. */
  @SuppressWarnings("unchecked")
  private E[] leafNodeArrayFor(int i) {
    // i is the index into this vector.  Each 5 bits represent an index into an array.  The
    // highest 5 bits (that are less than the shift value) are the index into the top-level
    // array. The lowest 5 bits index the the leaf.  The guts of this method indexes into the
    // array at each level, finally indexing into the leaf node.

    if (i >= 0 && i < size) {
      if (i >= tailoff()) {
        return tail;
      }
      Node node = root;
      for (int level = shift; level > 0; level -= NODE_LENGTH_POW_2) {
        node = (Node) node.array[(i >>> level) & LOW_BITS];
      }
      return (E[]) node.array;
    }
    throw new IndexOutOfBoundsException();
  }

  /** Returns the item specified by the given index. */
  @Override
  public E get(int i) {
    E[] node = leafNodeArrayFor(i);
    return node[i & LOW_BITS];
  }

  /** {@inheritDoc} */
  @SuppressWarnings("unchecked")
  @Override
  public PersistentVector<E> replace(int i, E val) {
    if (i >= 0 && i < size) {
      if (i >= tailoff()) {
        Object[] newTail = new Object[tail.length];
        System.arraycopy(tail, 0, newTail, 0, tail.length);
        newTail[i & LOW_BITS] = val;

        return new PersistentVector<>(size, shift, root, (E[]) newTail);
      }

      return new PersistentVector<>(size, shift, doAssoc(shift, root, i, val), tail);
    }
    if (i == size) {
      return append(val);
    }
    throw new IndexOutOfBoundsException();
  }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return size;
  }

  /**
   * Inserts a new item at the end of the vector
   *
   * @param val the value to insert
   * @return a new Vecsicle with the additional item.
   */
  @SuppressWarnings("unchecked")
  @Override
  public PersistentVector<E> append(E val) {
    // room in tail?
    //	if(tail.length < MAX_NODE_LENGTH)
    if (size - tailoff() < MAX_NODE_LENGTH) {
      E[] newTail = (E[]) new Object[tail.length + 1];
      System.arraycopy(tail, 0, newTail, 0, tail.length);
      newTail[tail.length] = val;
      return new PersistentVector<>(size + 1, shift, root, newTail);
    }
    // full tail, push into tree
    Node newroot;
    Node tailnode = new Node(root.edit, tail);
    int newshift = shift;
    // overflow root?
    if ((size >>> NODE_LENGTH_POW_2) > (1 << shift)) {
      newroot = new Node(root.edit);
      newroot.array[0] = root;
      newroot.array[1] = newPath(root.edit, shift, tailnode);
      newshift += NODE_LENGTH_POW_2;
    } else {
      newroot = pushTail(shift, root, tailnode);
    }
    return new PersistentVector<>(size + 1, newshift, newroot, (E[]) new Object[] {val});
  }

  /** {@inheritDoc} */
  @Override
  public PersistentVector<E> appendSome(Fn0<? extends Option<E>> supplier) {
    return supplier.apply().match((it) -> append(it), () -> this);
  }

  /**
   * Efficiently adds items to the end of this PersistentVector.
   *
   * @param items the values to insert
   * @return a new PersistentVector with the additional items at the end.
   */
  @Override
  public PersistentVector<E> concat(@Nullable Iterable<? extends E> items) {
    return (PersistentVector<E>) ImList.super.concat(items);
  }

  private Node pushTail(int level, Node parent, Node tailnode) {
    // if parent is leaf, insert node,
    // else does it map to an existing child? -> nodeToInsert = pushNode one more level
    // else alloc new path
    // return  nodeToInsert placed in copy of parent
    int subidx = ((size - 1) >>> level) & LOW_BITS;
    Node ret = new Node(parent.edit, parent.array.clone());
    Node nodeToInsert;
    if (level == NODE_LENGTH_POW_2) {
      nodeToInsert = tailnode;
    } else {
      Node child = (Node) parent.array[subidx];
      nodeToInsert =
          (child == null)
              ? newPath(root.edit, level - NODE_LENGTH_POW_2, tailnode)
              : pushTail(level - NODE_LENGTH_POW_2, child, tailnode);
    }
    ret.array[subidx] = nodeToInsert;
    return ret;
  }

  /** {@inheritDoc} */
  @Override
  public UnmodListIterator<E> listIterator(int index) {
    if ((index < 0) || (index > size)) {
      // To match ArrayList and other java.util.List expectations
      throw new IndexOutOfBoundsException("Index: " + index);
    }
    return new UnmodListIterator<>() {
      private int i = index;
      private int base = i - (i % MAX_NODE_LENGTH);
      private E[] array = (index < size()) ? leafNodeArrayFor(i) : null;

      /** {@inheritDoc} */
      @Override
      public boolean hasNext() {
        return i < size();
      }

      /** {@inheritDoc} */
      @Override
      public boolean hasPrevious() {
        return i > 0;
      }

      /** {@inheritDoc} */
      @Override
      public E next() {
        if (i >= size) {
          // To match ArrayList and other java.util.List expectations
          // If we didn't catch this, it would be an ArrayIndexOutOfBoundsException.
          throw new NoSuchElementException();
        }
        if (i - base == MAX_NODE_LENGTH) {
          array = leafNodeArrayFor(i);
          base += MAX_NODE_LENGTH;
        }
        return array[i++ & LOW_BITS];
      }

      /** {@inheritDoc} */
      @Override
      public int nextIndex() {
        return i;
      }

      /** {@inheritDoc} */
      @Override
      public E previous() {
        // To match contract of ListIterator and implementation of ArrayList
        if (i < 1) {
          // To match ArrayList and other java.util.List expectations.
          throw new NoSuchElementException();
        }
        if (i - base == 0) {
          //                    System.out.println("i - base was zero");
          array = leafNodeArrayFor(i - 1);
          base -= MAX_NODE_LENGTH;
        } else if (i == size) {
          // Can start with index past array.
          array = leafNodeArrayFor(i - 1);
          base = i - (i % MAX_NODE_LENGTH);
        }
        return array[--i & LOW_BITS];
      }
    };
  }

  //    Iterator<E> rangedIterator(final int start, final int end) {
  //        return new Iterator<E>() {
  //            int i = start;
  //            int base = i - (i % MAX_NODE_LENGTH);
  //            E[] array = (start < size()) ? leafNodeArrayFor(i) : null;
  //
  //            @Override
  //            public boolean hasNext() {
  //                return i < end;
  //            }
  //
  //            @Override
  //            public E next() {
  //                if (i - base == MAX_NODE_LENGTH) {
  //                    array = leafNodeArrayFor(i);
  //                    base += MAX_NODE_LENGTH;
  //                }
  //                return array[i++ & LOW_BITS];
  //            }
  //
  //            @Override
  //            public void remove() {
  //                throw new UnsupportedOperationException();
  //            }
  //        };
  //    }

  //    public UnmodIterator<E> iterator() {
  //        return rangedIterator(0, size());
  //    }

  //    @SuppressWarnings("unchecked")
  //    public <U> U reduce(Fn2<U, E, U> f, U init) {
  //        int step = 0;
  //        for (int i = 0; i < size; i += step) {
  //            E[] array = leafNodeArrayFor(i);
  //            for (int j = 0; j < array.length; ++j) {
  //                init = f.apply(init, array[j]);
  //
  //                if ( (init != null) && (init instanceof Reduced) ) {
  //                    return ((Reduced<U>) init).val;
  //                }
  //            }
  //            step = array.length;
  //        }
  //        return init;
  //    }

  //    @Override public IPersistentCollection<E> empty(){
  //    	return emptyPersistentCollection(meta());
  //    }

  //    @SuppressWarnings("unchecked")
  //    public ImVectorImpl<E> pop() {
  //        if (size == 0)
  //            throw new IllegalStateException("Can't pop empty vector");
  //        if (size == 1)
  //            return empty();
  //        //if(tail.length > 1)
  //        if (size - tailoff() > 1) {
  //            E[] newTail = (E[]) new Object[tail.length - 1];
  //            System.arraycopy(tail, 0, newTail, 0, newTail.length);
  //            return new ImVectorImpl<>(size - 1, shift, root, newTail);
  //        }
  //        E[] newtail = leafNodeArrayFor(size - 2);
  //
  //        Node newroot = popTail(shift, root);
  //        int newshift = shift;
  //        if (newroot == null) {
  //            newroot = EMPTY_NODE;
  //        }
  //        if (shift > NODE_LENGTH_POW_2 && newroot.array[1] == null) {
  //            newroot = (Node) newroot.array[0];
  //            newshift -= NODE_LENGTH_POW_2;
  //        }
  //        return new ImVectorImpl<>(size - 1, newshift, newroot, newtail);
  //    }

  //    private Node popTail(int level, Node node) {
  //        int subidx = ((size - 2) >>> level) & LOW_BITS;
  //        if (level > NODE_LENGTH_POW_2) {
  //            Node newchild = popTail(level - NODE_LENGTH_POW_2, (Node) node.array[subidx]);
  //            if (newchild == null && subidx == 0)
  //                return null;
  //            else {
  //                Node ret = new Node(root.edit, node.array.clone());
  //                ret.array[subidx] = newchild;
  //                return ret;
  //            }
  //        } else if (subidx == 0)
  //            return null;
  //        else {
  //            Node ret = new Node(root.edit, node.array.clone());
  //            ret.array[subidx] = null;
  //            return ret;
  //        }
  //    }

  private static Node doAssoc(int level, Node node, int i, Object val) {
    Node ret = new Node(node.edit, node.array.clone());
    if (level == 0) {
      ret.array[i & LOW_BITS] = val;
    } else {
      int subidx = (i >>> level) & LOW_BITS;
      ret.array[subidx] = doAssoc(level - NODE_LENGTH_POW_2, (Node) node.array[subidx], i, val);
    }
    return ret;
  }

  private static Node newPath(AtomicReference<Thread> edit, int level, Node node) {
    if (level == 0) {
      return node;
    }
    Node ret = new Node(edit);
    ret.array[0] = newPath(edit, level - NODE_LENGTH_POW_2, node);
    return ret;
  }

  //    public static class Reduced<A> {
  //        public final A val;
  //        private Reduced(A a) { val = a; }
  //    }
  //
  //    /**
  //     * This is an early exit indicator for reduce operations.  Return one of these when you want
  //     * the reduction to end. It uses types, but not in a "traditional" way.
  //     */
  //    public static <A> Reduced<A> done(A a) { return new Reduced<>(a); }

  // Implements Counted through ITransientVector<E> -> Indexed<E> -> Counted.
  @SuppressWarnings("WeakerAccess")
  public static final class MutVector<F> extends UnmodList.AbstractUnmodList<F>
      implements MutList<F> {

    // The number of items in this Vector.
    private int size;

    private int shift;

    // The root node of the data tree inside this vector.
    private Node root;

    private F[] tail;

    private MutVector(int c, int s, Node r, F[] t) {
      size = c;
      shift = s;
      root = r;
      tail = t;
    }

    private MutVector(PersistentVector<F> v) {
      this(v.size, v.shift, editableRoot(v.root), editableTail(v.tail));
    }

    private Node ensureEditable(Node node) {
      if (node.edit == root.edit) return node;
      return new Node(root.edit, node.array.clone());
    }

    private void ensureEditable() {
      if (root.edit.get() == null) {
        throw new IllegalStateException("Mutable used after immutable! call");
      }
      //		root = editableRoot(root);
      //		tail = editableTail(tail);
    }

    @Override
    public int size() {
      ensureEditable();
      return size;
    }

    @SuppressWarnings("unchecked")
    @Override
    public PersistentVector<F> immutable() {
      ensureEditable();
      //		Thread owner = root.edit.get();
      //		if(owner != null && owner != Thread.currentThread())
      //			{
      //			throw new IllegalStateException("Mutation release by non-owner thread");
      //			}
      root.edit.set(null);
      F[] trimmedTail = (F[]) new Object[size - tailoff()];
      System.arraycopy(tail, 0, trimmedTail, 0, trimmedTail.length);
      return new PersistentVector<>(size, shift, root, trimmedTail);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MutList<F> append(F val) {
      ensureEditable();
      int i = size;
      // room in tail?
      if (i - tailoff() < MAX_NODE_LENGTH) {
        tail[i & LOW_BITS] = val;
        ++size;
        return this;
      }
      // full tail, push into tree
      Node newroot;
      Node tailnode = new Node(root.edit, tail);
      tail = (F[]) new Object[MAX_NODE_LENGTH];
      tail[0] = val;
      int newshift = shift;
      // overflow root?
      if ((size >>> NODE_LENGTH_POW_2) > (1 << shift)) {
        newroot = new Node(root.edit);
        newroot.array[0] = root;
        newroot.array[1] = newPath(root.edit, shift, tailnode);
        newshift += NODE_LENGTH_POW_2;
      } else newroot = pushTail(shift, root, tailnode);
      root = newroot;
      shift = newshift;
      ++size;
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public MutList<F> appendSome(Fn0<? extends Option<F>> supplier) {
      return supplier.apply().match((it) -> append(it), () -> this);
    }

    // TODO: are these all node<F> or could this return a super-type of F?
    private Node pushTail(int level, Node parent, Node tailnode) {
      // if parent is leaf, insert node,
      // else does it map to an existing child? -> nodeToInsert = pushNode one more level
      // else alloc new path
      // return  nodeToInsert placed in parent
      parent = ensureEditable(parent);
      int subidx = ((size - 1) >>> level) & LOW_BITS;
      Node ret = parent;
      Node nodeToInsert;
      if (level == NODE_LENGTH_POW_2) {
        nodeToInsert = tailnode;
      } else {
        Node child = (Node) parent.array[subidx];
        nodeToInsert =
            (child != null)
                ? pushTail(level - NODE_LENGTH_POW_2, child, tailnode)
                : newPath(root.edit, level - NODE_LENGTH_POW_2, tailnode);
      }
      ret.array[subidx] = nodeToInsert;
      return ret;
    }

    // Returns the high (gt 5) bits of the index of the last item.
    // I think this is the index of the start of the last array in the tree.
    private int tailoff() {
      // ((size - 1) / 32) * 32
      // (Size - 1) is an index into an array because size starts counting from 1 and array
      //            indices start from 0.
      // /32 *32 zeroes out the low 5 bits.
      return (size < MAX_NODE_LENGTH) ? 0 : ((size - 1) >>> NODE_LENGTH_POW_2) << NODE_LENGTH_POW_2;
      // Last line can be replaced with (size -1) & HIGH_BITS
    }

    //        @SuppressWarnings("unchecked")
    //        private F[] leafNodeArrayFor(int i) {
    //            if (i >= 0 && i < size) {
    //                if (i >= tailoff()) {
    //                    return tail;
    //                }
    //                Node node = root;
    //                for (int level = shift; level > 0; level -= NODE_LENGTH_POW_2) {
    //                    node = (Node) node.array[(i >>> level) & LOW_BITS];
    //                }
    //                return (F[]) node.array;
    //            }
    //            throw new IndexOutOfBoundsException();
    //        }

    @SuppressWarnings("unchecked")
    private F[] editableArrayFor(int i) {
      if (i >= 0 && i < size) {
        if (i >= tailoff()) return tail;
        Node node = root;
        for (int level = shift; level > 0; level -= NODE_LENGTH_POW_2) {
          int idx = (i >>> level) & LOW_BITS;
          node.array[idx] = ensureEditable((Node) node.array[idx]);
          node = (Node) node.array[idx];
        }
        return (F[]) node.array;
      }
      throw new IndexOutOfBoundsException();
    }

    @Override
    public F get(int i) {
      ensureEditable();
      F[] node = editableArrayFor(i);
      return node[i & LOW_BITS];
    }

    @Override
    public MutList<F> replace(int idx, F e) {
      ensureEditable();
      F[] node = editableArrayFor(idx);
      node[idx & LOW_BITS] = e;
      return this;
    }

    //
    //        public F nth(int i, F notFound) {
    //            if (i >= 0 && i < size())
    //                return nth(i);
    //            return notFound;
    //        }
    //
    //        /** Convenience method for using any class that implements Number as a key. */
    //        public F nth(Number key) { return nth(key.intValue(), null); }
    //
    //        /** Convenience method for using any class that implements Number as a key. */
    //        public F nth(Number key, F notFound) { return nth(key.intValue(), notFound); }

    //        public MutList<F> insertAt(int i, F val) {
    //            ensureEditable();
    //            if (i >= 0 && i < size) {
    //                if (i >= tailoff()) {
    //                    tail[i & LOW_BITS] = val;
    //                    return this;
    //                }
    //
    //                root = doAssoc(shift, root, i, val);
    //                return this;
    //            } else if (i == size) {
    //                return concat(val);
    //            }
    //            throw new IndexOutOfBoundsException();
    //        }

    //        public MutList<F> assoc(int key, F val) {
    //            //note - relies on ensureEditable in insertAt
    //            return insertAt(key, val);
    //        }
    //
    //        public MutList<F> assoc(Number key, F val) {
    //            return insertAt(key.intValue(), val);
    //        }

    //        @SuppressWarnings("unchecked")
    //        private Node doAssoc(int level, Node node, int i, Object val) {
    //            node = ensureEditable(node);
    //            Node ret = node;
    //            if (level == 0) {
    //                ret.array[i & LOW_BITS] = val;
    //            } else {
    //                int subidx = (i >>> level) & LOW_BITS;
    //                ret.array[subidx] = doAssoc(level - NODE_LENGTH_POW_2,
    //                                            (Node) node.array[subidx], i, val);
    //            }
    //            return ret;
    //        }

    //        @SuppressWarnings("unchecked")
    //        public MutList<F> pop() {
    //            ensureEditable();
    //            if (size == 0)
    //                throw new IllegalStateException("Can't pop empty vector");
    //            if (size == 1) {
    //                size = 0;
    //                return this;
    //            }
    //            int i = size - 1;
    //            //pop in tail?
    //            if ((i & LOW_BITS) > 0) {
    //                --size;
    //                return this;
    //            }
    //
    //            F[] newtail = editableArrayFor(size - 2);
    //
    //            Node newroot = popTail(shift, root);
    //            int newshift = shift;
    //            if (newroot == null) {
    //                newroot = new Node(root.edit);
    //            }
    //            if (shift > NODE_LENGTH_POW_2 && newroot.array[1] == null) {
    //                newroot = ensureEditable((Node) newroot.array[0]);
    //                newshift -= NODE_LENGTH_POW_2;
    //            }
    //            root = newroot;
    //            shift = newshift;
    //            --size;
    //            tail = newtail;
    //            return this;
    //        }

    //        @SuppressWarnings("unchecked")
    //        private Node popTail(int level, Node node) {
    //            node = ensureEditable(node);
    //            int subidx = ((size - 2) >>> level) & LOW_BITS;
    //            if (level > NODE_LENGTH_POW_2) {
    //                Node newchild = popTail(level - NODE_LENGTH_POW_2, (Node) node.array[subidx]);
    //                if (newchild == null && subidx == 0)
    //                    return null;
    //                else {
    //                    node.array[subidx] = newchild;
    //                    return node;
    //                }
    //            } else if (subidx == 0)
    //                return null;
    //            else {
    //                node.array[subidx] = null;
    //                return node;
    //            }
    //        }

    private static Node editableRoot(Node node) {
      return new Node(new AtomicReference<>(Thread.currentThread()), node.array.clone());
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] editableTail(T[] tl) {
      Object[] ret = new Object[MAX_NODE_LENGTH];
      System.arraycopy(tl, 0, ret, 0, tl.length);
      return (T[]) ret;
    }
  } // end inner static class MutVector
}
