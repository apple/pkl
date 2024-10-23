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
package org.pkl.core.util;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CollectionUtils {
  private static final float LOAD_FACTOR = 0.75f;

  private CollectionUtils() {}

  @TruffleBoundary
  public static <T> HashSet<T> newHashSet() {
    return new HashSet<>();
  }

  @TruffleBoundary
  public static <T> HashSet<T> newHashSet(int expectedSize) {
    return new HashSet<>((int) (expectedSize / LOAD_FACTOR) + 1, LOAD_FACTOR);
  }

  @TruffleBoundary
  public static <T> LinkedHashSet<T> newLinkedHashSet(int expectedSize) {
    return new LinkedHashSet<>((int) (expectedSize / LOAD_FACTOR) + 1, LOAD_FACTOR);
  }

  @TruffleBoundary
  public static <K, V> HashMap<K, V> newHashMap(int expectedSize) {
    return new HashMap<>((int) (expectedSize / LOAD_FACTOR) + 1, LOAD_FACTOR);
  }

  @TruffleBoundary
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int expectedSize) {
    return new LinkedHashMap<>((int) (expectedSize / LOAD_FACTOR) + 1, LOAD_FACTOR);
  }

  @TruffleBoundary
  public static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMap(int expectedSize) {
    return new ConcurrentHashMap<>((int) (expectedSize / LOAD_FACTOR) + 1, LOAD_FACTOR);
  }
}
