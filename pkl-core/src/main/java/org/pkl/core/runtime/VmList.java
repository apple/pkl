/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.Iterators.ReverseTruffleIterator;
import org.pkl.core.runtime.Iterators.TruffleIterator;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.collections.RrbTree;
import org.pkl.core.util.paguro.collections.RrbTree.ImRrbt;
import org.pkl.core.util.paguro.collections.RrbTree.MutRrbt;
import org.pkl.core.util.paguro.collections.UnmodCollection;
import org.pkl.core.util.paguro.collections.UnmodIterable;

// currently the backing collection is realized at the end of each VmList operation
// this trades efficiency for ease of understanding, as it eliminates the complexity
// of users having to deal with deferred operations that only fail later
// perhaps we could find a compromise, e.g. realize every time a
// property/local is read or a method parameter is set in our language
public final class VmList extends VmCollection {
  public static final VmList EMPTY = new VmList(RrbTree.empty());

  private final ImRrbt<Object> rrbt;

  private boolean forced;

  private VmList(ImRrbt<Object> rrbt) {
    this.rrbt = rrbt;
  }

  @TruffleBoundary
  public static VmList of(Object value) {
    return new VmList(RrbTree.emptyMutable().append(value).immutable());
  }

  @TruffleBoundary
  public static VmList of(Object value1, Object value2) {
    return new VmList(RrbTree.emptyMutable().append(value1).append(value2).immutable());
  }

  @SuppressWarnings("unchecked")
  static VmList create(ImRrbt<?> rrbt) {
    if (rrbt.isEmpty()) return EMPTY;
    return new VmList((ImRrbt<Object>) rrbt);
  }

  @TruffleBoundary
  @SuppressWarnings("unchecked")
  static VmList create(MutRrbt<?> rrbt) {
    if (rrbt.isEmpty()) return EMPTY;
    return new VmList((ImRrbt<Object>) rrbt.immutable());
  }

  // keeping both `create(Iterable)` and `create(UnmodIterable)` around
  // allows to easily find call sites that create a VmList
  // from a non-Paguro collection (which should be rare)
  @TruffleBoundary
  public static VmList create(Iterable<?> iterable) {
    return create(RrbTree.emptyMutable().concat(iterable).immutable());
  }

  @SuppressWarnings("unchecked")
  static VmList create(UnmodIterable<?> iterable) {
    return create((Iterable<Object>) iterable);
  }

  @TruffleBoundary
  static VmList create(UnmodCollection<?> collection) {
    if (collection.isEmpty()) return EMPTY;
    return new VmList(RrbTree.emptyMutable().concat(collection).immutable());
  }

