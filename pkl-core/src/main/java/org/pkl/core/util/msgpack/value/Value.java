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
import org.pkl.core.util.msgpack.core.MessagePacker;
import org.pkl.core.util.msgpack.core.MessageTypeCastException;

/**
 * Value stores a value and its type in MessagePack type system.
 *
 * <h2>Type conversion</h2>
 *
 * <p>You can check type first using <b>isXxx()</b> methods or {@link #getValueType()} method, then
 * convert the value to a subtype using <b>asXxx()</b> methods. You can also call asXxx() methods
 * directly and catch {@link MessageTypeCastException}.
 *
 * <table>
 *   <tr><th>MessagePack type</th><th>Check method</th><th>Convert method</th><th>Value type</th></tr>
 *   <tr><td>Nil</td><td>{@link #isNilValue()}</td><td>{@link #asNumberValue()}</td><td>{@link NilValue}</td></tr>
 *   <tr><td>Boolean</td><td>{@link #isBooleanValue()}</td><td>{@link #asBooleanValue()}</td><td>{@link BooleanValue}</td></tr>
 *   <tr><td>Integer or Float</td><td>{@link #isNumberValue()}</td><td>{@link #asNumberValue()}</td><td>{@link NumberValue}</td></tr>
 *   <tr><td>Integer</td><td>{@link #isIntegerValue()}</td><td>{@link #asIntegerValue()}</td><td>{@link IntegerValue}</td></tr>
 *   <tr><td>Float</td><td>{@link #isFloatValue()}</td><td>{@link #asFloatValue()}</td><td>{@link FloatValue}</td></tr>
 *   <tr><td>String or Binary</td><td>{@link #isRawValue()}</td><td>{@link #asRawValue()}</td><td>{@link RawValue}</td></tr>
 *   <tr><td>String</td><td>{@link #isStringValue()}</td><td>{@link #asStringValue()}</td><td>{@link StringValue}</td></tr>
 *   <tr><td>Binary</td><td>{@link #isBinaryValue()}</td><td>{@link #asBinaryValue()}</td><td>{@link BinaryValue}</td></tr>
 *   <tr><td>Array</td><td>{@link #isArrayValue()}</td><td>{@link #asArrayValue()}</td><td>{@link ArrayValue}</td></tr>
 *   <tr><td>Map</td><td>{@link #isMapValue()}</td><td>{@link #asMapValue()}</td><td>{@link MapValue}</td></tr>
 *   <tr><td>Extension</td><td>{@link #isExtensionValue()}</td><td>{@link #asExtensionValue()}</td><td>{@link ExtensionValue}</td></tr>
 * </table>
 *
 * <h2>Immutable interface</h2>
 *
 * <p>Value interface is the base interface of all Value interfaces. Immutable subtypes are useful
 * so that you can declare that a (final) field or elements of a container object are immutable.
 * Method arguments should be a regular Value interface generally.
 *
 * <p>You can use {@link #immutableValue()} method to get immutable subtypes.
 *
 * <table>
 *   <tr><th>MessagePack type</th><th>Subtype method</th><th>Immutable value type</th></tr>
 *   <tr><td>any types</td><td>{@link Value}.{@link Value#immutableValue()}</td><td>{@link ImmutableValue}</td></tr>
 *   <tr><td>Nil</td><td>{@link NilValue}.{@link NilValue#immutableValue()}</td><td>{@link ImmutableNilValue}</td></tr>
 *   <tr><td>Boolean</td><td>{@link BooleanValue}.{@link BooleanValue#immutableValue()}</td><td>{@link ImmutableBooleanValue}</td></tr>
 *   <tr><td>Integer</td><td>{@link IntegerValue}.{@link IntegerValue#immutableValue()}</td><td>{@link ImmutableIntegerValue}</td></tr>
 *   <tr><td>Float</td><td>{@link FloatValue}.{@link FloatValue#immutableValue()}</td><td>{@link ImmutableFloatValue}</td></tr>
 *   <tr><td>Integer or Float</td><td>{@link NumberValue}.{@link NumberValue#immutableValue()}</td><td>{@link ImmutableNumberValue}</td></tr>
 *   <tr><td>String or Binary</td><td>{@link RawValue}.{@link RawValue#immutableValue()}</td><td>{@link ImmutableRawValue}</td></tr>
 *   <tr><td>String</td><td>{@link StringValue}.{@link StringValue#immutableValue()}</td><td>{@link ImmutableStringValue}</td></tr>
 *   <tr><td>Binary</td><td>{@link BinaryValue}.{@link BinaryValue#immutableValue()}</td><td>{@link ImmutableBinaryValue}</td></tr>
 *   <tr><td>Array</td><td>{@link ArrayValue}.{@link ArrayValue#immutableValue()}</td><td>{@link ImmutableArrayValue}</td></tr>
 *   <tr><td>Map</td><td>{@link MapValue}.{@link MapValue#immutableValue()}</td><td>{@link ImmutableMapValue}</td></tr>
 *   <tr><td>Extension</td><td>{@link ExtensionValue}.{@link ExtensionValue#immutableValue()}</td><td>{@link ImmutableExtensionValue}</td></tr>
 * </table>
 *
 * <h2>Converting to JSON</h2>
 *
 * <p>{@link #toJson()} method returns JSON representation of a Value. See its documents for
 * details.
 *
 * <p>toString() also returns a string representation of a Value that is similar to JSON. However,
 * unlike toJson() method, toString() may return a special format that is not be compatible with
 * JSON when JSON doesn't support the type such as ExtensionValue.
 */
