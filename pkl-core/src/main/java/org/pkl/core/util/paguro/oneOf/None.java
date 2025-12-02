/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.oneOf;

import org.pkl.core.util.paguro.function.Fn0;
import org.pkl.core.util.paguro.function.Fn1;

/** Represents the absence of a value */
public final class None<T> implements Option<T> {
  // For serializable.  Make sure to change whenever internal data format changes.
  private static final long serialVersionUID = 20170810211300L;

  // ========================================== Static ==========================================
  /**
   * None is a singleton and this is its only instance. In general, you want to use {@link
   * Option#none()} instead of accessing this directly so that the generic types work out.
   */
  @SuppressWarnings("rawtypes")
  static final Option NONE = new None();

  /** Private constructor for singleton. */
  private None() {}

  /** {@inheritDoc} */
  @Override
  public T get() {
    throw new IllegalStateException("Called get on None");
  }

  /** {@inheritDoc} */
  @Override
  public T getOrElse(T t) {
    return t;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSome() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public <U> U match(Fn1<T, U> has, Fn0<U> hasNot) {
    return hasNot.get();
  }

  /** {@inheritDoc} */
  @Override
  public <U> Option<U> then(Fn1<T, Option<U>> f) {
    return Option.none();
  }

  /** This final singleton class always returns zero (it represents None after all). */
  @Override
  public int hashCode() {
    return 0;
  }

  /** Asks if the other object is instanceof the final singleton class None */
  @Override
  public boolean equals(Object other) {
    return other instanceof org.pkl.core.util.paguro.oneOf.None;
  }

  /** Defend our singleton property in the face of deserialization. */
  private Object readResolve() {
    return NONE;
  }

  @Override
  public String toString() {
    return "None";
  }
}
