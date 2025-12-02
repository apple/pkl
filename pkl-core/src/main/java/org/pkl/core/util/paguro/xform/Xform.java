/*
 * Copyright © 2014-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.xform;

import java.util.*;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.collections.UnmodIterable;
import org.pkl.core.util.paguro.collections.UnmodIterator;
import org.pkl.core.util.paguro.function.Fn1;
import org.pkl.core.util.paguro.function.Fn2;
import org.pkl.core.util.paguro.oneOf.Or;

/**
 * An immutable description of operations to be performed (a transformation, transform, or x-form).
 * When fold() (or another terminating function) is called, the Xform definition is "compiled" into
 * a one-time mutable transformation which is then carried out. This allows certain performance
 * shortcuts (such as doing a drop with index addition instead of iteration) and also hides the
 * mutability otherwise inherent in a transformation.
 *
 * <p>Xform is an abstract class. Most of the methods on Xform produce immutable descriptions of
 * actions to take at a later time. These are represented by ___Desc classes. When fold() is called
 * (or any of the helper methods that wrap it), that produces a result by first stringing together a
 * bunch of Operations (____Op classes) and then "running" them. This is analogous to compiling a
 * program and running it. The ____Desc classes are like the immutable source, the ____Op classes
 * like the op-codes it's compiled into.
 *
 * <p>Special thanks to Nathan Williams for pointing me toward separating the mutation from the
 * description of a transformation. Also, to Paul Phillips (@extempore2) whose lectures provided an
 * outline for what was ideal and also what was important. All errors are my own. -Glen 2015-08-30
 */
@SuppressWarnings("rawtypes")
public abstract class Xform<A> implements UnmodIterable<A> {

  enum OpStrategy {
    HANDLE_INTERNALLY,
    ASK_SUPPLIER,
    CANNOT_HANDLE
  }

  private static final Object TERMINATE = new Object();

  @SuppressWarnings("unchecked")
  private A terminate() {
    return (A) TERMINATE;
  }

  /**
   * These are mutable operations that the transform carries out when it is run. This is like the
   * compiled "op codes" in contrast to the Xform is like the immutable "source code" of the
   * transformation description. Every operation can be carried out with these three functions.
   */
  abstract static class Operation {
    // Time using a linked list of ops instead of array, so that we can easily remove ops from
    // the list when they are used up.
    @Nullable Fn1<Object, Boolean> filter = null;
    @Nullable Fn1 map = null;
    @Nullable Fn1<Object, Iterable> flatMap = null;

    /**
     * Drops as many items as the source can handle.
     *
     * @param num the number of items to drop
     * @return whether the source can handle the take, or pass-through (ask-supplier), or can't do
     *     either.
     */
    public Or<Long, OpStrategy> drop(long num) {
      return (num < 1) ? Or.good(0L) : Or.bad(OpStrategy.CANNOT_HANDLE);
    }

    /**
     * Takes as many items as the source can handle.
     *
     * @param num the number of items to take.
     * @return whether the source can handle the take, or pass-through (ask-supplier), or can't do
     *     either.
     */
    public OpStrategy take(long num) {
      return OpStrategy.CANNOT_HANDLE;
    }

    /**
     * We need to model this as a separate op for when the previous op is CANNOT_HANDLE. It is coded
     * as a filter, but still needs to be modeled separately so that subsequent drops can be
     * combined into the earliest single explicit drop op. Such combinations are additive, meaning
     * that drop(3).drop(5) is equivalent to drop(8).
     */
    private static final class DropOp extends Operation {
      private long leftToDrop;

      DropOp(long drop) {
        leftToDrop = drop;
        filter =
            o -> {
              if (leftToDrop > 0) {
                leftToDrop = leftToDrop - 1;
                return Boolean.FALSE;
              }
              return Boolean.TRUE;
            };
      }

      @Override
      public Or<Long, OpStrategy> drop(long num) {
        leftToDrop = leftToDrop + num;
        return Or.good(num);
      }
    }

    private static final class FilterOp extends Operation {
      FilterOp(Fn1<Object, Boolean> func) {
        filter = func;
      }
    }

    private static final class MapOp extends Operation {
      MapOp(Fn1 func) {
        map = func;
      }

      @Override
      public Or<Long, OpStrategy> drop(long num) {
        return Or.bad(OpStrategy.ASK_SUPPLIER);
      }

      @Override
      public OpStrategy take(long num) {
        return OpStrategy.ASK_SUPPLIER;
      }
    }

