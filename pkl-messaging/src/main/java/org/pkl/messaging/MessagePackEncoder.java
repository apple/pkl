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
package org.pkl.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.pkl.messaging.Messages.*;
import org.pkl.messaging.externalreader.ExternalReader;
import org.pkl.messaging.externalreader.ModuleReaderSpec;
import org.pkl.messaging.externalreader.ResourceReaderSpec;
import org.pkl.messaging.util.ErrorMessages;

public final class MessagePackEncoder implements MessageEncoder {

  private final MessagePacker packer;

  public MessagePackEncoder(MessagePacker packer) {
    this.packer = packer;
  }

  public MessagePackEncoder(OutputStream stream) {
    this(MessagePack.newDefaultPacker(stream));
  }

  private void encodeMessage(Message msg) throws ProtocolException, IOException {
    switch (msg.type()) {
      case CREATE_EVALUATOR_REQUEST -> {
        var m = (CreateEvaluatorRequest) msg;
        packMapHeader(
            1,
            m.allowedModules(),
            m.allowedResources(),
            m.clientModuleReaders(),
            m.clientResourceReaders(),
            m.modulePaths(),
            m.env(),
            m.properties(),
            m.timeout(),
            m.rootDir(),
            m.cacheDir(),
            m.outputFormat(),
            m.project(),
            m.http(),
            m.externalModuleReaders(),
            m.externalResourceReaders(),
            m.traceMode());
        packKeyValue("requestId", m.requestId());
        packKeyValue("allowedModules", m.allowedModules());
        packKeyValue("allowedResources", m.allowedResources());
        if (m.clientModuleReaders() != null) {
          packer.packString("clientModuleReaders");
          packer.packArrayHeader(m.clientModuleReaders().size());
          for (var moduleReader : m.clientModuleReaders()) {
            packModuleReaderSpec(moduleReader);
          }
        }
        if (m.clientResourceReaders() != null) {
          packer.packString("clientResourceReaders");
          packer.packArrayHeader(m.clientResourceReaders().size());
          for (var resourceReader : m.clientResourceReaders()) {
            packResourceReaderSpec(resourceReader);
          }
        }
        packKeyValue("modulePaths", m.modulePaths(), Path::toString);
        packKeyValue("env", m.env());
        packKeyValue("properties", m.properties());
        packKeyValueLong("timeoutSeconds", m.timeout(), Duration::toSeconds);
        packKeyValueString("rootDir", m.rootDir(), Path::toString);
        packKeyValueString("cacheDir", m.cacheDir(), Path::toString);
        packKeyValue("outputFormat", m.outputFormat());
        if (m.project() != null) {
          packer.packString("project");
          packProject(m.project());
        }
        if (m.http() != null) {
          packer.packString("http");
          packHttp(m.http());
        }
        if (m.externalModuleReaders() != null) {
          packer.packString("externalModuleReaders");
          packExternalReaders(m.externalModuleReaders());
        }
        if (m.externalResourceReaders() != null) {
          packer.packString("externalResourceReaders");
          packExternalReaders(m.externalResourceReaders());
        }
        packKeyValue("traceMode", m.traceMode());
      }
      case CREATE_EVALUATOR_RESPONSE -> {
        var m = (CreateEvaluatorResponse) msg;
        packMapHeader(1, m.evaluatorId(), m.error());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("error", m.error());
      }
      case CLOSE_EVALUATOR -> {
        var m = (CloseEvaluator) msg;
        packer.packMapHeader(1);
        packKeyValue("evaluatorId", m.evaluatorId());
      }
      case EVALUATE_REQUEST -> {
        var m = (EvaluateRequest) msg;
        packMapHeader(3, m.moduleText(), m.expr());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("moduleUri", m.moduleUri().toString());
        packKeyValue("moduleText", m.moduleText());
        packKeyValue("expr", m.expr());
      }
      case EVALUATE_RESPONSE -> {
        var m = (EvaluateResponse) msg;
        packMapHeader(2, m.result(), m.error());
        packKeyValue("requestId", m.requestId());
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("result", m.result());
        packKeyValue("error", m.error());
      }
      case LOG_MESSAGE -> {
        var m = (LogMessage) msg;
        packer.packMapHeader(4);
        packKeyValue("evaluatorId", m.evaluatorId());
        packKeyValue("level", m.level());
        packKeyValue("message", m.message());
        packKeyValue("frameUri", m.frameUri());
      }
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
      default ->
          throw new ProtocolException(
              ErrorMessages.create("unhandledMessageType", msg.type().toString()));
    }
  }

