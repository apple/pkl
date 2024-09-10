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

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.pkl.core.messaging.Message.Type;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.module.PathElement;
import org.pkl.core.util.Nullable;

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

  protected static @Nullable List<ModuleReaderSpec> unpackModuleReaderSpec(Map<Value, Value> map)
      throws DecodeException {
    var keys = getNullable(map, "clientModuleReaders");
    if (keys == null) {
      return null;
    }

    var result = new ArrayList<ModuleReaderSpec>(keys.asArrayValue().size());
    for (Value value : keys.asArrayValue()) {
      var readerMap = value.asMapValue().map();
      result.add(
          new ModuleReaderSpec(
              unpackString(readerMap, "scheme"),
              unpackBoolean(readerMap, "hasHierarchicalUris"),
              unpackBoolean(readerMap, "isLocal"),
              unpackBoolean(readerMap, "isGlobbable")));
    }
    return result;
  }

  protected static @Nullable List<ResourceReaderSpec> unpackResourceReaderSpec(
      Map<Value, Value> map) throws DecodeException {
    var keys = getNullable(map, "clientResourceReaders");
    if (keys == null) {
      return null;
    }

    var result = new ArrayList<ResourceReaderSpec>(keys.asArrayValue().size());
    for (Value value : keys.asArrayValue()) {
      var readerMap = value.asMapValue().map();
      result.add(
          new ResourceReaderSpec(
              unpackString(readerMap, "scheme"),
              unpackBoolean(readerMap, "hasHierarchicalUris"),
              unpackBoolean(readerMap, "isGlobbable")));
    }
    return result;
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