public interface Value {
  /**
   * Returns type of this value.
   *
   * <p>Note that you can't use <code>instanceof</code> to check type of a value because type of a
   * mutable value is variable.
   */
  ValueType getValueType();

  /**
   * Returns immutable copy of this value.
   *
   * <p>This method simply returns <code>this</code> without copying the value if this value is
   * already immutable.
   */
  ImmutableValue immutableValue();

  /**
   * Returns true if type of this value is Nil.
   *
   * <p>If this method returns true, {@code asNilValue} never throws exceptions. Note that you can't
   * use <code>instanceof</code> or cast <code>((NilValue) thisValue)</code> to check type of a
   * value because type of a mutable value is variable.
   */
  boolean isNilValue();

  /**
   * Returns true if type of this value is Boolean.
   *
   * <p>If this method returns true, {@code asBooleanValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((BooleanValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isBooleanValue();

  /**
   * Returns true if type of this value is Integer or Float.
   *
   * <p>If this method returns true, {@code asNumberValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((NumberValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isNumberValue();

  /**
   * Returns true if type of this value is Integer.
   *
   * <p>If this method returns true, {@code asIntegerValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((IntegerValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isIntegerValue();

  /**
   * Returns true if type of this value is Float.
   *
   * <p>If this method returns true, {@code asFloatValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((FloatValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isFloatValue();

  /**
   * Returns true if type of this value is String or Binary.
   *
   * <p>If this method returns true, {@code asRawValue} never throws exceptions. Note that you can't
   * use <code>instanceof</code> or cast <code>((RawValue) thisValue)</code> to check type of a
   * value because type of a mutable value is variable.
   */
  boolean isRawValue();

  /**
   * Returns true if type of this value is Binary.
   *
   * <p>If this method returns true, {@code asBinaryValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((BinaryValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isBinaryValue();

  /**
   * Returns true if type of this value is String.
   *
   * <p>If this method returns true, {@code asStringValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((StringValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isStringValue();

  /**
   * Returns true if type of this value is Array.
   *
   * <p>If this method returns true, {@code asArrayValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((ArrayValue) thisValue)</code> to check type
   * of a value because type of a mutable value is variable.
   */
  boolean isArrayValue();

