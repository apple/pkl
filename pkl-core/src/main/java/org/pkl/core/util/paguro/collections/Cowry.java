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

import java.lang.reflect.Array;
import java.util.Arrays;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.tuple.Tuple2;

/**
 * Cowry is short for Copy On Write aRraY and contains utilities for doing this quickly and
 * correctly. While a key goal of Paguro is to get away from working with arrays, you still need to
 * do it sometimes and shouldn't have to re-implement common copy-on-write modifictions.
 *
 * <p>Never, ever, ever do this:
 *
 * <pre>{@code
 * // Evil, bad, and wrong!
 * (Class<T>) items[0].getClass()
 * }</pre>
 *
 * If you are using an array of Numbers and the first item is an Integer and the second a Double,
 * You'll get an array of Integers which will blow up when you try to add a Double.
 *
 * <p>This class is final and cannot be instantiated. Created by gpeterso on 5/21/2017.
 */
@SuppressWarnings("WeakerAccess")
public final class Cowry {
  private Cowry() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  /**
   * We only one empty array and it makes the code simpler than pointing to null all the time. Have
   * to time the difference between using this and null. The only difference I can imagine is that
   * this has an address in memory and null does not, so it could save a memory lookup in some
   * places.
   *
   * <p>Since no objects are ever added to or removed from an empty array, it is effectively
   * immutable and there only ever needs to be one. It also turns out that the type doesn't matter.
   */
  static final Object[] EMPTY_ARRAY = new Object[0];

  // =================================== Array Helper Functions ==================================
  // Helper function to return the empty array of whatever type you need.
  @SuppressWarnings("unchecked")
  public static <T> T[] emptyArray() {
    return (T[]) EMPTY_ARRAY;
  }

  //    // Thank you jeannicolas
  //    // http://stackoverflow.com/questions/80476/how-can-i-concatenate-two-arrays-in-java
  //    private static <T> T[] arrayGenericConcat(T[] a, T[] b) {
  //        int aLen = a.length;
  //        int bLen = b.length;
  //
  //        @SuppressWarnings("unchecked")
  //        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
  //        System.arraycopy(a, 0, c, 0, aLen);
  //        System.arraycopy(b, 0, c, aLen, bLen);
  //
  //        return c;
  //    }

  /**
   * Helper function to avoid type warnings.
   *
   * @return an Object[1] containing the single element.
   */
  @SuppressWarnings("unchecked")
  static <T> T[] singleElementArray(T elem) {
    return (T[]) new Object[] {elem};
  }

  /**
   * Helper function to avoid type warnings.
   *
   * @param elem the item to put in the array.
   * @param tClass the class of the array.
   * @return an array of the appropriate class containing the single element.
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] singleElementArray(T elem, @Nullable Class<T> tClass) {
    if (tClass == null) {
      return (T[]) new Object[] {elem};
    }

    T[] newItems = (T[]) Array.newInstance(tClass, 1);
    newItems[0] = elem;
    return newItems;
  }

  /**
   * Returns a new array one longer than the given one, with the specified item inserted at the
   * specified index.
   *
   * @param item The item to insert
   * @param items The original array (will not be modified)
   * @param idx the index to insert the item at.
   * @param tClass The runtime class to store in the new array (or null for an Object array).
   * @return A copy of the given array with the additional item at the appropriate index (will be
   *     one longer than the original array).
   */
  public static <T> T[] insertIntoArrayAt(T item, T[] items, int idx, @Nullable Class<T> tClass) {
    // Make an array that's one bigger.  It's too bad that the JVM bothers to
    // initialize this with nulls.
    @SuppressWarnings("unchecked")
    T[] newItems =
        (T[])
            ((tClass == null)
                ? new Object[items.length + 1]
                : Array.newInstance(tClass, items.length + 1));

    // If we aren't inserting at the first item, array-copy the items before the insert
    // point.
    if (idx > 0) {
      System.arraycopy(items, 0, newItems, 0, idx);
    }

    // Insert the new item.
    newItems[idx] = item;

    // If we aren't inserting at the last item, array-copy the items after the insert
    // point.
    if (idx < items.length) {
      System.arraycopy(items, idx, newItems, idx + 1, items.length - idx);
    }

    return newItems;
  }

  /**
   * Returns a new array containing the first n items of the given array.
   *
   * @param items the items to copy
   * @param length the maximum length of the new array
   * @param tClass the class of the items in the array (null for Object)
   * @return a copy of the original array with the given length
   */
  public static <T> T[] arrayCopy(T[] items, int length, @Nullable Class<T> tClass) {
    // Make an array of the appropriate size.  It's too bad that the JVM bothers to
    // initialize this with nulls.
    @SuppressWarnings("unchecked")
    T[] newItems =
        (T[]) ((tClass == null) ? new Object[length] : Array.newInstance(tClass, length));

    // array-copy the items up to the new length.
    if (length > 0) {
      System.arraycopy(items, 0, newItems, 0, Math.min(items.length, length));
    }
    return newItems;
  }

  //    private static <T> T[] insertIntoArrayAt(T item, T[] items, int idx) {
  //        return insertIntoArrayAt(item, items, idx, null);
  //    }

