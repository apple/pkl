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
import java.math.BigDecimal;
import java.math.BigInteger;
import org.pkl.core.util.msgpack.core.MessagePacker;
import org.pkl.core.util.msgpack.value.FloatValue;
import org.pkl.core.util.msgpack.value.ImmutableFloatValue;
import org.pkl.core.util.msgpack.value.ImmutableNumberValue;
import org.pkl.core.util.msgpack.value.Value;
import org.pkl.core.util.msgpack.value.ValueType;

/**
 * {@code ImmutableDoubleValueImpl} Implements {@code ImmutableFloatValue} using a {@code double}
 * field.
 *
 * @see FloatValue
 */
public class ImmutableDoubleValueImpl extends AbstractImmutableValue
    implements ImmutableFloatValue {
  private final double value;

  public ImmutableDoubleValueImpl(double value) {
    this.value = value;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.FLOAT;
  }

  @Override
  public ImmutableDoubleValueImpl immutableValue() {
    return this;
  }

  @Override
  public ImmutableNumberValue asNumberValue() {
    return this;
  }

  @Override
  public ImmutableFloatValue asFloatValue() {
    return this;
  }

  @Override
  public byte toByte() {
    return (byte) value;
  }

  @Override
  public short toShort() {
    return (short) value;
  }

  @Override
  public int toInt() {
    return (int) value;
  }

  @Override
  public long toLong() {
    return (long) value;
  }

  @Override
  public BigInteger toBigInteger() {
    return new BigDecimal(value).toBigInteger();
  }

  @Override
  public float toFloat() {
    return (float) value;
  }

  @Override
  public double toDouble() {
    return value;
  }

  @Override
  public void writeTo(MessagePacker pk) throws IOException {
    pk.packDouble(value);
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

    if (!v.isFloatValue()) {
      return false;
    }
    return value == v.asFloatValue().toDouble();
  }

  @Override
  public int hashCode() {
    long v = Double.doubleToLongBits(value);
    return (int) (v ^ (v >>> 32));
  }

  @Override
  public String toJson() {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "null";
    } else {
      return Double.toString(value);
    }
  }

  @Override
  public String toString() {
    return Double.toString(value);
  }
}