  @Override
  public void encode(Message msg) throws IOException, ProtocolException {
    // Serialize access to the packer.  In pkl server mode the main thread
    // (handling CreateEvaluatorRequest) and the executor thread (sending
    // EvaluateResponse / ReadModuleRequest) call encode() concurrently.
    // Without this lock their writes interleave, corrupting the MessagePack
    // stream.  See JvmServerTest "concurrent encoding" for a regression test.
    synchronized (packer) {
      packer.packArrayHeader(2);
      packer.packInt(msg.type().getCode());
      encodeMessage(msg);
      packer.flush();
    }
  }

  private void packMapHeader(int size, @Nullable Object value1) throws IOException {
    packer.packMapHeader(size + (value1 != null ? 1 : 0));
  }

  private void packMapHeader(int size, @Nullable Object value1, @Nullable Object value2)
      throws IOException {
    packer.packMapHeader(size + (value1 != null ? 1 : 0) + (value2 != null ? 1 : 0));
  }

  private void packMapHeader(
      int size,
      @Nullable Object value1,
      @Nullable Object value2,
      @Nullable Object value3,
      @Nullable Object value4)
      throws IOException {
    packer.packMapHeader(
        size
            + (value1 != null ? 1 : 0)
            + (value2 != null ? 1 : 0)
            + (value3 != null ? 1 : 0)
            + (value4 != null ? 1 : 0));
  }

