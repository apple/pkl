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
package org.pkl.core.collection;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Cursor to iterate over a mutable map.
 *
 * @since 19.0
 */
public interface MapCursor<K, V> extends UnmodifiableMapCursor<K, V> {
  /**
   * Remove the current entry from the map. May only be called once. After calling {@link
   * #remove()}, it is no longer valid to call {@link #getKey()} or {@link #getValue()} on the
   * current entry.
   *
   * @since 19.0
   */
  @TruffleBoundary
  void remove();

  /**
   * Set the value of the current entry.
   *
   * @param newValue new value to be associated with the current key.
   * @return previous value associated with the current key.
   * @since 22.1
   */
  @TruffleBoundary
  default V setValue(V newValue) {
    throw new UnsupportedOperationException();
  }
}
