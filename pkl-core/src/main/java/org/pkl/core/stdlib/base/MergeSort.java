/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib.base;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.stdlib.base.CollectionNodes.SortComparatorNode;
import org.pkl.core.util.Nullable;

final class MergeSort {
  // must be a power of two
  private static final int INITIAL_MERGE_SORT_STRIDE = 8;

  private MergeSort() {}

  public static Object[] sort(
      VirtualFrame frame,
      Object[] array,
      SortComparatorNode comparator,
      @Nullable VmFunction function) {

    var length = array.length;
    var temp = new Object[length];

    // start is inclusive, end is exclusive

    for (var start = 0; start < length; start += INITIAL_MERGE_SORT_STRIDE) {
      var end = Math.min(start + INITIAL_MERGE_SORT_STRIDE, length);
      insertionSort(frame, array, start, end, comparator, function);
    }

    for (var stride = INITIAL_MERGE_SORT_STRIDE; stride < length; stride *= 2) {
      for (var start = 0; start < length - stride; start += stride + stride) {
        var end = Math.min(start + stride + stride, length);
        var mid = start + stride; // start of second half
        merge(frame, array, temp, start, mid, end, comparator, function);
      }
    }

    return array;
  }

  private static void merge(
      VirtualFrame frame,
      Object[] array,
      Object[] temp,
      int start,
      int mid,
      int end,
      SortComparatorNode comparator,
      @Nullable VmFunction function) {

    if (comparator.executeWith(frame, array[mid - 1], array[mid], function)) {
      return; // already sorted
    }

    System.arraycopy(array, start, temp, start, end - start);

    var i = start;
    var j = mid;

    for (var k = start; k < end; k++) {
      if (i >= mid) array[k] = temp[j++];
      else if (j >= end) array[k] = temp[i++];
      else if (comparator.executeWith(frame, temp[j], temp[i], function)) array[k] = temp[j++];
      else array[k] = temp[i++];
    }
  }

  private static void insertionSort(
      VirtualFrame frame,
      Object[] array,
      int start,
      int end,
      SortComparatorNode comparator,
      @Nullable VmFunction function) {

    for (var i = start; i < end; i++) {
      for (var j = i;
          j > start && comparator.executeWith(frame, array[j], array[j - 1], function);
          j--) {
        Object swap = array[j];
        array[j] = array[j - 1];
        array[j - 1] = swap;
      }
    }
  }
}