  private void packMapHeader(
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
      @Nullable Object valueD,
      @Nullable Object valueE,
      @Nullable Object valueF,
      @Nullable Object valueG)
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
            + (valueD != null ? 1 : 0)
            + (valueE != null ? 1 : 0)
            + (valueF != null ? 1 : 0)
            + (valueG != null ? 1 : 0));
  }

  private void packKeyValue(String name, @Nullable Integer value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packInt(value);
  }

  private void packKeyValue(String name, @Nullable Long value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packLong(value);
  }

  private <T> void packKeyValueLong(String name, @Nullable T value, Function<T, Long> mapper)
      throws IOException {
    if (value == null) {
      return;
    }
    packKeyValue(name, mapper.apply(value));
  }

  private void packKeyValue(String name, @Nullable String value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packString(value);
  }

  private <T> void packKeyValueString(String name, @Nullable T value, Function<T, String> mapper)
      throws IOException {
    if (value == null) {
      return;
    }
    packKeyValue(name, mapper.apply(value));
  }

  private void packKeyValue(String name, @Nullable Collection<String> value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packArrayHeader(value.size());
    for (String elem : value) {
      packer.packString(elem);
    }
  }

  private <T> void packKeyValue(
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

  private void packKeyValue(String name, @Nullable Map<String, String> value) throws IOException {
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

  private void packKeyValue(String name, byte @Nullable [] value) throws IOException {
    if (value == null) {
      return;
    }
    packer.packString(name);
    packer.packBinaryHeader(value.length);
    packer.writePayload(value);
  }

  private void packKeyValue(String name, boolean value) throws IOException {
    packer.packString(name);
    packer.packBoolean(value);
  }

  private void packModuleReaderSpec(ModuleReaderSpec reader) throws IOException {
    packer.packMapHeader(4);
    packKeyValue("scheme", reader.scheme());
    packKeyValue("hasHierarchicalUris", reader.hasHierarchicalUris());
    packKeyValue("isLocal", reader.isLocal());
    packKeyValue("isGlobbable", reader.isGlobbable());
  }

  private void packResourceReaderSpec(ResourceReaderSpec reader) throws IOException {
    packer.packMapHeader(3);
    packKeyValue("scheme", reader.scheme());
    packKeyValue("hasHierarchicalUris", reader.hasHierarchicalUris());
    packKeyValue("isGlobbable", reader.isGlobbable());
  }

  private void packPathElement(PathElement pathElement) throws IOException {
    packer.packMapHeader(2);
    packKeyValue("name", pathElement.getName());
    packKeyValue("isDirectory", pathElement.isDirectory());
  }

  private void packProject(Project project) throws IOException {
    packer.packMapHeader(2);
    packKeyValue("projectFileUri", project.projectFileUri().toString());
    packer.packString("dependencies");
    packDependencies(project.dependencies());
  }

  private void packHttp(Http http) throws IOException {
    packMapHeader(0, http.caCertificates(), http.proxy(), http.rewrites(), http.headers());
    packKeyValue("caCertificates", http.caCertificates());
    var proxy = http.proxy();
    if (proxy != null) {
      packer.packString("proxy");
      packMapHeader(0, proxy.address(), proxy.noProxy());
      packKeyValueString("address", proxy.address(), Object::toString);
      packKeyValue("noProxy", proxy.noProxy());
    }
    var rewrites = http.rewrites();
    if (rewrites != null) {
      packer.packString("rewrites");
      packer.packMapHeader(rewrites.size());
      for (var entry : rewrites.entrySet()) {
        packer.packString(entry.getKey().toString());
        packer.packString(entry.getValue().toString());
      }
    }
    var headers = http.headers();
    if (headers != null) {
      packer.packString("headers");
      packer.packMapHeader(headers.size());
      for (var entry : headers.entrySet()) {
        packer.packString(entry.getKey());
        packer.packMapHeader(entry.getValue().size());
        for (var headerEntry : entry.getValue().entrySet()) {
          packer.packString(headerEntry.getKey());
          packer.packArrayHeader(headerEntry.getValue().size());
          for (var elem : headerEntry.getValue()) {
            packer.packString(elem);
          }
        }
      }
    }
  }

  private void packDependencies(Map<String, Dependency> dependencies) throws IOException {
    packer.packMapHeader(dependencies.size());
    for (var entry : dependencies.entrySet()) {
      packer.packString(entry.getKey());
      var dependency = entry.getValue();
      if (dependency instanceof Project project) {
        packer.packMapHeader(4);
        packKeyValue("type", project.type().getValue());
        packKeyValue("packageUri", String.valueOf(project.packageUri()));
        packKeyValue("projectFileUri", project.projectFileUri().toString());
        packer.packString("dependencies");
        packDependencies(project.dependencies());
      } else {
        var remote = (RemoteDependency) dependency;
        packer.packMapHeader(remote.checksums() != null ? 3 : 2);
        packKeyValue("type", remote.type().getValue());
        packKeyValue("packageUri", remote.packageUri().toString());
        if (remote.checksums() != null) {
          packer.packString("checksums");
          packer.packMapHeader(1);
          packKeyValue("sha256", remote.checksums().sha256());
        }
      }
    }
  }

  private void packExternalReaders(Map<String, ExternalReader> readers) throws IOException {
    packer.packMapHeader(readers.size());
    for (var entry : readers.entrySet()) {
      var reader = entry.getValue();
      packer.packString(entry.getKey());
      packMapHeader(1, reader.arguments(), reader.workingDir());
      packKeyValue("executable", reader.executable());
      packKeyValue("arguments", reader.arguments());
      packKeyValue("workingDir", reader.workingDir());
    }
  }
}