    // TODO: FlatMap should drop and take internally using addition/subtraction on each output
    // TODO: list instead of testing each list item individually.
    private static final class FlatMapOp extends Operation {
      //            ListSourceDesc<U> cache = null;
      //            int numToDrop = 0;

      FlatMapOp(Fn1<Object, Iterable> func) {
        flatMap = func;
      }
    }

    /**
     * We need to model this as a separate op for when the previous op is CANNOT_HANDLE. It is coded
     * as a map, but still needs to be modeled separately so that subsequent takes can be combined
     * into the earliest single explicit take op. Such combination is a pick-least of all the takes,
     * meaning that take(5).take(3) is equivalent to take(3).
     */
    private static final class TakeOp extends Operation {
      private long numToTake;

      TakeOp(long take) {
        numToTake = take;
        map =
            a -> {
              if (numToTake > 0) {
                numToTake = numToTake - 1;
                return a;
              }
              return TERMINATE;
            };
      }

      @Override
      public OpStrategy take(long num) {
        // This data condition is prevented in Xform.take()
        //                if (num < 0) {
        //                    throw new IllegalArgumentException("Can't take less than 0 items.");
        //                }
        if (num < numToTake) {
          numToTake = num;
        }
        return OpStrategy.HANDLE_INTERNALLY;
      }
    }
  } // end class Operation

  /**
   * A RunList is a list of Operations "compiled" from an Xform. It contains an Iterable data source
   * (or some day and array source or List source) and a List of Operation op-codes.
   *
   * <p>A RunList is also a SourceProvider, since the output of one transform can be the input to
   * another. FlatMap is implemented that way. Notice that there are almost no generic types used
   * here: Since the input could be one type, and each map or flatmap operation could change that to
   * another type.
   *
   * <p>For speed, we ignore all that in the "compiled" version and just use Objects and avoid any
   * wrapping or casting.
   */
  protected static class RunList implements Iterable {
    final Iterable source;
    final List<Operation> list = new ArrayList<>();
    //        RunList next = null;
    final RunList prev;

    private RunList(RunList prv, Iterable src) {
      prev = prv;
      source = src;
    }

    Operation[] opArray() {
      return list.toArray(new Operation[0]);
    }

    @Override
    public Iterator iterator() {
      return source.iterator();
    }
  }

  /**
   * When iterator() is called, the AppendOp processes the previous source and operation into an
   * ArrayList. Then yields an iterator that yield the result of that operation until it runs out.
   * Then continues to yield the appended items until they run out, at which point hasNext() returns
   * false;
   */
  private static final class AppendOp extends RunList {
    private AppendOp(RunList prv, Iterable src) {
      super(prv, src);
    }

    @Override
    public Iterator iterator() {
      @SuppressWarnings("Convert2Lambda")
      ArrayList prevSrc =
          _fold(
              prev,
              prev.opArray(),
              0,
              new ArrayList(),
              new Fn2<ArrayList, Object, ArrayList>() {
                @SuppressWarnings("unchecked")
                @Override
                public ArrayList applyEx(ArrayList res, Object item) {
                  res.add(item);
                  return res;
                }
              });
      return new Iterator() {
        Iterator innerIter = prevSrc.iterator();
        boolean usingPrevSrc = true;

        /** {@inheritDoc} */
        @Override
        public boolean hasNext() {
          if (innerIter.hasNext()) {
            return true;
          } else if (usingPrevSrc) {
            usingPrevSrc = false;
            innerIter = source.iterator();
          }
          return innerIter.hasNext();
        }

        @Override
        public Object next() {
          return innerIter.next();
        }
      };
    } // end iterator()
  }

  /** Describes a concat() operation, but does not perform it. */
  private static final class AppendIterDesc<T> extends Xform<T> {
    final Xform<T> src;

    AppendIterDesc(Xform<T> prev, Xform<T> s) {
      super(prev);
      src = s;
    }

    @Override
    protected RunList toRunList() {
      return new AppendOp(prevOp.toRunList(), src);
    }
  }

  /**
   * Describes a "drop" operation. Drops will be pushed as early in the operation-list as possible,
   * ideally being done using one-time pointer addition on the source.
   *
   * <p>I have working source-pointer-addition code, but it added a fair amount of complexity to
   * implement it for Lists and arrays, but not for Iterables in general, so it is not currently
   * (2015-08-21) part of this implementation.
   *
   * <p>When source-pointer-addition is not possible, a Drop op-code is created (implemented as a
   * filter function). Subsequent drop ops will be combined into the earliest drop (for speed).
   *
   * @param <T> the expected input type to drop.
   */
  private static final class DropDesc<T> extends Xform<T> {
    private final long dropAmt;

