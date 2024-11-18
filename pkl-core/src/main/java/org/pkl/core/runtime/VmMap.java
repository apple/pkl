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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import org.organicdesign.fp.collections.ImMap;
import org.organicdesign.fp.collections.MutMap;
import org.organicdesign.fp.collections.PersistentHashMap;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.ImRrbt;
import org.organicdesign.fp.collections.RrbTree.MutRrbt;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.Nullable;

public final class VmMap extends VmValue implements Iterable<Map.Entry<Object, Object>> {
  public static final VmMap EMPTY = new VmMap(PersistentHashMap.empty(), RrbTree.empty());

  private final ImMap<Object, Object> map;
  private final ImRrbt<Object> keyOrder;

  private boolean forced;

  @TruffleBoundary
  private VmMap(ImMap<Object, Object> map, ImRrbt<Object> keyOrder) {
    assert map.size() == keyOrder.size();
    assert map.keySet().equals(keyOrder.toImSet());
    this.map = map;
    this.keyOrder = keyOrder;
  }

  @TruffleBoundary
  public static VmMap create(ImMap<Object, Object> map, ImRrbt<Object> keyOrder) {
    if (map.isEmpty()) return EMPTY;
    return new VmMap(map, keyOrder);
  }

  @TruffleBoundary
  public static VmMap createFromConstantNodes(ExpressionNode[] constantNodes) {
    // builder takes care of handling empty case
    var builder = new Builder();
    for (var i = 0; i < constantNodes.length; i += 2) {
      var key = constantNodes[i];
      var value = constantNodes[i + 1];
      assert key instanceof ConstantNode;
      assert value instanceof ConstantNode;
      builder.add(((ConstantNode) key).getValue(), ((ConstantNode) value).getValue());
    }
    return builder.build();
  }

  @TruffleBoundary
  public static Builder builder(VmMap map) {
    return new Builder(map);
  }

