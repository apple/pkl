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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.pkl.core.util.LateInit;

/** A streaming parser for JSON text. The parser reports all events to a given handler. */
public final class JsonParser {

  private static final int MAX_NESTING_LEVEL = 1000;
  private static final int MIN_BUFFER_SIZE = 10;
  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private final JsonHandler<Object, Object> handler;
  @LateInit private Reader reader;
  @LateInit private char[] buffer;
  private int bufferOffset;
  private int index;
  private int fill;
  private int line;
  private int lineOffset;
  private int current;
  @LateInit private StringBuilder captureBuffer;
  private int captureStart;
  private int nestingLevel;

  /*
   * |                      bufferOffset
   *                        v
   * [a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t]        < input
   *                       [l|m|n|o|p|q|r|s|t|?|?]    < buffer
   *                          ^               ^
   *                       |  index           fill
   */

  /**
   * Creates a new JsonParser with the given handler. The parser will report all parser events to
   * this handler.
   *
   * @param handler the handler to process parser events
   */
  @SuppressWarnings("unchecked")
  public JsonParser(JsonHandler<?, ?> handler) {
    this.handler = (JsonHandler<Object, Object>) handler;
    handler.parser = this;
  }

  /**
   * Parses the given input string. The input must contain a valid JSON value, optionally padded
   * with whitespace.
   *
   * @param string the input string, must be valid JSON
   * @throws ParseException if the input is not valid JSON
   */
  public void parse(String string) {
    var bufferSize = Math.max(MIN_BUFFER_SIZE, Math.min(DEFAULT_BUFFER_SIZE, string.length()));
    try {
      parse(new StringReader(string), bufferSize);
    } catch (IOException exception) {
      // StringReader does not throw IOException
      throw new RuntimeException(exception);
    }
  }

