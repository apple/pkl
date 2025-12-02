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

import static org.pkl.core.util.paguro.collections.UnmodIterator.emptyUnmodIterator;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.collections.PersistentTreeMap.Box;
import org.pkl.core.util.paguro.function.Fn2;
import org.pkl.core.util.paguro.oneOf.Option;
import org.pkl.core.util.paguro.tuple.Tuple2;

/**
 * Rich Hickey's immutable rendition of Phil Bagwell's Hash Array Mapped Trie.
 *
 * <p>Uses path copying for persistence, HashCollision leaves vs. extended hashing, Node
 * polymorphism vs. conditionals, No sub-tree pools or root-resizing. Any errors are my own (said
 * Rich, but now says Glen 2015-06-06).
 *
 * <p>This file is a derivative work based on a Clojure collection licensed under the Eclipse Public
 * License 1.0 Copyright Rich Hickey. Errors are Glen Peterson's.
 */
public class PersistentHashMap<K, V> extends AbstractUnmodMap<K, V>
    implements ImMap<K, V>, Serializable {

  //    static private <K, V, R> R doKvreduce(Object[] array, Fn3<R,K,V,R> f, R init) {
  //        for (int i = 0; i < array.length; i += 2) {
  //            if (array[i] != null) {
  //                init = f.apply(init, k(array, i), v(array, i + 1));
  //            } else {
  //                INode<K,V> node = iNode(array, i + 1);
  //                if (node != null)
  //                    init = node.kvreduce(f, init);
  //            }
  //            if (isReduced(init)) {
  //                return init;
  //            }
  //        }
  //        return init;
  //    }

  private static class Iter<K, V, R> implements UnmodIterator<R> {
    //        , Serializable {
    //        // For serializable.  Make sure to change whenever internal data format changes.
    //        private static final long serialVersionUID = 20160903192900L;

    private boolean seen = false;
    private final UnmodIterator<R> rootIter;
    private final Fn2<K, V, R> aFn;
    private final V nullValue;

    private Iter(UnmodIterator<R> ri, Fn2<K, V, R> aFn, V nv) {
      rootIter = ri;
      this.aFn = aFn;
      nullValue = nv;
    }

    @Override
    public boolean hasNext() {
      //noinspection SimplifiableIfStatement
      if (!seen) {
        return true;
      } else {
        return rootIter.hasNext();
      }
    }

    @Override
    public R next() {
      if (!seen) {
        seen = true;
        return aFn.apply(null, nullValue);
      } else {
        return rootIter.next();
      }
    }
  }

  //    private static final class Reduced {
  //        Object val;
  //        public Reduced(Object val) { this.val = val; }
  ////        public Object deref() { return val; }
  //    }
  //
  //    private static boolean isReduced(Object r){
  //        return (r instanceof Reduced);
  //    }

  private static int mask(int hash, int shift) {
    // return ((hash << shift) >>> 27);// & 0x01f;
    return (hash >>> shift) & 0x01f;
  }

  // A method call is slow, but it keeps the cast localized.
  @SuppressWarnings("unchecked")
  private static <K> K k(Object[] array, int i) {
    return (K) array[i];
  }

  // A method call is slow, but it keeps the cast localized.
  @SuppressWarnings("unchecked")
  private static <V> V v(Object[] array, int i) {
    return (V) array[i];
  }

  // A method call is slow, but it keeps the cast localized.
  @SuppressWarnings("unchecked")
  private static <K, V> INode<K, V> iNode(Object[] array, int i) {
    return (INode<K, V>) array[i];
  }

  //    interface IFn {}

  public static final PersistentHashMap<Object, Object> EMPTY =
      new PersistentHashMap<>(null, 0, null, false, null);

  @SuppressWarnings("unchecked")
  public static <K, V> PersistentHashMap<K, V> empty() {
    return (PersistentHashMap<K, V>) EMPTY;
  }

  /** Works around some type inference limitations of Java 8. */
  public static <K, V> MutHashMap<K, V> emptyMutable() {
    return PersistentHashMap.<K, V>empty().mutable();
  }

  public static <K, V> PersistentHashMap<K, V> empty(@Nullable Equator<K> e) {
    return new PersistentHashMap<>(e, 0, null, false, null);
  }

  /** Works around some type inference limitations of Java 8. */
  public static <K, V> MutHashMap<K, V> emptyMutable(Equator<K> e) {
    return PersistentHashMap.<K, V>empty(e).mutable();
  }

  //    final private static Object NOT_FOUND = new Object();

  //    /** Returns a new PersistentHashMap of the given keys and their paired values. */
  //    public static <K,V> PersistentHashMap<K,V> of() {
  //        return empty();
  //    }

  /**
   * Returns a new PersistentHashMap of the given keys and their paired values, skipping any null
   * Entries.
   */
  @SuppressWarnings("WeakerAccess")
  public static <K, V> PersistentHashMap<K, V> ofEq(
      Equator<K> eq, @Nullable Iterable<Map.Entry<K, V>> es) {
    if (es == null) {
      return empty(eq);
    }
    MutHashMap<K, V> map = emptyMutable(eq);
    for (Map.Entry<K, V> entry : es) {
      if (entry != null) {
        map.assoc(entry.getKey(), entry.getValue());
      }
    }
    return map.immutable();
  }

  /**
   * Returns a new PersistentHashMap of the given keys and their paired values. There is also a
   * varargs version of this method: {@link
   * org.pkl.core.util.paguro.StaticImports#map(Map.Entry...)}. Use the {@link
   * org.pkl.core.util.paguro.StaticImports#tup(Object, Object)} method to define key/value pairs
   * briefly and easily.
   *
   * @param kvPairs Key/value pairs (to go into the map). In the case of a duplicate key, later
   *     values in the input list overwrite the earlier ones. The resulting map can contain zero or
   *     one null key and any number of null values. Null k/v pairs will be silently ignored.
   * @return a new PersistentHashMap of the given key/value pairs
   */
  public static <K, V> PersistentHashMap<K, V> of(@Nullable Iterable<Map.Entry<K, V>> kvPairs) {
    if (kvPairs == null) {
      return empty();
    }
    PersistentHashMap<K, V> m = empty();
    MutHashMap<K, V> map = m.mutable();
    for (Map.Entry<K, V> entry : kvPairs) {
      if (entry != null) {
        map.assoc(entry.getKey(), entry.getValue());
      }
    }
    return map.immutable();
  }

  // ==================================== Instance Variables ====================================
  private final Equator<K> equator;
  private final int size;
  private final transient @Nullable INode<K, V> root;
  private final boolean hasNull;
  private final V nullValue;

  // ======================================== Constructor ========================================
  private PersistentHashMap(
      @Nullable Equator<K> eq, int sz, @Nullable INode<K, V> root, boolean hasNull, V nullValue) {
    this.equator = (eq == null) ? Equator.defaultEquator() : eq;
    this.size = sz;
    this.root = root;
    this.hasNull = hasNull;
    this.nullValue = nullValue;
  }

  // ======================================= Serialization =======================================
  // This class has a custom serialized form designed to be as small as possible.  It does not
  // have the same internal structure as an instance of this class.

  // For serializable.  Make sure to change whenever internal data format changes.
  private static final long serialVersionUID = 20160903192900L;

  // Check out Josh Bloch Item 78, p. 312 for an explanation of what's going on here.
  private static class SerializationProxy<K, V> implements Serializable {
    private final Equator<K> equator;
    private final int size;
    private transient ImMap<K, V> theMap;

    SerializationProxy(PersistentHashMap<K, V> phm) {
      equator = phm.equator;
      size = phm.size;
      theMap = phm;
    }

    // Taken from Josh Bloch Item 75, p. 298
    private void writeObject(ObjectOutputStream s) throws IOException {
      s.defaultWriteObject();

      // Write out all elements in the proper order
      for (UnEntry<K, V> entry : theMap) {
        s.writeObject(entry.getKey());
        s.writeObject(entry.getValue());
      }
    }

    private static final long serialVersionUID = 20160827174100L;

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
      s.defaultReadObject();
      MutMap tempMap = new PersistentHashMap<K, V>(equator, 0, null, false, null).mutable();
      for (int i = 0; i < size; i++) {
        tempMap.assoc(s.readObject(), s.readObject());
      }
      theMap = tempMap.immutable();
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
  //    /** Not sure I like this - could disappear. */
  //    boolean hasNull() { return hasNull; }

  /** {@inheritDoc} */
  @Override
  public Equator<K> equator() {
    return equator;
  }

  @Override
  public PersistentHashMap<K, V> assoc(K key, V val) {
    if (key == null) {
      if (hasNull && (val == nullValue)) {
        return this;
      }
      return new PersistentHashMap<>(equator, hasNull ? size : size + 1, root, true, val);
    }
    Box<Box> addedLeaf = new Box<>(null);
    INode<K, V> newroot = (root == null ? BitmapIndexedNode.empty(equator) : root);
    newroot = newroot.assoc(0, equator.hash(key), key, val, addedLeaf);
    if (newroot == root) {
      return this;
    }
    return new PersistentHashMap<>(
        equator, addedLeaf.val == null ? size : size + 1, newroot, hasNull, nullValue);
  }

  @Override
  public MutHashMap<K, V> mutable() {
    return new MutHashMap<>(this);
  }

  @Override
  public Option<UnmodMap.UnEntry<K, V>> entry(K key) {
    if (key == null) {
      return hasNull ? Option.some(Tuple2.of(null, nullValue)) : Option.none();
    }
    if (root == null) {
      return Option.none();
    }
    UnEntry<K, V> entry = root.find(0, equator.hash(key), key);
    return Option.someOrNullNoneOf(entry);
  }

  // The iterator methods are identical to the Mutable version of this class below.

  @Override
  public UnmodIterator<UnEntry<K, V>> iterator() {
    return iterator(Tuple2::of);
  }

  @SuppressWarnings("unchecked")
  @Override
  public UnmodIterator<K> keyIterator() {
    return iterator(Fn2.Singletons.FIRST);
  }

  @SuppressWarnings("unchecked")
  @Override
  public UnmodIterator<V> valIterator() {
    return iterator(Fn2.Singletons.SECOND);
  }

  private <R> UnmodIterator<R> iterator(Fn2<K, V, R> aFn) {
    final UnmodIterator<R> rootIter = (root == null) ? emptyUnmodIterator() : root.iterator(aFn);
    return (hasNull) ? new Iter<>(rootIter, aFn, nullValue) : rootIter;
  }

  //    public <R> R kvreduce(Fn3<R,K,V,R> f, R init) {
  //        init = hasNull ? f.apply(init, null, nullValue) : init;
  //        if(RT.isReduced(init))
  //            return ((IDeref)init).deref();
  //        if(root != null){
  //            init = root.kvreduce(f,init);
  //            if(RT.isReduced(init))
  //                return ((IDeref)init).deref();
  //            else
  //                return init;
  //        }
  //        return init;
  //    }

  //    public <R> R fold(long n, final Fn2<R,R,R> combinef, final Fn3<R,K,V,R> reducef,
  //                      Fn1<Fn0<R>,R> fjinvoke, final Fn1<Fn0,R> fjtask,
  //                      final Fn1<R,Object> fjfork, final Fn1<Object,R> fjjoin){
  //        //we are ignoring n for now
  //        Fn0<R> top = () -> {
  //            R ret = combinef.apply(null,null);
  //            if(root != null)
  //                ret = combinef.apply(ret, root.fold(combinef, reducef, fjtask, fjfork, fjjoin));
  //            return hasNull ? combinef.apply(ret, reducef.apply(combinef.apply(null,null), null,
  //                                            nullValue))
  //                           : ret;
  //        };
  //        return fjinvoke.apply(top);
  //    }

  //    @SuppressWarnings("unchecked")
  //    @Override public Sequence<UnEntry<K,V>> seq() {
  ////        System.out.println("root: " + root);
  //        Sequence<UnEntry<K,V>> s = root != null ? root.nodeSeq() : Sequence.emptySequence();
  //        return hasNull ? s.prepend((UnEntry<K,V>) Tuple2.of((K) null, nullValue)) : s;
  //    }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return size;
  }

  @Override
  public PersistentHashMap<K, V> without(K key) {
    if (key == null)
      return hasNull ? new PersistentHashMap<>(equator, size - 1, root, false, null) : this;
    if (root == null) return this;
    INode<K, V> newroot = root.without(0, equator.hash(key), key);
    if (newroot == root) return this;
    return new PersistentHashMap<>(equator, size - 1, newroot, hasNull, nullValue);
  }

  public static final class MutHashMap<K, V> extends AbstractUnmodMap<K, V>
      implements MutMap<K, V> {

    private final AtomicReference<Thread> edit;
    private final Equator<K> equator;
    private INode<K, V> root;
    private int count;
    private boolean hasNull;
    private V nullValue;
    // This is a boolean reference, with value either being null, or set to point to the
    // box itself.  It might be clearer to replace this with an AtomicBoolean or similar.
    // I think the reason this can be a field instead of a local variable is that the
    // MutHashMap is not intended to be thread safe, thus no-one will call one method
    // while another thread calls another method.  Presumably having this here saves the cost
    // of allocating a local variable.  Setting it to null or itself saves storing anything
    // in memory.  Why did Rich go to all of this trouble?  Does it make a difference, or
    // could he have just passed a local AtomicBoolean or made remove() return a pair (Boolean
    // and INode) or even OneOf(left INode, right INode)?
    private final Box<Box> leafFlag = new Box<>(null);

    private MutHashMap(PersistentHashMap<K, V> m) {
      this(
          m.equator(),
          new AtomicReference<>(Thread.currentThread()),
          m.root,
          m.size,
          m.hasNull,
          m.nullValue);
    }

    private MutHashMap(
        Equator<K> e,
        AtomicReference<Thread> edit,
        INode<K, V> root,
        int count,
        boolean hasNull,
        V nullValue) {
      this.equator = (e == null) ? Equator.defaultEquator() : e;
      this.edit = edit;
      this.root = root;
      this.count = count;
      this.hasNull = hasNull;
      this.nullValue = nullValue;
    }

    @Override
    public Equator<K> equator() {
      return equator;
    }

    @Override
    public MutHashMap<K, V> assoc(K key, V val) {
      ensureEditable();
      if (key == null) {
        if (this.nullValue != val) this.nullValue = val;
        if (!hasNull) {
          this.count++;
          this.hasNull = true;
        }
        return this;
      }
      //        Box leafFlag = new Box(null);
      leafFlag.val = null;
      INode<K, V> n = (root == null ? BitmapIndexedNode.empty(equator) : root);
      n = n.assoc(edit, 0, equator.hash(key), key, val, leafFlag);
      if (n != this.root) this.root = n;
      if (leafFlag.val != null) this.count++;
      return this;
    }

    @Override
    public Option<UnEntry<K, V>> entry(K key) {
      ensureEditable();
      if (key == null) {
        return hasNull ? Option.some(Tuple2.of(null, nullValue)) : Option.none();
      }
      if (root == null) {
        return Option.none();
      }
      UnEntry<K, V> entry = root.find(0, equator.hash(key), key);
      return Option.someOrNullNoneOf(entry);
    }

    //        @Override
    //        @SuppressWarnings("unchecked")
    //        public Sequence<UnEntry<K,V>> seq() {
    //            Sequence<UnEntry<K,V>> s = root != null ? root.nodeSeq() :
    // Sequence.emptySequence();
    //            return hasNull ? s.prepend((UnEntry<K,V>) Tuple2.of((K) null, nullValue)) : s;
    //        }

    // The iterator methods are a duplicate of the same methods in the Persistent version of this
    // class above.

    @Override
    public UnmodIterator<UnEntry<K, V>> iterator() {
      return iterator(Tuple2::of);
    }

    @SuppressWarnings("unchecked")
    @Override
    public UnmodIterator<K> keyIterator() {
      return iterator(Fn2.Singletons.FIRST);
    }

    @SuppressWarnings("unchecked")
    @Override
    public UnmodIterator<V> valIterator() {
      return iterator(Fn2.Singletons.SECOND);
    }

    private <R> UnmodIterator<R> iterator(Fn2<K, V, R> aFn) {
      final UnmodIterator<R> rootIter = (root == null) ? emptyUnmodIterator() : root.iterator(aFn);
      return (hasNull) ? new Iter<>(rootIter, aFn, nullValue) : rootIter;
    }

    @Override
    public MutHashMap<K, V> without(K key) {
      ensureEditable();
      if (key == null) {
        if (!hasNull) return this;
        hasNull = false;
        nullValue = null;
        this.count--;
        return this;
      }
      if (root == null) return this;
      // Box leafFlag = new Box(null);
      leafFlag.val = null;
      INode<K, V> n = root.without(edit, 0, equator.hash(key), key, leafFlag);
      if (n != root) this.root = n;
      if (leafFlag.val != null) this.count--;
      return this;
    }

    @Override
    public PersistentHashMap<K, V> immutable() {
      ensureEditable();
      edit.set(null);
      return new PersistentHashMap<>(equator, count, root, hasNull, nullValue);
    }

    @Override
    public int size() {
      ensureEditable();
      return count;
    }

    private void ensureEditable() {
      if (edit.get() == null) throw new IllegalStateException("Mutable used after immutable! call");
    }
  }

  private interface INode<K, V> {
    INode<K, V> assoc(int shift, int hash, K key, V val, Box<Box> addedLeaf);

    INode<K, V> without(int shift, int hash, K key);

    UnEntry<K, V> find(int shift, int hash, K key);

    //        V findVal(int shift, int hash, K key, V notFound);

    //        Sequence<UnmodMap.UnEntry<K,V>> nodeSeq();

    INode<K, V> assoc(
        AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box<Box> addedLeaf);

    INode<K, V> without(
        AtomicReference<Thread> edit, int shift, int hash, K key, Box<Box> removedLeaf);

    //        <R> R kvreduce(Fn3<R,K,V,R> f, R init);

    //        <R> R fold(Fn2<R,R,R> combinef, Fn3<R,K,V,R> reducef,
    //                   final Fn1<Fn0,R> fjtask,
    //                   final Fn1<R,Object> fjfork, final Fn1<Object,R> fjjoin);

    <R> UnmodIterator<R> iterator(Fn2<K, V, R> aFn);
  }

  private static final class ArrayNode<K, V> implements INode<K, V>, UnmodIterable<UnEntry<K, V>> {
    private final Equator<K> equator;
    int count;
    final INode<K, V>[] array;
    final AtomicReference<Thread> edit;

    ArrayNode(Equator<K> eq, AtomicReference<Thread> edit, int count, INode<K, V>[] array) {
      this.equator = eq;
      this.array = array;
      this.edit = edit;
      this.count = count;
    }

    @Override
    public INode<K, V> assoc(int shift, int hash, K key, V val, Box<Box> addedLeaf) {
      int idx = mask(hash, shift);
      INode<K, V> node = array[idx];
      if (node == null) {
        BitmapIndexedNode<K, V> e = BitmapIndexedNode.empty(equator);
        INode<K, V> n = e.assoc(shift + 5, hash, key, val, addedLeaf);
        return new ArrayNode<>(equator, null, count + 1, cloneAndSet(array, idx, n));
      }
      INode<K, V> n = node.assoc(shift + 5, hash, key, val, addedLeaf);
      if (n == node) {
        return this;
      }
      return new ArrayNode<>(equator, null, count, cloneAndSet(array, idx, n));
    }

    @Override
    public INode<K, V> without(int shift, int hash, K key) {
      int idx = mask(hash, shift);
      INode<K, V> node = array[idx];
      if (node == null) return this;
      INode<K, V> n = node.without(shift + 5, hash, key);
      if (n == node) return this;
      if (n == null) {
        if (count <= 8) {
          // shrink
          return pack(null, idx);
        }
        return new ArrayNode<>(equator, null, count - 1, cloneAndSet(array, idx, null));
      } else return new ArrayNode<>(equator, null, count, cloneAndSet(array, idx, n));
    }

    @Override
    public UnmodMap.UnEntry<K, V> find(int shift, int hash, K key) {
      int idx = mask(hash, shift);
      INode<K, V> node = array[idx];
      if (node == null) return null;
      return node.find(shift + 5, hash, key);
    }

    //        @Override public V findVal(int shift, int hash, K key, V notFound){
    //            int idx = mask(hash, shift);
    //            INode<K,V> node = array[idx];
    //            if(node == null)
    //                return notFound;
    //            return node.findVal(shift + 5, hash, key, notFound);
    //        }

    //        @Override public Sequence<UnmodMap.UnEntry<K,V>> nodeSeq(){ return Seq.create(array);
    // }

    @Override
    public UnmodIterator<UnEntry<K, V>> iterator() {
      return iterator(Tuple2::of);
    }

    @Override
    public <R> UnmodIterator<R> iterator(Fn2<K, V, R> aFn) {
      return new Iter<>(array, aFn);
    }

    //        @Override public <R> R kvreduce(Fn3<R,K,V,R> f, R init){
    //            for(INode<K,V> node : array){
    //                if(node != null){
    //                    init = node.kvreduce(f,init);
    //                    if(isReduced(init))
    //                        return init;
    //                }
    //            }
    //            return init;
    //        }
    //        @Override public <R> R fold(Fn2<R,R,R> combinef, Fn3<R,K,V,R> reducef,
    //                                    final Fn1<Fn0,R> fjtask,
    //                                    final Fn1<R,Object> fjfork,
    //                                    final Fn1<Object,R> fjjoin){
    //            List<Callable<R>> tasks = new ArrayList<>();
    //            for(final INode<K,V> node : array){
    //                if(node != null){
    //                    tasks.add(() -> node.fold(combinef, reducef, fjtask, fjfork, fjjoin));
    //                }
    //            }
    //
    //            return foldTasks(tasks,combinef,fjtask,fjfork,fjjoin);
    //        }

    //        static private <R> R foldTasks(List<Callable<R>> tasks, final Fn2<R,R,R> combinef,
    //                                       final Fn1<Fn0,R> fjtask,
    //                                       final Fn1<R,Object> fjfork,
    //                                       final Fn1<Object,R> fjjoin) {
    //
    //            if(tasks.isEmpty())
    //                return combinef.apply(null,null);
    //
    //            if(tasks.size() == 1){
    //                try {
    //                    return tasks.get(0).call();
    //                } catch (RuntimeException re) {
    //                    throw re;
    //                } catch (Exception e) {
    //                    throw new RuntimeException(e);
    //                }
    //            }
    //
    //            List<Callable<R>> t1 = tasks.subList(0,tasks.size()/2);
    //            final List<Callable<R>> t2 = tasks.subList(tasks.size()/2, tasks.size());
    //
    //            Object forked = fjfork.apply(fjtask.apply(() -> foldTasks(t2, combinef, fjtask,
    //                                         fjfork, fjjoin)));
    //
    //            return combinef.apply(foldTasks(t1, combinef, fjtask, fjfork, fjjoin),
    //                                  fjjoin.apply(forked));
    //        }

    private ArrayNode<K, V> ensureEditable(AtomicReference<Thread> edit) {
      if (this.edit == edit) return this;
      return new ArrayNode<>(equator, edit, count, this.array.clone());
    }

    private ArrayNode<K, V> editAndSet(AtomicReference<Thread> edit, int i, INode<K, V> n) {
      ArrayNode<K, V> editable = ensureEditable(edit);
      editable.array[i] = n;
      return editable;
    }

    private INode<K, V> pack(AtomicReference<Thread> edit, int idx) {
      Object[] newArray = new Object[2 * (count - 1)];
      int j = 1;
      int bitmap = 0;
      for (int i = 0; i < idx; i++)
        if (array[i] != null) {
          newArray[j] = array[i];
          bitmap |= 1 << i;
          j += 2;
        }
      for (int i = idx + 1; i < array.length; i++)
        if (array[i] != null) {
          newArray[j] = array[i];
          bitmap |= 1 << i;
          j += 2;
        }
      return new BitmapIndexedNode<>(equator, edit, bitmap, newArray);
    }

    @Override
    public INode<K, V> assoc(
        AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box<Box> addedLeaf) {
      int idx = mask(hash, shift);
      INode<K, V> node = array[idx];
      if (node == null) {
        BitmapIndexedNode<K, V> en = BitmapIndexedNode.empty(equator);
        ArrayNode<K, V> editable =
            editAndSet(edit, idx, en.assoc(edit, shift + 5, hash, key, val, addedLeaf));
        editable.count++;
        return editable;
      }
      INode<K, V> n = node.assoc(edit, shift + 5, hash, key, val, addedLeaf);
      if (n == node) return this;
      return editAndSet(edit, idx, n);
    }

    @Override
    public INode<K, V> without(
        AtomicReference<Thread> edit, int shift, int hash, K key, Box<Box> removedLeaf) {
      int idx = mask(hash, shift);
      INode<K, V> node = array[idx];
      if (node == null) {
        return this;
      }
      INode<K, V> n = node.without(edit, shift + 5, hash, key, removedLeaf);
      if (n == node) {
        return this;
      }
      if (n == null) {
        if (count <= 8) // shrink
        return pack(edit, idx);
        ArrayNode<K, V> editable = editAndSet(edit, idx, null);
        editable.count--;
        return editable;
      }
      return editAndSet(edit, idx, n);
    }

    @Override
    public String toString() {
      return UnmodIterable.toString("ArrayNode", this);
    }

    private static class Iter<K, V, R> implements UnmodIterator<R> {
      //            , Serializable {
      //            // For serializable.  Make sure to change whenever internal data format changes.
      //            private static final long serialVersionUID = 20160903192900L;

      private final INode<K, V>[] array;
      private final Fn2<K, V, R> aFn;
      private int i = 0;
      private UnmodIterator<R> nestedIter;

      private Iter(INode<K, V>[] array, Fn2<K, V, R> aFn) {
        this.array = array;
        this.aFn = aFn;
      }

      @Override
      public boolean hasNext() {
        while (true) {
          if (nestedIter != null) {
            if (nestedIter.hasNext()) {
              return true;
            } else {
              nestedIter = null;
            }
          }
          if (i < array.length) {
            INode<K, V> node = array[i++];
            if (node != null) {
              nestedIter = node.iterator(aFn);
            }
          } else {
            return false;
          }
        }
      }

      @Override
      public R next() {
        if (hasNext()) {
          return nestedIter.next();
        } else {
          throw new NoSuchElementException();
        }
      }
    }
  } // end class ArrayNode<K,V>

  @SuppressWarnings("unchecked")
  private static final class BitmapIndexedNode<K, V> implements INode<K, V> {
    //        static final BitmapIndexedNode EMPTY = new BitmapIndexedNode(null, 0, new Object[0]);

    static <K, V> BitmapIndexedNode<K, V> empty(Equator<K> e) {
      return new BitmapIndexedNode(e, null, 0, new Object[0]);
    }

    private final Equator<K> equator;
    int bitmap;
    // even numbered cells are key or null, odd are val or node.
    Object[] array;
    final AtomicReference<Thread> edit;

    @Override
    public String toString() {
      return "BitmapIndexedNode(" + bitmap + "," + Arrays.toString(array) + "," + edit + ")";
    }

    int index(int bit) {
      return Integer.bitCount(bitmap & (bit - 1));
    }

    BitmapIndexedNode(
        Equator<K> equator, AtomicReference<Thread> edit, int bitmap, Object[] array) {
      this.equator = equator;
      this.bitmap = bitmap;
      this.array = array;
      this.edit = edit;
    }

    @Override
    public INode<K, V> assoc(int shift, int hash, K key, V val, Box<Box> addedLeaf) {
      int bit = bitpos(hash, shift);
      int idx = index(bit);
      if ((bitmap & bit) != 0) {
        K keyOrNull = k(array, 2 * idx);
        Object valOrNode = array[2 * idx + 1];
        if (keyOrNull == null) {
          INode<K, V> n = ((INode) valOrNode).assoc(shift + 5, hash, key, val, addedLeaf);
          if (n == valOrNode) return this;
          return new BitmapIndexedNode<>(equator, null, bitmap, cloneAndSet(array, 2 * idx + 1, n));
        }
        if (equator.eq(key, keyOrNull)) {
          if (val == valOrNode) return this;
          return new BitmapIndexedNode<>(
              equator, null, bitmap, cloneAndSet(array, 2 * idx + 1, val));
        }
        addedLeaf.val = addedLeaf;
        return new BitmapIndexedNode<>(
            equator,
            null,
            bitmap,
            cloneAndSet(
                array,
                2 * idx,
                2 * idx + 1,
                createNode(equator, shift + 5, keyOrNull, valOrNode, hash, key, val)));
      } else {
        int n = Integer.bitCount(bitmap);
        if (n >= 16) {
          INode[] nodes = new INode[32];
          int jdx = mask(hash, shift);
          nodes[jdx] = empty(equator).assoc(shift + 5, hash, key, val, addedLeaf);
          int j = 0;
          for (int i = 0; i < 32; i++)
            if (((bitmap >>> i) & 1) != 0) {
              if (array[j] == null) nodes[i] = (INode) array[j + 1];
              else
                nodes[i] =
                    empty(equator)
                        .assoc(
                            shift + 5,
                            equator.hash(k(array, j)),
                            k(array, j),
                            array[j + 1],
                            addedLeaf);
              j += 2;
            }
          return new ArrayNode(equator, null, n + 1, nodes);
        } else {
          Object[] newArray = new Object[2 * (n + 1)];
          System.arraycopy(array, 0, newArray, 0, 2 * idx);
          newArray[2 * idx] = key;
          addedLeaf.val = addedLeaf;
          newArray[2 * idx + 1] = val;
          System.arraycopy(array, 2 * idx, newArray, 2 * (idx + 1), 2 * (n - idx));
          return new BitmapIndexedNode<>(equator, null, bitmap | bit, newArray);
        }
      }
    }

    @Override
    public INode<K, V> without(int shift, int hash, K key) {
      int bit = bitpos(hash, shift);
      if ((bitmap & bit) == 0) return this;
      int idx = index(bit);
      K keyOrNull = (K) array[2 * idx];
      Object valOrNode = array[2 * idx + 1];
      if (keyOrNull == null) {
        INode<K, V> n = ((INode) valOrNode).without(shift + 5, hash, key);
        if (n == valOrNode) return this;
        if (n != null)
          return new BitmapIndexedNode<>(equator, null, bitmap, cloneAndSet(array, 2 * idx + 1, n));
        if (bitmap == bit) return null;
        return new BitmapIndexedNode<>(equator, null, bitmap ^ bit, removePair(array, idx));
      }
      if (equator.eq(key, keyOrNull))
        // TODO: collapse
        return new BitmapIndexedNode<>(equator, null, bitmap ^ bit, removePair(array, idx));
      return this;
    }

    @Override
    public UnEntry<K, V> find(int shift, int hash, K key) {
      int bit = bitpos(hash, shift);
      if ((bitmap & bit) == 0) return null;
      int idx = index(bit);
      K keyOrNull = k(array, 2 * idx);
      Object valOrNode = array[2 * idx + 1];
      if (keyOrNull == null) return ((INode) valOrNode).find(shift + 5, hash, key);
      if (equator.eq(key, keyOrNull)) return Tuple2.of(keyOrNull, (V) valOrNode);
      return null;
    }

    //        @Override public V findVal(int shift, int hash, K key, V notFound) {
    //            int bit = bitpos(hash, shift);
    //            if ((bitmap & bit) == 0) {
    //                return notFound;
    //            }
    //            int idx = index(bit);
    //            K keyOrNull = k(array, 2 * idx);
    //            if (keyOrNull == null) {
    //                INode<K,V> n = iNode(array, 2 * idx + 1);
    //                return n.findVal(shift + 5, hash, key, notFound);
    //            }
    //            if (equator.eq(key, keyOrNull)) {
    //                return v(array, 2 * idx + 1);
    //            }
    //            return notFound;
    //        }

    //        @Override public Sequence<UnEntry<K,V>> nodeSeq() { return NodeSeq.create(array); }

    @Override
    public <R> UnmodIterator<R> iterator(Fn2<K, V, R> aFn) {
      return new NodeIter<>(array, aFn);
    }

    //        @Override public <R> R kvreduce(Fn3<R,K,V,R> f, R init){
    //            return doKvreduce(array, f, init);
    //        }

    //        @Override public <R> R fold(Fn2<R,R,R> combinef, Fn3<R,K,V,R> reducef,
    //                                    final Fn1<Fn0,R> fjtask,
    //                                    final Fn1<R,Object> fjfork,
    //                                    final Fn1<Object,R> fjjoin){
    //            return doKvreduce(array, reducef, combinef.apply(null, null));
    //        }

    private BitmapIndexedNode<K, V> ensureEditable(AtomicReference<Thread> edit) {
      if (this.edit == edit) return this;
      int n = Integer.bitCount(bitmap);
      Object[] newArray = new Object[n >= 0 ? 2 * (n + 1) : 4]; // make room for next assoc
      System.arraycopy(array, 0, newArray, 0, 2 * n);
      return new BitmapIndexedNode<>(equator, edit, bitmap, newArray);
    }

    private BitmapIndexedNode<K, V> editAndSet(AtomicReference<Thread> edit, int i, Object a) {
      BitmapIndexedNode editable = ensureEditable(edit);
      editable.array[i] = a;
      return editable;
    }

    private BitmapIndexedNode<K, V> editAndSet(
        AtomicReference<Thread> edit, int i, int j, Object b) {
      BitmapIndexedNode editable = ensureEditable(edit);
      editable.array[i] = null;
      editable.array[j] = b;
      return editable;
    }

    private BitmapIndexedNode<K, V> editAndRemovePair(
        AtomicReference<Thread> edit, int bit, int i) {
      if (bitmap == bit) return null;
      BitmapIndexedNode<K, V> editable = ensureEditable(edit);
      editable.bitmap ^= bit;
      System.arraycopy(
          editable.array, 2 * (i + 1), editable.array, 2 * i, editable.array.length - 2 * (i + 1));
      editable.array[editable.array.length - 2] = null;
      editable.array[editable.array.length - 1] = null;
      return editable;
    }

    @Override
    public INode<K, V> assoc(
        AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box<Box> addedLeaf) {
      int bit = bitpos(hash, shift);
      int idx = index(bit);
      if ((bitmap & bit) != 0) {
        K keyOrNull = k(array, 2 * idx);
        Object valOrNode = array[2 * idx + 1];
        if (keyOrNull == null) {
          INode<K, V> n =
              ((INode<K, V>) valOrNode).assoc(edit, shift + 5, hash, key, val, addedLeaf);
          if (n == valOrNode) return this;
          return editAndSet(edit, 2 * idx + 1, n);
        }
        if (equator.eq(key, keyOrNull)) {
          if (val == valOrNode) return this;
          return editAndSet(edit, 2 * idx + 1, val);
        }
        addedLeaf.val = addedLeaf;
        return editAndSet(
            edit,
            2 * idx,
            2 * idx + 1,
            createNode(equator, edit, shift + 5, keyOrNull, valOrNode, hash, key, val));
      } else {
        int n = Integer.bitCount(bitmap);
        if (n * 2 < array.length) {
          addedLeaf.val = addedLeaf;
          BitmapIndexedNode<K, V> editable = ensureEditable(edit);
          System.arraycopy(editable.array, 2 * idx, editable.array, 2 * (idx + 1), 2 * (n - idx));
          editable.array[2 * idx] = key;
          editable.array[2 * idx + 1] = val;
          editable.bitmap |= bit;
          return editable;
        }
        if (n >= 16) {
          INode[] nodes = new INode[32];
          int jdx = mask(hash, shift);
          nodes[jdx] = empty(equator).assoc(edit, shift + 5, hash, key, val, addedLeaf);
          int j = 0;
          for (int i = 0; i < 32; i++)
            if (((bitmap >>> i) & 1) != 0) {
              if (array[j] == null) nodes[i] = (INode) array[j + 1];
              else
                nodes[i] =
                    empty(equator)
                        .assoc(
                            edit,
                            shift + 5,
                            equator.hash(k(array, j)),
                            k(array, j),
                            array[j + 1],
                            addedLeaf);
              j += 2;
            }
          return new ArrayNode(equator, edit, n + 1, nodes);
        } else {
          Object[] newArray = new Object[2 * (n + 4)];
          System.arraycopy(array, 0, newArray, 0, 2 * idx);
          newArray[2 * idx] = key;
          addedLeaf.val = addedLeaf;
          newArray[2 * idx + 1] = val;
          System.arraycopy(array, 2 * idx, newArray, 2 * (idx + 1), 2 * (n - idx));
          BitmapIndexedNode<K, V> editable = ensureEditable(edit);
          editable.array = newArray;
          editable.bitmap |= bit;
          return editable;
        }
      }
    }

    @Override
    public INode<K, V> without(
        AtomicReference<Thread> edit, int shift, int hash, K key, Box<Box> removedLeaf) {
      int bit = bitpos(hash, shift);
      if ((bitmap & bit) == 0) return this;
      int idx = index(bit);
      K keyOrNull = k(array, 2 * idx);
      Object valOrNode = array[2 * idx + 1];
      if (keyOrNull == null) {
        INode<K, V> n = ((INode) valOrNode).without(edit, shift + 5, hash, key, removedLeaf);
        if (n == valOrNode) return this;
        if (n != null) return editAndSet(edit, 2 * idx + 1, n);
        if (bitmap == bit) return null;
        return editAndRemovePair(edit, bit, idx);
      }
      if (equator.eq(key, keyOrNull)) {
        removedLeaf.val = removedLeaf;
        // TODO: collapse
        return editAndRemovePair(edit, bit, idx);
      }
      return this;
    }
  }

  private static final class HashCollisionNode<K, V> implements INode<K, V> {
    private final Equator<K> equator;
    final int hash;
    int count;
    Object[] array;
    final AtomicReference<Thread> edit;

    HashCollisionNode(
        Equator<K> eq, AtomicReference<Thread> edit, int hash, int count, Object... array) {
      this.equator = eq;
      this.edit = edit;
      this.hash = hash;
      this.count = count;
      this.array = array;
    }

    @Override
    public INode<K, V> assoc(int shift, int hash, K key, V val, Box<Box> addedLeaf) {
      if (hash == this.hash) {
        int idx = findIndex(key);
        if (idx != -1) {
          if (array[idx + 1] == val) return this;
          return new HashCollisionNode<>(
              equator, null, hash, count, cloneAndSet(array, idx + 1, val));
        }
        Object[] newArray = new Object[2 * (count + 1)];
        System.arraycopy(array, 0, newArray, 0, 2 * count);
        newArray[2 * count] = key;
        newArray[2 * count + 1] = val;
        addedLeaf.val = addedLeaf;
        return new HashCollisionNode<>(equator, edit, hash, count + 1, newArray);
      }
      // nest it in a bitmap node
      return new BitmapIndexedNode<K, V>(
              equator, null, bitpos(this.hash, shift), new Object[] {null, this})
          .assoc(shift, hash, key, val, addedLeaf);
    }

    @Override
    public INode<K, V> without(int shift, int hash, K key) {
      int idx = findIndex(key);
      if (idx == -1) return this;
      if (count == 1) return null;
      return new HashCollisionNode<>(equator, null, hash, count - 1, removePair(array, idx / 2));
    }

    @Override
    public UnmodMap.UnEntry<K, V> find(int shift, int hash, K key) {
      int idx = findIndex(key);
      if (idx < 0) return null;
      if (equator.eq(key, k(array, idx))) return Tuple2.of(k(array, idx), v(array, idx + 1));
      return null;
    }

    //        @Override public V findVal(int shift, int hash, K key, V notFound){
    //            int idx = findIndex(key);
    //            if(idx < 0)
    //                return notFound;
    //            if (equator.eq(key, k(array, idx))) {
    //                return v(array, idx + 1);
    //            }
    //            return notFound;
    //        }

    //        @Override public Sequence<UnEntry<K,V>> nodeSeq() { return NodeSeq.create(array); }

    @Override
    public <R> UnmodIterator<R> iterator(Fn2<K, V, R> aFn) {
      return new NodeIter<>(array, aFn);
    }

    //        @Override public <R> R kvreduce(Fn3<R,K,V,R> f, R init){
    //            return doKvreduce(array, f, init);
    //        }

    //        @Override public <R> R fold(Fn2<R,R,R> combinef, Fn3<R,K,V,R> reducef,
    //                                    final Fn1<Fn0,R> fjtask,
    //                                    final Fn1<R,Object> fjfork,
    //                                    final Fn1<Object,R> fjjoin){
    //            return doKvreduce(array, reducef, combinef.apply(null, null));
    //        }

    private int findIndex(K key) {
      for (int i = 0; i < 2 * count; i += 2) {
        if (equator.eq(key, k(array, i))) {
          return i;
        }
      }
      return -1;
    }

    private HashCollisionNode<K, V> ensureEditable(AtomicReference<Thread> edit) {
      if (this.edit == edit) return this;
      Object[] newArray = new Object[2 * (count + 1)]; // make room for next assoc
      System.arraycopy(array, 0, newArray, 0, 2 * count);
      return new HashCollisionNode<>(equator, edit, hash, count, newArray);
    }

    private HashCollisionNode<K, V> ensureEditable(
        AtomicReference<Thread> edit, int count, Object[] array) {
      if (this.edit == edit) {
        this.array = array;
        this.count = count;
        return this;
      }
      return new HashCollisionNode<>(equator, edit, hash, count, array);
    }

    private HashCollisionNode<K, V> editAndSet(AtomicReference<Thread> edit, int i, Object a) {
      HashCollisionNode<K, V> editable = ensureEditable(edit);
      editable.array[i] = a;
      return editable;
    }

    private HashCollisionNode<K, V> editAndSet(
        AtomicReference<Thread> edit, int i, Object a, int j, Object b) {
      HashCollisionNode<K, V> editable = ensureEditable(edit);
      editable.array[i] = a;
      editable.array[j] = b;
      return editable;
    }

    @Override
    public INode<K, V> assoc(
        AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box<Box> addedLeaf) {
      if (hash == this.hash) {
        int idx = findIndex(key);
        if (idx != -1) {
          if (array[idx + 1] == val) return this;
          return editAndSet(edit, idx + 1, val);
        }
        if (array.length > 2 * count) {
          addedLeaf.val = addedLeaf;
          HashCollisionNode<K, V> editable = editAndSet(edit, 2 * count, key, 2 * count + 1, val);
          editable.count++;
          return editable;
        }
        Object[] newArray = new Object[array.length + 2];
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[array.length] = key;
        newArray[array.length + 1] = val;
        addedLeaf.val = addedLeaf;
        return ensureEditable(edit, count + 1, newArray);
      }
      // nest it in a bitmap node
      return new BitmapIndexedNode<K, V>(
              equator, edit, bitpos(this.hash, shift), new Object[] {null, this, null, null})
          .assoc(edit, shift, hash, key, val, addedLeaf);
    }

    @Override
    public INode<K, V> without(
        AtomicReference<Thread> edit, int shift, int hash, K key, Box<Box> removedLeaf) {
      int idx = findIndex(key);
      if (idx == -1) return this;
      removedLeaf.val = removedLeaf;
      if (count == 1) return null;
      HashCollisionNode<K, V> editable = ensureEditable(edit);
      editable.array[idx] = editable.array[2 * count - 2];
      editable.array[idx + 1] = editable.array[2 * count - 1];
      editable.array[2 * count - 2] = editable.array[2 * count - 1] = null;
      editable.count--;
      return editable;
    }
  }

  /*
  public static void main(String[] args){
      try
          {
          ArrayList words = new ArrayList();
          Scanner s = new Scanner(new File(args[0]));
          s.useDelimiter(Pattern.compile("\\W"));
          while(s.hasNext())
              {
              String word = s.next();
              words.add(word);
              }
          System.out.println("words: " + words.size());
          ImMap map = PersistentHashMap.EMPTY;
          //ImMap map = new PersistentHashMap();
          //Map ht = new Hashtable();
          Map ht = new HashMap();
          Random rand;

          System.out.println("Building map");
          long startTime = System.nanoTime();
          for(Object word5 : words)
              {
              map = map.assoc(word5, word5);
              }
          rand = new Random(42);
          ImMap snapshotMap = map;
          for(int i = 0; i < words.size() / 200; i++)
              {
              map = map.without(words.get(rand.nextInt(words.size() / 2)));
              }
          long estimatedTime = System.nanoTime() - startTime;
          System.out.println("size = " + map.size() + ", time: " + estimatedTime / 1000000);

          System.out.println("Building ht");
          startTime = System.nanoTime();
          for(Object word1 : words)
              {
              ht.put(word1, word1);
              }
          rand = new Random(42);
          for(int i = 0; i < words.size() / 200; i++)
              {
              ht.remove(words.get(rand.nextInt(words.size() / 2)));
              }
          estimatedTime = System.nanoTime() - startTime;
          System.out.println("size = " + ht.size() + ", time: " + estimatedTime / 1000000);

          System.out.println("map lookup");
          startTime = System.nanoTime();
          int c = 0;
          for(Object word2 : words)
              {
              if(!map.contains(word2))
                  ++c;
              }
          estimatedTime = System.nanoTime() - startTime;
          System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
          System.out.println("ht lookup");
          startTime = System.nanoTime();
          c = 0;
          for(Object word3 : words)
              {
              if(!ht.containsKey(word3))
                  ++c;
              }
          estimatedTime = System.nanoTime() - startTime;
          System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
          System.out.println("snapshotMap lookup");
          startTime = System.nanoTime();
          c = 0;
          for(Object word4 : words)
              {
              if(!snapshotMap.contains(word4))
                  ++c;
              }
          estimatedTime = System.nanoTime() - startTime;
          System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
          }
      catch(FileNotFoundException e)
          {
          e.printStackTrace();
          }

  }
  */

  private static <K, V> INode<K, V>[] cloneAndSet(INode<K, V>[] array, int i, INode<K, V> a) {
    INode<K, V>[] clone = array.clone();
    clone[i] = a;
    return clone;
  }

  private static Object[] cloneAndSet(Object[] array, int i, Object a) {
    Object[] clone = array.clone();
    clone[i] = a;
    return clone;
  }

  private static Object[] cloneAndSet(Object[] array, int i, int j, Object b) {
    Object[] clone = array.clone();
    clone[i] = null;
    clone[j] = b;
    return clone;
  }

  private static Object[] removePair(Object[] array, int i) {
    Object[] newArray = new Object[array.length - 2];
    System.arraycopy(array, 0, newArray, 0, 2 * i);
    System.arraycopy(array, 2 * (i + 1), newArray, 2 * i, newArray.length - 2 * i);
    return newArray;
  }

  private static <K, V> INode<K, V> createNode(
      Equator<K> equator, int shift, K key1, V val1, int key2hash, K key2, V val2) {
    int key1hash = equator.hash(key1);
    if (key1hash == key2hash)
      return new HashCollisionNode<>(
          equator, null, key1hash, 2, new Object[] {key1, val1, key2, val2});
    Box<Box> addedLeaf = new Box<>(null);
    AtomicReference<Thread> edit = new AtomicReference<>();
    return BitmapIndexedNode.<K, V>empty(equator)
        .assoc(edit, shift, key1hash, key1, val1, addedLeaf)
        .assoc(edit, shift, key2hash, key2, val2, addedLeaf);
  }

  private static <K, V> INode<K, V> createNode(
      Equator<K> equator,
      AtomicReference<Thread> edit,
      int shift,
      K key1,
      V val1,
      int key2hash,
      K key2,
      V val2) {
    int key1hash = equator.hash(key1);
    if (key1hash == key2hash)
      return new HashCollisionNode<>(
          equator, null, key1hash, 2, new Object[] {key1, val1, key2, val2});
    Box<Box> addedLeaf = new Box<>(null);
    return BitmapIndexedNode.<K, V>empty(equator)
        .assoc(edit, shift, key1hash, key1, val1, addedLeaf)
        .assoc(edit, shift, key2hash, key2, val2, addedLeaf);
  }

  private static int bitpos(int hash, int shift) {
    return 1 << mask(hash, shift);
  }

  private static final class NodeIter<K, V, R> implements UnmodIterator<R> {
    //        , Serializable {
    //        // For serializable.  Make sure to change whenever internal data format changes.
    //        private static final long serialVersionUID = 20160903192900L;
    final Object[] array;
    private final Fn2<K, V, R> aFn;
    private int mutableIndex = 0;
    private R nextEntry = null;
    private boolean absent = true;
    private UnmodIterator<R> nextIter;

    NodeIter(Object[] array, Fn2<K, V, R> aFn) {
      this.array = array;
      this.aFn = aFn;
    }

    private boolean advance() {
      //            while (i < array.length) {
      //                K key = k(array, i);
      //                Object nodeOrVal = array[i+1];
      //                i += 2;
      //                if (key != null) {
      //                    nextEntry = Tuple2.of(key, (V) nodeOrVal);
      //                    return true;
      //                } else if(nodeOrVal != null) {
      //                    Iterator<UnEntry<K,V>> iter = ((INode<K,V>) nodeOrVal).iterator();
      //                    if (iter != null && iter.hasNext()) {
      //                        nextIter = iter;
      //                        return true;
      //                    }
      //                }
      //            }
      while (mutableIndex < array.length) {
        int i = mutableIndex;
        mutableIndex = i + 2;
        if (array[i] != null) {
          nextEntry = aFn.apply(k(array, i), v(array, i + 1));
          absent = false;
          return true;
        } else {
          INode<K, V> node = iNode(array, i + 1);
          if (node != null) {
            UnmodIterator<R> iter = node.iterator(aFn);
            if (iter != null && iter.hasNext()) {
              nextIter = iter;
              return true;
            }
          }
        }
      }
      return false;
    }

    @Override
    public boolean hasNext() {
      //noinspection SimplifiableIfStatement
      if (!absent || nextIter != null) {
        return true;
      }
      return advance();
    }

    @Override
    public R next() {
      R ret = nextEntry;
      if (!absent) {
        nextEntry = null;
        absent = true;
        return ret;
      } else if (nextIter != null) {
        ret = nextIter.next();
        if (!nextIter.hasNext()) {
          nextIter = null;
        }
        return ret;
      } else if (advance()) {
        return next();
      }
      throw new NoSuchElementException();
    }
  }

  //    static final class NodeSeq<K,V> implements Sequence<UnmodMap.UnEntry<K,V>> {
  //        private final Object[] array;
  //        private final int i;
  //        private final Sequence<UnmodMap.UnEntry<K,V>> s;
  //
  //        static <K,V> Sequence<UnmodMap.UnEntry<K,V>> create(Object[] array) {
  //            return create(array, 0, null);
  //        }
  //
  //        private static <K,V> Sequence<UnmodMap.UnEntry<K,V>> create(Object[] array, int i,
  //                                                              Sequence<UnmodMap.UnEntry<K,V>> s)
  // {
  //            if ( (s != null) && (s != Sequence.EMPTY_SEQUENCE) ) {
  //                return new NodeSeq<>(array, i, s);
  //            }
  //
  //            for (int j = i; j < array.length; j += 2) {
  //                if (array[j] != null) { return new NodeSeq<>(array, j, null); }
  //
  //                INode<K,V> node = iNode(array, j + 1);
  //                if (node != null) {
  //                    Sequence<UnmodMap.UnEntry<K,V>> nodeSeq = node.nodeSeq();
  //
  //                    if (nodeSeq != null) { return new NodeSeq<>(array, j + 2, nodeSeq); }
  //                }
  //            }
  //            return Sequence.emptySequence();
  //        }
  //
  //        private NodeSeq(Object[] array, int i, Sequence<UnmodMap.UnEntry<K,V>> s) {
  //            super();
  //            this.array = array;
  //            this.i = i;
  //            this.s = s;
  //        }
  //
  //        @Override public Option<UnmodMap.UnEntry<K,V>> head() {
  //            return ( (s != null) && (s != Sequence.EMPTY_SEQUENCE) ) ? s.head() :
  //                   i < array.length - 1 ? Option.of(Tuple2.of(k(array, i), v(array, i+1))) :
  //                   Option.none();
  //        }
  //
  //        @Override public Sequence<UnmodMap.UnEntry<K,V>> tail() {
  //            if ( (s != null) && (s != Sequence.EMPTY_SEQUENCE) ) {
  //                return create(array, i, s.tail());
  //            }
  //            return create(array, i + 2, null);
  //        }
  //
  //        @Override public String toString() { return UnmodIterable.toString("NodeSeq", this); }
  //
  //    } // end class NodeSeq
}
