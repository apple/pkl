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

/** A cursor for iterating over map entries without allocating Entry objects. */
public interface MapCursor<K, V> {
  /**
   * Advances the cursor to the next entry.
   *
   * @return true if there is a next entry, false if iteration is complete
   */
  boolean advance();

  /**
   * Returns the key at the current cursor position.
   *
   * @throws IllegalStateException if called before {@link #advance()} or after it returns false
   */
  K getKey();

  /**
   * Returns the value at the current cursor position.
   *
   * @throws IllegalStateException if called before {@link #advance()} or after it returns false
   */
  V getValue();
}
