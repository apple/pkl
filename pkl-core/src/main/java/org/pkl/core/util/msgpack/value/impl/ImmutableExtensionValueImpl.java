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
import java.util.Arrays;
import org.pkl.core.util.msgpack.core.MessagePacker;
import org.pkl.core.util.msgpack.value.ExtensionValue;
import org.pkl.core.util.msgpack.value.ImmutableExtensionValue;
import org.pkl.core.util.msgpack.value.Value;
import org.pkl.core.util.msgpack.value.ValueType;

/**
 * {@code ImmutableExtensionValueImpl} Implements {@code ImmutableExtensionValue} using a {@code
 * byte} and a {@code byte[]} fields.
 *
 * @see ExtensionValue
 */
public class ImmutableExtensionValueImpl extends AbstractImmutableValue
    implements ImmutableExtensionValue {
  private final byte type;
  private final byte[] data;

  public ImmutableExtensionValueImpl(byte type, byte[] data) {
    this.type = type;
    this.data = data;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.EXTENSION;
  }

  @Override
  public ImmutableExtensionValue immutableValue() {
    return this;
  }

  @Override
  public ImmutableExtensionValue asExtensionValue() {
    return this;
  }

  @Override
  public byte getType() {
    return type;
  }

  @Override
  public byte[] getData() {
    return data;
  }

  @Override
  public void writeTo(MessagePacker packer) throws IOException {
    packer.packExtensionTypeHeader(type, data.length);
    packer.writePayload(data);
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

    if (!v.isExtensionValue()) {
      return false;
    }
    ExtensionValue ev = v.asExtensionValue();
    return type == ev.getType() && Arrays.equals(data, ev.getData());
  }

  @Override
  public int hashCode() {
    int hash = 31 + type;
    for (byte e : data) {
      hash = 31 * hash + e;
    }
    return hash;
  }

  @Override
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    sb.append(Byte.toString(type));
    sb.append(",\"");
    for (byte e : data) {
      sb.append(Integer.toString((int) e, 16));
    }
    sb.append("\"]");
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('(');
    sb.append(Byte.toString(type));
    sb.append(",0x");
    for (byte e : data) {
      sb.append(Integer.toString((int) e, 16));
    }
    sb.append(")");
    return sb.toString();
  }
}
