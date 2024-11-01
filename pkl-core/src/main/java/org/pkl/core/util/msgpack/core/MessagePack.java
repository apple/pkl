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
package org.pkl.core.util.msgpack.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import org.pkl.core.util.msgpack.core.buffer.ArrayBufferInput;
import org.pkl.core.util.msgpack.core.buffer.MessageBufferInput;
import org.pkl.core.util.msgpack.core.buffer.MessageBufferOutput;
import org.pkl.core.util.msgpack.core.buffer.OutputStreamBufferOutput;

/**
 * Convenience class to build packer and unpacker classes.
 *
 * <p>You can select an appropriate factory method as following.
 *
 * <p>Deserializing objects from binary:
 *
 * <table>
 *   <tr><th>Input type</th><th>Factory method</th><th>Return type</th></tr>
 *   <tr><td>byte[]</td><td>{@link #newDefaultUnpacker(byte[], int, int)}</td><td>{@link MessageUnpacker}</td></tr>
 *   <tr><td>InputStream</td><td>{@link #newDefaultUnpacker(InputStream)}</td><td>{@link MessageUnpacker}</td></tr>
 *   <tr><td>{@link MessageBufferInput}</td><td>{@link #newDefaultUnpacker(MessageBufferInput)}</td><td>{@link MessageUnpacker}</td></tr>
 * </table>
 *
 * <p>Serializing objects into binary:
 *
 * <table>
 *   <tr><th>Output type</th><th>Factory method</th><th>Return type</th></tr>
 *   <tr><td>byte[]</td><td>{@link #newDefaultBufferPacker()}</td><td>{@link MessageBufferPacker}</td><tr>
 *   <tr><td>OutputStream</td><td>{@link #newDefaultPacker(OutputStream)}</td><td>{@link MessagePacker}</td></tr>
 * </table>
 */
@SuppressWarnings("ALL")
public class MessagePack {
  /**
   * @exclude Applications should use java.nio.charset.StandardCharsets.UTF_8 instead since Java 7.
   */
  public static final Charset UTF8 = Charset.forName("UTF-8");

  /**
   * Configuration of a {@link MessagePacker} used by {@link #newDefaultPacker(MessageBufferOutput)}
   * and {@link #newDefaultBufferPacker()} methods.
   */
  public static final MessagePack.PackerConfig DEFAULT_PACKER_CONFIG =
      new MessagePack.PackerConfig();

  /**
   * Configuration of a {@link MessagePacker} used by {@link
   * #newDefaultUnpacker(MessageBufferInput)} methods.
   */
  public static final MessagePack.UnpackerConfig DEFAULT_UNPACKER_CONFIG =
      new MessagePack.UnpackerConfig();

  /**
   * The prefix code set of MessagePack format. See also
   * https://github.com/msgpack/msgpack/blob/master/spec.md for details.
   */
  public static final class Code {
    public static final boolean isFixInt(byte b) {
      int v = b & 0xFF;
      return v <= 0x7f || v >= 0xe0;
    }

    public static final boolean isPosFixInt(byte b) {
      return (b & POSFIXINT_MASK) == 0;
    }

    public static final boolean isNegFixInt(byte b) {
      return (b & NEGFIXINT_PREFIX) == NEGFIXINT_PREFIX;
    }

    public static final boolean isFixStr(byte b) {
      return (b & (byte) 0xe0) == MessagePack.Code.FIXSTR_PREFIX;
    }

    public static final boolean isFixedArray(byte b) {
      return (b & (byte) 0xf0) == MessagePack.Code.FIXARRAY_PREFIX;
    }

    public static final boolean isFixedMap(byte b) {
      return (b & (byte) 0xf0) == MessagePack.Code.FIXMAP_PREFIX;
    }

    public static final boolean isFixedRaw(byte b) {
      return (b & (byte) 0xe0) == MessagePack.Code.FIXSTR_PREFIX;
    }

    public static final byte POSFIXINT_MASK = (byte) 0x80;

    public static final byte FIXMAP_PREFIX = (byte) 0x80;
    public static final byte FIXARRAY_PREFIX = (byte) 0x90;
    public static final byte FIXSTR_PREFIX = (byte) 0xa0;

