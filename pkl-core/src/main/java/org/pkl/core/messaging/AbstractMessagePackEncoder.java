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
package org.pkl.core.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.pkl.core.util.Nullable;

public abstract class AbstractMessagePackEncoder implements MessageEncoder {

  protected final MessagePacker packer;

  public AbstractMessagePackEncoder(MessagePacker packer) {
    this.packer = packer;
  }

  public AbstractMessagePackEncoder(OutputStream stream) {
    this(MessagePack.newDefaultPacker(stream));
  }

  protected abstract @Nullable void encodeMessage(Message msg)
      throws ProtocolException, IOException;

  @Override
  public final void encode(Message msg) throws IOException, ProtocolException {
    packer.packArrayHeader(2);
    packer.packInt(msg.getType().getCode());
    encodeMessage(msg);
    packer.flush();
  }

  protected void packMapHeader(int size, @Nullable Object value1) throws IOException {
    packer.packMapHeader(size + (value1 != null ? 1 : 0));
  }

  protected void packMapHeader(int size, @Nullable Object value1, @Nullable Object value2)
      throws IOException {
    packer.packMapHeader(size + (value1 != null ? 1 : 0) + (value2 != null ? 1 : 0));
  }

  protected void packMapHeader(
      int size,
      @Nullable Object value1,
      @Nullable Object value2,
      @Nullable Object value3,
      @Nullable Object value4,
      @Nullable Object value5,
      @Nullable Object value6,
      @Nullable Object value7,
      @Nullable Object value8,
      @Nullable Object value9,
      @Nullable Object valueA,
      @Nullable Object valueB,
      @Nullable Object valueC,
      @Nullable Object valueD)
      throws IOException {
    packer.packMapHeader(
        size
            + (value1 != null ? 1 : 0)
            + (value2 != null ? 1 : 0)
            + (value3 != null ? 1 : 0)
            + (value4 != null ? 1 : 0)
            + (value5 != null ? 1 : 0)
            + (value6 != null ? 1 : 0)
            + (value7 != null ? 1 : 0)
            + (value8 != null ? 1 : 0)
            + (value9 != null ? 1 : 0)
            + (valueA != null ? 1 : 0)
            + (valueB != null ? 1 : 0)
            + (valueC != null ? 1 : 0)
            + (valueD != null ? 1 : 0));
  }

  protected void packKeyValue(String name, @Nullable Integer value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packInt(value);
  }

  protected void packKeyValue(String name, @Nullable Long value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packLong(value);
  }

  protected <T> void packKeyValueLong(String name, @Nullable T value, Function<T, Long> mapper)
      throws IOException {
    if (value == null) {
      return;
    }
    packKeyValue(name, mapper.apply(value));
  }

  protected void packKeyValue(String name, @Nullable String value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packString(value);
  }

  protected <T> void packKeyValueString(String name, @Nullable T value, Function<T, String> mapper)
      throws IOException {
    if (value == null) {
      return;
    }
    packKeyValue(name, mapper.apply(value));
  }

  protected void packKeyValue(String name, @Nullable Collection<String> value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packArrayHeader(value.size());
    for (String elem : value) {
      packer.packString(elem);
    }
  }

  protected <T> void packKeyValue(
      String name, @Nullable Collection<T> value, Function<T, String> mapper) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packArrayHeader(value.size());
    for (T elem : value) {
      packer.packString(mapper.apply(elem));
    }
  }

  protected void packKeyValue(String name, @Nullable Map<String, String> value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packMapHeader(value.size());
    for (Map.Entry<String, String> e : value.entrySet()) {
      packer.packString(e.getKey());
      packer.packString(e.getValue());
    }
  }

  protected void packKeyValue(String name, byte[] value) throws IOException {
    if (value.length == 0) {
      return;
    }
    packer.packString(name);
    packer.packBinaryHeader(value.length);
    packer.writePayload(value);
  }

  protected void packKeyValue(String name, boolean value) throws IOException {
    packer.packString(name);
    packer.packBoolean(value);
  }
}
