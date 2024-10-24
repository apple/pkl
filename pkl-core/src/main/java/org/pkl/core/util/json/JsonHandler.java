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
package org.pkl.core.util.json;

import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

/**
 * A handler for parser events. Instances of this class can be given to a {@link JsonParser}. The
 * parser will then call the methods of the given handler while reading the input.
 *
 * <p>The default implementations of these methods do nothing. Subclasses may override only those
 * methods they are interested in. They can use <code>getLocation()</code> to access the current
 * character position of the parser at any point. The <code>start*</code> methods will be called
 * while the location points to the first character of the parsed element. The <code>end*</code>
 * methods will be called while the location points to the character position that directly follows
 * the last character of the parsed element. Example:
 *
 * <pre>
 * ["lorem ipsum"]
 *  ^            ^
 *  startString  endString
 * </pre>
 *
 * <p>Subclasses that build an object representation of the parsed JSON can return arbitrary handler
 * objects for JSON arrays and JSON objects in {@link #startArray()} and {@link #startObject()}.
 * These handler objects will then be provided in all subsequent parser events for this particular
 * array or object. They can be used to keep track the elements of a JSON array or object.
 *
 * @param <A> The type of handlers used for JSON arrays
 * @param <O> The type of handlers used for JSON objects
 * @see JsonParser
 */
public abstract class JsonHandler<A, O> {
  @LateInit JsonParser parser;

  /**
   * Returns the current parser location.
   *
   * @return the current parser location
   */
  protected Location getLocation() {
    return parser.getLocation();
  }

  /**
   * Indicates the beginning of a <code>null</code> literal in the JSON input. This method will be
   * called when reading the first character of the literal.
   */
  public void startNull() {}

  /**
   * Indicates the end of a <code>null</code> literal in the JSON input. This method will be called
   * after reading the last character of the literal.
   */
  public void endNull() {}

  /**
   * Indicates the beginning of a boolean literal (<code>true</code> or <code>false</code>) in the
   * JSON input. This method will be called when reading the first character of the literal.
   */
  public void startBoolean() {}

  /**
   * Indicates the end of a boolean literal (<code>true</code> or <code>false</code>) in the JSON
   * input. This method will be called after reading the last character of the literal.
   *
   * @param value the parsed boolean value
   */
  public void endBoolean(boolean value) {}

  /**
   * Indicates the beginning of a string in the JSON input. This method will be called when reading
   * the opening double quote character (<code>'&quot;'</code>).
   */
  public void startString() {}

  /**
   * Indicates the end of a string in the JSON input. This method will be called after reading the
   * closing double quote character (<code>'&quot;'</code>).
   *
   * @param string the parsed string
   */
  public void endString(String string) {}

  /**
   * Indicates the beginning of a number in the JSON input. This method will be called when reading
   * the first character of the number.
   */
  public void startNumber() {}

  /**
   * Indicates the end of a number in the JSON input. This method will be called after reading the
   * last character of the number.
   *
   * @param string the parsed number string
   */
  public void endNumber(String string) {}

  /**
   * Indicates the beginning of an array in the JSON input. This method will be called when reading
   * the opening square bracket character (<code>'['</code>).
   *
   * <p>This method may return an object to handle subsequent parser events for this array. This
   * array handler will then be provided in all calls to {@link #startArrayValue(Object)
   * startArrayValue()}, {@link #endArrayValue(Object) endArrayValue()}, and {@link
   * #endArray(Object) endArray()} for this array.
   *
   * @return a handler for this array, or <code>null</code> if not needed
   */
  public @Nullable A startArray() {
    return null;
  }

  /**
   * Indicates the end of an array in the JSON input. This method will be called after reading the
   * closing square bracket character (<code>']'</code>).
   *
   * @param array the array handler returned from {@link #startArray()}, or <code>null</code> if not
   *     provided
   */
  public void endArray(@Nullable A array) {}

  /**
   * Indicates the beginning of an array element in the JSON input. This method will be called when
   * reading the first character of the element, just before the call to the <code>start</code>
   * method for the specific element type ({@link #startString()}, {@link #startNumber()}, etc.).
   *
   * @param array the array handler returned from {@link #startArray()}, or <code>null</code> if not
   *     provided
   */
  public void startArrayValue(@SuppressWarnings("unused") @Nullable A array) {}

  /**
   * Indicates the end of an array element in the JSON input. This method will be called after
   * reading the last character of the element value, just after the <code>end</code> method for the
   * specific element type (like {@link #endString(String) endString()}, {@link #endNumber(String)
   * endNumber()}, etc.).
   *
   * @param array the array handler returned from {@link #startArray()}, or <code>null</code> if not
   *     provided
   */
  public void endArrayValue(@Nullable A array) {}

  /**
   * Indicates the beginning of an object in the JSON input. This method will be called when reading
   * the opening curly bracket character (<code>'{'</code>).
   *
   * <p>This method may return an object to handle subsequent parser events for this object. This
   * object handler will be provided in all calls to {@link #startObjectName(Object)
   * startObjectName()}, {@link #endObjectName(Object, String) endObjectName()}, {@link
   * #startObjectValue(Object, String) startObjectValue()}, {@link #endObjectValue(Object, String)
   * endObjectValue()}, and {@link #endObject(Object) endObject()} for this object.
   *
   * @return a handler for this object, or <code>null</code> if not needed
   */
  public @Nullable O startObject() {
    return null;
  }

  /**
   * Indicates the end of an object in the JSON input. This method will be called after reading the
   * closing curly bracket character (<code>'}'</code>).
   *
   * @param object the object handler returned from {@link #startObject()}, or null if not provided
   */
  public void endObject(@Nullable O object) {}

  /**
   * Indicates the beginning of the name of an object member in the JSON input. This method will be
   * called when reading the opening quote character ('&quot;') of the member name.
   *
   * @param object the object handler returned from {@link #startObject()}, or <code>null</code> if
   *     not provided
   */
  public void startObjectName(@SuppressWarnings("unused") @Nullable O object) {}

  /**
   * Indicates the end of an object member name in the JSON input. This method will be called after
   * reading the closing quote character (<code>'"'</code>) of the member name.
   *
   * @param object the object handler returned from {@link #startObject()}, or null if not provided
   * @param name the parsed member name
   */
  @SuppressWarnings("unused")
  public void endObjectName(@Nullable O object, String name) {}

  /**
   * Indicates the beginning of the name of an object member in the JSON input. This method will be
   * called when reading the opening quote character ('&quot;') of the member name.
   *
   * @param object the object handler returned from {@link #startObject()}, or <code>null</code> if
   *     not provided
   * @param name the member name
   */
  public void startObjectValue(@Nullable O object, String name) {}

  /**
   * Indicates the end of an object member value in the JSON input. This method will be called after
   * reading the last character of the member value, just after the <code>end</code> method for the
   * specific member type (like {@link #endString(String) endString()}, {@link #endNumber(String)
   * endNumber()}, etc.).
   *
   * @param object the object handler returned from {@link #startObject()}, or null if not provided
   * @param name the parsed member name
   */
  public void endObjectValue(@Nullable O object, String name) {}
}