  /**
   * Reads the entire input from the given reader and parses it as JSON. The input must contain a
   * valid JSON value, optionally padded with whitespace.
   *
   * <p>Characters are read in chunks into a default-sized input buffer. Hence, wrapping a reader in
   * an additional <code>BufferedReader</code> likely won't improve reading performance.
   *
   * @param reader the reader to read the input from
   * @throws IOException if an I/O error occurs in the reader
   * @throws ParseException if the input is not valid JSON
   */
  public void parse(Reader reader) throws IOException {
    parse(reader, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Reads the entire input from the given reader and parses it as JSON. The input must contain a
   * valid JSON value, optionally padded with whitespace.
   *
   * <p>Characters are read in chunks into an input buffer of the given size. Hence, wrapping a
   * reader in an additional <code>BufferedReader</code> likely won't improve reading performance.
   *
   * @param reader the reader to read the input from
   * @param buffersize the size of the input buffer in chars
   * @throws IOException if an I/O error occurs in the reader
   * @throws ParseException if the input is not valid JSON
   */
  public void parse(Reader reader, int buffersize) throws IOException {
    if (buffersize <= 0) {
      throw new IllegalArgumentException("buffersize is zero or negative");
    }
    this.reader = reader;
    buffer = new char[buffersize];
    bufferOffset = 0;
    index = 0;
    fill = 0;
    line = 1;
    lineOffset = 0;
    current = 0;
    captureStart = -1;
    read();
    skipWhiteSpace();
    readValue();
    skipWhiteSpace();
    if (!isEndOfText()) {
      throw error("Unexpected character");
    }
  }

  private void readValue() throws IOException {
    switch (current) {
      case 'n' -> readNull();
      case 't' -> readTrue();
      case 'f' -> readFalse();
      case '"' -> readString();
      case '[' -> readArray();
      case '{' -> readObject();
      case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readNumber();
      default -> throw expected("value");
    }
  }

  private void readArray() throws IOException {
    var array = handler.startArray();
    read();
    if (++nestingLevel > MAX_NESTING_LEVEL) {
      throw error("Nesting too deep");
    }
    skipWhiteSpace();
    if (readChar(']')) {
      nestingLevel--;
      handler.endArray(array);
      return;
    }
    do {
      skipWhiteSpace();
      handler.startArrayValue(array);
      readValue();
      handler.endArrayValue(array);
      skipWhiteSpace();
    } while (readChar(','));
    if (!readChar(']')) {
      throw expected("',' or ']'");
    }
    nestingLevel--;
    handler.endArray(array);
  }

  private void readObject() throws IOException {
    var object = handler.startObject();
    read();
    if (++nestingLevel > MAX_NESTING_LEVEL) {
      throw error("Nesting too deep");
    }
    skipWhiteSpace();
    if (readChar('}')) {
      nestingLevel--;
      handler.endObject(object);
      return;
    }
    do {
      skipWhiteSpace();
      handler.startObjectName(object);
      var name = readName();
      handler.endObjectName(object, name);
      skipWhiteSpace();
      if (!readChar(':')) {
        throw expected("':'");
      }
      skipWhiteSpace();
      handler.startObjectValue(object, name);
      readValue();
      handler.endObjectValue(object, name);
      skipWhiteSpace();
    } while (readChar(','));
    if (!readChar('}')) {
      throw expected("',' or '}'");
    }
    nestingLevel--;
    handler.endObject(object);
  }

  private String readName() throws IOException {
    if (current != '"') {
      throw expected("name");
    }
    return readStringInternal();
  }

  private void readNull() throws IOException {
    handler.startNull();
    read();
    readRequiredChar('u');
    readRequiredChar('l');
    readRequiredChar('l');
    handler.endNull();
  }

  private void readTrue() throws IOException {
    handler.startBoolean();
    read();
    readRequiredChar('r');
    readRequiredChar('u');
    readRequiredChar('e');
    handler.endBoolean(true);
  }

  private void readFalse() throws IOException {
    handler.startBoolean();
    read();
    readRequiredChar('a');
    readRequiredChar('l');
    readRequiredChar('s');
    readRequiredChar('e');
    handler.endBoolean(false);
  }

  private void readRequiredChar(char ch) throws IOException {
    if (!readChar(ch)) {
      throw expected("'" + ch + "'");
    }
  }

  private void readString() throws IOException {
    handler.startString();
    handler.endString(readStringInternal());
  }

  private String readStringInternal() throws IOException {
    read();
    startCapture();
    while (current != '"') {
      if (current == '\\') {
        pauseCapture();
        readEscape();
        startCapture();
      } else if (current < 0x20) {
        throw expected("valid string character");
      } else {
        read();
      }
    }
    var string = endCapture();
    read();
    return string;
  }

  private void readEscape() throws IOException {
    read();
    switch (current) {
      case '"', '/', '\\' -> captureBuffer.append((char) current);
      case 'b' -> captureBuffer.append('\b');
      case 'f' -> captureBuffer.append('\f');
      case 'n' -> captureBuffer.append('\n');
      case 'r' -> captureBuffer.append('\r');
      case 't' -> captureBuffer.append('\t');
      case 'u' -> {
        var hexChars = new char[4];
        for (var i = 0; i < 4; i++) {
          read();
          if (!isHexDigit()) {
            throw expected("hexadecimal digit");
          }
          hexChars[i] = (char) current;
        }
        captureBuffer.append((char) Integer.parseInt(new String(hexChars), 16));
      }
      default -> throw expected("valid escape sequence");
    }
    read();
  }

  private void readNumber() throws IOException {
    handler.startNumber();
    startCapture();
    readChar('-');
    var firstDigit = current;
    if (!readDigit()) {
      throw expected("digit");
    }
    if (firstDigit != '0') {
      //noinspection StatementWithEmptyBody
      while (readDigit()) {}
    }
    readFraction();
    readExponent();
    handler.endNumber(endCapture());
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean readFraction() throws IOException {
    if (!readChar('.')) {
      return false;
    }
    if (!readDigit()) {
      throw expected("digit");
    }
    //noinspection StatementWithEmptyBody
    while (readDigit()) {}
    return true;
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean readExponent() throws IOException {
    if (!readChar('e') && !readChar('E')) {
      return false;
    }
    if (!readChar('+')) {
      readChar('-');
    }
    if (!readDigit()) {
      throw expected("digit");
    }
    //noinspection StatementWithEmptyBody
    while (readDigit()) {}
    return true;
  }

  private boolean readChar(char ch) throws IOException {
    if (current != ch) {
      return false;
    }
    read();
    return true;
  }

  private boolean readDigit() throws IOException {
    if (!isDigit()) {
      return false;
    }
    read();
    return true;
  }

  private void skipWhiteSpace() throws IOException {
    while (isWhiteSpace()) {
      read();
    }
  }

  private void read() throws IOException {
    if (index == fill) {
      if (captureStart != -1) {
        captureBuffer.append(buffer, captureStart, fill - captureStart);
        captureStart = 0;
      }
      bufferOffset += fill;
      fill = reader.read(buffer, 0, buffer.length);
      index = 0;
      if (fill == -1) {
        current = -1;
        index++;
        return;
      }
    }
    if (current == '\n') {
      line++;
      lineOffset = bufferOffset + index;
    }
    current = buffer[index++];
  }

  private void startCapture() {
    if (captureBuffer == null) {
      captureBuffer = new StringBuilder();
    }
    captureStart = index - 1;
  }

  private void pauseCapture() {
    var end = current == -1 ? index : index - 1;
    captureBuffer.append(buffer, captureStart, end - captureStart);
    captureStart = -1;
  }

  private String endCapture() {
    var start = captureStart;
    var end = index - 1;
    captureStart = -1;
    if (!captureBuffer.isEmpty()) {
      captureBuffer.append(buffer, start, end - start);
      var captured = captureBuffer.toString();
      captureBuffer.setLength(0);
      return captured;
    }
    return new String(buffer, start, end - start);
  }

  Location getLocation() {
    var offset = bufferOffset + index - 1;
    var column = offset - lineOffset + 1;
    return new Location(offset, line, column);
  }

  private ParseException expected(String expected) {
    if (isEndOfText()) {
      return error("Unexpected end of input");
    }
    return error("Expected " + expected);
  }

  private ParseException error(String message) {
    return new ParseException(message, getLocation());
  }

  private boolean isWhiteSpace() {
    return current == ' ' || current == '\t' || current == '\n' || current == '\r';
  }

  private boolean isDigit() {
    return current >= '0' && current <= '9';
  }

  private boolean isHexDigit() {
    return current >= '0' && current <= '9'
        || current >= 'a' && current <= 'f'
        || current >= 'A' && current <= 'F';
  }

  private boolean isEndOfText() {
    return current == -1;
  }
}