  @TruffleBoundary
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getMapClass();
  }

  @TruffleBoundary
  public Iterator<Map.Entry<Object, Object>> iterator() {
    if (keyOrder.isEmpty()) return Iterators.emptyTruffleIterator();

    return new Iterator<>() {
      final Iterator<Object> keyIterator = keyOrder.iterator();

      @Override
      @TruffleBoundary
      public boolean hasNext() {
        return keyIterator.hasNext();
      }

      @Override
      @TruffleBoundary
      public Map.Entry<Object, Object> next() {
        var key = keyIterator.next();
        var value = map.get(key);
        assert value != null;
        return Map.entry(key, value);
      }

      @Override
      @TruffleBoundary
      public void remove() {
        throw new UnsupportedOperationException("remove");
      }

      @Override
      @TruffleBoundary
      public void forEachRemaining(Consumer<? super Map.Entry<Object, Object>> action) {
        throw new UnsupportedOperationException("forEachRemaining");
      }
    };
  }

  @TruffleBoundary
  public @Nullable Object getOrNull(Object key) {
    return map.get(key);
  }

  @TruffleBoundary
  public Object getOrVmNull(Object key) {
    var result = map.get(key);
    return result != null ? result : VmNull.withoutDefault();
  }

  @TruffleBoundary
  public int getLength() {
    return keyOrder.size();
  }

  @TruffleBoundary
  public boolean isEmpty() {
    return keyOrder.isEmpty();
  }

  @TruffleBoundary
  public VmMap concatenate(VmMap other) {
    if (other.isEmpty()) return this;

    var mapBuilder = map.mutable();
    var keyOrderBuilder = keyOrder.mutable();

    for (var key : other.keyOrder) {
      var value = other.map.get(key);

      if (!map.containsKey(key)) {
        keyOrderBuilder.append(key);
      }
      mapBuilder.assoc(key, value);
    }

    return VmMap.create(mapBuilder.immutable(), keyOrderBuilder.immutable());
  }

  @TruffleBoundary
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @TruffleBoundary
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @TruffleBoundary
  public VmMap put(Object key, Object value) {
    var newKeyOrder = map.containsKey(key) ? keyOrder : keyOrder.append(key);
    return new VmMap(map.assoc(key, value), newKeyOrder);
  }

  @TruffleBoundary
  public VmMap remove(Object key) {
    if (!map.containsKey(key)) return this;
    return new VmMap(
        map.without(key), keyOrder.without(keyOrder.indexOf(key))); // `indexOf` is in O(n)
  }

  @TruffleBoundary
  public VmSet keys() {
    return VmSet.create(map.keySet(), keyOrder);
  }

  @TruffleBoundary
  public VmList values() {
    var builder = VmList.EMPTY.builder();
    for (var key : keyOrder) {
      var value = map.get(key);
      assert value != null;
      builder.add(value);
    }
    return builder.build();
  }

  @TruffleBoundary
  public VmList entries() {
    var builder = VmList.EMPTY.builder();
    for (var key : keyOrder) {
      var value = map.get(key);
      assert value != null;
      builder.add(new VmPair(key, value));
    }
    return builder.build();
  }

  @Override
  @TruffleBoundary
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    try {
      for (var elem : map) {
        VmValue.force(elem.getKey(), allowUndefinedValues);
        VmValue.force(elem.getValue(), allowUndefinedValues);
      }
    } catch (Throwable t) {
      forced = false;
      throw t;
    }
  }

  public VmMapping toMapping() {
    var builder = new VmObjectBuilder(getLength());
    for (var entry : this) {
      builder.addEntry(VmUtils.getKey(entry), VmUtils.getValue(entry));
    }
    return builder.toMapping();
  }

  public VmDynamic toDynamic() {
    var builder = new VmObjectBuilder(getLength());
    for (var entry : this) {
      var key = VmUtils.getKey(entry);
      var value = VmUtils.getValue(entry);
      if (key instanceof String) {
        builder.addProperty(Identifier.get((String) key), value);
      } else {
        builder.addEntry(key, value);
      }
    }
    return builder.toDynamic();
  }

  @Override
  @TruffleBoundary
  public Map<Object, Object> export() {
    var result = CollectionUtils.newLinkedHashMap(keyOrder.size());
    for (var key : keyOrder) {
      var value = map.get(key);
      assert value != null;
      result.put(VmValue.export(key), VmValue.export(value));
    }
    return result;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitMap(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertMap(this, path);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object other) {
    if (this == other) return true;
    //noinspection SimplifiableIfStatement
    if (!(other instanceof VmMap vmMap)) return false;
    return map.equals(vmMap.map);
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    return map.hashCode();
  }

  @TruffleBoundary
  public String toString() {
    return VmValueRenderer.singleLine(Integer.MAX_VALUE).render(this);
  }

  public static final class Builder {
    private final MutMap<Object, Object> mapBuilder;
    private final MutRrbt<Object> keyOrderBuilder;

    @TruffleBoundary
    private Builder(VmMap map) {
      mapBuilder = map.map.mutable();
      keyOrderBuilder = map.keyOrder.mutable();
    }

    @TruffleBoundary
    private Builder() {
      mapBuilder = PersistentHashMap.emptyMutable();
      keyOrderBuilder = RrbTree.emptyMutable();
    }

    @TruffleBoundary
    public void add(Object key, Object value) {
      if (!mapBuilder.containsKey(key)) {
        keyOrderBuilder.append(key);
      }
      mapBuilder.assoc(key, value);
    }

    @TruffleBoundary
    public @Nullable Object get(Object key) {
      return mapBuilder.get(key);
    }

    @TruffleBoundary
    public VmMap build() {
      if (mapBuilder.isEmpty()) return EMPTY;
      return VmMap.create(mapBuilder.immutable(), keyOrderBuilder.immutable());
    }
  }
}