  /**
   * Called splice, but handles precat (idx = 0) and concat (idx = origItems.length).
   *
   * @param insertedItems the items to insert
   * @param origItems the original items.
   * @param idx the index to insert new items at
   * @param tClass the class of the resulting new array
   * @return a new array with the new items inserted at the proper position of the old array.
   */
  public static <A> A[] spliceIntoArrayAt(
      A[] insertedItems, A[] origItems, int idx, @Nullable Class<A> tClass) {
    // Make an array that big enough.  It's too bad that the JVM bothers to
    // initialize this with nulls.
    @SuppressWarnings("unchecked")
    A[] newItems =
        tClass == null
            ? (A[]) new Object[insertedItems.length + origItems.length]
            : (A[]) Array.newInstance(tClass, insertedItems.length + origItems.length);

    // If we aren't inserting at the first item, array-copy the items before the insert
    // point.
    if (idx > 0) {
      //               src,  srcPos, dest,destPos,length
      System.arraycopy(origItems, 0, newItems, 0, idx);
    }

    // Insert the new items
    //               src,      srcPos,     dest, destPos, length
    System.arraycopy(insertedItems, 0, newItems, idx, insertedItems.length);

    // If we aren't inserting at the last item, array-copy the items after the insert
    // point.
    if (idx < origItems.length) {
      System.arraycopy(
          origItems, idx, newItems, idx + insertedItems.length, origItems.length - idx);
    }
    return newItems;
  }

  //    private static int[] replaceInIntArrayAt(int replacedItem, int[] origItems, int idx) {
  //        // Make an array that big enough.  It's too bad that the JVM bothers to
  //        // initialize this with nulls.
  //        int[] newItems = new int[origItems.length];
  //        System.arraycopy(origItems, 0, newItems, 0, origItems.length);
  //        newItems[idx] = replacedItem;
  //        return newItems;
  //    }

  public static <T> T[] replaceInArrayAt(
      T replacedItem, T[] origItems, int idx, @Nullable Class<T> tClass) {
    // Make an array that big enough.  It's too bad that the JVM bothers to
    // initialize this with nulls.
    @SuppressWarnings("unchecked")
    T[] newItems =
        (T[])
            ((tClass == null)
                ? new Object[origItems.length]
                : Array.newInstance(tClass, origItems.length));
    System.arraycopy(origItems, 0, newItems, 0, origItems.length);
    newItems[idx] = replacedItem;
    return newItems;
  }

  /**
   * Only call this if the array actually needs to be split (0 &lt; splitPoint &lt; orig.length).
   *
   * @param orig array to split
   * @param splitIndex items less than this index go in the left, equal or greater in the right.
   * @return a pair of leftItems and rightItems
   */
  public static <T> Tuple2<T[], T[]> splitArray(T[] orig, int splitIndex) { // , Class<T> tClass) {
    //        if (splitIndex < 1) {
    //            throw new IllegalArgumentException("Called split when splitIndex < 1");
    //        }
    //        if (splitIndex > orig.length - 1) {
    //            throw new IllegalArgumentException("Called split when splitIndex > orig.length -
    // 1");
    //        }

    // NOTE:
    // I sort of suspect that generic 2D array creation where the two arrays are of a different
    // length is not possible in Java, or if it is, it's not likely to be much faster than
    // what we have here.  I'd just copy the Arrays.copyOf code everywhere this function is used
    // if you want more speed.
    //        int rightLength = orig.length - splitIndex;
    //        Class<T> tClass = (Class<T>) orig.getClass().getComponentType();
    //        Tuple2<T[],T[]> split = Tuple2.of((T[]) Array.newInstance(tClass, splitIndex),
    //                                          (T[]) Array.newInstance(tClass, rightLength));
    //
    // Tuple2<T[],T[]> split =
    return Tuple2.of(
        Arrays.copyOf(orig, splitIndex), Arrays.copyOfRange(orig, splitIndex, orig.length));

    //        // original array, offset, newArray, offset, length
    //        System.arraycopy(orig, 0, split._1(), 0, splitIndex);
    //
    //        System.arraycopy(orig, splitIndex, split._2(), 0, rightLength);
    //        return split;
  }

  /**
   * Only call this if the array actually needs to be split (0 &lt; splitPoint &lt; orig.length).
   *
   * @param orig array to split
   * @param splitIndex items less than this index go in the left, equal or greater in the right.
   * @return a 2D array of leftItems then rightItems
   */
  public static int[][] splitArray(int[] orig, int splitIndex) {
    // This function started an exact duplicate of the one above, but for ints.
    //        if (splitIndex < 1) {
    //            throw new IllegalArgumentException("Called split when splitIndex < 1");
    //        }
    //        if (splitIndex > orig.length - 1) {
    //            throw new IllegalArgumentException("Called split when splitIndex > orig.length -
    // 1");
    //        }
    int rightLength = orig.length - splitIndex;
    int[][] split = new int[][] {new int[splitIndex], new int[rightLength]};
    // original array, offset, newArray, offset, length
    System.arraycopy(orig, 0, split[0], 0, splitIndex);
    System.arraycopy(orig, splitIndex, split[1], 0, rightLength);
    return split;
  }

  //    static <T> T[] truncateArray(T[] origItems, int newLength, Class<T> tClass) {
  //        if (origItems.length == newLength) {
  //            return origItems;
  //        }
  //
  //        @SuppressWarnings("unchecked")
  //        T[] newItems = (T[]) ((tClass == null) ? new Object[newLength]
  //                                               : Array.newInstance(tClass, newLength) );
  //
  //        //                      src, srcPos,    dest,destPos, length
  //        System.arraycopy(origItems, 0, newItems, 0, newLength);
  //        return newItems;
  //    }
}