    DropDesc(Xform<T> prev, long d) {
      super(prev);
      dropAmt = d;
    }

    @Override
    protected RunList toRunList() {
      //                System.out.println("in toRunList() for drop");
      RunList ret = prevOp.toRunList();
      int i = ret.list.size() - 1;
      //              System.out.println("\tchecking previous items to see if they can handle a
      // drop...");
      Or<Long, OpStrategy> earlierDs;
      for (; i >= 0; i--) {
        Operation op = ret.list.get(i);
        earlierDs = op.drop(dropAmt);
        if (earlierDs.isBad() && (earlierDs.bad() == OpStrategy.CANNOT_HANDLE)) {
          //                        System.out.println("\tNone can handle a drop...");
          break;
        } else if (earlierDs.isGood()) {
          //                        System.out.println("\tHandled internally by " + opRun);
          return ret;
        }
      }
      //            if ( !Or.bad(OpStrategy.CANNOT_HANDLE).equals(earlierDs) && (i <= 0) ) {
      //                Or<Long,OpStrategy> srcDs = ret.source.drop(dropAmt);
      //                if (srcDs.isGood()) {
      //                    if (srcDs.good() == dropAmt) {
      ////                        System.out.println("\tHandled internally by source: " +
      // ret.source);
      //                        return ret;
      //                    } else {
      //                        // TODO: Think about this and implement!
      //                        throw new UnsupportedOperationException("Not implemented yet!");
      //                    }
      //                }
      //            }
      //                System.out.println("\tSource could not handle drop.");
      //                System.out.println("\tMake a drop for " + dropAmt + " items.");
      ret.list.add(new Operation.DropOp(dropAmt));
      return ret;
    }
  }

  /** Describes a dropWhile() operation (implemented as a filter), but does not perform it. */
  private static final class DropWhileDesc<T> extends Xform<T> {
    final Fn1<? super T, Boolean> f;

    DropWhileDesc(Xform<T> prev, Fn1<? super T, Boolean> func) {
      super(prev);
      f = func;
    }

    @Override
    protected RunList toRunList() {
      RunList ret = prevOp.toRunList();
      ret.list.add(
          new Operation.FilterOp(
              new Fn1<>() {
                // Starts out active (meaning dropping items until the inner function returns true).
                // Once inner function returns true, switches into passive mode in which this
                // (outer)
                // function always returns true.
                // There are probably more efficient ways to do this, but I'm going for correct
                // first.
                private boolean active = true;

                @Override
                public Boolean applyEx(Object o) {
                  if (!active) {
                    return true;
                  }
                  @SuppressWarnings("unchecked")
                  boolean ret = !((Fn1<Object, Boolean>) f).apply(o);
                  if (ret) {
                    active = false;
                  }
                  return ret;
                }
              }));
      return ret;
    }
  }

  /** Describes a filter() operation, but does not perform it. */
  private static final class FilterDesc<T> extends Xform<T> {
    final Fn1<? super T, Boolean> f;

    FilterDesc(Xform<T> prev, Fn1<? super T, Boolean> func) {
      super(prev);
      f = func;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected RunList toRunList() {
      RunList ret = prevOp.toRunList();
      ret.list.add(new Operation.FilterOp((Fn1<Object, Boolean>) f));
      return ret;
    }
  }

  /** Describes a map() operation, but does not perform it. */
  private static final class MapDesc<T, U> extends Xform<U> {
    final Fn1<? super T, ? extends U> f;

    MapDesc(Xform<T> prev, Fn1<? super T, ? extends U> func) {
      super(prev);
      f = func;
    }

    @Override
    protected RunList toRunList() {
      RunList ret = prevOp.toRunList();
      ret.list.add(new Operation.MapOp(f));
      return ret;
    }
  }

  /** Describes a flatMap() operation, but does not perform it. */
  private static final class FlatMapDesc<T, U> extends Xform<U> {
    final Fn1<? super T, Iterable<U>> f;

    FlatMapDesc(Xform<T> prev, Fn1<? super T, Iterable<U>> func) {
      super(prev);
      f = func;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected RunList toRunList() {
      RunList ret = prevOp.toRunList();
      ret.list.add(new Operation.FlatMapOp((Fn1) f));
      return ret;
    }
  }

