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
import org.msgpack.core.MessagePacker;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.module.PathElement;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

public class BaseMessagePackEncoder extends AbstractMessagePackEncoder {

  public BaseMessagePackEncoder(MessagePacker packer) {
    super(packer);
  }

  public BaseMessagePackEncoder(OutputStream stream) {
    super(stream);
  }

  protected void packModuleReaderSpec(ModuleReaderSpec reader) throws IOException {
    packer.packMapHeader(4);
    packKeyValue("scheme", reader.scheme());
    packKeyValue("hasHierarchicalUris", reader.hasHierarchicalUris());
    packKeyValue("isLocal", reader.isLocal());
    packKeyValue("isGlobbable", reader.isGlobbable());
  }

  protected void packResourceReaderSpec(ResourceReaderSpec reader) throws IOException {
    packer.packMapHeader(3);
    packKeyValue("scheme", reader.scheme());
    packKeyValue("hasHierarchicalUris", reader.hasHierarchicalUris());
    packKeyValue("isGlobbable", reader.isGlobbable());
  }

  protected void packPathElement(PathElement pathElement) throws IOException {
    packer.packMapHeader(2);
    packKeyValue("name", pathElement.getName());
    packKeyValue("isDirectory", pathElement.isDirectory());
  }

  protected @Nullable void encodeMessage(Message msg) throws ProtocolException, IOException {
    switch (msg.type()) {
      case READ_RESOURCE_REQUEST -> {
        var m = (ReadResourceRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("uri", m.uri().toString());
      }
      case READ_RESOURCE_RESPONSE -> {
        var m = (ReadResourceResponse) msg;
        packMapHeader(2, m.contents(), m.error());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("contents", m.contents());
        packKeyValue("error", m.error());
      }
      case READ_MODULE_REQUEST -> {
        var m = (ReadModuleRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("uri", m.uri().toString());
      }
      case READ_MODULE_RESPONSE -> {
        var m = (ReadModuleResponse) msg;
        packMapHeader(2, m.contents(), m.error());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("contents", m.contents());
        packKeyValue("error", m.error());
      }
      case LIST_RESOURCES_REQUEST -> {
        var m = (ListResourcesRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("uri", m.uri().toString());
      }
      case LIST_RESOURCES_RESPONSE -> {
        var m = (ListResourcesResponse) msg;
        packMapHeader(2, m.pathElements(), m.error());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        if (m.pathElements() != null) {
          packer.packString("pathElements");
          packer.packArrayHeader(m.pathElements().size());
          for (var pathElement : m.pathElements()) {
            packPathElement(pathElement);
          }
        }
        packKeyValue("error", m.error());
      }
      case LIST_MODULES_REQUEST -> {
        var m = (ListModulesRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("uri", m.uri().toString());
      }
      case LIST_MODULES_RESPONSE -> {
        var m = (ListModulesResponse) msg;
        packMapHeader(2, m.pathElements(), m.error());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        if (m.pathElements() != null) {
          packer.packString("pathElements");
          packer.packArrayHeader(m.pathElements().size());
          for (var pathElement : m.pathElements()) {
            packPathElement(pathElement);
          }
        }
        packKeyValue("error", m.error());
      }
      default ->
          throw new ProtocolException(
              ErrorMessages.create("unhandledMessageType", msg.type().toString()));
    }
  }
}
