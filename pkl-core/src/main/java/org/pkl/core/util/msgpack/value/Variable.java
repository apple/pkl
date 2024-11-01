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
package org.pkl.core.util.msgpack.value;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.pkl.core.util.msgpack.core.MessageFormat;
import org.pkl.core.util.msgpack.core.MessageIntegerOverflowException;
import org.pkl.core.util.msgpack.core.MessagePack;
import org.pkl.core.util.msgpack.core.MessagePacker;
import org.pkl.core.util.msgpack.core.MessageStringCodingException;
import org.pkl.core.util.msgpack.core.MessageTypeCastException;
import org.pkl.core.util.msgpack.value.impl.ImmutableBigIntegerValueImpl;

public class Variable implements Value {
  private abstract class AbstractValueAccessor implements Value {
    @Override
    public boolean isNilValue() {
      return getValueType().isNilType();
    }

    @Override
    public boolean isBooleanValue() {
      return getValueType().isBooleanType();
    }

    @Override
    public boolean isNumberValue() {
      return getValueType().isNumberType();
    }

    @Override
    public boolean isIntegerValue() {
      return getValueType().isIntegerType();
    }

    @Override
    public boolean isFloatValue() {
      return getValueType().isFloatType();
    }

    @Override
    public boolean isRawValue() {
      return getValueType().isRawType();
    }

    @Override
    public boolean isBinaryValue() {
      return getValueType().isBinaryType();
    }

    @Override
    public boolean isStringValue() {
      return getValueType().isStringType();
    }

    @Override
    public boolean isArrayValue() {
      return getValueType().isArrayType();
    }

    @Override
    public boolean isMapValue() {
      return getValueType().isMapType();
    }

    @Override
    public boolean isExtensionValue() {
      return getValueType().isExtensionType();
    }

    @Override
    public boolean isTimestampValue() {
      return false;
    }

    @Override
    public NilValue asNilValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public BooleanValue asBooleanValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public NumberValue asNumberValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public IntegerValue asIntegerValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public FloatValue asFloatValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public RawValue asRawValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public BinaryValue asBinaryValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public StringValue asStringValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public ArrayValue asArrayValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public MapValue asMapValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public ExtensionValue asExtensionValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public TimestampValue asTimestampValue() {
      throw new MessageTypeCastException();
    }

    @Override
    public boolean equals(Object obj) {
      return Variable.this.equals(obj);
    }

    @Override
    public int hashCode() {
      return Variable.this.hashCode();
    }

    @Override
    public String toJson() {
      return Variable.this.toJson();
    }

    @Override
    public String toString() {
      return Variable.this.toString();
    }
  }

  public static enum Type {
    NULL(ValueType.NIL),
    BOOLEAN(ValueType.BOOLEAN),
    LONG(ValueType.INTEGER),
    BIG_INTEGER(ValueType.INTEGER),
    DOUBLE(ValueType.FLOAT),
    BYTE_ARRAY(ValueType.BINARY),
    RAW_STRING(ValueType.STRING),
    LIST(ValueType.ARRAY),
    MAP(ValueType.MAP),
    EXTENSION(ValueType.EXTENSION),
    TIMESTAMP(ValueType.EXTENSION);

    private final ValueType valueType;

    private Type(ValueType valueType) {
      this.valueType = valueType;
    }

    public ValueType getValueType() {
      return valueType;
    }
  }

  private final NilValueAccessor nilAccessor = new NilValueAccessor();
  private final BooleanValueAccessor booleanAccessor = new BooleanValueAccessor();
  private final IntegerValueAccessor integerAccessor = new IntegerValueAccessor();
  private final FloatValueAccessor floatAccessor = new FloatValueAccessor();
  private final BinaryValueAccessor binaryAccessor = new BinaryValueAccessor();
  private final StringValueAccessor stringAccessor = new StringValueAccessor();
  private final ArrayValueAccessor arrayAccessor = new ArrayValueAccessor();
  private final MapValueAccessor mapAccessor = new MapValueAccessor();
  private final ExtensionValueAccessor extensionAccessor = new ExtensionValueAccessor();
  private final TimestampValueAccessor timestampAccessor = new TimestampValueAccessor();

