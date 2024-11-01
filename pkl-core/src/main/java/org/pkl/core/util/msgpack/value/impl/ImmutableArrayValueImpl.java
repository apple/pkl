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
package org.pkl.core.util.msgpack.value.impl;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.pkl.core.util.msgpack.core.MessagePacker;
import org.pkl.core.util.msgpack.value.ArrayValue;
import org.pkl.core.util.msgpack.value.ImmutableArrayValue;
import org.pkl.core.util.msgpack.value.IntegerValue;
import org.pkl.core.util.msgpack.value.Value;
import org.pkl.core.util.msgpack.value.ValueType;

/**
 * {@code ImmutableArrayValueImpl} Implements {@code ImmutableArrayValue} using a {@code Value[]}
 * field.
 *
 * @see IntegerValue
 */
public class ImmutableArrayValueImpl extends AbstractImmutableValue implements ImmutableArrayValue {
  private static final ImmutableArrayValueImpl EMPTY = new ImmutableArrayValueImpl(new Value[0]);

  public static ImmutableArrayValue empty() {
    return EMPTY;
  }

  private final Value[] array;

  public ImmutableArrayValueImpl(Value[] array) {
    this.array = array;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.ARRAY;
  }

  @Override
  public ImmutableArrayValue immutableValue() {
    return this;
  }

  @Override
  public ImmutableArrayValue asArrayValue() {
    return this;
  }

  @Override
  public int size() {
    return array.length;
  }

  @Override
  public Value get(int index) {
    return array[index];
  }

  @Override
  public Value getOrNilValue(int index) {
    if (index < array.length && index >= 0) {
      return array[index];
    }
    return ImmutableNilValueImpl.get();
  }

  @Override
  public Iterator<Value> iterator() {
    return new Ite(array);
  }

  @Override
  public List<Value> list() {
    return new ImmutableArrayValueList(array);
  }

  @Override
  public void writeTo(MessagePacker pk) throws IOException {
    pk.packArrayHeader(array.length);
    for (int i = 0; i < array.length; i++) {
      array[i].writeTo(pk);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Value)) {
      return false;
    }
    Value v = (Value) o;

    if (v instanceof ImmutableArrayValueImpl) {
      ImmutableArrayValueImpl oa = (ImmutableArrayValueImpl) v;
      return Arrays.equals(array, oa.array);
    } else {
      if (!v.isArrayValue()) {
        return false;
      }
      ArrayValue av = v.asArrayValue();
      if (size() != av.size()) {
        return false;
      }
      Iterator<Value> oi = av.iterator();
      int i = 0;
      while (i < array.length) {
        if (!oi.hasNext() || !array[i].equals(oi.next())) {
          return false;
        }
        i++;
      }
      return true;
    }
  }

  @Override
  public int hashCode() {
    int h = 1;
    for (int i = 0; i < array.length; i++) {
      Value obj = array[i];
      h = 31 * h + obj.hashCode();
    }
    return h;
  }

  @Override
  public String toJson() {
    if (array.length == 0) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append(array[0].toJson());
    for (int i = 1; i < array.length; i++) {
      sb.append(",");
      sb.append(array[i].toJson());
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public String toString() {
    if (array.length == 0) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    appendString(sb, array[0]);
    for (int i = 1; i < array.length; i++) {
      sb.append(",");
      appendString(sb, array[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  private static void appendString(StringBuilder sb, Value value) {
    if (value.isRawValue()) {
      sb.append(value.toJson());
    } else {
      sb.append(value.toString());
    }
  }

  private static class ImmutableArrayValueList extends AbstractList<Value> {
    private final Value[] array;

    public ImmutableArrayValueList(Value[] array) {
      this.array = array;
    }

    @Override
    public Value get(int index) {
      return array[index];
    }

    @Override
    public int size() {
      return array.length;
    }
  }

  private static class Ite implements Iterator<Value> {
    private final Value[] array;
    private int index;

    public Ite(Value[] array) {
      this.array = array;
      this.index = 0;
    }

    @Override
    public boolean hasNext() {
      return index != array.length;
    }

    @Override
    public Value next() {
      int i = index;
      if (i >= array.length) {
        throw new NoSuchElementException();
      }
      index = i + 1;
      return array[i];
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
