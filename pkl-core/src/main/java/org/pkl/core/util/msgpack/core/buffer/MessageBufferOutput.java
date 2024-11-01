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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Provides a buffered output stream that writes sequence of MessageBuffer instances.
 *
 * <p>A MessageBufferOutput implementation has total control of the buffer memory so that it can
 * reuse buffer memory, use buffer pools, or use memory-mapped files.
 */
@SuppressWarnings("ALL")
public interface MessageBufferOutput extends Closeable, Flushable {
  /**
   * Allocates the next buffer to write.
   *
   * <p>This method should return a MessageBuffer instance that has specified size of capacity at
   * least.
   *
   * <p>When this method is called twice, the previously returned buffer is no longer used. This
   * method may be called twice without call of {@link #writeBuffer(int)} in between. In this case,
   * the buffer should be discarded without flushing it to the output.
   *
   * @param minimumSize the mimium required buffer size to allocate
   * @return the MessageBuffer instance with at least minimumSize bytes of capacity
   * @throws IOException
   */
  MessageBuffer next(int minimumSize) throws IOException;

  /**
   * Writes the previously allocated buffer.
   *
   * <p>This method should write the buffer previously returned from {@link #next(int)} method until
   * specified number of bytes. Once the buffer is written, the buffer is no longer used.
   *
   * <p>This method is not always called for each {@link #next(int)} call. In this case, the buffer
   * should be discarded without flushing it to the output when the next {@link #next(int)} is
   * called.
   *
   * @param length the number of bytes to write
   * @throws IOException
   */
  void writeBuffer(int length) throws IOException;

  /**
   * Writes an external payload data. This method should follow semantics of OutputStream.
   *
   * @param buffer the data to write
   * @param offset the start offset in the data
   * @param length the number of bytes to write
   * @throws IOException
   */
  void write(byte[] buffer, int offset, int length) throws IOException;

  /**
   * Writes an external payload data.
   *
   * <p>Unlike {@link #write(byte[], int, int)} method, the buffer is given - this
   * MessageBufferOutput implementation gets ownership of the buffer and may modify contents of the
   * buffer. Contents of this buffer won't be modified by the caller.
   *
   * @param buffer the data to add
   * @param offset the start offset in the data
   * @param length the number of bytes to add
   * @throws IOException
   */
  void add(byte[] buffer, int offset, int length) throws IOException;
}
