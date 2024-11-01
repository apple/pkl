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

import java.util.ArrayList;
import java.util.List;

/**
 * MessageBufferOutput adapter that writes data into a list of byte arrays.
 *
 * <p>This class allocates a new buffer instead of resizing the buffer when data doesn't fit in the
 * initial capacity. This is faster than ByteArrayOutputStream especially when size of written bytes
 * is large because resizing a buffer usually needs to copy contents of the buffer.
 */
@SuppressWarnings("ALL")
public class ArrayBufferOutput implements MessageBufferOutput {
  private final List<MessageBuffer> list;
  private final int bufferSize;
  private MessageBuffer lastBuffer;

  public ArrayBufferOutput() {
    this(8192);
  }

  public ArrayBufferOutput(int bufferSize) {
    this.bufferSize = bufferSize;
    this.list = new ArrayList<MessageBuffer>();
  }

  /**
   * Gets the size of the written data.
   *
   * @return number of bytes
   */
  public int getSize() {
    int size = 0;
    for (MessageBuffer buffer : list) {
      size += buffer.size();
    }
    return size;
  }

  /**
   * Gets a copy of the written data as a byte array.
   *
   * <p>If your application needs better performance and smaller memory consumption, you may prefer
   * {@link #toMessageBuffer()} or {@link #toBufferList()} to avoid copying.
   *
   * @return the byte array
   */
  public byte[] toByteArray() {
    byte[] data = new byte[getSize()];
    int off = 0;
    for (MessageBuffer buffer : list) {
      buffer.getBytes(0, data, off, buffer.size());
      off += buffer.size();
    }
    return data;
  }

  /**
   * Gets the written data as a MessageBuffer.
   *
   * <p>Unlike {@link #toByteArray()}, this method omits copy of the contents if size of the written
   * data is smaller than a single buffer capacity.
   *
   * @return the MessageBuffer instance
   */
  public MessageBuffer toMessageBuffer() {
    if (list.size() == 1) {
      return list.get(0);
    } else if (list.isEmpty()) {
      return MessageBuffer.allocate(0);
    } else {
      return MessageBuffer.wrap(toByteArray());
    }
  }

  /**
   * Returns the written data as a list of MessageBuffer.
   *
   * <p>Unlike {@link #toByteArray()} or {@link #toMessageBuffer()}, this is the fastest method that
   * doesn't copy contents in any cases.
   *
   * @return the list of MessageBuffer instances
   */
  public List<MessageBuffer> toBufferList() {
    return new ArrayList<MessageBuffer>(list);
  }

  /** Clears the written data. */
  public void clear() {
    list.clear();
  }

  @Override
  public MessageBuffer next(int minimumSize) {
    if (lastBuffer != null && lastBuffer.size() > minimumSize) {
      return lastBuffer;
    } else {
      int size = Math.max(bufferSize, minimumSize);
      MessageBuffer buffer = MessageBuffer.allocate(size);
      lastBuffer = buffer;
      return buffer;
    }
  }

  @Override
  public void writeBuffer(int length) {
    list.add(lastBuffer.slice(0, length));
    if (lastBuffer.size() - length > bufferSize / 4) {
      lastBuffer = lastBuffer.slice(length, lastBuffer.size() - length);
    } else {
      lastBuffer = null;
    }
  }

  @Override
  public void write(byte[] buffer, int offset, int length) {
    MessageBuffer copy = MessageBuffer.allocate(length);
    copy.putBytes(0, buffer, offset, length);
    list.add(copy);
  }

  @Override
  public void add(byte[] buffer, int offset, int length) {
    MessageBuffer wrapped = MessageBuffer.wrap(buffer, offset, length);
    list.add(wrapped);
  }

  @Override
  public void close() {}

  @Override
  public void flush() {}
}
