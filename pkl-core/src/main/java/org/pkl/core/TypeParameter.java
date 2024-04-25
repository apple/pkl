/**
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
package org.pkl.core;

import java.io.Serial;
import java.io.Serializable;
import org.pkl.core.util.LateInit;

/** A type parameter of a generic class, type alias, or method. */
public final class TypeParameter implements Serializable {
  @Serial private static final long serialVersionUID = 0L;

  private final Variance variance;
  private final String name;
  private final int index;

  @LateInit private volatile Member owner;

  public TypeParameter(Variance variance, String name, int index) {
    this.variance = variance;
    this.name = name;
    this.index = index;
  }

  /**
   * Initializes the generic class, type alias, or method that this type parameter belongs to. This
   * method must be called as part of initializing this object. It is kept separate from the
   * constructor to help clients avoid circular evaluation.
   */
  public void initOwner(Member owner) {
    assert this.owner == null;
    this.owner = owner;
  }

  /** Returns the generic class, type alias, or method that this type parameter belongs to. */
  public Member getOwner() {
    assert owner != null;
    return owner;
  }

  /** Returns the variance of this type parameter. */
  public Variance getVariance() {
    return variance;
  }

  /** Returns the name of this type parameter. */
  public String getName() {
    return name;
  }

  /**
   * Returns the index of this type parameter in its owner's type parameter list, starting from
   * zero.
   */
  public int getIndex() {
    return index;
  }

  /** The variance of a type parameter. */
  public enum Variance {
    INVARIANT,
    /** An `out` parameter. */
    COVARIANT,
    /** An `in` parameter. */
    CONTRAVARIANT
  }
}