    public static final byte NIL = (byte) 0xc0;
    public static final byte NEVER_USED = (byte) 0xc1;
    public static final byte FALSE = (byte) 0xc2;
    public static final byte TRUE = (byte) 0xc3;
    public static final byte BIN8 = (byte) 0xc4;
    public static final byte BIN16 = (byte) 0xc5;
    public static final byte BIN32 = (byte) 0xc6;
    public static final byte EXT8 = (byte) 0xc7;
    public static final byte EXT16 = (byte) 0xc8;
    public static final byte EXT32 = (byte) 0xc9;
    public static final byte FLOAT32 = (byte) 0xca;
    public static final byte FLOAT64 = (byte) 0xcb;
    public static final byte UINT8 = (byte) 0xcc;
    public static final byte UINT16 = (byte) 0xcd;
    public static final byte UINT32 = (byte) 0xce;
    public static final byte UINT64 = (byte) 0xcf;

    public static final byte INT8 = (byte) 0xd0;
    public static final byte INT16 = (byte) 0xd1;
    public static final byte INT32 = (byte) 0xd2;
    public static final byte INT64 = (byte) 0xd3;

    public static final byte FIXEXT1 = (byte) 0xd4;
    public static final byte FIXEXT2 = (byte) 0xd5;
    public static final byte FIXEXT4 = (byte) 0xd6;
    public static final byte FIXEXT8 = (byte) 0xd7;
    public static final byte FIXEXT16 = (byte) 0xd8;

    public static final byte STR8 = (byte) 0xd9;
    public static final byte STR16 = (byte) 0xda;
    public static final byte STR32 = (byte) 0xdb;

    public static final byte ARRAY16 = (byte) 0xdc;
    public static final byte ARRAY32 = (byte) 0xdd;

    public static final byte MAP16 = (byte) 0xde;
    public static final byte MAP32 = (byte) 0xdf;

    public static final byte NEGFIXINT_PREFIX = (byte) 0xe0;

    public static final byte EXT_TIMESTAMP = (byte) -1;
  }

  private MessagePack() {
    // Prohibit instantiation of this class
  }

  /**
   * Creates a packer that serializes objects into the specified output.
   *
   * <p>{@link MessageBufferOutput} is an interface that lets applications customize memory
   * allocation of internal buffer of {@link MessagePacker}. You may prefer {@link
   * #newDefaultBufferPacker()}, or {@link #newDefaultPacker(OutputStream)} methods instead.
   *
   * <p>This method is equivalent to <code>DEFAULT_PACKER_CONFIG.newPacker(out)</code>.
   *
   * @param out A MessageBufferOutput that allocates buffer chunks and receives the buffer chunks
   *     with packed data filled in them
   * @return A new MessagePacker instance
   */
  public static MessagePacker newDefaultPacker(MessageBufferOutput out) {
    return DEFAULT_PACKER_CONFIG.newPacker(out);
  }

  /**
   * Creates a packer that serializes objects into the specified output stream.
   *
   * <p>Note that you don't have to wrap OutputStream in BufferedOutputStream because MessagePacker
   * has buffering internally.
   *
   * <p>This method is equivalent to <code>DEFAULT_PACKER_CONFIG.newPacker(out)</code>.
   *
   * @param out The output stream that receives sequence of bytes
   * @return A new MessagePacker instance
   */
  public static MessagePacker newDefaultPacker(OutputStream out) {
    return DEFAULT_PACKER_CONFIG.newPacker(out);
  }

  /**
   * Creates a packer that serializes objects into byte arrays.
   *
   * <p>This method provides an optimized implementation of <code>
   * newDefaultBufferPacker(new ByteArrayOutputStream())</code>.
   *
   * <p>This method is equivalent to <code>DEFAULT_PACKER_CONFIG.newBufferPacker()</code>.
   *
   * @return A new MessageBufferPacker instance
   */
  public static MessageBufferPacker newDefaultBufferPacker() {
    return DEFAULT_PACKER_CONFIG.newBufferPacker();
  }

  /**
   * Creates an unpacker that deserializes objects from a specified input.
   *
   * <p>{@link MessageBufferInput} is an interface that lets applications customize memory
   * allocation of internal buffer of {@link MessageUnpacker}. You may prefer {@link
   * #newDefaultUnpacker(InputStream)} {@link #newDefaultUnpacker(byte[], int, int)} methods
   * instead.
   *
   * <p>This method is equivalent to <code>DEFAULT_UNPACKER_CONFIG.newDefaultUnpacker(in)</code>.
   *
   * @param in The input stream that provides sequence of buffer chunks and optionally reuses them
   *     when MessageUnpacker consumed one completely
   * @return A new MessageUnpacker instance
   */
  public static MessageUnpacker newDefaultUnpacker(MessageBufferInput in) {
    return DEFAULT_UNPACKER_CONFIG.newUnpacker(in);
  }

