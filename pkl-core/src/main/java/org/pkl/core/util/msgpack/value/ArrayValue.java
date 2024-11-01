/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.msgpack.value;

import java.util.Iterator;
import java.util.List;

/**
 * Representation of MessagePack's Array type.
 *
 * <p>MessagePack's Array type can represent sequence of values.
 */
public interface ArrayValue extends Value, Iterable<Value> {
  /** Returns number of elements in this array. */
  int size();

  /**
   * Returns the element at the specified position in this array.
   *
   * @throws IndexOutOfBoundsException If the index is out of range (<tt>index &lt; 0 || index &gt;=
   *     size()</tt>)
   */
  Value get(int index);

  /**
   * Returns the element at the specified position in this array. This method returns an
   * ImmutableNilValue if the index is out of range.
   */
  Value getOrNilValue(int index);

  /** Returns an iterator over elements. */
  Iterator<Value> iterator();

  /** Returns the value as {@code List}. */
  List<Value> list();
}
