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
import java.util.Iterator;
import java.util.Set;
import org.organicdesign.fp.collections.ImSet;
import org.organicdesign.fp.collections.MutSet;
import org.organicdesign.fp.collections.PersistentHashSet;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.ImRrbt;
import org.organicdesign.fp.collections.RrbTree.MutRrbt;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.Iterators.ReverseTruffleIterator;
import org.pkl.core.runtime.Iterators.TruffleIterator;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.Nullable;

public final class VmSet extends VmCollection {
  public static final VmSet EMPTY = new VmSet(PersistentHashSet.empty(), RrbTree.empty());

  private final ImSet<Object> set;
  private final ImRrbt<Object> elementOrder;

  boolean forced;

  private VmSet(ImSet<Object> set, ImRrbt<Object> elementOrder) {
    assert set.size() == elementOrder.size();
    // assert set.equals(elementOrder.toImSet());
    this.set = set;
    this.elementOrder = elementOrder;
  }

  @TruffleBoundary
  public static VmSet of(Object value) {
    return new VmSet(
        PersistentHashSet.emptyMutable().put(value).immutable(),
        RrbTree.emptyMutable().append(value).immutable());
  }

  @TruffleBoundary
  static VmSet create(ImSet<Object> set, ImRrbt<Object> elementOrder) {
    if (elementOrder.isEmpty()) return EMPTY;
    return new VmSet(set, elementOrder);
  }

  @TruffleBoundary
  public static VmSet create(Iterable<?> iterable) {
    // builder takes care of handling empty case
    var builder = EMPTY.builder();
    builder.addAll(iterable);
    return builder.build();
  }

  @TruffleBoundary
  public static VmSet createFromConstantNodes(ExpressionNode[] elements) {
    // builder takes care of handling empty case
    var builder = EMPTY.builder();
    for (var elem : elements) {
      assert elem instanceof ConstantNode;
      builder.add(((ConstantNode) elem).getValue());
    }
    return builder.build();
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getSetClass();
  }

  @Override
  @TruffleBoundary
  public int getLength() {
    return elementOrder.size();
  }

  @Override
  @TruffleBoundary
  public boolean isEmpty() {
    return elementOrder.isEmpty();
  }

  @Override
  @TruffleBoundary
  public boolean isLengthOne() {
    return elementOrder.size() == 1;
  }

  @Override
  @TruffleBoundary
  public VmSet add(Object element) {
    if (set.contains(element)) return this;
    return VmSet.create(set.put(element), elementOrder.append(element));
  }

  @Override
  @TruffleBoundary
  public VmSet concatenate(VmCollection other) {
    if (other.isEmpty()) return this;

    var setBuilder = set.mutable();
    var elementOrderBuilder = elementOrder.mutable();

    for (var element : other) {
      if (!setBuilder.contains(element)) {
        setBuilder.put(element);
        elementOrderBuilder.append(element);
      }
    }
    return VmSet.create(setBuilder.immutable(), elementOrderBuilder.immutable());
  }

  @Override
  public Iterator<Object> iterator() {
    if (elementOrder.isEmpty()) return Iterators.emptyTruffleIterator();
    return new TruffleIterator<>(elementOrder);
  }

  @Override
  @TruffleBoundary
  public Iterator<Object> reverseIterator() {
    if (elementOrder.isEmpty()) return Iterators.emptyTruffleIterator();
    return new ReverseTruffleIterator<>(elementOrder);
  }

  @Override
  public VmCollection.Builder<VmSet> builder() {
    return new Builder();
  }

  public static VmCollection.Builder<VmSet> builder(VmSet set) {
    return new Builder(set);
  }

  @TruffleBoundary
  public Object getFirst() {
    checkNonEmpty();
    return elementOrder.get(0);
  }

  @TruffleBoundary
  public Object getFirstOrNull() {
    if (elementOrder.isEmpty()) return VmNull.withoutDefault();
    return elementOrder.get(0);
  }

  @TruffleBoundary
  public VmSet getRest() {
    checkNonEmpty();
    var first = elementOrder.get(0);
    return VmSet.create(set.without(first), elementOrder.without(0));
  }

  @TruffleBoundary
  public Object getRestOrNull() {
    if (elementOrder.isEmpty()) return VmNull.withoutDefault();
    var first = elementOrder.get(0);
    return VmSet.create(set.without(first), elementOrder.without(0));
  }

  @TruffleBoundary
  public Object getLast() {
    checkNonEmpty();
    return elementOrder.get(elementOrder.size() - 1);
  }

  @TruffleBoundary
  public Object getLastOrNull() {
    if (elementOrder.isEmpty()) return VmNull.withoutDefault();
    return elementOrder.get(elementOrder.size() - 1);
  }

