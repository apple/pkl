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
package org.pkl.core.util.msgpack.core.buffer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.pkl.core.util.msgpack.core.Preconditions;
import sun.misc.Unsafe;

/**
 * MessageBuffer class is an abstraction of memory with fast methods to serialize and deserialize
 * primitive values to/from the memory. All MessageBuffer implementations ensure
 * short/int/float/long/double values are written in big-endian order.
 *
 * <p>Applications can allocate a new buffer using {@link #allocate(int)} method, or wrap an byte
 * array or ByteBuffer using {@link #wrap(byte[], int, int)} methods. {@link #wrap(ByteBuffer)}
 * method supports both direct buffers and array-backed buffers.
 *
 * <p>MessageBuffer class itself is optimized for little-endian CPU archtectures so that JVM
 * (HotSpot) can take advantage of the fastest JIT format which skips TypeProfile checking. To
 * ensure this performance, applications must not import unnecessary classes such as MessagePackBE.
 * On big-endian CPU archtectures, it automatically uses a subclass that includes TypeProfile
 * overhead but still faster than stndard ByteBuffer class. On JVMs older than Java 7 and JVMs
 * without Unsafe API (such as Android), implementation falls back to an universal implementation
 * that uses ByteBuffer internally.
 */
@SuppressWarnings("ALL")
public class MessageBuffer {
  static final boolean isUniversalBuffer;
  static final Unsafe unsafe;
  static final int javaVersion = getJavaVersion();

  /** Reference to MessageBuffer Constructors */
  private static final Constructor<?> mbArrConstructor;

  private static final Constructor<?> mbBBConstructor;

  /** The offset from the object memory header to its byte array data */
  static final int ARRAY_BYTE_BASE_OFFSET;

  private static final String UNIVERSAL_MESSAGE_BUFFER = "MessageBufferU";
  private static final String BIGENDIAN_MESSAGE_BUFFER = "MessageBufferBE";
  private static final String DEFAULT_MESSAGE_BUFFER = "MessageBuffer";

  static {
    boolean useUniversalBuffer = false;
    Unsafe unsafeInstance = null;
    int arrayByteBaseOffset = 16;

    try {
      boolean hasUnsafe = false;
      try {
        hasUnsafe = Class.forName("sun.misc.Unsafe") != null;
      } catch (Exception e) {
      }

      // Detect android VM
      boolean isAndroid =
          System.getProperty("java.runtime.name", "").toLowerCase().contains("android");

      // Is Google App Engine?
      boolean isGAE = System.getProperty("com.google.appengine.runtime.version") != null;

      // For Java6, android and JVM that has no Unsafe class, use Universal MessageBuffer (based on
      // ByteBuffer).
      useUniversalBuffer =
          Boolean.parseBoolean(System.getProperty("msgpack.universal-buffer", "false"))
              || isAndroid
              || isGAE
              || javaVersion < 7
              || !hasUnsafe;

      if (!useUniversalBuffer) {
        // Fetch theUnsafe object for Oracle and OpenJDK
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafeInstance = (Unsafe) field.get(null);
        if (unsafeInstance == null) {
          throw new RuntimeException("Unsafe is unavailable");
        }
        arrayByteBaseOffset = unsafeInstance.arrayBaseOffset(byte[].class);
        int arrayByteIndexScale = unsafeInstance.arrayIndexScale(byte[].class);

        // Make sure the VM thinks bytes are only one byte wide
        if (arrayByteIndexScale != 1) {
          throw new IllegalStateException(
              "Byte array index scale must be 1, but is " + arrayByteIndexScale);
        }
      }
    } catch (Exception e) {
      e.printStackTrace(System.err);
      // Use MessageBufferU
      useUniversalBuffer = true;
    } finally {
      // Initialize the static fields
      unsafe = unsafeInstance;
      ARRAY_BYTE_BASE_OFFSET = arrayByteBaseOffset;

      // Switch MessageBuffer implementation according to the environment
      isUniversalBuffer = useUniversalBuffer;
      String bufferClsName;
      if (isUniversalBuffer) {
        bufferClsName = UNIVERSAL_MESSAGE_BUFFER;
      } else {
        // Check the endian of this CPU
        boolean isLittleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
        bufferClsName = isLittleEndian ? DEFAULT_MESSAGE_BUFFER : BIGENDIAN_MESSAGE_BUFFER;
      }

      if (DEFAULT_MESSAGE_BUFFER.equals(bufferClsName)) {
        // No need to use reflection here, we're not using a MessageBuffer subclass.
        mbArrConstructor = null;
        mbBBConstructor = null;
      } else {
        try {
          // We need to use reflection here to find MessageBuffer implementation classes because
          // importing these classes creates TypeProfile and adds some overhead to method calls.

          // MessageBufferX (default, BE or U) class
          Class<?> bufferCls = Class.forName(bufferClsName);

          // MessageBufferX(byte[]) constructor
          Constructor<?> mbArrCstr =
              bufferCls.getDeclaredConstructor(byte[].class, int.class, int.class);
          mbArrCstr.setAccessible(true);
          mbArrConstructor = mbArrCstr;

          // MessageBufferX(ByteBuffer) constructor
          Constructor<?> mbBBCstr = bufferCls.getDeclaredConstructor(ByteBuffer.class);
          mbBBCstr.setAccessible(true);
          mbBBConstructor = mbBBCstr;
        } catch (Exception e) {
          e.printStackTrace(System.err);
          throw new RuntimeException(
              e); // No more fallback exists if MessageBuffer constructors are inaccessible
        }
      }
    }
  }

