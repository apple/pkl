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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.pkl.core.Pair;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmPair extends VmValue implements Iterable<Object> {
  private final Object first;
  private final Object second;

  private boolean forced;

  public VmPair(Object first, Object second) {
    this.first = first;
    this.second = second;
  }

  public Object getFirst() {
    return first;
  }

  public Object getSecond() {
    return second;
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<>() {
      int pos = 0;

      @Override
      public boolean hasNext() {
        return pos < 2;
      }

      @Override
      public Object next() {
        return switch (pos++) {
          case 0 -> first;
          case 1 -> second;
          default -> throw new NoSuchElementException("VmPair only has two elements.");
        };
      }
    };
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getPairClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    try {
      VmValue.force(first, allowUndefinedValues);
      VmValue.force(second, allowUndefinedValues);
    } catch (Throwable t) {
      forced = false;
      throw t;
    }
  }

  @Override
  public Object export() {
    return new Pair<>(VmValue.export(first), VmValue.export(second));
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitPair(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertPair(this, path);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmPair other)) return false;
    return first.equals(other.first) && second.equals(other.second);
  }

  @Override
  public int hashCode() {
    return first.hashCode() * 31 + second.hashCode();
  }

  @Override
  @TruffleBoundary
  public String toString() {
    force(true);
    return VmValueRenderer.singleLine(Integer.MAX_VALUE).render(this);
  }
}
