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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.msgpack.core.MessageTypeException;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableStringValueImpl;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.Proxy;
import org.pkl.core.messaging.Message.*;
import org.pkl.core.module.PathElement;
import org.pkl.core.packages.Checksums;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

public class MessagePackDecoder implements MessageDecoder {

  private final MessageUnpacker unpacker;

  public MessagePackDecoder(MessageUnpacker unpacker) {
    this.unpacker = unpacker;
  }

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
          ErrorMessages.create("malformedMessageHeaderUnrecognizedCode", code), e);
    }

    try {
      var map = unpacker.unpackValue().asMapValue().map();
      return switch (msgType) {
        case CREATE_EVALUATOR_REQUEST ->
            new CreateEvaluatorRequest(
                get(map, "requestId").asIntegerValue().asLong(),
                unpackStringListOrNull(map, "allowedModules", Pattern::compile),
                unpackStringListOrNull(map, "allowedResources", Pattern::compile),
                unpackModuleReaderSpec(map),
                unpackResourceReaderSpec(map),
                unpackStringListOrNull(map, "modulePaths", Path::of),
                unpackStringMapOrNull(map, "env"),
                unpackStringMapOrNull(map, "properties"),
                unpackLongOrNull(map, "timeoutSeconds", Duration::ofSeconds),
                unpackStringOrNull(map, "rootDir", Path::of),
                unpackStringOrNull(map, "cacheDir", Path::of),
                unpackStringOrNull(map, "outputFormat"),
                unpackProject(map),
                unpackHttp(map));
        case CREATE_EVALUATOR_RESPONSE ->
            new CreateEvaluatorResponse(
                unpackLong(map, "requestId"),
                unpackLongOrNull(map, "evaluatorId"),
                unpackStringOrNull(map, "error"));
        case CLOSE_EVALUATOR -> new CloseEvaluator(unpackLong(map, "evaluatorId"));
        case EVALUATE_REQUEST ->
            new EvaluateRequest(
                unpackLong(map, "requestId"),
                unpackLong(map, "evaluatorId"),
                new URI(unpackString(map, "moduleUri")),
                unpackStringOrNull(map, "moduleText"),
                unpackStringOrNull(map, "expr"));
        case EVALUATE_RESPONSE ->
            new EvaluateResponse(
                unpackLong(map, "requestId"),
                unpackLong(map, "evaluatorId"),
                unpackByteArray(map, "result"),
                unpackStringOrNull(map, "error"));
        case LOG_MESSAGE ->
            new LogMessage(
                unpackLong(map, "evaluatorId"),
                unpackInt(map, "level"),
                unpackString(map, "message"),
                unpackString(map, "frameUri"));
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
      };

    } catch (MessageTypeException | URISyntaxException e) {
      throw new DecodeException(ErrorMessages.create("malformedMessageBody", code), e);
    }
  }

  private static @Nullable Value getNullable(Map<Value, Value> map, String key) {
    return map.get(new ImmutableStringValueImpl(key));
  }

  private static Value get(Map<Value, Value> map, String key) throws DecodeException {
    var value = map.get(new ImmutableStringValueImpl(key));
    if (value == null) {
      throw new DecodeException(ErrorMessages.create("missingMessageParameter", key));
    }
    return value;
  }

  private static String unpackString(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asStringValue().asString();
  }

  private static <T> T unpackString(Map<Value, Value> map, String key, Function<String, T> mapper)
      throws DecodeException {
    return mapper.apply(get(map, key).asStringValue().asString());
  }

  private static @Nullable String unpackStringOrNull(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return value.asStringValue().asString();
  }

  private static <T> @Nullable T unpackStringOrNull(
      Map<Value, Value> map, String key, Function<String, T> mapper) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return mapper.apply(value.asStringValue().asString());
  }

  private static byte[] unpackByteArray(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return new byte[0];
    }
    return value.asBinaryValue().asByteArray();
  }

  private static boolean unpackBoolean(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asBooleanValue().getBoolean();
  }

  private static int unpackInt(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asIntegerValue().asInt();
  }

  private static long unpackLong(Map<Value, Value> map, String key) throws DecodeException {
    return get(map, key).asIntegerValue().asLong();
  }

  private static @Nullable Long unpackLongOrNull(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    return value.asIntegerValue().asLong();
  }

  private static <T> @Nullable T unpackLongOrNull(
      Map<Value, Value> map, String key, Function<Long, T> mapper) {
    var value = unpackLongOrNull(map, key);
    if (value == null) {
      return null;
    }
    return mapper.apply(value);
  }

  private static @Nullable List<String> unpackStringListOrNull(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }

    return value.asArrayValue().list().stream().map((it) -> it.asStringValue().asString()).toList();
  }

  private static @Nullable Map<String, String> unpackStringMapOrNull(
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

  private static <T> @Nullable List<T> unpackStringListOrNull(
      Map<Value, Value> map, String key, Function<String, T> mapper) {
    var value = unpackStringListOrNull(map, key);
    if (value == null) {
      return null;
    }

    return value.stream().map(mapper).toList();
  }

  private static @Nullable List<ModuleReaderSpec> unpackModuleReaderSpec(Map<Value, Value> map)
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

  private static @Nullable List<ResourceReaderSpec> unpackResourceReaderSpec(Map<Value, Value> map)
      throws DecodeException {
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

  private static @Nullable Project unpackProject(Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    var projVal = getNullable(map, "project");
    if (projVal == null) {
      return null;
    }
    var projMap = projVal.asMapValue().map();
    var projectFileUri = new URI(unpackString(projMap, "projectFileUri"));
    var dependencies = unpackDependencies(projMap, "dependencies");
    return new Project(projectFileUri, null, dependencies);
  }

  private static Map<String, Dependency> unpackDependencies(Map<Value, Value> map, String name)
      throws DecodeException, URISyntaxException {
    var mapValue = get(map, name).asMapValue().map();

    var result = new HashMap<String, Dependency>(mapValue.size());
    for (Map.Entry<Value, Value> entry : mapValue.entrySet()) {
      var dependencyName = entry.getKey().asStringValue().asString();
      var dependencyObj = entry.getValue().asMapValue().map();
      var type = unpackString(dependencyObj, "type");
      var packageUri = new URI(unpackString(dependencyObj, "packageUri"));

      if (type.equals(Dependency.Type.REMOTE.getType())) {
        var checksumsObj = getNullable(dependencyObj, "checksums");
        if (checksumsObj == null) {
          result.put(dependencyName, new RemoteDependency(packageUri, null));
        } else {
          result.put(
              dependencyName,
              new RemoteDependency(
                  packageUri,
                  new Checksums(unpackString(checksumsObj.asMapValue().map(), "sha256"))));
        }
      } else {
        var dependencies = unpackDependencies(dependencyObj, "dependencies");
        var projectFileUri = unpackString(dependencyObj, "projectFileUri");
        result.put(dependencyName, new Project(new URI(projectFileUri), packageUri, dependencies));
      }
    }
    return result;
  }

  private static @Nullable Http unpackHttp(Map<Value, Value> map) throws DecodeException {
    var httpVal = getNullable(map, "http");
    if (httpVal == null) {
      return null;
    }

    var httpMap = httpVal.asMapValue().map();
    var proxy = unpackProxy(httpMap);

    var caCertificates = getNullable(httpMap, "caCertificates");
    if (caCertificates == null) {
      return new Http(new byte[0], proxy);
    }

    return new Http(caCertificates.asBinaryValue().asByteArray(), proxy);
  }

  private static @Nullable PklEvaluatorSettings.Proxy unpackProxy(Map<Value, Value> map)
      throws DecodeException {
    var proxyVal = getNullable(map, "proxy");
    if (proxyVal == null) {
      return null;
    }
    var proxyMap = proxyVal.asMapValue().map();
    var address = unpackString(proxyMap, "address");
    var noProxy = unpackStringListOrNull(proxyMap, "noProxy");
    return Proxy.create(address, noProxy);
  }

  private static @Nullable List<PathElement> unpackPathElements(Map<Value, Value> map, String key)
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
