/*
 * Copyright © 2006-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Stack;
import org.pkl.core.util.paguro.function.Fn1;
import org.pkl.core.util.paguro.oneOf.Option;
import org.pkl.core.util.paguro.tuple.Tuple2;

/**
 * Persistent Red Black Tree. Note that instances of this class are constant values i.e. add/remove
 * etc return new values.
 *
 * <p>See Okasaki, Kahrs, Larsen et al
 *
 * <p>This file is a derivative work based on a Clojure collection licensed under the Eclipse Public
 * License 1.0 Copyright Rich Hickey
 *
 * @author Rich Hickey: Original author
 * @author Glen Peterson: Added generic types, static factories, custom serialization, and made
 *     Nodes extend Tuple2. All errors are Glen's.
 */
public class PersistentTreeMap<K, V> extends AbstractUnmodMap<K, V>
    implements ImSortedMap<K, V>, Serializable {

  /**
   * This is a throw-away class used internally by PersistentTreeMap and PersistentHashMap like a
   * mutable Option class to hold either null, or some value. I don't want to remove this without
   * checking the effect on performance.
   */
  static class Box<E> {
    E val;

    Box(E val) {
      this.val = val;
    }
  }

  /**
   * Returns a new PersistentTreeMap of the given comparable keys and their paired values, skipping
   * any null Entries.
   */
  public static <K extends Comparable<K>, V> PersistentTreeMap<K, V> of(
      Iterable<Map.Entry<K, V>> es) {
    if (es == null) {
      return empty();
    }
    PersistentTreeMap<K, V> map = new PersistentTreeMap<>(Equator.defaultComparator(), null, 0);
    for (Map.Entry<K, V> entry : es) {
      if (entry != null) {
        map = map.assoc(entry.getKey(), entry.getValue());
      }
    }
    return map;
  }

  /**
   * Returns a new PersistentTreeMap of the specified comparator and the given key/value pairs.
   *
   * @param comp A comparator (on the keys) that defines the sort order inside the new map. This
   *     becomes a permanent part of the map and all sub-maps or appended maps derived from it. If
   *     you want to use a null key, make sure the comparator treats nulls correctly in all
   *     circumstances!
   * @param kvPairs Key/value pairs (to go into the map). In the case of a duplicate key, later
   *     values in the input list overwrite the earlier ones. The resulting map can contain zero or
   *     one null key (if your comparator knows how to sort nulls) and any number of null values.
   *     Null k/v pairs will be silently ignored.
   * @return a new PersistentTreeMap of the specified comparator and the given key/value pairs
   */
  public static <K, V> PersistentTreeMap<K, V> ofComp(
      Comparator<? super K> comp, Iterable<Map.Entry<K, V>> kvPairs) {
    if (kvPairs == null) {
      return new PersistentTreeMap<>(comp, null, 0);
    }
    PersistentTreeMap<K, V> map = new PersistentTreeMap<>(comp, null, 0);
    for (Map.Entry<K, V> entry : kvPairs) {
      if (entry != null) {
        map = map.assoc(entry.getKey(), entry.getValue());
      }
    }
    return map;
  }

  /**
   * Be extremely careful with this because it uses the default comparator, which only works for
   * items that implement Comparable (have a "natural ordering"). An attempt to use it with other
   * items will blow up at runtime. Either a withComparator() method will be added, or this will be
   * removed.
   */
  public static final PersistentTreeMap EMPTY =
      new PersistentTreeMap<>(Equator.defaultComparator(), null, 0);

  /**
   * Be extremely careful with this because it uses the default comparator, which only works for
   * items that implement Comparable (have a "natural ordering"). An attempt to use it with other
   * items will blow up at runtime. Either a withComparator() method will be added, or this will be
   * removed.
   */
  @SuppressWarnings("unchecked")
  public static <K extends Comparable<K>, V> PersistentTreeMap<K, V> empty() {
    return (PersistentTreeMap<K, V>) EMPTY;
  }

  /** Returns a new empty PersistentTreeMap that will use the specified comparator. */
  public static <K, V> PersistentTreeMap<K, V> empty(Comparator<? super K> c) {
    return new PersistentTreeMap<>(c, null, 0);
  }

  /**
   * This would be private, except that PersistentTreeSet needs to check that the wrapped comparator
   * is serializable.
   */
  static final class KeyComparator<T> implements Comparator<Map.Entry<T, ?>>, Serializable {
    private static final long serialVersionUID = 20160827174100L;

    private final Comparator<? super T> wrappedComparator;

    private KeyComparator(Comparator<? super T> c) {
      wrappedComparator = c;
    }

    @Override
    public int compare(Map.Entry<T, ?> a, Map.Entry<T, ?> b) {
      return wrappedComparator.compare(a.getKey(), b.getKey());
    }

    @Override
    public String toString() {
      return "KeyComparator(" + wrappedComparator + ")";
    }

    /**
     * This would be private, except that PersistentTreeSet needs to check that the wrapped
     * comparator is serializable.
     */
    Comparator<? super T> unwrap() {
      return wrappedComparator;
    }
  }

  // ==================================== Instance Variables ====================================
  private final Comparator<? super K> comp;
  private final transient Node<K, V> tree;
  private final int size;

  // ======================================== Constructor ========================================
  private PersistentTreeMap(Comparator<? super K> c, Node<K, V> t, int n) {
    comp = c;
    tree = t;
    size = n;
  }

  //    /** Returns a new PersistentTreeMap of the given comparable keys and their paired values. */
  //    public static <K extends Comparable<K>,V> PersistentTreeMap<K,V> of() {
  //        return empty();
  //    }

  // ======================================= Serialization =======================================
  // This class has a custom serialized form designed to be as small as possible.  It does not
  // have the same internal structure as an instance of this class.

  // For serializable.  Make sure to change whenever internal data format changes.
  private static final long serialVersionUID = 20160904095000L;

  // Check out Josh Bloch Item 78, p. 312 for an explanation of what's going on here.
  private static class SerializationProxy<K, V> implements Serializable {
    // For serializable.  Make sure to change whenever internal data format changes.
    private static final long serialVersionUID = 20160904095000L;

    private final Comparator<? super K> comparator;
    private final int size;
    private transient PersistentTreeMap<K, V> theMap;

    SerializationProxy(PersistentTreeMap<K, V> phm) {
      comparator = phm.comp;
      if (!(comparator instanceof Serializable)) {
        throw new IllegalStateException(
            "Comparator must equal serializable." + "  Instead it was " + comparator);
      }
      size = phm.size;
      theMap = phm;
    }

    // Taken from Josh Bloch Item 75, p. 298
    private void writeObject(ObjectOutputStream s) throws IOException {
      s.defaultWriteObject();

      // Serializing in iteration-order yields a worst-case deserialization because
      // without re-balancing (rotating nodes) such an order yields an completely unbalanced
      // linked list internal structure.
      //       4
      //      /
      //     3
      //    /
      //   2
      //  /
      // 1
      //
      // That seems unnecessary since before Serialization we might have something like this
      // which, while not perfect, requires no re-balancing:
      //
      //                    11
      //            ,------'  `----.
      //           8                14
      //        ,-' `-.            /  \
      //       4       9         13    15
      //    ,-' `-.     \       /        \
      //   2       6     10   12          16
      //  / \     / \
      // 1   3   5   7
      //
      // If we serialize the middle value (n/2) first.  Then the n/4 and 3n/4,
      // followed by n/8, 3n/8, 5n/8, 7n/8, then n/16, 3n/16, etc.  Finally, the odd-numbered
      // values last.  That gives us the order:
      // 8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15
      //
      // Deserializing in that order yields an ideally balanced tree without any shuffling:
      //               8
      //        ,-----' `-------.
      //       4                 12
      //    ,-' `-.          ,--'  `--.
      //   2       6       10          14
      //  / \     / \     /  \        /  \
      // 1   3   5   7   9    11    13    15
      //
      // That would be ideal, but I don't see how to do that without a significant
      // intermediate data structure.
      //
      // A good improvement could be made by serializing breadth-first instead of depth first
      // to at least yield a tree no worse than the original without requiring shuffling.
      //
      // This improvement does not change the serialized form, or break compatibility.
      // But it has a superior ordering for deserialization without (or with minimal)
      // rotations.

      //            System.out.println("Serializing tree map...");
      if (theMap.tree != null) {
        Queue<Node<K, V>> queue = new ArrayDeque<>();
        queue.add(theMap.tree);
        while (queue.peek() != null) {
          Node<K, V> node = queue.remove();
          //                    System.out.println("Node: " + node);
          s.writeObject(node.getKey());
          s.writeObject(node.getValue());
          Node<K, V> child = node.left();
          if (child != null) {
            queue.add(child);
          }
          child = node.right();
          if (child != null) {
            queue.add(child);
          }
        }
      }
      //            for (UnEntry<K,V> entry : theMap) {
      //                s.writeObject(entry.getKey());
      //                s.writeObject(entry.getValue());
      //            }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
      s.defaultReadObject();
      theMap = new PersistentTreeMap<>(comparator, null, 0);
      for (int i = 0; i < size; i++) {
        theMap = theMap.assoc((K) s.readObject(), (V) s.readObject());
      }
    }

    private Object readResolve() {
      return theMap;
    }
  }

  private Object writeReplace() {
    return new SerializationProxy<>(this);
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    throw new InvalidObjectException("Proxy required");
  }

  // ===================================== Instance Methods =====================================
  /**
   * Returns a view of the mappings contained in this map. The set should actually contain
   * UnmodMap.UnEntry items, but that return signature is illegal in Java, so you'll just have to
   * remember.
   */
  @Override
  public ImSortedSet<Entry<K, V>> entrySet() {
    // This is the pretty way to do it.
    return this.fold(PersistentTreeSet.ofComp(new KeyComparator<>(comp)), PersistentTreeSet::put);
  }

  //    public static final Equator<SortedMap> EQUATOR = new Equator<SortedMap>() {
  //        @Override
  //        public int hash(SortedMap kvSortedMap) {
  //            return UnmodIterable.hashCode(kvSortedMap.entrySet());
  //        }
  //
  //        @Override
  //        public boolean eq(SortedMap o1, SortedMap o2) {
  //            if (o1 == o2) { return true; }
  //            if ( o1.size() != o2.size() ) { return false; }
  //            return UnmodSortedIterable.equals(UnmodSortedIterable.castFromSortedMap(o1),
  //                                              UnmodSortedIterable.castFromSortedMap(o2));
  //        }
  //    };

  //    /** Returns a view of the keys contained in this map. */
  //    @Override public ImSet<K> keySet() { return PersistentTreeSet.ofMap(this); }

  /** {@inheritDoc} */
  @Override
  public ImSortedMap<K, V> subMap(K fromKey, K toKey) {
    int diff = comp.compare(fromKey, toKey);

    if (diff > 0) {
      throw new IllegalArgumentException("fromKey is greater than toKey");
    }
    UnEntry<K, V> last = last();
    K lastKey = last.getKey();
    int compFromKeyLastKey = comp.compare(fromKey, lastKey);

    // If no intersect, return empty. We aren't checking the toKey vs. the firstKey() because
    // that's a single pass through the iterator loop which is probably as cheap as checking
    // here.
    if ((diff == 0) || (compFromKeyLastKey > 0)) {
      return new PersistentTreeMap<>(comp, null, 0);
    }
    // If map is entirely contained, just return it.
    if ((comp.compare(fromKey, firstKey()) <= 0) && (comp.compare(toKey, lastKey) > 0)) {
      return this;
    }
    // Don't iterate through entire map for only the last item.
    if (compFromKeyLastKey == 0) {
      return ofComp(comp, Collections.singletonList(last));
    }

    ImSortedMap<K, V> ret = new PersistentTreeMap<>(comp, null, 0);
    UnmodIterator<UnEntry<K, V>> iter = this.iterator();
    while (iter.hasNext()) {
      UnEntry<K, V> next = iter.next();
      K key = next.getKey();
      if (comp.compare(toKey, key) <= 0) {
        break;
      }
      if (comp.compare(fromKey, key) > 0) {
        continue;
      }
      ret = ret.assoc(key, next.getValue());
    }
    return ret;
  }

  //    String debugStr() {
  //        return "PersistentTreeMap(size=" + size +
  //               " comp=" + comp +
  //               " tree=" + tree + ")";
  //    }

  //    /** {@inheritDoc} */
  //    @Override public UnmodCollection<V> values() {
  //        class ValueColl<B,Z> implements UnmodCollection<B>, UnmodSortedIterable<B> {
  //            private final Fn0<UnmodSortedIterator<UnEntry<Z,B>>> iterFactory;
  //            private ValueColl(Fn0<UnmodSortedIterator<UnEntry<Z, B>>> f) { iterFactory = f; }
  //
  //            @Override public int size() { return size; }
  //
  //            @Override public UnmodSortedIterator<B> iterator() {
  //                final UnmodSortedIterator<UnmodMap.UnEntry<Z,B>> iter = iterFactory.apply();
  //                return new UnmodSortedIterator<B>() {
  //                    @Override public boolean hasNext() { return iter.hasNext(); }
  //                    @Override public B next() { return iter.next().getValue(); }
  //                };
  //            }
  //            @Override public int hashCode() { return UnmodIterable.hashCode(this); }
  //            @Override public boolean equals(Object o) {
  //                if (this == o) { return true; }
  //                if ( !(o instanceof UnmodSortedIterable) ) { return false; }
  //                return UnmodSortedIterable.equals(this, (UnmodSortedIterable) o);
  //            }
  //            @Override public String toString() {
  //                return UnmodSortedIterable.toString("ValueColl", this);
  //            }
  //        }
  //        return new ValueColl<>(() -> this.iterator());
  //    }

  /** {@inheritDoc} */
  @Override
  public Option<UnEntry<K, V>> head() {
    Node<K, V> t = tree;
    if (t != null) {
      while (t.left() != null) {
        t = t.left();
      }
    }
    return Option.some(t);
  }

  /** {@inheritDoc} */
  @Override
  public ImSortedMap<K, V> tailMap(K fromKey) {
    UnEntry<K, V> last = last();
    K lastKey = last.getKey();
    int compFromKeyLastKey = comp.compare(fromKey, lastKey);

    // If no intersect, return empty. We aren't checking the toKey vs. the firstKey() because
    // that's a single pass through the iterator loop which is probably as cheap as checking
    // here.
    if (compFromKeyLastKey > 0) {
      return new PersistentTreeMap<>(comp, null, 0);
    }
    // If map is entirely contained, just return it.
    if (comp.compare(fromKey, firstKey()) <= 0) {
      return this;
    }
    // Don't iterate through entire map for only the last item.
    if (compFromKeyLastKey == 0) {
      return ofComp(comp, Collections.singletonList(last));
    }

    ImSortedMap<K, V> ret = new PersistentTreeMap<>(comp, null, 0);
    UnmodIterator<UnEntry<K, V>> iter = this.iterator();
    while (iter.hasNext()) {
      UnEntry<K, V> next = iter.next();
      K key = next.getKey();
      if (comp.compare(fromKey, key) > 0) {
        continue;
      }
      ret = ret.assoc(key, next.getValue());
    }
    return ret;
  }

  //    /** {@inheritDoc} */
  //    @Override public Sequence<UnEntry<K,V>> tail() {
  //        if (size() > 1) {
  //            return without(firstKey());
  ////            // The iterator is designed to do this quickly.  It also prevents an infinite
  // loop.
  ////            UnmodIterator<UnEntry<K,V>> iter = this.iterator();
  ////            // Drop the head
  ////            iter.next();
  ////            return tailMap(iter.next().getKey());
  //        }
  //        return Sequence.emptySequence();
  //    }

  //    @SuppressWarnings("unchecked")
  //    static public <S, K extends S, V extends S> PersistentTreeMap<K,V> create(ISeq<S> items) {
  //        PersistentTreeMap<K,V> ret = empty();
  //        for (; items != null; items = items.next().next()) {
  //            if (items.next() == null)
  //                throw new IllegalArgumentException(String.format("No value supplied for key:
  // %s",
  //                                                                 items.head()));
  //            ret = ret.assoc((K) items.head(), (V) RT.second(items));
  //        }
  //        return ret;
  //    }

  //    @SuppressWarnings("unchecked")
  //    static public <S, K extends S, V extends S>
  //    PersistentTreeMap<K,V> create(Comparator<? super K> comp, ISeq<S> items) {
  //        PersistentTreeMap<K,V> ret = new PersistentTreeMap<>(comp);
  //        for (; items != null; items = items.next().next()) {
  //            if (items.next() == null)
  //                throw new IllegalArgumentException(String.format("No value supplied for key:
  // %s",
  //                                                                 items.head()));
  //            ret = ret.assoc((K) items.head(), (V) RT.second(items));
  //        }
  //        return ret;
  //    }

  /**
   * Returns the comparator used to order the keys in this map, or null if it uses
   * Fn2.DEFAULT_COMPARATOR (for compatibility with java.util.SortedMap).
   */
  @Override
  public Comparator<? super K> comparator() {
    return (comp == Equator.Comp.DEFAULT) ? null : comp;
  }

  //    /** Returns true if the map contains the given key. */
  //    @SuppressWarnings("unchecked")
  //    @Override public boolean containsKey(Object key) {
  //        return entryAt((K) key) != null;
  //    }

  //    /** Returns the value associated with the given key. */
  //    @SuppressWarnings("unchecked")
  //    @Override
  //    public V get(Object key) {
  //        if (key == null) { return null; }
  //        Entry<K,V> entry = entryAt((K) key);
  //        if (entry == null) { return null; }
  //        return entry.getValue();
  //    }

  // public PersistentTreeMap<K,V> assocEx(K key, V val) {
  // Inherits default implementation of assocEx from IPersistentMap

  /** {@inheritDoc} */
  @Override
  public PersistentTreeMap<K, V> assoc(K key, V val) {
    Box<Node<K, V>> found = new Box<>(null);
    Node<K, V> t = add(tree, key, val, found);
    // null == already contains key
    if (t == null) {
      Node<K, V> foundNode = found.val;

      // note only get same collection on identity of val, not equals()
      if (foundNode.getValue() == val) {
        return this;
      }
      return new PersistentTreeMap<>(comp, replace(tree, key, val), size);
    }
    return new PersistentTreeMap<>(comp, t.blacken(), size + 1);
  }

  /** {@inheritDoc} */
  @Override
  public PersistentTreeMap<K, V> without(K key) {
    Box<Node<K, V>> found = new Box<>(null);
    Node<K, V> t = remove(tree, key, found);
    if (t == null) {
      // null == doesn't contain key
      if (found.val == null) {
        return this;
      }
      // empty
      return new PersistentTreeMap<>(comp, null, 0);
    }
    return new PersistentTreeMap<>(comp, t.blacken(), size - 1);
  }

  //    @Override
  //    public ISeq<Map.Entry<K,V>> seq() {
  //        if (size > 0)
  //            return Iter.create(tree, true, size);
  //        return null;
  //    }
  //
  //    @Override
  //    public ISeq<Map.Entry<K,V>> rseq() {
  //        if (size > 0)
  //            return Iter.create(tree, false, size);
  //        return null;
  //    }

  //    @Override
  //    public Object entryKey(Map.Entry<K,V> entry) {
  //        return entry.getKey();
  //    }

  //    // This lets you make a sequence of map entries from this HashMap.
  //// The other methods on Sorted seem to care only about the key, and the implementations of them
  //// here work that way.  This one, however, returns a sequence of Map.Entry<K,V> or Node<K,V>
  //// If I understood why, maybe I could do better.
  //    @SuppressWarnings("unchecked")
  //    @Override
  //    public ISeq<Map.Entry<K,V>> seq(boolean ascending) {
  //        if (size > 0)
  //            return Iter.create(tree, ascending, size);
  //        return null;
  //    }

  //    @SuppressWarnings("unchecked")
  //    @Override
  //    public ISeq<Map.Entry<K,V>> seqFrom(Object key, boolean ascending) {
  //        if (size > 0) {
  //            ISeq<Node<K,V>> stack = null;
  //            Node<K,V> t = tree;
  //            while (t != null) {
  //                int c = doCompare((K) key, t.key);
  //                if (c == 0) {
  //                    stack = RT.cons(t, stack);
  //                    return new Iter<>(stack, ascending);
  //                } else if (ascending) {
  //                    if (c < 0) {
  //                        stack = RT.cons(t, stack);
  //                        t = t.left();
  //                    } else
  //                        t = t.right();
  //                } else {
  //                    if (c > 0) {
  //                        stack = RT.cons(t, stack);
  //                        t = t.right();
  //                    } else
  //                        t = t.left();
  //                }
  //            }
  //            if (stack != null)
  //                return new Iter<>(stack, ascending);
  //        }
  //        return null;
  //    }

  /** {@inheritDoc} */
  @Override
  public UnmodSortedIterator<UnEntry<K, V>> iterator() {
    return iterator(Tuple2::of);
  }

  @Override
  public UnmodSortedIterator<K> keyIterator() {
    return iterator(Node::getKey);
  }

  @Override
  public UnmodSortedIterator<V> valIterator() {
    return iterator(Node::getValue);
  }

  public <R> UnmodSortedIterator<R> iterator(Fn1<Node<K, V>, R> aFn) {
    return new NodeIterator<>(tree, true, aFn);
  }

  //    public NodeIterator<K,V> reverseIterator() { return new NodeIterator<>(tree, false); }

  /** Returns the first key in this map or throws a NoSuchElementException if the map is empty. */
  @Override
  public K firstKey() {
    if (size() < 1) {
      throw new NoSuchElementException("this map is empty");
    }
    return head().get().getKey();
  }

  /** Returns the last key in this map or throws a NoSuchElementException if the map is empty. */
  @Override
  public K lastKey() {
    UnEntry<K, V> max = last();
    if (max == null) {
      throw new NoSuchElementException("this map is empty");
    }
    return max.getKey();
  }

  /** Returns the last key/value pair in this map, or null if the map is empty. */
  public UnEntry<K, V> last() {
    Node<K, V> t = tree;
    if (t != null) {
      while (t.right() != null) t = t.right();
    }
    return t;
  }

  //    public int depth() {
  //        return depth(tree);
  //    }

  //    int depth(Node<K,V> t) {
  //        if (t == null)
  //            return 0;
  //        return 1 + Math.max(depth(t.left()), depth(t.right()));
  //    }

  // public Object valAt(Object key){
  // Default implementation now inherited from ILookup

  /** Returns the number of key/value mappings in this map. */
  @Override
  public int size() {
    return size;
  }

  /**
   * Returns an Option of the key/value pair matching the given key, or Option.none() if the key is
   * not found.
   */
  @Override
  public Option<UnmodMap.UnEntry<K, V>> entry(K key) {
    Node<K, V> t = tree;
    while (t != null) {
      int c = comp.compare(key, t.getKey());
      if (c == 0) return Option.some(t);
      else if (c < 0) t = t.left();
      else t = t.right();
    }
    return Option.none(); // t; // t is always null
  }

  //    // In TreeMap, this is final Entry<K,V> getEntry(Object key)
  //    /** Returns the key/value pair matching the given key, or null if the key is not found. */
  //    public UnEntry<K,V> entryAt(K key) {
  //        Node<K,V> t = tree;
  //        while (t != null) {
  //            int c = comp.compare(key, t.key);
  //            if (c == 0)
  //                return t;
  //            else if (c < 0)
  //                t = t.left();
  //            else
  //                t = t.right();
  //        }
  //        return null; // t; // t is always null
  //    }

  private Node<K, V> add(Node<K, V> t, K key, V val, Box<Node<K, V>> found) {
    if (t == null) {
      //            if (val == null)
      //                return new Red<>(key);
      return new Red<>(key, val);
    }
    int c = comp.compare(key, t.getKey());
    if (c == 0) {
      found.val = t;
      return null;
    }
    Node<K, V> ins = add(c < 0 ? t.left() : t.right(), key, val, found);
    if (ins == null) // found below
    return null;
    if (c < 0) return t.addLeft(ins);
    return t.addRight(ins);
  }

  private Node<K, V> remove(Node<K, V> t, K key, Box<Node<K, V>> found) {
    if (t == null) return null; // not found indicator
    int c = comp.compare(key, t.getKey());
    if (c == 0) {
      found.val = t;
      return append(t.left(), t.right());
    }
    Node<K, V> del = remove(c < 0 ? t.left() : t.right(), key, found);
    if (del == null && found.val == null) // not found below
    return null;
    if (c < 0) {
      if (t.left() instanceof PersistentTreeMap.Black)
        return balanceLeftDel(t.getKey(), t.getValue(), del, t.right());
      else return red(t.getKey(), t.getValue(), del, t.right());
    }
    if (t.right() instanceof PersistentTreeMap.Black)
      return balanceRightDel(t.getKey(), t.getValue(), t.left(), del);
    return red(t.getKey(), t.getValue(), t.left(), del);
    //		return t.removeLeft(del);
    //	return t.removeRight(del);
  }

  // static <K,V, K1 extends K, K2 extends K, V1 extends V, V2 extends V>
  // Node<K,V> concat(Node<K1,V1> left, Node<K2,V2> right){
  @SuppressWarnings("unchecked")
  private static <K, V> Node<K, V> append(
      Node<? extends K, ? extends V> left, Node<? extends K, ? extends V> right) {
    if (left == null) return (Node<K, V>) right;
    else if (right == null) return (Node<K, V>) left;
    else if (left instanceof PersistentTreeMap.Red) {
      if (right instanceof PersistentTreeMap.Red) {
        Node<K, V> app = append(left.right(), right.left());
        if (app instanceof PersistentTreeMap.Red)
          return red(
              app.getKey(),
              app.getValue(),
              red(left.getKey(), left.getValue(), left.left(), app.left()),
              red(right.getKey(), right.getValue(), app.right(), right.right()));
        else
          return red(
              left.getKey(),
              left.getValue(),
              left.left(),
              red(right.getKey(), right.getValue(), app, right.right()));
      } else return red(left.getKey(), left.getValue(), left.left(), append(left.right(), right));
    } else if (right instanceof PersistentTreeMap.Red)
      return red(right.getKey(), right.getValue(), append(left, right.left()), right.right());
    else // black/black
    {
      Node<K, V> app = append(left.right(), right.left());
      if (app instanceof PersistentTreeMap.Red)
        return red(
            app.getKey(),
            app.getValue(),
            black(left.getKey(), left.getValue(), left.left(), app.left()),
            black(right.getKey(), right.getValue(), app.right(), right.right()));
      else
        return balanceLeftDel(
            left.getKey(),
            left.getValue(),
            left.left(),
            black(right.getKey(), right.getValue(), app, right.right()));
    }
  }

  private static <K, V, K1 extends K, V1 extends V> Node<K, V> balanceLeftDel(
      K1 key, V1 val, Node<? extends K, ? extends V> del, Node<? extends K, ? extends V> right) {
    if (del instanceof PersistentTreeMap.Red) return red(key, val, del.blacken(), right);
    else if (right instanceof PersistentTreeMap.Black)
      return rightBalance(key, val, del, right.redden());
    else if (right instanceof PersistentTreeMap.Red
        && right.left() instanceof PersistentTreeMap.Black)
      return red(
          right.left().getKey(),
          right.left().getValue(),
          black(key, val, del, right.left().left()),
          rightBalance(
              right.getKey(), right.getValue(), right.left().right(), right.right().redden()));
    else throw new UnsupportedOperationException("Invariant violation");
  }

  private static <K, V, K1 extends K, V1 extends V> Node<K, V> balanceRightDel(
      K1 key, V1 val, Node<? extends K, ? extends V> left, Node<? extends K, ? extends V> del) {
    if (del instanceof PersistentTreeMap.Red) return red(key, val, left, del.blacken());
    else if (left instanceof PersistentTreeMap.Black)
      return leftBalance(key, val, left.redden(), del);
    else if (left instanceof PersistentTreeMap.Red
        && left.right() instanceof PersistentTreeMap.Black)
      return red(
          left.right().getKey(),
          left.right().getValue(),
          leftBalance(left.getKey(), left.getValue(), left.left().redden(), left.right().left()),
          black(key, val, left.right().right(), del));
    else throw new UnsupportedOperationException("Invariant violation");
  }

  private static <K, V, K1 extends K, V1 extends V> Node<K, V> leftBalance(
      K1 key, V1 val, Node<? extends K, ? extends V> ins, Node<? extends K, ? extends V> right) {
    if (ins instanceof PersistentTreeMap.Red && ins.left() instanceof PersistentTreeMap.Red)
      return red(
          ins.getKey(), ins.getValue(), ins.left().blacken(), black(key, val, ins.right(), right));
    else if (ins instanceof PersistentTreeMap.Red && ins.right() instanceof PersistentTreeMap.Red)
      return red(
          ins.right().getKey(),
          ins.right().getValue(),
          black(ins.getKey(), ins.getValue(), ins.left(), ins.right().left()),
          black(key, val, ins.right().right(), right));
    else return black(key, val, ins, right);
  }

  private static <K, V, K1 extends K, V1 extends V> Node<K, V> rightBalance(
      K1 key, V1 val, Node<? extends K, ? extends V> left, Node<? extends K, ? extends V> ins) {
    if (ins instanceof PersistentTreeMap.Red && ins.right() instanceof PersistentTreeMap.Red)
      return red(
          ins.getKey(), ins.getValue(), black(key, val, left, ins.left()), ins.right().blacken());
    else if (ins instanceof PersistentTreeMap.Red && ins.left() instanceof PersistentTreeMap.Red)
      return red(
          ins.left().getKey(),
          ins.left().getValue(),
          black(key, val, left, ins.left().left()),
          black(ins.getKey(), ins.getValue(), ins.left().right(), ins.right()));
    else return black(key, val, left, ins);
  }

  private Node<K, V> replace(Node<K, V> t, K key, V val) {
    int c = comp.compare(key, t.getKey());
    return t.replace(
        t.getKey(),
        c == 0 ? val : t.getValue(),
        c < 0 ? replace(t.left(), key, val) : t.left(),
        c > 0 ? replace(t.right(), key, val) : t.right());
  }

  @SuppressWarnings({"unchecked", "RedundantCast", "Convert2Diamond"})
  private static <K, V, K1 extends K, V1 extends V> Red<K, V> red(
      K1 key, V1 val, Node<? extends K, ? extends V> left, Node<? extends K, ? extends V> right) {
    if (left == null && right == null) {
      //            if (val == null)
      //                return new Red<K,V>(key, val);
      return new Red<K, V>(key, val);
    }
    //        if (val == null)
    //            return new RedBranch<K,V>((K) key, (Node<K,V>) left, (Node<K,V>) right);
    return new RedBranch<K, V>((K) key, (V) val, (Node<K, V>) left, (Node<K, V>) right);
  }

  @SuppressWarnings({"unchecked", "RedundantCast", "Convert2Diamond"})
  private static <K, V, K1 extends K, V1 extends V> Black<K, V> black(
      K1 key, V1 val, Node<? extends K, ? extends V> left, Node<? extends K, ? extends V> right) {
    if (left == null && right == null) {
      //            if (val == null)
      //                return new Black<>(key);
      return new Black<K, V>(key, val);
    }
    //        if (val == null)
    //            return new BlackBranch<K,V>((K) key, (Node<K,V>) left, (Node<K,V>) right);
    return new BlackBranch<K, V>((K) key, (V) val, (Node<K, V>) left, (Node<K, V>) right);
  }

  //    public static class Reduced<A> {
  //        public final A val;
  //        private Reduced(A a) { val = a; }
  //    }

  private abstract static class Node<K, V> extends Tuple2<K, V> {
    Node(K key, V val) {
      super(key, val);
    }

    Node<K, V> left() {
      return null;
    }

    Node<K, V> right() {
      return null;
    }

    abstract Node<K, V> addLeft(Node<K, V> ins);

    abstract Node<K, V> addRight(Node<K, V> ins);

    @SuppressWarnings("UnusedDeclaration")
    abstract Node<K, V> removeLeft(Node<K, V> del);

    @SuppressWarnings("UnusedDeclaration")
    abstract Node<K, V> removeRight(Node<K, V> del);

    abstract Node<K, V> blacken();

    abstract Node<K, V> redden();

    Node<K, V> balanceLeft(Node<K, V> parent) {
      return black(parent._1, parent._2, this, parent.right());
    }

    Node<K, V> balanceRight(Node<K, V> parent) {
      return black(parent._1, parent._2, parent.left(), this);
    }

    abstract Node<K, V> replace(K key, V val, Node<K, V> left, Node<K, V> right);

    @Override
    public String toString() {
      return stringify(_1) + "=" + stringify(_2);
    }

    //        public <R> R kvreduce(Fn3<R,K,V,R> f, R init) {
    //            if (left() != null) {
    //                init = left().kvreduce(f, init);
    //                if (init instanceof Reduced)
    //                    return init;
    //            }
    //            init = f.apply(init, key(), val());
    //            if (init instanceof Reduced)
    //                return init;
    //
    //            if (right() != null) {
    //                init = right().kvreduce(f, init);
    //            }
    //            return init;
    //        }
  } // end class Node.

  private static class Black<K, V> extends Node<K, V> {
    Black(K key, V val) {
      super(key, val);
    }

    @Override
    Node<K, V> addLeft(Node<K, V> ins) {
      return ins.balanceLeft(this);
    }

    @Override
    Node<K, V> addRight(Node<K, V> ins) {
      return ins.balanceRight(this);
    }

    @Override
    Node<K, V> removeLeft(Node<K, V> del) {
      return balanceLeftDel(_1, _2, del, right());
    }

    @Override
    Node<K, V> removeRight(Node<K, V> del) {
      return balanceRightDel(_1, _2, left(), del);
    }

    @Override
    Node<K, V> blacken() {
      return this;
    }

    @Override
    Node<K, V> redden() {
      return new Red<>(_1, _2);
    }

    @Override
    Node<K, V> replace(K key, V val, Node<K, V> left, Node<K, V> right) {
      return black(key, val, left, right);
    }
  }

  private static class BlackBranch<K, V> extends Black<K, V> {
    final transient Node<K, V> left;
    final transient Node<K, V> right;

    BlackBranch(K key, V val, Node<K, V> l, Node<K, V> r) {
      super(key, val);
      left = l;
      right = r;
    }

    @Override
    public Node<K, V> left() {
      return left;
    }

    @Override
    public Node<K, V> right() {
      return right;
    }

    @Override
    Node<K, V> redden() {
      return new RedBranch<>(_1, _2, left, right);
    }
  }

  private static class Red<K, V> extends Node<K, V> {
    Red(K key, V val) {
      super(key, val);
    }

    @Override
    Node<K, V> addLeft(Node<K, V> ins) {
      return red(_1, _2, ins, right());
    }

    @Override
    Node<K, V> addRight(Node<K, V> ins) {
      return red(_1, _2, left(), ins);
    }

    @Override
    Node<K, V> removeLeft(Node<K, V> del) {
      return red(_1, _2, del, right());
    }

    @Override
    Node<K, V> removeRight(Node<K, V> del) {
      return red(_1, _2, left(), del);
    }

    @Override
    Node<K, V> blacken() {
      return new Black<>(_1, _2);
    }

    @Override
    Node<K, V> redden() {
      throw new UnsupportedOperationException("Invariant violation");
    }

    @Override
    Node<K, V> replace(K key, V val, Node<K, V> left, Node<K, V> right) {
      return red(key, val, left, right);
    }
  }

  private static class RedBranch<K, V> extends Red<K, V> {
    final transient Node<K, V> left;
    final transient Node<K, V> right;

    RedBranch(K key, V val, Node<K, V> left, Node<K, V> right) {
      super(key, val);
      this.left = left;
      this.right = right;
    }

    @Override
    public Node<K, V> left() {
      return left;
    }

    @Override
    public Node<K, V> right() {
      return right;
    }

    @Override
    Node<K, V> balanceLeft(Node<K, V> parent) {
      if (left instanceof PersistentTreeMap.Red)
        return red(
            _1,
            _2,
            left.blacken(),
            black(parent.getKey(), parent.getValue(), right, parent.right()));
      else if (right instanceof PersistentTreeMap.Red)
        return red(
            right.getKey(),
            right.getValue(),
            black(_1, _2, left, right.left()),
            black(parent.getKey(), parent.getValue(), right.right(), parent.right()));
      else return super.balanceLeft(parent);
    }

    @Override
    Node<K, V> balanceRight(Node<K, V> parent) {
      if (right instanceof PersistentTreeMap.Red)
        return red(
            _1,
            _2,
            black(parent.getKey(), parent.getValue(), parent.left(), left),
            right.blacken());
      else if (left instanceof PersistentTreeMap.Red)
        return red(
            left.getKey(),
            left.getValue(),
            black(parent.getKey(), parent.getValue(), parent.left(), left.left()),
            black(_1, _2, left.right(), right));
      else return super.balanceRight(parent);
    }

    @Override
    Node<K, V> blacken() {
      return new BlackBranch<>(_1, _2, left, right);
    }
  }

  //    static public class Iter<K, V> extends ASeq<Map.Entry<K,V>> {
  //        final ISeq<Node<K,V>> stack;
  //        final boolean asc;
  //        final int cnt;
  //
  //        public Iter(ISeq<Node<K,V>> stack, boolean asc) {
  //            this.stack = stack;
  //            this.asc = asc;
  //            this.cnt = -1;
  //        }
  //
  //        public Iter(ISeq<Node<K,V>> stack, boolean asc, int cnt) {
  //            this.stack = stack;
  //            this.asc = asc;
  //            this.cnt = cnt;
  //        }
  //
  //        Iter(ISeq<Node<K,V>> stack, boolean asc, int cnt) {
  //            super();
  //            this.stack = stack;
  //            this.asc = asc;
  //            this.cnt = cnt;
  //        }
  //
  //        static <K, V> Iter<K,V> create(Node<K,V> t, boolean asc, int cnt) {
  //            return new Iter<>(push(t, null, asc), asc, cnt);
  //        }
  //
  //        static <K, V> ISeq<Node<K,V>> push(Node<K,V> t, ISeq<Node<K,V>> stack, boolean asc) {
  //            while (t != null) {
  //                stack = RT.cons(t, stack);
  //                t = asc ? t.left() : t.right();
  //            }
  //            return stack;
  //        }
  //
  //        @Override
  //        public Node<K,V> head() {
  //            return stack.head();
  //        }
  //
  //        @Override
  //        public ISeq<Map.Entry<K,V>> next() {
  //            Node<K,V> t = stack.head();
  //            ISeq<Node<K,V>> nextstack = push(asc ? t.right() : t.left(), stack.next(), asc);
  //            if (nextstack != null) {
  //                return new Iter<>(nextstack, asc, cnt - 1);
  //            }
  //            return null;
  //        }
  //
  //        @Override
  //        public int count() {
  //            if (cnt < 0)
  //                return super.count();
  //            return cnt;
  //        }
  //    }

  /**
   * This currently returns chunks of the inner tree structure that implement Map.Entry. They are
   * not serializable and should not be made so. I can alter this to return nice, neat, Tuple2
   * objects which are serializable, but we've made it this far without so...
   */
  private static class NodeIterator<K, V, R> implements UnmodSortedIterator<R> {
    // , Serializable {
    // For serializable.  Make sure to change whenever internal data format changes.
    // private static final long serialVersionUID = 20160827174100L;

    private Stack<Node<K, V>> stack = new Stack<>();
    private final boolean asc;
    private Fn1<Node<K, V>, R> aFn;

    NodeIterator(Node<K, V> t, boolean asc, Fn1<Node<K, V>, R> aFn) {
      this.asc = asc;
      this.aFn = aFn;
      push(t);
    }

    private void push(Node<K, V> t) {
      while (t != null) {
        stack.push(t);
        t = asc ? t.left() : t.right();
      }
    }

    @Override
    public boolean hasNext() {
      return !stack.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public R next() {
      Node<K, V> t = stack.pop();
      push(asc ? t.right() : t.left());

      return aFn.apply(t);
    }
  }

  //    static class KeyIterator<K> implements Iterator<K> {
  //        NodeIterator<K,?> it;
  //
  //        KeyIterator(NodeIterator<K,?> it) {
  //            this.it = it;
  //        }
  //
  //        @Override
  //        public boolean hasNext() {
  //            return it.hasNext();
  //        }
  //
  //        @Override
  //        public K next() {
  //            return it.next().getKey();
  //        }
  //
  //        @Override
  //        public void remove() {
  //            throw new UnsupportedOperationException();
  //        }
  //    }
  //
  //    static class ValIterator<V> implements Iterator<V> {
  //        NodeIterator<?,V> it;
  //
  //        ValIterator(NodeIterator<?,V> it) {
  //            this.it = it;
  //        }
  //
  //        @Override
  //        public boolean hasNext() {
  //            return it.hasNext();
  //        }
  //
  //        @Override
  //        public V next() {
  //            return it.next().getValue();
  //        }
  //
  //        @Override
  //        public void remove() {
  //            throw new UnsupportedOperationException();
  //        }
  //    }
}