  /**
   * Creates an unpacker that deserializes objects from a specified input stream.
   *
   * <p>Note that you don't have to wrap InputStream in BufferedInputStream because MessageUnpacker
   * has buffering internally.
   *
   * <p>This method is equivalent to <code>DEFAULT_UNPACKER_CONFIG.newDefaultUnpacker(in)</code>.
   *
   * @param in The input stream that provides sequence of bytes
   * @return A new MessageUnpacker instance
   */
  public static MessageUnpacker newDefaultUnpacker(InputStream in) {
    return DEFAULT_UNPACKER_CONFIG.newUnpacker(in);
  }

  /**
   * Creates an unpacker that deserializes objects from a specified byte array.
   *
   * <p>This method provides an optimized implementation of <code>
   * newDefaultUnpacker(new ByteArrayInputStream(contents))</code>.
   *
   * <p>This method is equivalent to <code>DEFAULT_UNPACKER_CONFIG.newDefaultUnpacker(contents)
   * </code>.
   *
   * @param contents The byte array that contains packed objects in MessagePack format
   * @return A new MessageUnpacker instance that will never throw IOException
   */
  public static MessageUnpacker newDefaultUnpacker(byte[] contents) {
    return DEFAULT_UNPACKER_CONFIG.newUnpacker(contents);
  }

  /**
   * Creates an unpacker that deserializes objects from subarray of a specified byte array.
   *
   * <p>This method provides an optimized implementation of <code>
   * newDefaultUnpacker(new ByteArrayInputStream(contents, offset, length))</code>.
   *
   * <p>This method is equivalent to <code>DEFAULT_UNPACKER_CONFIG.newDefaultUnpacker(contents)
   * </code>.
   *
   * @param contents The byte array that contains packed objects
   * @param offset The index of the first byte
   * @param length The number of bytes
   * @return A new MessageUnpacker instance that will never throw IOException
   */
  public static MessageUnpacker newDefaultUnpacker(byte[] contents, int offset, int length) {
    return DEFAULT_UNPACKER_CONFIG.newUnpacker(contents, offset, length);
  }

  /** MessagePacker configuration. */
  public static class PackerConfig implements Cloneable {
    private int smallStringOptimizationThreshold = 512;

    private int bufferFlushThreshold = 8192;

    private int bufferSize = 8192;

    private boolean str8FormatSupport = true;

    public PackerConfig() {}

    private PackerConfig(MessagePack.PackerConfig copy) {
      this.smallStringOptimizationThreshold = copy.smallStringOptimizationThreshold;
      this.bufferFlushThreshold = copy.bufferFlushThreshold;
      this.bufferSize = copy.bufferSize;
      this.str8FormatSupport = copy.str8FormatSupport;
    }

    @Override
    public MessagePack.PackerConfig clone() {
      return new MessagePack.PackerConfig(this);
    }

