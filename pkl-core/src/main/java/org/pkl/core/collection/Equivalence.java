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
 * Strategy for comparing two objects. Default predefined strategies are {@link #DEFAULT}, {@link
 * #IDENTITY}, and {@link #IDENTITY_WITH_SYSTEM_HASHCODE}.
 *
 * @since 19.0
 */
public abstract class Equivalence {

  /**
   * Default equivalence calling {@link #equals(Object)} to check equality and {@link #hashCode()}
   * for obtaining hash values. Do not change the logic of this class as it may be inlined in other
   * places.
   *
   * @since 19.0
   */
  public static final Equivalence DEFAULT =
      new Equivalence() {

        @Override
        @TruffleBoundary
        public boolean equals(Object a, Object b) {
          return a.equals(b);
        }

        @Override
        @TruffleBoundary
        public int hashCode(Object o) {
          return o.hashCode();
        }
      };

  /**
   * Identity equivalence using {@code ==} to check equality and {@link #hashCode()} for obtaining
   * hash values. Do not change the logic of this class as it may be inlined in other places.
   *
   * @since 19.0
   */
  public static final Equivalence IDENTITY =
      new Equivalence() {

        @Override
        public boolean equals(Object a, Object b) {
          return a == b;
        }

        @Override
        @TruffleBoundary
        public int hashCode(Object o) {
          return o.hashCode();
        }
      };

  /**
   * Identity equivalence using {@code ==} to check equality and {@link
   * System#identityHashCode(Object)} for obtaining hash values. Do not change the logic of this
   * class as it may be inlined in other places.
   *
   * @since 19.0
   */
  public static final Equivalence IDENTITY_WITH_SYSTEM_HASHCODE =
      new Equivalence() {

        @Override
        public boolean equals(Object a, Object b) {
          return a == b;
        }

        @Override
        public int hashCode(Object o) {
          return System.identityHashCode(o);
        }
      };

  /**
   * Subclass for creating custom equivalence definitions.
   *
   * @since 19.0
   */
  protected Equivalence() {}

  /**
   * Returns {@code true} if the non-{@code null} arguments are equal to each other and {@code
   * false} otherwise.
   *
   * @since 19.0
   */
  public abstract boolean equals(Object a, Object b);

  /**
   * Returns the hash code of a non-{@code null} argument {@code o}.
   *
   * @since 19.0
   */
  public abstract int hashCode(Object o);
}
