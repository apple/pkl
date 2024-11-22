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
import java.util.Objects;
import org.pkl.core.util.Nullable;

/**
 * Utility class representing a pair of values.
 *
 * @since 19.0
 */
public final class Pair<L, R> {

  private static final Pair<Object, Object> EMPTY = new Pair<>(null, null);

  private final @Nullable L left;
  private final @Nullable R right;

  /**
   * Returns an empty pair.
   *
   * @since 19.0
   */
  @SuppressWarnings("unchecked")
  public static <L, R> Pair<L, R> empty() {
    return (Pair<L, R>) EMPTY;
  }

  /**
   * Constructs a pair with its left value being {@code left}, or returns an empty pair if {@code
   * left} is null.
   *
   * @return the constructed pair or an empty pair if {@code left} is null.
   * @since 19.0
   */
  public static <L, R> Pair<L, R> createLeft(@Nullable L left) {
    if (left == null) {
      return empty();
    } else {
      return new Pair<>(left, null);
    }
  }

  /**
   * Constructs a pair with its right value being {@code right}, or returns an empty pair if {@code
   * right} is null.
   *
   * @return the constructed pair or an empty pair if {@code right} is null.
   * @since 19.0
   */
  public static <L, R> Pair<L, R> createRight(@Nullable R right) {
    if (right == null) {
      return empty();
    } else {
      return new Pair<>(null, right);
    }
  }

  /**
   * Constructs a pair with its left value being {@code left}, and its right value being {@code
   * right}, or returns an empty pair if both inputs are null.
   *
   * @return the constructed pair or an empty pair if both inputs are null.
   * @since 19.0
   */
  public static <L, R> Pair<L, R> create(@Nullable L left, @Nullable R right) {
    if (right == null && left == null) {
      return empty();
    } else {
      return new Pair<>(left, right);
    }
  }

  private Pair(@Nullable L left, @Nullable R right) {
    this.left = left;
    this.right = right;
  }

  /**
   * Returns the left value of this pair.
   *
   * @since 19.0
   */
  public @Nullable L getLeft() {
    return left;
  }

  /**
   * Returns the right value of this pair.
   *
   * @since 19.0
   */
  public @Nullable R getRight() {
    return right;
  }

  /**
   * {@inheritDoc}
   *
   * @since 19.0
   */
  @Override
  @TruffleBoundary
  public int hashCode() {
    return Objects.hashCode(left) + 31 * Objects.hashCode(right);
  }

  /**
   * {@inheritDoc}
   *
   * @since 19.0
   */
  @SuppressWarnings("unchecked")
  @Override
  @TruffleBoundary
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof Pair) {
      Pair<L, R> pair = (Pair<L, R>) obj;
      return Objects.equals(left, pair.left) && Objects.equals(right, pair.right);
    }

    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @since 19.0
   */
  @Override
  @TruffleBoundary
  public String toString() {
    // String.format isn't used here since it tends to pull a lot of types into image.
    return "(" + left + ", " + right + ")";
  }
}