    @Override
    public int hashCode() {
      int result = smallStringOptimizationThreshold;
      result = 31 * result + bufferFlushThreshold;
      result = 31 * result + bufferSize;
      result = 31 * result + (str8FormatSupport ? 1 : 0);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MessagePack.PackerConfig)) {
        return false;
      }
      MessagePack.PackerConfig o = (MessagePack.PackerConfig) obj;
      return this.smallStringOptimizationThreshold == o.smallStringOptimizationThreshold
          && this.bufferFlushThreshold == o.bufferFlushThreshold
          && this.bufferSize == o.bufferSize
          && this.str8FormatSupport == o.str8FormatSupport;
    }

    /**
     * Creates a packer that serializes objects into the specified output.
     *
     * <p>{@link MessageBufferOutput} is an interface that lets applications customize memory
     * allocation of internal buffer of {@link MessagePacker}.
     *
     * @param out A MessageBufferOutput that allocates buffer chunks and receives the buffer chunks
     *     with packed data filled in them
     * @return A new MessagePacker instance
     */
    public MessagePacker newPacker(MessageBufferOutput out) {
      return new MessagePacker(out, this);
    }

    /**
     * Creates a packer that serializes objects into the specified output stream.
     *
     * <p>Note that you don't have to wrap OutputStream in BufferedOutputStream because
     * MessagePacker has buffering internally.
     *
     * @param out The output stream that receives sequence of bytes
     * @return A new MessagePacker instance
     */
    public MessagePacker newPacker(OutputStream out) {
      return newPacker(new OutputStreamBufferOutput(out, bufferSize));
    }

    /**
     * Creates a packer that serializes objects into byte arrays.
     *
     * <p>This method provides an optimized implementation of <code>
     * newDefaultBufferPacker(new ByteArrayOutputStream())</code>.
     *
     * @return A new MessageBufferPacker instance
     */
    public MessageBufferPacker newBufferPacker() {
      return new MessageBufferPacker(this);
    }

    /**
     * Use String.getBytes() for converting Java Strings that are shorter than this threshold. Note
     * that this parameter is subject to change.
     */
    public MessagePack.PackerConfig withSmallStringOptimizationThreshold(int length) {
      MessagePack.PackerConfig copy = clone();
      copy.smallStringOptimizationThreshold = length;
      return copy;
    }

    public int getSmallStringOptimizationThreshold() {
      return smallStringOptimizationThreshold;
    }

    /**
     * When the next payload size exceeds this threshold, MessagePacker will call {@link
     * MessageBufferOutput#flush()} before writing more data (default: 8192).
     */
    public MessagePack.PackerConfig withBufferFlushThreshold(int bytes) {
      MessagePack.PackerConfig copy = clone();
      copy.bufferFlushThreshold = bytes;
      return copy;
    }

    public int getBufferFlushThreshold() {
      return bufferFlushThreshold;
    }

    /**
     * When a packer is created with {@link #newPacker(OutputStream)}, the stream will be buffered
     * with this size of buffer (default: 8192).
     */
    public MessagePack.PackerConfig withBufferSize(int bytes) {
      MessagePack.PackerConfig copy = clone();
      copy.bufferSize = bytes;
      return copy;
    }

    public int getBufferSize() {
      return bufferSize;
    }

    /**
     * Disable str8 format when needed backward compatibility between different msgpack serializer
     * versions. default true (str8 supported enabled)
     */
    public MessagePack.PackerConfig withStr8FormatSupport(boolean str8FormatSupport) {
      MessagePack.PackerConfig copy = clone();
      copy.str8FormatSupport = str8FormatSupport;
      return copy;
    }

    public boolean isStr8FormatSupport() {
      return str8FormatSupport;
    }
  }

  /** MessageUnpacker configuration. */
  public static class UnpackerConfig implements Cloneable {
    private boolean allowReadingStringAsBinary = true;

    private boolean allowReadingBinaryAsString = true;

    private CodingErrorAction actionOnMalformedString = CodingErrorAction.REPLACE;

    private CodingErrorAction actionOnUnmappableString = CodingErrorAction.REPLACE;

    private int stringSizeLimit = Integer.MAX_VALUE;

    private int bufferSize = 8192;

    private int stringDecoderBufferSize = 8192;

    public UnpackerConfig() {}

    private UnpackerConfig(MessagePack.UnpackerConfig copy) {
      this.allowReadingStringAsBinary = copy.allowReadingStringAsBinary;
      this.allowReadingBinaryAsString = copy.allowReadingBinaryAsString;
      this.actionOnMalformedString = copy.actionOnMalformedString;
      this.actionOnUnmappableString = copy.actionOnUnmappableString;
      this.stringSizeLimit = copy.stringSizeLimit;
      this.bufferSize = copy.bufferSize;
    }

    @Override
    public MessagePack.UnpackerConfig clone() {
      return new MessagePack.UnpackerConfig(this);
    }

    @Override
    public int hashCode() {
      int result = (allowReadingStringAsBinary ? 1 : 0);
      result = 31 * result + (allowReadingBinaryAsString ? 1 : 0);
      result =
          31 * result + (actionOnMalformedString != null ? actionOnMalformedString.hashCode() : 0);
      result =
          31 * result
              + (actionOnUnmappableString != null ? actionOnUnmappableString.hashCode() : 0);
      result = 31 * result + stringSizeLimit;
      result = 31 * result + bufferSize;
      result = 31 * result + stringDecoderBufferSize;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MessagePack.UnpackerConfig)) {
        return false;
      }
      MessagePack.UnpackerConfig o = (MessagePack.UnpackerConfig) obj;
      return this.allowReadingStringAsBinary == o.allowReadingStringAsBinary
          && this.allowReadingBinaryAsString == o.allowReadingBinaryAsString
          && this.actionOnMalformedString == o.actionOnMalformedString
          && this.actionOnUnmappableString == o.actionOnUnmappableString
          && this.stringSizeLimit == o.stringSizeLimit
          && this.stringDecoderBufferSize == o.stringDecoderBufferSize
          && this.bufferSize == o.bufferSize;
    }

    /**
     * Creates an unpacker that deserializes objects from a specified input.
     *
     * <p>{@link MessageBufferInput} is an interface that lets applications customize memory
     * allocation of internal buffer of {@link MessageUnpacker}.
     *
     * @param in The input stream that provides sequence of buffer chunks and optionally reuses them
     *     when MessageUnpacker consumed one completely
     * @return A new MessageUnpacker instance
     */
    public MessageUnpacker newUnpacker(MessageBufferInput in) {
      return new MessageUnpacker(in, this);
    }

    /**
     * Creates an unpacker that deserializes objects from a specified input stream.
     *
     * <p>Note that you don't have to wrap InputStream in BufferedInputStream because
     * MessageUnpacker has buffering internally.
     *
     * @param in The input stream that provides sequence of bytes
     * @return A new MessageUnpacker instance
     */
    public MessageUnpacker newUnpacker(InputStream in) {
      return newUnpacker(new InputStreamBufferInput(in, bufferSize));
    }

    /**
     * Creates an unpacker that deserializes objects from a specified byte array.
     *
     * <p>This method provides an optimized implementation of <code>
     * newDefaultUnpacker(new ByteArrayInputStream(contents))</code>.
     *
     * @param contents The byte array that contains packed objects in MessagePack format
     * @return A new MessageUnpacker instance that will never throw IOException
     */
    public MessageUnpacker newUnpacker(byte[] contents) {
      return newUnpacker(new ArrayBufferInput(contents));
    }

    /**
     * Creates an unpacker that deserializes objects from subarray of a specified byte array.
     *
     * <p>This method provides an optimized implementation of <code>
     * newDefaultUnpacker(new ByteArrayInputStream(contents, offset, length))</code>.
     *
     * @param contents The byte array that contains packed objects
     * @param offset The index of the first byte
     * @param length The number of bytes
     * @return A new MessageUnpacker instance that will never throw IOException
     */
    public MessageUnpacker newUnpacker(byte[] contents, int offset, int length) {
      return newUnpacker(new ArrayBufferInput(contents, offset, length));
    }

    /** Allows unpackBinaryHeader to read str format family (default: true) */
    public MessagePack.UnpackerConfig withAllowReadingStringAsBinary(boolean enable) {
      MessagePack.UnpackerConfig copy = clone();
      copy.allowReadingStringAsBinary = enable;
      return copy;
    }

    public boolean getAllowReadingStringAsBinary() {
      return allowReadingStringAsBinary;
    }

    /**
     * Allows unpackString and unpackRawStringHeader and unpackString to read bin format family
     * (default: true)
     */
    public MessagePack.UnpackerConfig withAllowReadingBinaryAsString(boolean enable) {
      MessagePack.UnpackerConfig copy = clone();
      copy.allowReadingBinaryAsString = enable;
      return copy;
    }

    public boolean getAllowReadingBinaryAsString() {
      return allowReadingBinaryAsString;
    }

    /** Sets action when encountered a malformed input (default: REPLACE) */
    public MessagePack.UnpackerConfig withActionOnMalformedString(CodingErrorAction action) {
      MessagePack.UnpackerConfig copy = clone();
      copy.actionOnMalformedString = action;
      return copy;
    }

    public CodingErrorAction getActionOnMalformedString() {
      return actionOnMalformedString;
    }

    /** Sets action when an unmappable character is found (default: REPLACE) */
    public MessagePack.UnpackerConfig withActionOnUnmappableString(CodingErrorAction action) {
      MessagePack.UnpackerConfig copy = clone();
      copy.actionOnUnmappableString = action;
      return copy;
    }

    public CodingErrorAction getActionOnUnmappableString() {
      return actionOnUnmappableString;
    }

    /** unpackString size limit (default: Integer.MAX_VALUE). */
    public MessagePack.UnpackerConfig withStringSizeLimit(int bytes) {
      MessagePack.UnpackerConfig copy = clone();
      copy.stringSizeLimit = bytes;
      return copy;
    }

    public int getStringSizeLimit() {
      return stringSizeLimit;
    }

    /** */
    public MessagePack.UnpackerConfig withStringDecoderBufferSize(int bytes) {
      MessagePack.UnpackerConfig copy = clone();
      copy.stringDecoderBufferSize = bytes;
      return copy;
    }

    public int getStringDecoderBufferSize() {
      return stringDecoderBufferSize;
    }

    /**
     * When a packer is created with newUnpacker(OutputStream) or newUnpacker(WritableByteChannel),
     * the stream will be buffered with this size of buffer (default: 8192).
     */
    public MessagePack.UnpackerConfig withBufferSize(int bytes) {
      MessagePack.UnpackerConfig copy = clone();
      copy.bufferSize = bytes;
      return copy;
    }

    public int getBufferSize() {
      return bufferSize;
    }
  }
}
