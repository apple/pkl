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
package org.pkl.parser.util;

import java.util.Arrays;

public class IntArray {
  int[] store = new int[16];
  private int size = 0;

  public void push(int value) {
    if (size == store.length) {
      store = Arrays.copyOf(store, size * 2);
    }
    store[size++] = value;
  }

  public int pop() {
    if (size == 0) return -1;
    size--;
    return store[size];
  }

  public int size() {
    return size;
  }

  public void clear() {
    this.size = 0;
  }

  public void sort() {
    Arrays.sort(store, 0, size);
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public boolean isNotEmpty() {
    return size != 0;
  }

  public int get(int index) {
    assert index < size;
    return store[index];
  }
}