  /**
   * Describes a "take" operation, but does not perform it. Takes will be pushed as early in the
   * operation-list as possible, ideally being done using one-time pointer addition on the source.
   * When source pointer addition is not possible, a Take op-code is created (implemented as a
   * filter function). Subsequent take ops will be combined into the earliest take (for speed).
   *
   * @param <T> the expected input type to take.
   */
  private static final class TakeDesc<T> extends Xform<T> {
    private final long take;

    TakeDesc(Xform<T> prev, long t) {
      super(prev);
      take = t;
    }

    @Override
    protected RunList toRunList() {
      //                System.out.println("in toRunList() for take");
      RunList ret = prevOp.toRunList();
      int i = ret.list.size() - 1;
      //              System.out.println("\tchecking previous items to see if they can handle a
      // take...");
      OpStrategy earlierTs;
      for (; i >= 0; i--) {
        Operation op = ret.list.get(i);
        earlierTs = op.take(take);
        if (earlierTs == OpStrategy.CANNOT_HANDLE) {
          //                        System.out.println("\tNone can handle a take...");
          break;
        } else if (earlierTs == OpStrategy.HANDLE_INTERNALLY) {
          //                        System.out.println("\tHandled internally by " + opRun);
          return ret;
        }
      }
      //            if ( (earlierTs != OpStrategy.CANNOT_HANDLE) && (i <= 0) ) {
      //                OpStrategy srcDs = ret.source.take(take);
      //                if (srcDs == OpStrategy.HANDLE_INTERNALLY) {
      ////                        System.out.println("\tHandled internally by source: " +
      // ret.source);
      //                    return ret;
      //                }
      //            }
      //                System.out.println("\tSource could not handle take.");
      //                System.out.println("\tMake a take for " + take + " items.");
      ret.list.add(new Operation.TakeOp(take));
      return ret;
    }
  }

  static final class SourceProviderIterableDesc<T> extends Xform<T> {
    private final Iterable<? extends T> list;

    SourceProviderIterableDesc(Iterable<? extends T> l) {
      super(null);
      list = l;
    }

    @Override
    protected RunList toRunList() {
      return new RunList(null, list);
    }

    @Override
    public int hashCode() {
      return UnmodIterable.hash(this);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      //noinspection SimplifiableIfStatement
      if (!(other instanceof SourceProviderIterableDesc)) {
        return false;
      }
      return Objects.equals(this.list, ((SourceProviderIterableDesc) other).list);
    }
  }

  public static final Xform EMPTY = new SourceProviderIterableDesc<>(Collections.emptyList());

  @SuppressWarnings("unchecked")
  public static <T> Xform<T> empty() {
    return (Xform<T>) EMPTY;
  }

  /** Static factory methods */
  public static <T> Xform<T> of(@Nullable Iterable<? extends T> list) {
    if (list == null) {
      return empty();
    }
    return new SourceProviderIterableDesc<>(list);
  }

  // ========================================= Instance =========================================

  // Fields
  /** This is the previous operation or source. */
  final Xform prevOp;

  // Constructor
  Xform(Xform pre) {
    prevOp = pre;
  }

  // TODO: Everything should be implemented in terms of foldUntil now that we have that.
  /**
   * @param reducer combines each value in the list with the result so far. The result so far is the
   *     first argument. the current value to combine with it is the second argument. The return
   *     type is the same as the result so far. Fn2&lt;? super U,? super T,U&gt;
   */
  // This is the main method of this whole file.  Everything else lives to serve this.
  // We used a linked-list to build the type-safe operations so if that code compiles, the types
  // should work out here too.  However, for performance, we don't want to be stuck creating and
  // passing Options around, nor do we want a telescoping stack of hasNext() and next() calls.
  // So abandon type safety, store all the intermediate results as Objects, and use loops and
  // sentinel values to break out or skip processing as appropriate.  Initial tests indicate this
  // is 2.6 times faster than wrapping items type-safely in Options and 10 to 100 times faster
  // than lazily evaluated and cached linked-list, Sequence model.
  @SuppressWarnings("unchecked")
  private static <H> H _fold(Iterable source, Operation[] ops, int opIdx, H ident, Fn2 reducer) {
    Object ret = ident;

    // This is a label - the first one I have used in Java in years, or maybe ever.
    // I'm assuming this is fast, but will have to test to confirm it.
    sourceLoop:
    for (Object o : source) {
      for (int j = opIdx; j < ops.length; j++) {
        Operation op = ops[j];
        if ((op.filter != null) && !op.filter.apply(o)) {
          // stop processing this source item and go to the next one.
          continue sourceLoop;
        }
        if (op.map != null) {
          o = op.map.apply(o);
          // This is how map can handle takeWhile, take, and other termination marker
          // roles.  Remember, the fewer functions we have to check for, the faster this
          // will execute.
          if (o == TERMINATE) {
            return (H) ret;
          }
        } else if (op.flatMap != null) {
          ret = _fold(op.flatMap.apply(o), ops, j + 1, (H) ret, reducer);
          // stop processing this source item and go to the next one.
          continue sourceLoop;
        }
        //                    if ( (op.terminate != null) && op.terminate.apply(o) ) {
        //                        return (G) ret;
        //                    }
      }
      // Here, the item made it through all the operations.  Combine it with the result.
      ret = reducer.apply(ret, o);
    }
    return (H) ret;
  } // end _fold();