  private static int getJavaVersion() {
    String javaVersion = System.getProperty("java.specification.version", "");
    int dotPos = javaVersion.indexOf('.');
    if (dotPos != -1) {
      try {
        int major = Integer.parseInt(javaVersion.substring(0, dotPos));
        int minor = Integer.parseInt(javaVersion.substring(dotPos + 1));
        return major > 1 ? major : minor;
      } catch (NumberFormatException e) {
        e.printStackTrace(System.err);
      }
    } else {
      try {
        return Integer.parseInt(javaVersion);
      } catch (NumberFormatException e) {
        e.printStackTrace(System.err);
      }
    }
    return 6;
  }

  /**
   * Base object for resolving the relative address of the raw byte array. If base == null, the
   * address value is a raw memory address
   */
  protected final Object base;

  /**
   * Head address of the underlying memory. If base is null, the address is a direct memory address,
   * and if not, it is the relative address within an array object (base)
   */
  protected final long address;

  /** Size of the underlying memory */
  protected final int size;

  /**
   * Reference is used to hold a reference to an object that holds the underlying memory so that it
   * cannot be released by the garbage collector.
   */
  protected final ByteBuffer reference;

  /**
   * Allocates a new MessageBuffer backed by a byte array.
   *
   * @throws IllegalArgumentException If the capacity is a negative integer
   */
  public static MessageBuffer allocate(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative");
    }
    return wrap(new byte[size]);
  }

  /**
   * Wraps a byte array into a MessageBuffer.
   *
   * <p>The new MessageBuffer will be backed by the given byte array. Modifications to the new
   * MessageBuffer will cause the byte array to be modified and vice versa.
   *
   * <p>The new buffer's size will be array.length. hasArray() will return true.
   *
   * @param array the byte array that will gack this MessageBuffer
   * @return a new MessageBuffer that wraps the given byte array
   */
  public static MessageBuffer wrap(byte[] array) {
    return newMessageBuffer(array, 0, array.length);
  }

  /**
   * Wraps a byte array into a MessageBuffer.
   *
   * <p>The new MessageBuffer will be backed by the given byte array. Modifications to the new
   * MessageBuffer will cause the byte array to be modified and vice versa.
   *
   * <p>The new buffer's size will be length. hasArray() will return true.
   *
   * @param array the byte array that will gack this MessageBuffer
   * @param offset The offset of the subarray to be used; must be non-negative and no larger than
   *     array.length
   * @param length The length of the subarray to be used; must be non-negative and no larger than
   *     array.length - offset
   * @return a new MessageBuffer that wraps the given byte array
   */
  public static MessageBuffer wrap(byte[] array, int offset, int length) {
    return newMessageBuffer(array, offset, length);
  }

  /**
   * Wraps a ByteBuffer into a MessageBuffer.
   *
   * <p>The new MessageBuffer will be backed by the given byte buffer. Modifications to the new
   * MessageBuffer will cause the byte buffer to be modified and vice versa. However, change of
   * position, limit, or mark of given byte buffer doesn't affect MessageBuffer.
   *
   * <p>The new buffer's size will be bb.remaining(). hasArray() will return the same result with
   * bb.hasArray().
   *
   * @param bb the byte buffer that will gack this MessageBuffer
   * @throws IllegalArgumentException given byte buffer returns false both from hasArray() and
   *     isDirect()
   * @throws UnsupportedOperationException given byte buffer is a direct buffer and this platform
   *     doesn't support Unsafe API
   * @return a new MessageBuffer that wraps the given byte array
   */
  public static MessageBuffer wrap(ByteBuffer bb) {
    return newMessageBuffer(bb);
  }

  /**
   * Creates a new MessageBuffer instance backed by a java heap array
   *
   * @param arr
   * @return
   */
  private static MessageBuffer newMessageBuffer(byte[] arr, int off, int len) {
    if (mbArrConstructor != null) {
      return newInstance(mbArrConstructor, arr, off, len);
    }
    return new MessageBuffer(arr, off, len);
  }

  /**
   * Creates a new MessageBuffer instance backed by ByteBuffer
   *
   * @param bb
   * @return
   */
  private static MessageBuffer newMessageBuffer(ByteBuffer bb) {
    if (mbBBConstructor != null) {
      return newInstance(mbBBConstructor, bb);
    }
    return new MessageBuffer(bb);
  }

  /**
   * Creates a new MessageBuffer instance
   *
   * @param constructor A MessageBuffer constructor
   * @return new MessageBuffer instance
   */
  private static MessageBuffer newInstance(Constructor<?> constructor, Object... args) {
    try {
      // We need to use reflection to create MessageBuffer instances in order to prevent TypeProfile
      // generation for getInt method. TypeProfile will be
      // generated to resolve one of the method references when two or more classes overrides the
      // method.
      return (MessageBuffer) constructor.newInstance(args);
    } catch (InstantiationException e) {
      // should never happen
      throw new IllegalStateException(e);
    } catch (IllegalAccessException e) {
      // should never happen unless security manager restricts this reflection
      throw new IllegalStateException(e);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException) {
        // underlying constructor may throw RuntimeException
        throw (RuntimeException) e.getCause();
      } else if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      // should never happen
      throw new IllegalStateException(e.getCause());
    }
  }

  public static void releaseBuffer(MessageBuffer buffer) {}

  /**
   * Create a MessageBuffer instance from an java heap array
   *
   * @param arr
   * @param offset
   * @param length
   */
  MessageBuffer(byte[] arr, int offset, int length) {
    this.base = arr; // non-null is already checked at newMessageBuffer
    this.address = ARRAY_BYTE_BASE_OFFSET + offset;
    this.size = length;
    this.reference = null;
  }

  /**
   * Create a MessageBuffer instance from a given ByteBuffer instance
   *
   * @param bb
   */
  MessageBuffer(ByteBuffer bb) {
    this.base = bb.array();
    this.address = ARRAY_BYTE_BASE_OFFSET + bb.arrayOffset() + bb.position();
    this.size = bb.remaining();
    this.reference = null;
  }

  protected MessageBuffer(Object base, long address, int length) {
    this.base = base;
    this.address = address;
    this.size = length;
    this.reference = null;
  }

  /**
   * Gets the size of the buffer.
   *
   * <p>MessageBuffer doesn't have limit unlike ByteBuffer. Instead, you can use {@link #slice(int,
   * int)} to get a part of the buffer.
   *
   * @return number of bytes
   */
  public int size() {
    return size;
  }

  public MessageBuffer slice(int offset, int length) {
    // TODO ensure deleting this slice does not collapse this MessageBuffer
    if (offset == 0 && length == size()) {
      return this;
    } else {
      Preconditions.checkArgument(offset + length <= size());
      return new MessageBuffer(base, address + offset, length);
    }
  }

  public byte getByte(int index) {
    return unsafe.getByte(base, address + index);
  }

  public boolean getBoolean(int index) {
    return unsafe.getBoolean(base, address + index);
  }

  public short getShort(int index) {
    short v = unsafe.getShort(base, address + index);
    return Short.reverseBytes(v);
  }

  /**
   * Read a big-endian int value at the specified index
   *
   * @param index
   * @return
   */
  public int getInt(int index) {
    // Reading little-endian value
    int i = unsafe.getInt(base, address + index);
    // Reversing the endian
    return Integer.reverseBytes(i);
  }

  public float getFloat(int index) {
    return Float.intBitsToFloat(getInt(index));
  }

  public long getLong(int index) {
    long l = unsafe.getLong(base, address + index);
    return Long.reverseBytes(l);
  }

  public double getDouble(int index) {
    return Double.longBitsToDouble(getLong(index));
  }

  public void getBytes(int index, byte[] dst, int dstOffset, int length) {
    unsafe.copyMemory(base, address + index, dst, ARRAY_BYTE_BASE_OFFSET + dstOffset, length);
  }

  public void getBytes(int index, int len, ByteBuffer dst) {
    if (dst.remaining() < len) {
      throw new BufferOverflowException();
    }
    ByteBuffer src = sliceAsByteBuffer(index, len);
    dst.put(src);
  }

  public void putByte(int index, byte v) {
    unsafe.putByte(base, address + index, v);
  }

  public void putBoolean(int index, boolean v) {
    unsafe.putBoolean(base, address + index, v);
  }

  public void putShort(int index, short v) {
    v = Short.reverseBytes(v);
    unsafe.putShort(base, address + index, v);
  }

  /**
   * Write a big-endian integer value to the memory
   *
   * @param index
   * @param v
   */
  public void putInt(int index, int v) {
    // Reversing the endian
    v = Integer.reverseBytes(v);
    unsafe.putInt(base, address + index, v);
  }

  public void putFloat(int index, float v) {
    putInt(index, Float.floatToRawIntBits(v));
  }

  public void putLong(int index, long l) {
    // Reversing the endian
    l = Long.reverseBytes(l);
    unsafe.putLong(base, address + index, l);
  }

  public void putDouble(int index, double v) {
    putLong(index, Double.doubleToRawLongBits(v));
  }

  public void putBytes(int index, byte[] src, int srcOffset, int length) {
    unsafe.copyMemory(src, ARRAY_BYTE_BASE_OFFSET + srcOffset, base, address + index, length);
  }

  public void putByteBuffer(int index, ByteBuffer src, int len) {
    assert (len <= src.remaining());
    assert (!isUniversalBuffer);
    assert (!src.isDirect());
    if (src.hasArray()) {
      byte[] srcArray = src.array();
      unsafe.copyMemory(
          srcArray, ARRAY_BYTE_BASE_OFFSET + src.position(), base, address + index, len);
      src.position(src.position() + len);
    } else {
      if (hasArray()) {
        src.get((byte[]) base, index, len);
      } else {
        for (int i = 0; i < len; ++i) {
          unsafe.putByte(base, address + index, src.get());
        }
      }
    }
  }

  public void putMessageBuffer(int index, MessageBuffer src, int srcOffset, int len) {
    unsafe.copyMemory(src.base, src.address + srcOffset, base, address + index, len);
  }

  /**
   * Create a ByteBuffer view of the range [index, index+length) of this memory
   *
   * @param index
   * @param length
   * @return
   */
  public ByteBuffer sliceAsByteBuffer(int index, int length) {
    assert hasArray();
    return ByteBuffer.wrap(
        (byte[]) base, (int) ((address - ARRAY_BYTE_BASE_OFFSET) + index), length);
  }

  /**
   * Get a ByteBuffer view of this buffer
   *
   * @return
   */
  public ByteBuffer sliceAsByteBuffer() {
    return sliceAsByteBuffer(0, size());
  }

  public boolean hasArray() {
    return base != null;
  }

  /**
   * Get a copy of this buffer
   *
   * @return
   */
  public byte[] toByteArray() {
    byte[] b = new byte[size()];
    unsafe.copyMemory(base, address, b, ARRAY_BYTE_BASE_OFFSET, size());
    return b;
  }

  public byte[] array() {
    return (byte[]) base;
  }

  public int arrayOffset() {
    return (int) address - ARRAY_BYTE_BASE_OFFSET;
  }

  /**
   * Copy this buffer contents to another MessageBuffer
   *
   * @param index
   * @param dst
   * @param offset
   * @param length
   */
  public void copyTo(int index, MessageBuffer dst, int offset, int length) {
    unsafe.copyMemory(base, address + index, dst.base, dst.address + offset, length);
  }

  public String toHexString(int offset, int length) {
    StringBuilder s = new StringBuilder();
    for (int i = offset; i < length; ++i) {
      if (i != offset) {
        s.append(" ");
      }
      s.append(String.format("%02x", getByte(i)));
    }
    return s.toString();
  }
}