  private Type type;

  private long longValue;
  private double doubleValue;
  private Object objectValue;

  private AbstractValueAccessor accessor;

  public Variable() {
    setNilValue();
  }

  ////
  // NilValue
  //

  public Variable setNilValue() {
    this.type = Type.NULL;
    this.accessor = nilAccessor;
    return this;
  }

  private class NilValueAccessor extends AbstractValueAccessor implements NilValue {
    @Override
    public ValueType getValueType() {
      return ValueType.NIL;
    }

    @Override
    public NilValue asNilValue() {
      return this;
    }

    @Override
    public ImmutableNilValue immutableValue() {
      return ValueFactory.newNil();
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      pk.packNil();
    }
  }

  ////
  // BooleanValue
  //

  public Variable setBooleanValue(boolean v) {
    this.type = Type.BOOLEAN;
    this.accessor = booleanAccessor;
    this.longValue = (v ? 1L : 0L);
    return this;
  }

  private class BooleanValueAccessor extends AbstractValueAccessor implements BooleanValue {
    @Override
    public ValueType getValueType() {
      return ValueType.BOOLEAN;
    }

    @Override
    public BooleanValue asBooleanValue() {
      return this;
    }

    @Override
    public ImmutableBooleanValue immutableValue() {
      return ValueFactory.newBoolean(getBoolean());
    }

    @Override
    public boolean getBoolean() {
      return longValue == 1L;
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      pk.packBoolean(longValue == 1L);
    }
  }

  ////
  // NumberValue
  // IntegerValue
  // FloatValue
  //