  @Override
  public UnmodIterator<A> iterator() {
    return toMutList().iterator();
  }

  // =============================================================================================
  // These will come from Transformable, but (will be) overridden to have a different return type.

  @Override
  public Xform<A> concat(@Nullable Iterable<? extends A> list) {
    return new AppendIterDesc<>(this, new SourceProviderIterableDesc<>(list));
  }

  @Override
  public Xform<A> precat(@Nullable Iterable<? extends A> list) {
    return new AppendIterDesc<>(of(list), this);
  }

  /** The number of items to drop from the beginning of the output. */
  @Override
  public Xform<A> drop(long n) {
    if (n < 0) {
      throw new IllegalArgumentException("Can't drop less than zero items.");
    }
    return new DropDesc<>(this, n);
  }

  /** The number of items to drop from the beginning of the output. */
  @Override
  public Xform<A> dropWhile(Fn1<? super A, Boolean> predicate) {
    return new DropWhileDesc<>(this, predicate);
  }

  /** Provides a way to collect the results of the transformation. */
  @Override
  public <B> B fold(B ident, Fn2<? super B, ? super A, B> reducer) {

    // Construct an optimized array of OpRuns (mutable operations for this run)
    RunList runList = toRunList();
    return _fold(runList, runList.opArray(), 0, ident, reducer);
  }

  /**
   * This implementation should be correct, but could be slow in the case where previous operations
   * are slow and the terminateWhen operation is fast and terminates early. It actually renders
   * items to a mutable List, then runs through the list performing the requested reduction,
   * checking for early termination on the result. If you can do a takeWhile() or take() earlier in
   * the transform chain instead of doing it here, always do that. If you really need early
   * termination based on the *result* of a fold, and the operations are expensive or the input is
   * huge, try using a View instead. If you don't care about those things, then this method is
   * perfect for you.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public <G, B> Or<G, B> foldUntil(
      G accum,
      @Nullable Fn2<? super G, ? super A, B> terminator,
      Fn2<? super G, ? super A, G> reducer) {
    if (terminator == null) {
      return Or.good(fold(accum, reducer));
    }

    // Yes, this is a cheap plastic imitation of what you'd hope for if you really need this
    // method.  The trouble is that when I implemented it correctly in _fold, I found
    // it was going to be incredibly difficult, or more likely impossible to implement
    // when the previous operation was flatMap, since you don't have the right result type to
    // check against when you recurse in to the flat mapping function, and if you check the
    // return from the recursion, it may have too many elements already.
    // In XformTest.java, there's something marked "Early termination test" that illustrates
    // this exact problem.
    List<A> as = this.toMutList();
    for (A a : as) {
      B term = terminator.apply(accum, a);
      if (term != null) {
        return Or.bad(term);
      }
      accum = reducer.apply(accum, a);
    }
    return Or.good(accum);
  }

  @Override
  public Xform<A> filter(Fn1<? super A, Boolean> f) {
    return new FilterDesc<>(this, f);
  }

  @Override
  public <B> Xform<B> flatMap(Fn1<? super A, Iterable<B>> f) {
    return new FlatMapDesc<>(this, f);
  }

  @Override
  public <B> Xform<B> map(Fn1<? super A, ? extends B> f) {
    return new MapDesc<>(this, f);
  }

  protected abstract RunList toRunList();

  @Override
  public Xform<A> take(long numItems) {
    if (numItems < 0) {
      throw new IllegalArgumentException("Num items must be >= 0");
    }
    return new TakeDesc<>(this, numItems);
  }

  @Override
  public Xform<A> takeWhile(Fn1<? super A, Boolean> f) {
    // I'm coding this as a map operation that either returns the source, or a TERMINATE
    // sentinel value.
    return new MapDesc<>(this, a -> f.apply(a) ? a : terminate());
  }
}
