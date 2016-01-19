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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import org.pkl.core.util.Nullable;

// Some code copied from kotlin.ranges.Progressions, kotlin.ranges.ProgressionIterators,
// kotlin.internal.ProgressionUtil (Apache 2).
@ValueType
public final class VmIntSeq extends VmValue implements Iterable<Long> {
  public final long start;
  public final long end;
  public final long step;

  public final long last;

  public VmIntSeq(long start, long end, long step) {
    assert step != 0;

    this.start = start;
    this.end = end;
    this.step = step;

    last =
        step > 0
            ? start >= end ? end : end - diffMod(end, start, step)
            : start <= end ? end : end + diffMod(start, end, -step);
  }

  public boolean isEmpty() {
    return step > 0 ? start > last : start < last;
  }

  public long getLength() {
    return (Math.abs((end - start) / step)) + 1;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getIntSeqClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {}

  @Override
  public Object export() {
    throw new VmExceptionBuilder().evalError("cannotExportValue", getVmClass()).build();
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitIntSeq(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertIntSeq(this, path);
  }

  @Override
  public PrimitiveIterator.OfLong iterator() {
    return new PrimitiveIterator.OfLong() {
      boolean hasNext = !isEmpty();
      long next = hasNext ? start : last;

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public long nextLong() {
        var result = next;
        if (result == last) {
          if (!hasNext) {
            CompilerDirectives.transferToInterpreter();
            throw new NoSuchElementException();
          }
          hasNext = false;
        } else {
          next += step;
        }
        return result;
      }
    };
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmIntSeq)) return false;

    var other = (VmIntSeq) obj;
    return isEmpty()
        ? other.isEmpty()
        : start == other.start && last == other.last && step == other.step;
  }

  @Override
  public int hashCode() {
    if (isEmpty()) return 1;

    var result = 1;
    result = result * 31 + Long.hashCode(start);
    result = result * 31 + Long.hashCode(last);
    result = result * 31 + Long.hashCode(step);
    return result;
  }

  @Override
  @TruffleBoundary
  public String toString() {
    return step == 1
        ? "IntSeq(" + start + ", " + end + ")"
        : "IntSeq(" + start + ", " + end + ").step(" + step + ")";
  }

  private static long mod(long a, long b) {
    var mod = a % b;
    return mod >= 0 ? mod : mod + b;
  }

  // (a - b) mod c
  private static long diffMod(long a, long b, long c) {
    return mod(mod(a, c) - mod(b, c), c);
  }
}
