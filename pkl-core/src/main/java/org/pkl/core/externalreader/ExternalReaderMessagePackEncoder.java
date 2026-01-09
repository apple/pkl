/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.pkl.core.externalreader.ExternalReaderMessages.*;
import org.pkl.core.messaging.BaseMessagePackEncoder;
import org.pkl.core.messaging.Message;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.util.Nullable;

public final class ExternalReaderMessagePackEncoder extends BaseMessagePackEncoder {

  public ExternalReaderMessagePackEncoder(MessagePacker packer) {
    super(packer);
  }

  public ExternalReaderMessagePackEncoder(OutputStream outputStream) {
    this(MessagePack.newDefaultPacker(outputStream));
  }

  @Override
  protected @Nullable void encodeMessage(Message msg) throws ProtocolException, IOException {
    switch (msg.type()) {
      case INITIALIZE_MODULE_READER_REQUEST -> {
        var m = (InitializeModuleReaderRequest) msg;
        packer.packMapHeader(2);
        packKeyValue("requestId", m.requestId());
        packKeyValue("scheme", m.scheme());
      }
      case INITIALIZE_RESOURCE_READER_REQUEST -> {
        var m = (InitializeResourceReaderRequest) msg;
        packer.packMapHeader(2);
        packKeyValue("requestId", m.requestId());
        packKeyValue("scheme", m.scheme());
      }
      case INITIALIZE_MODULE_READER_RESPONSE -> {
        var m = (InitializeModuleReaderResponse) msg;
        packMapHeader(1, m.spec());
        packKeyValue("requestId", m.requestId());
        if (m.spec() != null) {
          packer.packString("spec");
          packModuleReaderSpec(m.spec());
        }
      }
      case INITIALIZE_RESOURCE_READER_RESPONSE -> {
        var m = (InitializeResourceReaderResponse) msg;
        packMapHeader(1, m.spec());
        packKeyValue("requestId", m.requestId());
        if (m.spec() != null) {
          packer.packString("spec");
          packResourceReaderSpec(m.spec());
        }
      }
      case CLOSE_EXTERNAL_PROCESS -> packer.packMapHeader(0);
      default -> super.encodeMessage(msg);
    }
  }
}
