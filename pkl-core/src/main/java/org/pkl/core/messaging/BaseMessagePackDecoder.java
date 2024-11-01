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

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.pkl.core.messaging.Message.Type;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.module.PathElement;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.msgpack.core.MessageUnpacker;
import org.pkl.core.util.msgpack.value.Value;

public class BaseMessagePackDecoder extends AbstractMessagePackDecoder {

  public BaseMessagePackDecoder(MessageUnpacker unpacker) {
    super(unpacker);
  }

  public BaseMessagePackDecoder(InputStream stream) {
    super(stream);
  }

  protected @Nullable Message decodeMessage(Type msgType, Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    return switch (msgType) {
      case READ_RESOURCE_REQUEST ->
          new ReadResourceRequest(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              new URI(unpackString(map, "uri")));
      case READ_RESOURCE_RESPONSE ->
          new ReadResourceResponse(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              unpackByteArray(map, "contents"),
              unpackStringOrNull(map, "error"));
      case READ_MODULE_REQUEST ->
          new ReadModuleRequest(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              new URI(unpackString(map, "uri")));
      case READ_MODULE_RESPONSE ->
          new ReadModuleResponse(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              unpackStringOrNull(map, "contents"),
              unpackStringOrNull(map, "error"));
      case LIST_RESOURCES_REQUEST ->
          new ListResourcesRequest(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              new URI(unpackString(map, "uri")));
      case LIST_RESOURCES_RESPONSE ->
          new ListResourcesResponse(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              unpackPathElements(map, "pathElements"),
              unpackStringOrNull(map, "error"));
      case LIST_MODULES_REQUEST ->
          new ListModulesRequest(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              new URI(unpackString(map, "uri")));
      case LIST_MODULES_RESPONSE ->
          new ListModulesResponse(
              unpackLong(map, "requestId"),
              unpackLong(map, "evaluatorId"),
              unpackPathElements(map, "pathElements"),
              unpackStringOrNull(map, "error"));
      default -> null;
    };
  }

  protected static @Nullable ModuleReaderSpec unpackModuleReaderSpec(@Nullable Value value)
      throws DecodeException {
    if (value == null) {
      return null;
    }
    var map = value.asMapValue().map();
    return new ModuleReaderSpec(
        unpackString(map, "scheme"),
        unpackBoolean(map, "hasHierarchicalUris"),
        unpackBoolean(map, "isLocal"),
        unpackBoolean(map, "isGlobbable"));
  }

  protected static @Nullable ResourceReaderSpec unpackResourceReaderSpec(@Nullable Value value)
      throws DecodeException {
    if (value == null) {
      return null;
    }
    var map = value.asMapValue().map();
    return new ResourceReaderSpec(
        unpackString(map, "scheme"),
        unpackBoolean(map, "hasHierarchicalUris"),
        unpackBoolean(map, "isGlobbable"));
  }

  protected static @Nullable List<PathElement> unpackPathElements(Map<Value, Value> map, String key)
      throws DecodeException {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }

    var result = new ArrayList<PathElement>(value.asArrayValue().size());
    for (Value pathElement : value.asArrayValue()) {
      var pathElementMap = pathElement.asMapValue().map();
      result.add(
          new PathElement(
              unpackString(pathElementMap, "name"), unpackBoolean(pathElementMap, "isDirectory")));
    }
    return result;
  }
}
