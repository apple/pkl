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

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

/**
 * A {@link MapCursor} implementation that iterates over the properties of a {@link DynamicObject}.
 *
 * <p>This cursor provides allocation-free iteration over DynamicObject properties by caching the
 * key array at construction time and accessing values on demand.
 */
public final class DynamicObjectMapCursor implements MapCursor<Object, Object> {
  private final DynamicObject object;
  private final DynamicObjectLibrary library;
  private final Object[] keys;
  private int index = -1;

  /**
   * Creates a cursor for iterating over the given DynamicObject's properties.
   *
   * @param object the DynamicObject to iterate over
   */
  public DynamicObjectMapCursor(DynamicObject object) {
    this.object = object;
    this.library = DynamicObjectLibrary.getUncached();
    this.keys = library.getKeyArray(object);
  }

  @Override
  public boolean advance() {
    index++;
    return index < keys.length;
  }

  @Override
  public Object getKey() {
    if (index < 0 || index >= keys.length) {
      throw new IllegalStateException("Cursor not positioned on a valid entry");
    }
    return keys[index];
  }

  @Override
  public Object getValue() {
    if (index < 0 || index >= keys.length) {
      throw new IllegalStateException("Cursor not positioned on a valid entry");
    }
    return library.getOrDefault(object, keys[index], null);
  }
}