  @TruffleBoundary
  public static VmList create(Object[] elements) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var elem : elements) {
      vector.append(elem);
    }
    return new VmList(vector.immutable());
  }

  @TruffleBoundary
  public static VmList create(byte[] elements) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var elem : elements) {
      vector.append(Byte.toUnsignedLong(elem));
    }
    return new VmList(vector.immutable());
  }

  @TruffleBoundary
  public static VmList create(Object[] elements, int length) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var i = 0; i < length; i++) {
      vector.append(elements[i]);
    }
    return new VmList(vector.immutable());
  }

  @TruffleBoundary
  public static VmList createFromConstantNodes(ExpressionNode[] elements) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var elem : elements) {
      assert elem instanceof ConstantNode;
      vector.append(((ConstantNode) elem).getValue());
    }
    return new VmList(vector.immutable());
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getListClass();
  }

  @Override
  @TruffleBoundary
  public int getLength() {
    return rrbt.size();
  }

  @Override
  @TruffleBoundary
  public boolean isEmpty() {
    return rrbt.isEmpty();
  }

  @Override
  @TruffleBoundary
  public boolean isLengthOne() {
    return rrbt.size() == 1;
  }

  public long getLastIndex() {
    return rrbt.size() - 1;
  }

  @Override
  @TruffleBoundary
  public VmList add(Object element) {
    return VmList.create(rrbt.append(element));
  }

  @TruffleBoundary
  public VmList replace(long index, Object element) {
    return VmList.create(rrbt.replace((int) index, element));
  }

  @TruffleBoundary
  public Object replaceOrNull(long index, Object element) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return VmList.create(rrbt.replace((int) index, element));
  }

  @Override
  @TruffleBoundary
  public VmList concatenate(VmCollection other) {
    return other.isEmpty() ? this : VmList.create(rrbt.concat(other));
  }

  @TruffleBoundary
  public Object get(long index) {
    return rrbt.get((int) index);
  }

  @TruffleBoundary
  public Object getOrNull(long index) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return rrbt.get((int) index);
  }

  @TruffleBoundary
  public VmList subList(long start, long exclusiveEnd) {
    return VmList.create(rrbt.subList((int) start, (int) exclusiveEnd));
  }

  @TruffleBoundary
  public Object subListOrNull(long start, long exclusiveEnd) {
    var length = getLength();

    if (start < 0 || start > length) {
      return VmNull.withoutDefault();
    }
    if (exclusiveEnd < start || exclusiveEnd > length) {
      return VmNull.withoutDefault();
    }
    return VmList.create(rrbt.subList((int) start, (int) exclusiveEnd));
  }

  @Override
  public Iterator<Object> iterator() {
    if (rrbt.isEmpty()) return Iterators.emptyTruffleIterator();
    return new TruffleIterator<>(rrbt);
  }

  @Override
  public Iterator<Object> reverseIterator() {
    if (rrbt.isEmpty()) return Iterators.emptyTruffleIterator();
    return new ReverseTruffleIterator<>(rrbt);
  }

  @Override
  @TruffleBoundary
  public VmCollection.Builder<VmList> builder() {
    return new Builder();
  }

  @TruffleBoundary
  public Object getFirst() {
    checkNonEmpty();
    return rrbt.get(0);
  }

  @TruffleBoundary
  public Object getFirstOrNull() {
    if (rrbt.isEmpty()) return VmNull.withoutDefault();
    return rrbt.get(0);
  }

  @TruffleBoundary
  public VmList getRest() {
    checkNonEmpty();
    return VmList.create(rrbt.drop(1));
  }

  @TruffleBoundary
  public Object getRestOrNull() {
    if (rrbt.isEmpty()) return VmNull.withoutDefault();
    return VmList.create(rrbt.drop(1));
  }

  @TruffleBoundary
  public Object getLast() {
    checkNonEmpty();
    return rrbt.get(rrbt.size() - 1);
  }

  @TruffleBoundary
  public Object getLastOrNull() {
    if (isEmpty()) return VmNull.withoutDefault();
    return rrbt.get(rrbt.size() - 1);
  }

  @TruffleBoundary
  public Object getSingle() {
    checkLengthOne();
    return rrbt.get(0);
  }

  @TruffleBoundary
  public Object getSingleOrNull() {
    if (!isLengthOne()) return VmNull.withoutDefault();
    return rrbt.get(0);
  }

  @TruffleBoundary
  @SuppressWarnings("deprecation")
  public boolean contains(Object element) {
    return rrbt.contains(element);
  }

  @TruffleBoundary
  public long indexOf(Object elem) {
    return rrbt.indexOf(elem);
  }

  @TruffleBoundary
  public Object indexOfOrNull(Object elem) {
    long result = rrbt.indexOf(elem);
    if (result == -1) return VmNull.withoutDefault();
    return result;
  }

  @TruffleBoundary
  public long lastIndexOf(Object elem) {
    return rrbt.lastIndexOf(elem);
  }

  @TruffleBoundary
  public Object lastIndexOfOrNull(Object elem) {
    long result = rrbt.lastIndexOf(elem);
    if (result == -1) return VmNull.withoutDefault();
    return result;
  }

  @TruffleBoundary
  public VmPair split(long index) {
    var tuple = rrbt.split((int) index);
    return new VmPair(VmList.create(tuple._1()), VmList.create(tuple._2()));
  }

  @TruffleBoundary
  public Object splitOrNull(long index) {
    if (index < 0 || index > getLength()) {
      return VmNull.withoutDefault();
    }
    return split(index);
  }

  @TruffleBoundary
  public VmList take(long n) {
    if (n == 0) return EMPTY;
    if (n >= rrbt.size()) return this;

    checkPositive(n);
    return VmList.create(rrbt.take(n));
  }

  @TruffleBoundary
  public VmList takeLast(long n) {
    if (n == 0) return EMPTY;
    if (n >= rrbt.size()) return this;

    checkPositive(n);
    return VmList.create(rrbt.drop(rrbt.size() - n));
  }

  @TruffleBoundary
  public VmList drop(long n) {
    if (n == 0) return this;
    if (n >= rrbt.size()) return EMPTY;

    checkPositive(n);
    return VmList.create(rrbt.drop(n));
  }

  @TruffleBoundary
  public VmList dropLast(long n) {
    if (n == 0) return this;
    if (n >= rrbt.size()) return EMPTY;

    checkPositive(n);
    return VmList.create(rrbt.take(rrbt.size() - n));
  }

  @TruffleBoundary
  public VmList repeat(long n) {
    if (n == 0) return EMPTY;
    if (n == 1) return this;

    checkPositive(n);

    var result = rrbt.mutable();
    for (var i = 1; i < n; i++) {
      result = result.concat(rrbt);
    }
    return VmList.create(result);
  }

  @TruffleBoundary
  public VmList reverse() {
    return VmList.create(rrbt.reverse());
  }

  @TruffleBoundary
  public Object[] toArray() {
    return rrbt.toArray();
  }

  public VmList toList() {
    return this;
  }

  @TruffleBoundary
  public VmSet toSet() {
    if (rrbt.isEmpty()) return VmSet.EMPTY;
    return VmSet.create(rrbt);
  }

  @TruffleBoundary
  public VmListing toListing() {
    var builder = new VmObjectBuilder(rrbt.size());
    for (var elem : rrbt) builder.addElement(elem);
    return builder.toListing();
  }

  @TruffleBoundary
  public VmDynamic toDynamic() {
    var builder = new VmObjectBuilder(rrbt.size());
    for (var elem : rrbt) builder.addElement(elem);
    return builder.toDynamic();
  }

  @Override
  @TruffleBoundary
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    try {
      for (var elem : rrbt) {
        VmValue.force(elem, allowUndefinedValues);
      }
    } catch (Throwable t) {
      forced = false;
      throw t;
    }
  }

  @Override
  @TruffleBoundary
  public List<Object> export() {
    var result = new ArrayList<>(rrbt.size());
    for (var elem : rrbt) {
      result.add(VmValue.export(elem));
    }
    return result;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitList(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertList(this, path);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object other) {
    if (this == other) return true;
    //noinspection SimplifiableIfStatement
    if (!(other instanceof VmList list)) return false;
    return rrbt.equals(list.rrbt);
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    return rrbt.hashCode();
  }

  private static final class Builder implements VmCollection.Builder<VmList> {
    private final MutRrbt<Object> list = RrbTree.emptyMutable();

    @Override
    @TruffleBoundary
    public void add(Object element) {
      list.append(element);
    }

    @Override
    @TruffleBoundary
    public void addAll(Iterable<?> elements) {
      list.concat(elements);
    }

    @Override
    public VmList build() {
      return VmList.create(list);
    }
  }
}