  @TruffleBoundary
  public Object getSingle() {
    checkLengthOne();
    return elementOrder.get(0);
  }

  @TruffleBoundary
  public Object getSingleOrNull() {
    if (!isLengthOne()) return VmNull.withoutDefault();
    return elementOrder.get(0);
  }

  @TruffleBoundary
  public boolean contains(Object element) {
    return set.contains(element);
  }

  @TruffleBoundary
  public VmPair split(long index) {
    var tuple = elementOrder.split((int) index);
    return new VmPair(
        VmSet.create(tuple._1().toImSet(), tuple._1()),
        VmSet.create(tuple._2().toImSet(), tuple._2()));
  }

  @TruffleBoundary
  public Object splitOrNull(long index) {
    if (index < 0 || index > getLength()) {
      return VmNull.withoutDefault();
    }
    return split(index);
  }

  @TruffleBoundary
  public VmSet take(long n) {
    if (n == 0) return EMPTY;
    if (n >= elementOrder.size()) return this;

    checkPositive(n);
    var keepAndRemove = elementOrder.split(VmSafeMath.toInt32(n));
    return VmSet.create(keepAndRemove._1().toImSet(), keepAndRemove._1());
  }

  @TruffleBoundary
  public VmSet takeLast(long n) {
    if (n == 0) return EMPTY;
    if (n >= elementOrder.size()) return this;

    checkPositive(n);
    var removeAndKeep = elementOrder.split(elementOrder.size() - VmSafeMath.toInt32(n));
    return VmSet.create(removeAndKeep._2().toImSet(), removeAndKeep._2());
  }

  @TruffleBoundary
  public VmSet drop(long n) {
    if (n == 0) return this;
    if (n >= elementOrder.size()) return EMPTY;

    checkPositive(n);
    var removeAndKeep = elementOrder.split(VmSafeMath.toInt32(n));
    return VmSet.create(removeAndKeep._2().toImSet(), removeAndKeep._2());
  }

  @TruffleBoundary
  public VmSet dropLast(long n) {
    if (n == 0) return this;
    if (n >= elementOrder.size()) return EMPTY;

    checkPositive(n);
    var keepAndRemove = elementOrder.split(elementOrder.size() - VmSafeMath.toInt32(n));
    return VmSet.create(keepAndRemove._1().toImSet(), keepAndRemove._1());
  }

  @TruffleBoundary
  public VmList repeat(long n) {
    return VmList.create(elementOrder).repeat(n);
  }

  @TruffleBoundary
  public VmList reverse() {
    return VmList.create(elementOrder).reverse();
  }

  @TruffleBoundary
  public Object[] toArray() {
    return elementOrder.toArray();
  }

  @TruffleBoundary
  public VmList toList() {
    return VmList.create(elementOrder);
  }

  public VmSet toSet() {
    return this;
  }

  @Override
  @TruffleBoundary
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    try {
      for (var elem : elementOrder) {
        VmValue.force(elem, allowUndefinedValues);
      }
    } catch (Throwable t) {
      forced = false;
      throw t;
    }
  }

  @Override
  @TruffleBoundary
  public Set<Object> export() {
    var result = CollectionUtils.newLinkedHashSet(elementOrder.size());
    for (var elem : elementOrder) {
      result.add(VmValue.export(elem));
    }
    return result;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitSet(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertSet(this, path);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object other) {
    if (this == other) return true;
    //noinspection SimplifiableIfStatement
    if (!(other instanceof VmSet)) return false;
    return set.equals(((VmSet) other).set);
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    return set.hashCode();
  }

  private static final class Builder implements VmCollection.Builder<VmSet> {
    private final MutSet<Object> setBuilder;
    private final MutRrbt<Object> elementOrderBuilder;

    @TruffleBoundary
    private Builder() {
      setBuilder = PersistentHashSet.emptyMutable();
      elementOrderBuilder = RrbTree.emptyMutable();
    }

    @TruffleBoundary
    private Builder(VmSet set) {
      setBuilder = set.set.mutable();
      elementOrderBuilder = set.elementOrder.mutable();
    }

    @Override
    @TruffleBoundary
    public void add(Object element) {
      if (!setBuilder.contains(element)) {
        setBuilder.put(element);
        elementOrderBuilder.append(element);
      }
    }

    @Override
    @TruffleBoundary
    public void addAll(Iterable<?> elements) {
      for (var elem : elements) {
        if (!setBuilder.contains(elem)) {
          setBuilder.put(elem);
          elementOrderBuilder.append(elem);
        }
      }
    }

    @Override
    @TruffleBoundary
    public VmSet build() {
      if (elementOrderBuilder.isEmpty()) return EMPTY;
      return VmSet.create(setBuilder.immutable(), elementOrderBuilder.immutable());
    }
  }
}
