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

import static org.pkl.core.util.msgpack.core.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import org.pkl.core.util.msgpack.core.buffer.MessageBuffer;
import org.pkl.core.util.msgpack.core.buffer.MessageBufferInput;

/** {@link MessageBufferInput} adapter for {@link InputStream} */
@SuppressWarnings("ALL")
public class InputStreamBufferInput implements MessageBufferInput {
  private InputStream in;
  private final byte[] buffer;

  public static MessageBufferInput newBufferInput(InputStream in) {
    checkNotNull(in, "InputStream is null");
    return new InputStreamBufferInput(in);
  }

  public InputStreamBufferInput(InputStream in) {
    this(in, 8192);
  }

  public InputStreamBufferInput(InputStream in, int bufferSize) {
    this.in = checkNotNull(in, "input is null");
    this.buffer = new byte[bufferSize];
  }

  /**
   * Reset Stream. This method doesn't close the old resource.
   *
   * @param in new stream
   * @return the old resource
   */
  public InputStream reset(InputStream in) throws IOException {
    InputStream old = this.in;
    this.in = in;
    return old;
  }

  @Override
  public MessageBuffer next() throws IOException {
    int readLen = in.read(buffer);
    if (readLen == -1) {
      return null;
    }
    return MessageBuffer.wrap(buffer, 0, readLen);
  }

  @Override
  public void close() throws IOException {
    in.close();
  }
}
