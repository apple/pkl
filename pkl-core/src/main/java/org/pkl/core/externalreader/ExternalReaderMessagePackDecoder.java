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
package org.pkl.core.externalreader;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Map;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.pkl.core.externalreader.ExternalReaderMessages.*;
import org.pkl.core.messaging.BaseMessagePackDecoder;
import org.pkl.core.messaging.DecodeException;
import org.pkl.core.messaging.Message;
import org.pkl.core.messaging.Message.Type;
import org.pkl.core.util.Nullable;

public class ExternalReaderMessagePackDecoder extends BaseMessagePackDecoder {

  public ExternalReaderMessagePackDecoder(MessageUnpacker unpacker) {
    super(unpacker);
  }

  public ExternalReaderMessagePackDecoder(InputStream inputStream) {
    this(MessagePack.newDefaultUnpacker(inputStream));
  }

  @Override
  protected @Nullable Message decodeMessage(Type msgType, Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    return switch (msgType) {
      case INITIALIZE_MODULE_READER_REQUEST ->
          new InitializeModuleReaderRequest(
              unpackLong(map, "requestId"), unpackString(map, "scheme"));
      case INITIALIZE_RESOURCE_READER_REQUEST ->
          new InitializeResourceReaderRequest(
              unpackLong(map, "requestId"), unpackString(map, "scheme"));
      case INITIALIZE_MODULE_READER_RESPONSE ->
          new InitializeModuleReaderResponse(
              unpackLong(map, "requestId"), unpackModuleReaderSpec(getNullable(map, "spec")));
      case INITIALIZE_RESOURCE_READER_RESPONSE ->
          new InitializeResourceReaderResponse(
              unpackLong(map, "requestId"), unpackResourceReaderSpec(getNullable(map, "spec")));
      case CLOSE_EXTERNAL_PROCESS -> new CloseExternalProcess();
      default -> super.decodeMessage(msgType, map);
    };
  }
}
