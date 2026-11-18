/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageTypeException;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableStringValueImpl;
import org.pkl.messaging.Message.Type;
import org.pkl.messaging.Messages.*;
import org.pkl.messaging.externalreader.ExternalReader;
import org.pkl.messaging.externalreader.ModuleReaderSpec;
import org.pkl.messaging.externalreader.ResourceReaderSpec;
import org.pkl.messaging.util.ErrorMessages;

/** Decodes all messages exchanged between a Pkl server, its clients, and external readers. */
public final class MessagePackDecoder implements MessageDecoder {

  private final MessageUnpacker unpacker;

  public MessagePackDecoder(MessageUnpacker unpacker) {
    this.unpacker = unpacker;
  }

  public MessagePackDecoder(InputStream stream) {
    this(MessagePack.newDefaultUnpacker(stream));
  }

  private Message decodeMessage(Type msgType, Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    return switch (msgType) {
      case CREATE_EVALUATOR_REQUEST ->
          new CreateEvaluatorRequest(
              unpackLong(map, "requestId"),
              unpackStringListOrNull(map, "allowedModules"),
              unpackStringListOrNull(map, "allowedResources"),
              unpackModuleReaderSpecs(map, "clientModuleReaders"),
              unpackResourceReaderSpecs(map, "clientResourceReaders"),
              unpackStringListOrNull(map, "modulePaths", Path::of),
              unpackStringMapOrNull(map, "env"),
              unpackStringMapOrNull(map, "properties"),
              unpackLongOrNull(map, "timeoutSeconds", Duration::ofSeconds),
              unpackStringOrNull(map, "rootDir", Path::of),
              unpackStringOrNull(map, "cacheDir", Path::of),
              unpackStringOrNull(map, "outputFormat"),
              unpackProject(map),
              unpackHttp(map),
              unpackExternalReaders(map, "externalModuleReaders"),
              unpackExternalReaders(map, "externalResourceReaders"),
              unpackStringOrNull(map, "traceMode"));
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
    };
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
          ErrorMessages.create("malformedMessageHeaderUnrecognizedCode", Integer.toHexString(code)),
          e);
    }

    try {
      var map = unpacker.unpackValue().asMapValue().map();
      return decodeMessage(msgType, map);
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

  private static byte @Nullable [] unpackByteArray(Map<Value, Value> map, String key) {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
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

  private static <T> @Nullable List<T> unpackListOrNull(
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

  private static <T> @Nullable Map<String, T> unpackStringMapOrNull(
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

  private static @Nullable ModuleReaderSpec unpackModuleReaderSpec(@Nullable Value value)
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

  private static @Nullable ResourceReaderSpec unpackResourceReaderSpec(@Nullable Value value)
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

  private static @Nullable List<ModuleReaderSpec> unpackModuleReaderSpecs(
      Map<Value, Value> map, String key) throws DecodeException {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    var result = new ArrayList<ModuleReaderSpec>(value.asArrayValue().size());
    for (var element : value.asArrayValue()) {
      var spec = unpackModuleReaderSpec(element);
      if (spec != null) {
        result.add(spec);
      }
    }
    return result;
  }

  private static @Nullable List<ResourceReaderSpec> unpackResourceReaderSpecs(
      Map<Value, Value> map, String key) throws DecodeException {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    var result = new ArrayList<ResourceReaderSpec>(value.asArrayValue().size());
    for (var element : value.asArrayValue()) {
      var spec = unpackResourceReaderSpec(element);
      if (spec != null) {
        result.add(spec);
      }
    }
    return result;
  }

  private static @Nullable Map<String, ExternalReader> unpackExternalReaders(
      Map<Value, Value> map, String key) throws DecodeException {
    var value = getNullable(map, key);
    if (value == null) {
      return null;
    }
    var result = new LinkedHashMap<String, ExternalReader>();
    for (var entry : value.asMapValue().map().entrySet()) {
      var reader = entry.getValue().asMapValue().map();
      result.put(
          entry.getKey().asStringValue().asString(),
          new ExternalReader(
              unpackString(reader, "executable"),
              unpackStringListOrNull(reader, "arguments"),
              unpackStringOrNull(reader, "workingDir")));
    }
    return result;
  }

  private static @Nullable Project unpackProject(Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    var value = getNullable(map, "project");
    if (value == null) {
      return null;
    }
    var projectMap = value.asMapValue().map();
    var projectFileUri = new URI(unpackString(projectMap, "projectFileUri"));
    var dependencies = unpackDependencies(projectMap, "dependencies");
    return new Project(projectFileUri, null, dependencies);
  }

  private static @Nullable Http unpackHttp(Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    var value = getNullable(map, "http");
    if (value == null) {
      return null;
    }
    var httpMap = value.asMapValue().map();
    var proxy = unpackProxy(httpMap);
    var caCertificates = unpackByteArray(httpMap, "caCertificates");

    Map<URI, URI> rewrites = null;
    var rewritesValue = getNullable(httpMap, "rewrites");
    if (rewritesValue != null) {
      rewrites = new LinkedHashMap<>();
      for (var entry : rewritesValue.asMapValue().map().entrySet()) {
        rewrites.put(
            new URI(entry.getKey().asStringValue().asString()),
            new URI(entry.getValue().asStringValue().asString()));
      }
    }

    Map<String, Map<String, List<String>>> headers = null;
    var headersValue = getNullable(httpMap, "headers");
    if (headersValue != null) {
      headers = new LinkedHashMap<>();
      for (var entry : headersValue.asMapValue().map().entrySet()) {
        var inner = new LinkedHashMap<String, List<String>>();
        for (var innerEntry : entry.getValue().asMapValue().map().entrySet()) {
          inner.put(
              innerEntry.getKey().asStringValue().asString(),
              innerEntry.getValue().asArrayValue().list().stream()
                  .map(it -> it.asStringValue().asString())
                  .toList());
        }
        headers.put(entry.getKey().asStringValue().asString(), inner);
      }
    }
    return new Http(caCertificates, proxy, rewrites, headers);
  }

  private static @Nullable Proxy unpackProxy(Map<Value, Value> map)
      throws DecodeException, URISyntaxException {
    var value = getNullable(map, "proxy");
    if (value == null) {
      return null;
    }
    var proxyMap = value.asMapValue().map();
    var address = new URI(unpackString(proxyMap, "address"));
    return new Proxy(address, unpackStringListOrNull(proxyMap, "noProxy"));
  }

  private static Map<String, Dependency> unpackDependencies(Map<Value, Value> map, String key)
      throws DecodeException, URISyntaxException {
    var result = new LinkedHashMap<String, Dependency>();
    for (var entry : get(map, key).asMapValue().map().entrySet()) {
      var name = entry.getKey().asStringValue().asString();
      var dependencyMap = entry.getValue().asMapValue().map();
      var type = unpackString(dependencyMap, "type");
      var packageUri = new URI(unpackString(dependencyMap, "packageUri"));
      if (type.equals(DependencyType.REMOTE.getValue())) {
        Checksums checksums = null;
        var checksumsValue = getNullable(dependencyMap, "checksums");
        if (checksumsValue != null) {
          checksums = new Checksums(unpackString(checksumsValue.asMapValue().map(), "sha256"));
        }
        result.put(name, new RemoteDependency(packageUri, checksums));
      } else {
        var dependencies = unpackDependencies(dependencyMap, "dependencies");
        var projectFileUri = new URI(unpackString(dependencyMap, "projectFileUri"));
        result.put(name, new Project(projectFileUri, packageUri, dependencies));
      }
    }
    return result;
  }
}
