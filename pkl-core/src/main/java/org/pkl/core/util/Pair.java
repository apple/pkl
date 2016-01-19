/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.ValueType;

@ValueType
public final class Pair<S, T> {
  public final S first;
  public final T second;

  private Pair(S first, T second) {
    this.first = first;
    this.second = second;
  }

  public static <S, T> Pair<S, T> of(S first, T second) {
    return new Pair<>(first, second);
  }

  public S getFirst() {
    return first;
  }

  public T getSecond() {
    return second;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Pair)) return false;

    var other = (Pair<?, ?>) obj;

    return first.equals(other.first) && second.equals(other.second);
  }

  @Override
  public int hashCode() {
    return 31 * first.hashCode() + second.hashCode();
  }

  @Override
  @TruffleBoundary
  public String toString() {
    return "Pair(" + first + ", " + second + ")";
  }
}
