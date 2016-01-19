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
import java.util.Iterator;
import org.organicdesign.fp.xform.Xform;

public abstract class VmCollection extends VmValue implements Iterable<Object> {
  public interface Builder<T extends VmCollection> {
    void add(Object element);

    void addAll(Iterable<?> elements);

    T build();
  }

  public abstract int getLength();

  public abstract boolean isEmpty();

  @Override
  public boolean isSequence() {
    return true;
  }

  public abstract VmCollection add(Object element);

  public abstract VmCollection concatenate(VmCollection other);

  public abstract Iterator<Object> reverseIterator();

  public abstract Builder<? extends VmCollection> builder();

  public final void checkNonEmpty() {
    if (isEmpty()) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder()
          .evalError("expectedNonEmptyCollection")
          .withProgramValue("Collection", this)
          .build();
    }
  }

  public abstract boolean isLengthOne();

  public final void checkLengthOne() {
    if (!isLengthOne()) {
      CompilerDirectives.transferToInterpreter();
      throw new VmExceptionBuilder()
          .evalError("expectedSingleElementCollection")
          .withProgramValue("Collection", this)
          .build();
    }
  }

  protected static void checkPositive(long n) {
    VmUtils.checkPositive(n);
  }

  @TruffleBoundary
  public final boolean startsWith(VmCollection other) {
    if (getLength() < other.getLength()) return false;

    var iter = iterator();
    var otherIter = other.iterator();

    //noinspection WhileLoopReplaceableByForEach
    while (otherIter.hasNext()) {
      if (!iter.next().equals(otherIter.next())) return false;
    }

    return true;
  }

  @TruffleBoundary
  public final boolean endsWith(VmCollection other) {
    if (getLength() < other.getLength()) return false;

    var iter = reverseIterator();
    var otherIter = other.reverseIterator();

    while (otherIter.hasNext()) {
      if (!iter.next().equals(otherIter.next())) return false;
    }

    return true;
  }

  @TruffleBoundary
  public final VmList replaceRange(long start, long exclusiveEnd, VmCollection replacement) {
    var result =
        Xform.of(this).take(start).concat(replacement).concat(Xform.of(this).drop(exclusiveEnd));
    return VmList.create(result);
  }

  @TruffleBoundary
  public final Object replaceRangeOrNull(long start, long exclusiveEnd, VmCollection replacement) {
    var length = getLength();

    if (start < 0 || start > length) {
      return VmNull.withoutDefault();
    }

    if (exclusiveEnd < start || exclusiveEnd > length) {
      return VmNull.withoutDefault();
    }

    var result =
        Xform.of(this).take(start).concat(replacement).concat(Xform.of(this).drop(exclusiveEnd));
    return VmList.create(result);
  }

  @TruffleBoundary
  public final VmCollection flatten() {
    var builder = builder();
    for (var elem : this) {
      if (elem instanceof Iterable) {
        builder.addAll((Iterable<?>) elem);
      } else if (elem instanceof VmListing) {
        var listing = (VmListing) elem;
        listing.forceAndIterateMemberValues(
            (key, member, value) -> {
              builder.add(value);
              return true;
            });
      } else {
        CompilerDirectives.transferToInterpreter();
        throw new VmExceptionBuilder()
            .evalError("cannotFlattenCollectionWithNonCollectionElement")
            .withProgramValue("Element", elem)
            .build();
      }
    }
    return builder.build();
  }

  @TruffleBoundary
  public final VmCollection zip(VmCollection other) {
    var builder = builder();
    var iter1 = iterator();
    var iter2 = other.iterator();
    while (iter1.hasNext() && iter2.hasNext()) {
      builder.add(new VmPair(iter1.next(), iter2.next()));
    }
    return builder.build();
  }

  @TruffleBoundary
  public final String join(String separator) {
    if (isEmpty()) return "";

    var iter = iterator();
    var builder = new StringBuilder();
    builder.append(iter.next());

    while (iter.hasNext()) {
      builder.append(separator);
      builder.append(iter.next());
    }

    return builder.toString();
  }

  public final String toString() {
    return VmValueRenderer.multiLine(Integer.MAX_VALUE).render(this);
  }
}
