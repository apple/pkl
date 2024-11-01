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
import org.pkl.core.util.msgpack.value.ImmutableBinaryValue;
import org.pkl.core.util.msgpack.value.StringValue;
import org.pkl.core.util.msgpack.value.Value;
import org.pkl.core.util.msgpack.value.ValueType;

/**
 * {@code ImmutableBinaryValueImpl} Implements {@code ImmutableBinaryValue} using a {@code byte[]}
 * field. This implementation caches result of {@code toString()} and {@code asString()} using a
 * private {@code String} field.
 *
 * @see StringValue
 */
public class ImmutableBinaryValueImpl extends AbstractImmutableRawValue
    implements ImmutableBinaryValue {
  public ImmutableBinaryValueImpl(byte[] data) {
    super(data);
  }

  @Override
  public ValueType getValueType() {
    return ValueType.BINARY;
  }

  @Override
  public ImmutableBinaryValue immutableValue() {
    return this;
  }

  @Override
  public ImmutableBinaryValue asBinaryValue() {
    return this;
  }

  @Override
  public void writeTo(MessagePacker pk) throws IOException {
    pk.packBinaryHeader(data.length);
    pk.writePayload(data);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Value)) {
      return false;
    }
    Value v = (Value) o;
    if (!v.isBinaryValue()) {
      return false;
    }

    if (v instanceof ImmutableBinaryValueImpl) {
      ImmutableBinaryValueImpl bv = (ImmutableBinaryValueImpl) v;
      return Arrays.equals(data, bv.data);
    } else {
      return Arrays.equals(data, v.asBinaryValue().asByteArray());
    }
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }
}
