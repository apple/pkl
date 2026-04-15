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

/** A cursor that iterates over zero entries. */
public final class EmptyMapCursor<K, V> implements MapCursor<K, V> {
  private static final EmptyMapCursor<Object, Object> INSTANCE = new EmptyMapCursor<>();

  private EmptyMapCursor() {}

  /** Returns the singleton empty cursor instance. */
  @SuppressWarnings("unchecked")
  public static <K, V> MapCursor<K, V> instance() {
    return (MapCursor<K, V>) INSTANCE;
  }

  @Override
  public boolean advance() {
    return false;
  }

  @Override
  public K getKey() {
    throw new IllegalStateException("Cannot get key from empty cursor");
  }

  @Override
  public V getValue() {
    throw new IllegalStateException("Cannot get value from empty cursor");
  }
}
