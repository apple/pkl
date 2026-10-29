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
package org.pkl.core.runtime;

import java.util.Objects;
import org.graalvm.collections.Equivalence;
import org.jspecify.annotations.Nullable;
import org.organicdesign.fp.collections.Equator;

/** Equality and hashing for VM collection elements and keys. */
public final class VmValueEquivalence extends Equivalence implements Equator<Object> {
  public static final VmValueEquivalence INSTANCE = new VmValueEquivalence();

  private VmValueEquivalence() {}

  @Override
  public boolean equals(@Nullable Object left, @Nullable Object right) {
    if (left instanceof Long integer && right instanceof Double floating) {
      return equals(integer.longValue(), floating.doubleValue());
    }
    if (left instanceof Double floating && right instanceof Long integer) {
      return equals(integer.longValue(), floating.doubleValue());
    }
    if (left instanceof Double a && right instanceof Double b) {
      return a.doubleValue() == b.doubleValue() || a.isNaN() && b.isNaN();
    }
    return Objects.equals(left, right);
  }

  private static boolean equals(long integer, double floating) {
    // A cast saturates 2^63 to Long.MAX_VALUE, so exclude that upper bound explicitly.
    return floating < 0x1.0p63 && integer == (long) floating && (double) integer == floating;
  }

  @Override
  public int hashCode(@Nullable Object value) {
    if (value instanceof Double floating) {
      double number = floating;
      if (number >= -0x1.0p63 && number < 0x1.0p63 && number == (long) number) {
        return Long.hashCode((long) number);
      }
    }
    return Objects.hashCode(value);
  }

  @Override
  public boolean eq(@Nullable Object left, @Nullable Object right) {
    return equals(left, right);
  }

  @Override
  public int hash(@Nullable Object value) {
    return hashCode(value);
  }
}
