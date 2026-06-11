/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.Arrays;

public final class ArrayUtils {
  private ArrayUtils() {}

  public static final int[] EMPTY_INT_ARRAY = new int[0];

  public static int[] append(int[] array, int elem) {
    if (array.length == 0) {
      return new int[] {elem};
    }
    var ret = Arrays.copyOf(array, array.length + 1);
    ret[array.length] = elem;
    return ret;
  }

  public static int[] append(int[] array, int elem1, int elem2) {
    if (array.length == 0) {
      return new int[] {elem1, elem2};
    }
    var ret = Arrays.copyOf(array, array.length + 2);
    ret[array.length] = elem1;
    ret[array.length + 1] = elem2;
    return ret;
  }

  public static int[] concat(int[] array1, int[] array2) {
    var result = new int[array1.length + array2.length];
    System.arraycopy(array1, 0, result, 0, array1.length);
    System.arraycopy(array2, 0, result, array1.length, array2.length);
    return result;
  }
}
