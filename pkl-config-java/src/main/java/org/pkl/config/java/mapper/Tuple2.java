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
package org.pkl.config.java.mapper;

import java.util.Objects;
import org.pkl.core.util.Nullable;

// avoid name clash with org.pkl.core.Pair
final class Tuple2<S, T> {
  final S first;
  final T second;

  private Tuple2(S first, T second) {
    this.first = Objects.requireNonNull(first);
    this.second = Objects.requireNonNull(second);
  }

  static <S, T> Tuple2<S, T> of(S first, T second) {
    return new Tuple2<>(first, second);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Tuple2<?, ?> other)) return false;
    return first.equals(other.first) && second.equals(other.second);
  }

  @Override
  public int hashCode() {
    return 31 * first.hashCode() + second.hashCode();
  }

  @Override
  public String toString() {
    return "Tuple2(" + first + ", " + second + ")";
  }
}