  /**
   * Returns true if type of this value is Map.
   *
   * <p>If this method returns true, {@code asMapValue} never throws exceptions. Note that you can't
   * use <code>instanceof</code> or cast <code>((MapValue) thisValue)</code> to check type of a
   * value because type of a mutable value is variable.
   */
  boolean isMapValue();

  /**
   * Returns true if type of this an Extension.
   *
   * <p>If this method returns true, {@code asExtensionValue} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((ExtensionValue) thisValue)</code> to check
   * type of a value because type of a mutable value is variable.
   */
  boolean isExtensionValue();

  /**
   * Returns true if the type of this value is Timestamp.
   *
   * <p>If this method returns true, {@code asTimestamp} never throws exceptions. Note that you
   * can't use <code>instanceof</code> or cast <code>((MapValue) thisValue)</code> to check type of
   * a value because type of a mutable value is variable.
   */
  boolean isTimestampValue();

  /**
   * Returns the value as {@code NilValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((NilValue) thisValue)</code>
   * to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Nil.
   */
  NilValue asNilValue();

  /**
   * Returns the value as {@code BooleanValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((BooleanValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Boolean.
   */
  BooleanValue asBooleanValue();

  /**
   * Returns the value as {@code NumberValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((NumberValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Integer or Float.
   */
  NumberValue asNumberValue();

  /**
   * Returns the value as {@code IntegerValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((IntegerValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Integer.
   */
  IntegerValue asIntegerValue();

  /**
   * Returns the value as {@code FloatValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((FloatValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Float.
   */
  FloatValue asFloatValue();

  /**
   * Returns the value as {@code RawValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((RawValue) thisValue)</code>
   * to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Binary or String.
   */
  RawValue asRawValue();

  /**
   * Returns the value as {@code BinaryValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((BinaryValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Binary.
   */
  BinaryValue asBinaryValue();

  /**
   * Returns the value as {@code StringValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((StringValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not String.
   */
  StringValue asStringValue();

  /**
   * Returns the value as {@code ArrayValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((ArrayValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Array.
   */
  ArrayValue asArrayValue();

  /**
   * Returns the value as {@code MapValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((MapValue) thisValue)</code>
   * to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Map.
   */
  MapValue asMapValue();

  /**
   * Returns the value as {@code ExtensionValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((ExtensionValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not an Extension.
   */
  ExtensionValue asExtensionValue();

  /**
   * Returns the value as {@code TimestampValue}. Otherwise throws {@code MessageTypeCastException}.
   *
   * <p>Note that you can't use <code>instanceof</code> or cast <code>((TimestampValue) thisValue)
   * </code> to check type of a value because type of a mutable value is variable.
   *
   * @throws MessageTypeCastException If type of this value is not Map.
   */
  TimestampValue asTimestampValue();

  /**
   * Serializes the value using the specified {@code MessagePacker}
   *
   * @see MessagePacker
   */
  void writeTo(MessagePacker pk) throws IOException;

  /**
   * Compares this value to the specified object.
   *
   * <p>This method returns {@code true} if type and value are equivalent. If this value is {@code
   * MapValue} or {@code ArrayValue}, this method check equivalence of elements recursively.
   */
  boolean equals(Object obj);

  /**
   * Returns json representation of this Value.
   *
   * <p>Following behavior is not configurable at this release and they might be changed at future
   * releases:
   *
   * <ul>
   *   <li>if a key of MapValue is not string, the key is converted to a string using toString
   *       method.
   *   <li>NaN and Infinity of DoubleValue are converted to null.
   *   <li>ExtensionValue is converted to a 2-element array where first element is a number and
   *       second element is the data encoded in hex.
   *   <li>BinaryValue is converted to a string using UTF-8 encoding. Invalid byte sequence is
   *       replaced with <code>U+FFFD replacement character</code>.
   *   <li>Invalid UTF-8 byte sequences in StringValue is replaced with <code>
   *       U+FFFD replacement character</code>
   *       <ul>
   */
  String toJson();
}