  private static final BigInteger LONG_MIN = BigInteger.valueOf((long) Long.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf((long) Long.MAX_VALUE);
  private static final long BYTE_MIN = (long) Byte.MIN_VALUE;
  private static final long BYTE_MAX = (long) Byte.MAX_VALUE;
  private static final long SHORT_MIN = (long) Short.MIN_VALUE;
  private static final long SHORT_MAX = (long) Short.MAX_VALUE;
  private static final long INT_MIN = (long) Integer.MIN_VALUE;
  private static final long INT_MAX = (long) Integer.MAX_VALUE;

  private abstract class AbstractNumberValueAccessor extends AbstractValueAccessor
      implements NumberValue {
    @Override
    public NumberValue asNumberValue() {
      return this;
    }

    @Override
    public byte toByte() {
      if (type == Type.BIG_INTEGER) {
        return ((BigInteger) objectValue).byteValue();
      }
      return (byte) longValue;
    }

    @Override
    public short toShort() {
      if (type == Type.BIG_INTEGER) {
        return ((BigInteger) objectValue).shortValue();
      }
      return (short) longValue;
    }

    @Override
    public int toInt() {
      if (type == Type.BIG_INTEGER) {
        return ((BigInteger) objectValue).intValue();
      }
      return (int) longValue;
    }

    @Override
    public long toLong() {
      if (type == Type.BIG_INTEGER) {
        return ((BigInteger) objectValue).longValue();
      }
      return (long) longValue;
    }

    @Override
    public BigInteger toBigInteger() {
      if (type == Type.BIG_INTEGER) {
        return (BigInteger) objectValue;
      } else if (type == Type.DOUBLE) {
        return new BigDecimal(doubleValue).toBigInteger();
      }
      return BigInteger.valueOf(longValue);
    }

    @Override
    public float toFloat() {
      if (type == Type.BIG_INTEGER) {
        return ((BigInteger) objectValue).floatValue();
      } else if (type == Type.DOUBLE) {
        return (float) doubleValue;
      }
      return (float) longValue;
    }

    @Override
    public double toDouble() {
      if (type == Type.BIG_INTEGER) {
        return ((BigInteger) objectValue).doubleValue();
      } else if (type == Type.DOUBLE) {
        return doubleValue;
      }
      return (double) longValue;
    }
  }

  ////
  // IntegerValue
  //

  public Variable setIntegerValue(long v) {
    this.type = Type.LONG;
    this.accessor = integerAccessor;
    this.longValue = v;
    return this;
  }

  public Variable setIntegerValue(BigInteger v) {
    if (0 <= v.compareTo(LONG_MIN) && v.compareTo(LONG_MAX) <= 0) {
      this.type = Type.LONG;
      this.accessor = integerAccessor;
      this.longValue = v.longValue();
    } else {
      this.type = Type.BIG_INTEGER;
      this.accessor = integerAccessor;
      this.objectValue = v;
    }
    return this;
  }

  private class IntegerValueAccessor extends AbstractNumberValueAccessor implements IntegerValue {
    @Override
    public ValueType getValueType() {
      return ValueType.INTEGER;
    }

    @Override
    public IntegerValue asIntegerValue() {
      return this;
    }

    @Override
    public ImmutableIntegerValue immutableValue() {
      if (type == Type.BIG_INTEGER) {
        return ValueFactory.newInteger((BigInteger) objectValue);
      }
      return ValueFactory.newInteger(longValue);
    }

    @Override
    public boolean isInByteRange() {
      if (type == Type.BIG_INTEGER) {
        return false;
      }
      return BYTE_MIN <= longValue && longValue <= BYTE_MAX;
    }

    @Override
    public boolean isInShortRange() {
      if (type == Type.BIG_INTEGER) {
        return false;
      }
      return SHORT_MIN <= longValue && longValue <= SHORT_MAX;
    }

    @Override
    public boolean isInIntRange() {
      if (type == Type.BIG_INTEGER) {
        return false;
      }
      return INT_MIN <= longValue && longValue <= INT_MAX;
    }

    @Override
    public boolean isInLongRange() {
      if (type == Type.BIG_INTEGER) {
        return false;
      }
      return true;
    }

    @Override
    public MessageFormat mostSuccinctMessageFormat() {
      return ImmutableBigIntegerValueImpl.mostSuccinctMessageFormat(this);
    }

    @Override
    public byte asByte() {
      if (!isInByteRange()) {
        throw new MessageIntegerOverflowException(longValue);
      }
      return (byte) longValue;
    }

    @Override
    public short asShort() {
      if (!isInByteRange()) {
        throw new MessageIntegerOverflowException(longValue);
      }
      return (short) longValue;
    }

    @Override
    public int asInt() {
      if (!isInIntRange()) {
        throw new MessageIntegerOverflowException(longValue);
      }
      return (int) longValue;
    }

    @Override
    public long asLong() {
      if (!isInLongRange()) {
        throw new MessageIntegerOverflowException(longValue);
      }
      return longValue;
    }

    @Override
    public BigInteger asBigInteger() {
      if (type == Type.BIG_INTEGER) {
        return (BigInteger) objectValue;
      } else {
        return BigInteger.valueOf(longValue);
      }
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      if (type == Type.BIG_INTEGER) {
        pk.packBigInteger((BigInteger) objectValue);
      } else {
        pk.packLong(longValue);
      }
    }
  }

  ////
  // FloatValue
  //

  public Variable setFloatValue(double v) {
    this.type = Type.DOUBLE;
    this.accessor = floatAccessor;
    this.doubleValue = v;
    this.longValue = (long) v; // AbstractNumberValueAccessor uses toLong
    return this;
  }

  public Variable setFloatValue(float v) {
    this.type = Type.DOUBLE;
    this.accessor = floatAccessor;
    this.longValue = (long) v; // AbstractNumberValueAccessor uses toLong
    return this;
  }

  private class FloatValueAccessor extends AbstractNumberValueAccessor implements FloatValue {
    @Override
    public FloatValue asFloatValue() {
      return this;
    }

    @Override
    public ImmutableFloatValue immutableValue() {
      return ValueFactory.newFloat(doubleValue);
    }

    @Override
    public ValueType getValueType() {
      return ValueType.FLOAT;
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      pk.packDouble(doubleValue);
    }
  }

  ////
  // RawValue
  // BinaryValue
  // StringValue
  //

  private abstract class AbstractRawValueAccessor extends AbstractValueAccessor
      implements RawValue {
    @Override
    public RawValue asRawValue() {
      return this;
    }

    @Override
    public byte[] asByteArray() {
      return (byte[]) objectValue;
    }

    @Override
    public ByteBuffer asByteBuffer() {
      return ByteBuffer.wrap(asByteArray());
    }

    @Override
    public String asString() {
      byte[] raw = (byte[]) objectValue;
      try {
        CharsetDecoder reportDecoder =
            MessagePack.UTF8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return reportDecoder.decode(ByteBuffer.wrap(raw)).toString();
      } catch (CharacterCodingException ex) {
        throw new MessageStringCodingException(ex);
      }
    }

    // override for performance optimization
    @Override
    public String toString() {
      byte[] raw = (byte[]) objectValue;
      try {
        CharsetDecoder reportDecoder =
            MessagePack.UTF8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return reportDecoder.decode(ByteBuffer.wrap(raw)).toString();
      } catch (CharacterCodingException ex) {
        throw new MessageStringCodingException(ex);
      }
    }
  }

  ////
  // BinaryValue
  //

  public Variable setBinaryValue(byte[] v) {
    this.type = Type.BYTE_ARRAY;
    this.accessor = binaryAccessor;
    this.objectValue = v;
    return this;
  }

  private class BinaryValueAccessor extends AbstractRawValueAccessor implements BinaryValue {
    @Override
    public ValueType getValueType() {
      return ValueType.BINARY;
    }

    @Override
    public BinaryValue asBinaryValue() {
      return this;
    }

    @Override
    public ImmutableBinaryValue immutableValue() {
      return ValueFactory.newBinary(asByteArray());
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      byte[] data = (byte[]) objectValue;
      pk.packBinaryHeader(data.length);
      pk.writePayload(data);
    }
  }

  ////
  // StringValue
  //

  public Variable setStringValue(String v) {
    return setStringValue(v.getBytes(MessagePack.UTF8));
  }

  public Variable setStringValue(byte[] v) {
    this.type = Type.RAW_STRING;
    this.accessor = stringAccessor;
    this.objectValue = v;
    return this;
  }

  private class StringValueAccessor extends AbstractRawValueAccessor implements StringValue {
    @Override
    public ValueType getValueType() {
      return ValueType.STRING;
    }

    @Override
    public StringValue asStringValue() {
      return this;
    }

    @Override
    public ImmutableStringValue immutableValue() {
      return ValueFactory.newString((byte[]) objectValue);
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      byte[] data = (byte[]) objectValue;
      pk.packRawStringHeader(data.length);
      pk.writePayload(data);
    }
  }

  ////
  // ArrayValue
  //

  public Variable setArrayValue(List<Value> v) {
    this.type = Type.LIST;
    this.accessor = arrayAccessor;
    this.objectValue = v.toArray(new Value[v.size()]);
    return this;
  }

  public Variable setArrayValue(Value[] v) {
    this.type = Type.LIST;
    this.accessor = arrayAccessor;
    this.objectValue = v;
    return this;
  }

  private class ArrayValueAccessor extends AbstractValueAccessor implements ArrayValue {
    @Override
    public ValueType getValueType() {
      return ValueType.ARRAY;
    }

    @Override
    public ArrayValue asArrayValue() {
      return this;
    }

    @Override
    public ImmutableArrayValue immutableValue() {
      return ValueFactory.newArray(array());
    }

    @Override
    public int size() {
      return array().length;
    }

    @Override
    public Value get(int index) {
      return array()[index];
    }

    @Override
    public Value getOrNilValue(int index) {
      Value[] a = array();
      if (a.length < index && index >= 0) {
        return ValueFactory.newNil();
      }
      return a[index];
    }

    @Override
    public Iterator<Value> iterator() {
      return list().iterator();
    }

    @Override
    public List<Value> list() {
      return Arrays.asList(array());
    }

    public Value[] array() {
      return (Value[]) objectValue;
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      immutableValue().writeTo(pk);
    }
  }

  ////
  // MapValue
  //

  public Variable setMapValue(Map<Value, Value> v) {
    this.type = Type.MAP;
    this.accessor = mapAccessor;
    Value[] kvs = new Value[v.size() * 2];
    Iterator<Map.Entry<Value, Value>> ite = v.entrySet().iterator();
    int i = 0;
    while (ite.hasNext()) {
      Map.Entry<Value, Value> pair = ite.next();
      kvs[i] = pair.getKey();
      i++;
      kvs[i] = pair.getValue();
      i++;
    }
    this.objectValue = kvs;
    return this;
  }

  public Variable setMapValue(Value[] kvs) {
    this.type = Type.MAP;
    this.accessor = mapAccessor;
    this.objectValue = kvs;
    return this;
  }

  private class MapValueAccessor extends AbstractValueAccessor implements MapValue {
    @Override
    public ValueType getValueType() {
      return ValueType.MAP;
    }

    @Override
    public MapValue asMapValue() {
      return this;
    }

    @Override
    public ImmutableMapValue immutableValue() {
      return ValueFactory.newMap(getKeyValueArray());
    }

    @Override
    public int size() {
      return getKeyValueArray().length / 2;
    }

    @Override
    public Set<Value> keySet() {
      return immutableValue().keySet();
    }

    @Override
    public Set<Map.Entry<Value, Value>> entrySet() {
      return immutableValue().entrySet();
    }

    @Override
    public Collection<Value> values() {
      return immutableValue().values();
    }

    @Override
    public Value[] getKeyValueArray() {
      return (Value[]) objectValue;
    }

    public Map<Value, Value> map() {
      return immutableValue().map();
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      immutableValue().writeTo(pk);
    }
  }

  ////
  // ExtensionValue
  //
  public Variable setExtensionValue(byte type, byte[] data) {
    this.type = Type.EXTENSION;
    this.accessor = extensionAccessor;
    this.objectValue = ValueFactory.newExtension(type, data);
    return this;
  }

  private class ExtensionValueAccessor extends AbstractValueAccessor implements ExtensionValue {
    @Override
    public ValueType getValueType() {
      return ValueType.EXTENSION;
    }

    @Override
    public ExtensionValue asExtensionValue() {
      return this;
    }

    @Override
    public ImmutableExtensionValue immutableValue() {
      return (ImmutableExtensionValue) objectValue;
    }

    @Override
    public byte getType() {
      return ((ImmutableExtensionValue) objectValue).getType();
    }

    @Override
    public byte[] getData() {
      return ((ImmutableExtensionValue) objectValue).getData();
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      ((ImmutableExtensionValue) objectValue).writeTo(pk);
    }
  }

  public Variable setTimestampValue(Instant timestamp) {
    this.type = Type.TIMESTAMP;
    this.accessor = timestampAccessor;
    this.objectValue = ValueFactory.newTimestamp(timestamp);
    return this;
  }

  private class TimestampValueAccessor extends AbstractValueAccessor implements TimestampValue {
    @Override
    public boolean isTimestampValue() {
      return true;
    }

    @Override
    public ValueType getValueType() {
      return ValueType.EXTENSION;
    }

    @Override
    public TimestampValue asTimestampValue() {
      return this;
    }

    @Override
    public ImmutableTimestampValue immutableValue() {
      return (ImmutableTimestampValue) objectValue;
    }

    @Override
    public byte getType() {
      return ((ImmutableTimestampValue) objectValue).getType();
    }

    @Override
    public byte[] getData() {
      return ((ImmutableTimestampValue) objectValue).getData();
    }

    @Override
    public void writeTo(MessagePacker pk) throws IOException {
      ((ImmutableTimestampValue) objectValue).writeTo(pk);
    }

    @Override
    public long getEpochSecond() {
      return ((ImmutableTimestampValue) objectValue).getEpochSecond();
    }

    @Override
    public int getNano() {
      return ((ImmutableTimestampValue) objectValue).getNano();
    }

    @Override
    public long toEpochMillis() {
      return ((ImmutableTimestampValue) objectValue).toEpochMillis();
    }

    @Override
    public Instant toInstant() {
      return ((ImmutableTimestampValue) objectValue).toInstant();
    }
  }

  ////
  // Value
  //

  @Override
  public ImmutableValue immutableValue() {
    return accessor.immutableValue();
  }

  @Override
  public void writeTo(MessagePacker pk) throws IOException {
    accessor.writeTo(pk);
  }

  @Override
  public int hashCode() {
    return immutableValue().hashCode(); // TODO optimize
  }

  @Override
  public boolean equals(Object o) {
    return immutableValue().equals(o); // TODO optimize
  }

  @Override
  public String toJson() {
    return immutableValue().toJson(); // TODO optimize
  }

  @Override
  public String toString() {
    return immutableValue().toString(); // TODO optimize
  }

  @Override
  public ValueType getValueType() {
    return type.getValueType();
  }

  @Override
  public boolean isNilValue() {
    return getValueType().isNilType();
  }

  @Override
  public boolean isBooleanValue() {
    return getValueType().isBooleanType();
  }

  @Override
  public boolean isNumberValue() {
    return getValueType().isNumberType();
  }

  @Override
  public boolean isIntegerValue() {
    return getValueType().isIntegerType();
  }

  @Override
  public boolean isFloatValue() {
    return getValueType().isFloatType();
  }

  @Override
  public boolean isRawValue() {
    return getValueType().isRawType();
  }

  @Override
  public boolean isBinaryValue() {
    return getValueType().isBinaryType();
  }

  @Override
  public boolean isStringValue() {
    return getValueType().isStringType();
  }

  @Override
  public boolean isArrayValue() {
    return getValueType().isArrayType();
  }

  @Override
  public boolean isMapValue() {
    return getValueType().isMapType();
  }

  @Override
  public boolean isExtensionValue() {
    return getValueType().isExtensionType();
  }

  @Override
  public boolean isTimestampValue() {
    return this.type == Type.TIMESTAMP;
  }

  @Override
  public NilValue asNilValue() {
    if (!isNilValue()) {
      throw new MessageTypeCastException();
    }
    return (NilValue) accessor;
  }

  @Override
  public BooleanValue asBooleanValue() {
    if (!isBooleanValue()) {
      throw new MessageTypeCastException();
    }
    return (BooleanValue) accessor;
  }

  @Override
  public NumberValue asNumberValue() {
    if (!isNumberValue()) {
      throw new MessageTypeCastException();
    }
    return (NumberValue) accessor;
  }

  @Override
  public IntegerValue asIntegerValue() {
    if (!isIntegerValue()) {
      throw new MessageTypeCastException();
    }
    return (IntegerValue) accessor;
  }

  @Override
  public FloatValue asFloatValue() {
    if (!isFloatValue()) {
      throw new MessageTypeCastException();
    }
    return (FloatValue) accessor;
  }

  @Override
  public RawValue asRawValue() {
    if (!isRawValue()) {
      throw new MessageTypeCastException();
    }
    return (RawValue) accessor;
  }

  @Override
  public BinaryValue asBinaryValue() {
    if (!isBinaryValue()) {
      throw new MessageTypeCastException();
    }
    return (BinaryValue) accessor;
  }

  @Override
  public StringValue asStringValue() {
    if (!isStringValue()) {
      throw new MessageTypeCastException();
    }
    return (StringValue) accessor;
  }

  @Override
  public ArrayValue asArrayValue() {
    if (!isArrayValue()) {
      throw new MessageTypeCastException();
    }
    return (ArrayValue) accessor;
  }

  @Override
  public MapValue asMapValue() {
    if (!isMapValue()) {
      throw new MessageTypeCastException();
    }
    return (MapValue) accessor;
  }

  @Override
  public ExtensionValue asExtensionValue() {
    if (!isExtensionValue()) {
      throw new MessageTypeCastException();
    }
    return (ExtensionValue) accessor;
  }

  @Override
  public TimestampValue asTimestampValue() {
    if (!isTimestampValue()) {
      throw new MessageTypeCastException();
    }
    return (TimestampValue) accessor;
  }
}
