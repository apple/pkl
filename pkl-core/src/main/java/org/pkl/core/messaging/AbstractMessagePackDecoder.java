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
package org.pkl.core.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.pkl.core.messaging.Message.Type;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.msgpack.core.MessagePack;
import org.pkl.core.util.msgpack.core.MessageTypeException;
import org.pkl.core.util.msgpack.core.MessageUnpacker;
import org.pkl.core.util.msgpack.value.Value;
import org.pkl.core.util.msgpack.value.impl.ImmutableStringValueImpl;

public abstract class AbstractMessagePackDecoder implements MessageDecoder {

  protected final MessageUnpacker unpacker;

  public AbstractMessagePackDecoder(MessageUnpacker unpacker) {
    this.unpacker = unpacker;
  }

  public AbstractMessagePackDecoder(InputStream stream) {
    this(MessagePack.newDefaultUnpacker(stream));
  }

  protected abstract @Nullable Message decodeMessage(Type msgType, Map<Value, Value> map)
      throws DecodeException, URISyntaxException;

  @Override
  public @Nullable Message decode() throws IOException, DecodeException {
    if (!unpacker.hasNext()) {
      return null;
    }

    int code;
    try {
      var arraySize = unpacker.unpackArrayHeader();
      if (arraySize != 2) {
        throw new DecodeException(ErrorMessages.create("malformedMessageHeaderLength", arraySize));
      }
      code = unpacker.unpackInt();
    } catch (MessageTypeException e) {
      throw new DecodeException(ErrorMessages.create("malformedMessageHeaderException"), e);
    }

    Type msgType;
    try {
      msgType = Type.fromInt(code);
    } catch (IllegalArgumentException e) {
      throw new DecodeException(
          ErrorMessages.create("malformedMessageHeaderUnrecognizedCode", Integer.toHexString(code)),
          e);
    }

    try {
      var map = unpacker.unpackValue().asMapValue().map();
      var decoded = decodeMessage(msgType, map);
      if (decoded != null) {
        return decoded;
      }
      throw new DecodeException(
          ErrorMessages.create("unhandledMessageCode", Integer.toHexString(code)));
    } catch (MessageTypeException | URISyntaxException e) {
      throw new DecodeException(ErrorMessages.create("malformedMessageBody", code), e);
    }
  }

  protected static @Nullable Value getNullable(Map<Value, Value> map, String key) {
    return map.get(new ImmutableStringValueImpl(key));
  }

  protected static Value get(Map<Value, Value> map, String key) throws DecodeException {
    var value = map.get(new ImmutableStringValueImpl(key));
    if (value == null) {
      throw new DecodeException(ErrorMessages.create("missingMessageParameter", key));
    }
    return value;
  }

  protected static String unpackString(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asStringValue().asString();
  }

  protected static @Nullable String unpackStringOrNull(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return value.asStringValue().asString();
  }

  protected static <T> @Nullable T unpackStringOrNull(
      Map<Value, Value> map, String key, Function<String, T> mapper) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return mapper.apply(value.asStringValue().asString());
  }

  protected static byte @Nullable [] unpackByteArray(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return value.asBinaryValue().asByteArray();
  }

  protected static boolean unpackBoolean(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asBooleanValue().getBoolean();
  }

  protected static int unpackInt(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asIntegerValue().asInt();
  }

  protected static long unpackLong(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asIntegerValue().asLong();
  }

  protected static @Nullable Long unpackLongOrNull(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return value.asIntegerValue().asLong();
  }

  protected static <T> @Nullable T unpackLongOrNull(
      Map<Value, Value> map, String key, Function<Long, T> mapper) {
    var value = unpackLongOrNull(map, key);
    if (value == null) {
      return null;
    }
    return mapper.apply(value);
  }

  protected static @Nullable List<String> unpackStringListOrNull(
      Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }

    return value.asArrayValue().list().stream().map((it) -> it.asStringValue().asString()).toList();
  }

  protected static @Nullable Map<String, String> unpackStringMapOrNull(
      Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }

    return value.asMapValue().entrySet().stream()
        .collect(
            Collectors.toMap(
                (e) -> e.getKey().asStringValue().asString(),
                (e) -> e.getValue().asStringValue().asString()));
  }

  protected static <T> @Nullable List<T> unpackStringListOrNull(
      Map<Value, Value> map, String key, Function<String, T> mapper) {
    var value = unpackStringListOrNull(map, key);
    if (value == null) {
      return null;
    }

    return value.stream().map(mapper).toList();
  }

  protected static <T> @Nullable List<T> unpackListOrNull(
      Map<Value, Value> map, String key, Function<Value, T> mapper) {
    var keys = getNullable(map, key);
    if (keys == null) {
      return null;
    }

    var result = new ArrayList<T>(keys.asArrayValue().size());
    for (Value value : keys.asArrayValue()) {
      result.add(mapper.apply(value));
    }
    return result;
  }

  protected static <T> @Nullable Map<String, T> unpackStringMapOrNull(
      Map<Value, Value> map, String key, Function<Map<Value, Value>, T> mapper) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }

    return value.asMapValue().entrySet().stream()
        .collect(
            Collectors.toMap(
                (e) -> e.getKey().asStringValue().asString(),
                (e) -> mapper.apply(e.getValue().asMapValue().map())));
  }
}
