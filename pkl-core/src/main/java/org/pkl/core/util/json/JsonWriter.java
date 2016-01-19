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
package org.pkl.core.util.json;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import org.pkl.core.util.Nullable;

/**
 * Writes a JSON (<a href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>) encoded value to a
 * stream, one token at a time. The stream includes both literal values (strings, numbers, booleans
 * and nulls) as well as the begin and end delimiters of objects and arrays.
 *
 * <h3>Encoding JSON</h3>
 *
 * To encode your data as JSON, create a new {@code JsonWriter}. Each JSON document must contain one
 * top-level array or object. Call methods on the writer as you walk the structure's contents,
 * nesting arrays and objects as necessary:
 *
 * <ul>
 *   <li>To write <strong>arrays</strong>, first call {@link #beginArray()}. Write each of the
 *       array's elements with the appropriate {@link #value} methods or by nesting other arrays and
 *       objects. Finally close the array using {@link #endArray()}.
 *   <li>To write <strong>objects</strong>, first call {@link #beginObject()}. Write each of the
 *       object's properties by alternating calls to {@link #name} with the property's value. Write
 *       property values with the appropriate {@link #value} method or by nesting other objects or
 *       arrays. Finally close the object using {@link #endObject()}.
 * </ul>
 *
 * <h3>Example</h3>
 *
 * Suppose we'd like to encode a stream of messages such as the following:
 *
 * <pre>{@code
 * [
 *   {
 *     "id": 912345678901,
 *     "text": "How do I stream JSON in Java?",
 *     "geo": null,
 *     "user": {
 *       "name": "json_newb",
 *       "followers_count": 41
 *      }
 *   },
 *   {
 *     "id": 912345678902,
 *     "text": "@json_newb just use JsonWriter!",
 *     "geo": [50.454722, -104.606667],
 *     "user": {
 *       "name": "jesse",
 *       "followers_count": 2
 *     }
 *   }
 * ]
 * }</pre>
 *
 * This code encodes the above structure:
 *
 * <pre>{@code
 * public void writeJsonStream(OutputStream out, List<Message> messages) throws IOException {
 *   JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
 *   writer.setIndent("    ");
 *   writeMessagesArray(writer, messages);
 *   writer.close();
 * }
 *
 * public void writeMessagesArray(JsonWriter writer, List<Message> messages) throws IOException {
 *   writer.beginArray();
 *   for (Message message : messages) {
 *     writeMessage(writer, message);
 *   }
 *   writer.endArray();
 * }
 *
 * public void writeMessage(JsonWriter writer, Message message) throws IOException {
 *   writer.beginObject();
 *   writer.name("id").value(message.getId());
 *   writer.name("text").value(message.getText());
 *   if (message.getGeo() != null) {
 *     writer.name("geo");
 *     writeDoublesArray(writer, message.getGeo());
 *   } else {
 *     writer.name("geo").nullValue();
 *   }
 *   writer.name("user");
 *   writeUser(writer, message.getUser());
 *   writer.endObject();
 * }
 *
 * public void writeUser(JsonWriter writer, User user) throws IOException {
 *   writer.beginObject();
 *   writer.name("name").value(user.getName());
 *   writer.name("followers_count").value(user.getFollowersCount());
 *   writer.endObject();
 * }
 *
 * public void writeDoublesArray(JsonWriter writer, List<Double> doubles) throws IOException {
 *   writer.beginArray();
 *   for (Double value : doubles) {
 *     writer.value(value);
 *   }
 *   writer.endArray();
 * }
 * }</pre>
 *
 * <p>Each {@code JsonWriter} may be used to write a single JSON stream. Instances of this class are
 * not thread safe. Calls that would result in a malformed JSON string will fail with an {@link
 * IllegalStateException}.
 *
 * @author Jesse Wilson
 * @since 1.6
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class JsonWriter implements Closeable, Flushable {
  /** An array with no elements requires no separators or newlines before it is closed. */
  static final int EMPTY_ARRAY = 1;

  /** A array with at least one value requires a comma and newline before the next element. */
  static final int NONEMPTY_ARRAY = 2;

  /** An object with no name/value pairs requires no separators or newlines before it is closed. */
  static final int EMPTY_OBJECT = 3;

  /** An object whose most recent element is a key. The next element must be a value. */
  static final int DANGLING_NAME = 4;

  /**
   * An object with at least one name/value pair requires a comma and newline before the next
   * element.
   */
  static final int NONEMPTY_OBJECT = 5;

  /** No object or array has been started. */
  static final int EMPTY_DOCUMENT = 6;

  /** A document with at an array or object. */
  static final int NONEMPTY_DOCUMENT = 7;

  /** A document that's been closed and cannot be accessed. */
  static final int CLOSED = 8;

  /** The output data, containing at most one top-level array or object. */
  private final Writer out;

  private int[] stack = new int[32];
  private int stackSize = 0;

  {
    push(EMPTY_DOCUMENT);
  }

  /**
   * A string containing a full set of spaces for a single level of indentation, or null for no
   * pretty printing.
   */
  private @Nullable String indent;

  /** The name/value separator; either ":" or ": ". */
  private String separator = ":";

  private boolean lenient;

  private boolean htmlSafe;

  private JsonEscaper escaper = new JsonEscaper(false);

  private @Nullable String deferredName;

  private boolean serializeNulls = true;

  /**
   * Creates a new instance that writes a JSON-encoded stream to {@code out}. For best performance,
   * ensure {@link Writer} is buffered; wrapping in {@link java.io.BufferedWriter BufferedWriter} if
   * necessary.
   */
  public JsonWriter(Writer out) {
    this.out = out;
  }

  /**
   * Sets the indentation string to be repeated for each level of indentation in the encoded
   * document. If {@code indent.isEmpty()} the encoded document will be compact. Otherwise the
   * encoded document will be more human-readable.
   *
   * @param indent a string containing only whitespace.
   */
  public final void setIndent(String indent) {
    if (indent.length() == 0) {
      this.indent = null;
      this.separator = ":";
    } else {
      this.indent = indent;
      this.separator = ": ";
    }
  }

  /**
   * Configure this writer to relax its syntax rules. By default, this writer only emits well-formed
   * JSON as specified by <a href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>. Setting the
   * writer to lenient permits the following:
   *
   * <ul>
   *   <li>Top-level values of any type. With strict writing, the top-level value must be an object
   *       or an array.
   *   <li>Numbers may be {@link Double#isNaN() NaNs} or {@link Double#isInfinite() infinities}.
   * </ul>
   */
  public final void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  /** Returns true if this writer has relaxed syntax rules. */
  public boolean isLenient() {
    return lenient;
  }

  /**
   * Configure this writer to emit JSON that's safe for direct inclusion in HTML and XML documents.
   * This escapes the HTML characters {@code <}, {@code >}, {@code &} and {@code =} before writing
   * them to the stream. Without this setting, your XML/HTML encoder should replace these characters
   * with the corresponding escape sequences.
   */
  public final void setHtmlSafe(boolean htmlSafe) {
    this.htmlSafe = htmlSafe;
    this.escaper = new JsonEscaper(htmlSafe);
  }

  /**
   * Returns true if this writer writes JSON that's safe for inclusion in HTML and XML documents.
   */
  public final boolean isHtmlSafe() {
    return htmlSafe;
  }

  /**
   * Sets whether object members are serialized when their value is null. This has no impact on
   * array elements. The default is true.
   */
  public final void setSerializeNulls(boolean serializeNulls) {
    this.serializeNulls = serializeNulls;
  }

  /**
   * Returns true if object members are serialized when their value is null. This has no impact on
   * array elements. The default is true.
   */
  public final boolean getSerializeNulls() {
    return serializeNulls;
  }

  /**
   * Begins encoding a new array. Each call to this method must be paired with a call to {@link
   * #endArray}.
   *
   * @return this writer.
   */
  public JsonWriter beginArray() throws IOException {
    writeDeferredName();
    return open(EMPTY_ARRAY, '[');
  }

  /**
   * Ends encoding the current array.
   *
   * @return this writer.
   */
  public JsonWriter endArray() throws IOException {
    return close(EMPTY_ARRAY, NONEMPTY_ARRAY, ']');
  }

  /**
   * Begins encoding a new object. Each call to this method must be paired with a call to {@link
   * #endObject}.
   *
   * @return this writer.
   */
  public JsonWriter beginObject() throws IOException {
    writeDeferredName();
    return open(EMPTY_OBJECT, '{');
  }

  /**
   * Ends encoding the current object.
   *
   * @return this writer.
   */
  public JsonWriter endObject() throws IOException {
    return close(EMPTY_OBJECT, NONEMPTY_OBJECT, '}');
  }

  /** Enters a new scope by appending any necessary whitespace and the given bracket. */
  private JsonWriter open(int empty, char openBracket) throws IOException {
    beforeValue();
    push(empty);
    out.write(openBracket);
    return this;
  }

  /** Closes the current scope by appending any necessary whitespace and the given bracket. */
  private JsonWriter close(int empty, int nonempty, char closeBracket) throws IOException {
    var context = peek();
    if (context != nonempty && context != empty) {
      throw new IllegalStateException("Nesting problem.");
    }
    if (deferredName != null) {
      throw new IllegalStateException("Dangling name: " + deferredName);
    }

    stackSize--;
    if (context == nonempty) {
      newline();
    }
    out.write(closeBracket);
    return this;
  }

  private void push(int newTop) {
    if (stackSize == stack.length) {
      stack = Arrays.copyOf(stack, stackSize * 2);
    }
    stack[stackSize++] = newTop;
  }

  /** Returns the value on the top of the stack. */
  private int peek() {
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
    return stack[stackSize - 1];
  }

  /** Replace the value on the top of the stack with the given value. */
  private void replaceTop(int topOfStack) {
    stack[stackSize - 1] = topOfStack;
  }

  /**
   * Encodes the property name.
   *
   * @param name the name of the forthcoming value. May not be null.
   * @return this writer.
   */
  public JsonWriter name(String name) {
    if (deferredName != null) {
      throw new IllegalStateException();
    }
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
    deferredName = name;
    return this;
  }

  private void writeDeferredName() throws IOException {
    if (deferredName != null) {
      beforeName();
      string(deferredName);
      deferredName = null;
    }
  }

  /**
   * Encodes {@code value}.
   *
   * @param value the literal string value, or null to encode a null literal.
   * @return this writer.
   */
  public JsonWriter value(@Nullable String value) throws IOException {
    if (value == null) {
      return nullValue();
    }
    writeDeferredName();
    beforeValue();
    string(value);
    return this;
  }

  /**
   * Encodes {@code null}.
   *
   * @return this writer.
   */
  public JsonWriter nullValue() throws IOException {
    if (deferredName != null) {
      if (serializeNulls) {
        writeDeferredName();
      } else {
        deferredName = null;
        return this; // skip the name and the value
      }
    }
    beforeValue();
    out.write("null");
    return this;
  }

  /**
   * Encodes {@code value}.
   *
   * @return this writer.
   */
  public JsonWriter value(boolean value) throws IOException {
    writeDeferredName();
    beforeValue();
    out.write(value ? "true" : "false");
    return this;
  }

  /**
   * Encodes {@code value}.
   *
   * @return this writer.
   */
  public JsonWriter value(@Nullable Boolean value) throws IOException {
    if (value == null) {
      return nullValue();
    }
    writeDeferredName();
    beforeValue();
    out.write(value ? "true" : "false");
    return this;
  }

  /**
   * Encodes {@code value}.
   *
   * @param value a finite value. May not be {@link Double#isNaN() NaNs} or {@link
   *     Double#isInfinite() infinities}.
   * @return this writer.
   */
  public JsonWriter value(double value) throws IOException {
    writeDeferredName();
    if (!lenient && (Double.isNaN(value) || Double.isInfinite(value))) {
      throw new IllegalArgumentException("Numeric values must be finite, but was " + value);
    }
    beforeValue();
    out.append(Double.toString(value));
    return this;
  }

  /**
   * Encodes {@code value}.
   *
   * @return this writer.
   */
  public JsonWriter value(long value) throws IOException {
    writeDeferredName();
    beforeValue();
    out.write(Long.toString(value));
    return this;
  }

  /**
   * Encodes {@code value}.
   *
   * @param value a finite value. May not be {@link Double#isNaN() NaNs} or {@link
   *     Double#isInfinite() infinities}.
   * @return this writer.
   */
  public JsonWriter value(@Nullable Number value) throws IOException {
    if (value == null) {
      return nullValue();
    }

    writeDeferredName();
    var string = value.toString();
    if (!lenient
        && (string.equals("-Infinity") || string.equals("Infinity") || string.equals("NaN"))) {
      throw new IllegalArgumentException("Numeric values must be finite, but was " + value);
    }
    beforeValue();
    out.append(string);
    return this;
  }

  /** Writes the given raw text to the underlying writer. */
  public JsonWriter rawText(String text) throws IOException {
    out.append(text);
    return this;
  }

  /**
   * Ensures all buffered data is written to the underlying {@link Writer} and flushes that writer.
   */
  public void flush() throws IOException {
    if (stackSize == 0) {
      throw new IllegalStateException("JsonWriter is closed.");
    }
    out.flush();
  }

  /**
   * Flushes and closes this writer and the underlying {@link Writer}.
   *
   * @throws IOException if the JSON document is incomplete.
   */
  public void close() throws IOException {
    out.close();

    var size = stackSize;
    if (size > 1 || size == 1 && stack[size - 1] != NONEMPTY_DOCUMENT) {
      throw new IOException("Incomplete document");
    }
    stackSize = 0;
  }

  @SuppressWarnings("DuplicatedCode")
  private void string(String value) throws IOException {
    out.write('"');
    out.write(escaper.escape(value));
    out.write('"');
  }

  public void newline() throws IOException {
    if (indent == null) {
      return;
    }

    out.write('\n');
    for (int i = 1, size = stackSize; i < size; i++) {
      out.write(indent);
    }
  }

  /**
   * Inserts any necessary separators and whitespace before a name. Also adjusts the stack to expect
   * the name's value.
   */
  private void beforeName() throws IOException {
    var context = peek();
    if (context == NONEMPTY_OBJECT) { // first in object
      out.write(',');
    } else if (context != EMPTY_OBJECT) { // not in an object!
      throw new IllegalStateException("Nesting problem.");
    }
    newline();
    replaceTop(DANGLING_NAME);
  }

  /**
   * Inserts any necessary separators and whitespace before a literal value, inline array, or inline
   * object. Also adjusts the stack to expect either a closing bracket or another element.
   */
  private void beforeValue() throws IOException {
    switch (peek()) {
      case NONEMPTY_DOCUMENT:
        if (!lenient) {
          throw new IllegalStateException("JSON must have only one top-level value.");
        }
        // fall-through
      case EMPTY_DOCUMENT: // first in document
        replaceTop(NONEMPTY_DOCUMENT);
        break;

      case EMPTY_ARRAY: // first in array
        replaceTop(NONEMPTY_ARRAY);
        newline();
        break;

      case NONEMPTY_ARRAY: // another in array
        out.append(',');
        newline();
        break;

      case DANGLING_NAME: // value for name
        out.append(separator);
        replaceTop(NONEMPTY_OBJECT);
        break;

      default:
        throw new IllegalStateException("Nesting problem.");
    }
  }
}
