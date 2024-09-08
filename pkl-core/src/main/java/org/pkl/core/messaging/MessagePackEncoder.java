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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.msgpack.core.MessagePacker;
import org.pkl.core.messaging.Message.*;
import org.pkl.core.module.PathElement;
import org.pkl.core.packages.Checksums;
import org.pkl.core.util.Nullable;

public class MessagePackEncoder implements MessageEncoder {

  private final MessagePacker packer;

  public MessagePackEncoder(MessagePacker packer) {
    this.packer = packer;
  }

  private void packModuleReaderSpec(ModuleReaderSpec reader) throws IOException {
    packer.packMapHeader(4);
    packKeyValue("scheme", reader.getScheme());
    packKeyValue("hasHierarchicalUris", reader.getHasHierarchicalUris());
    packKeyValue("isLocal", reader.isLocal());
    packKeyValue("isGlobbable", reader.isGlobbable());
  }

  private void packResourceReaderSpec(ResourceReaderSpec reader) throws IOException {
    packer.packMapHeader(3);
    packKeyValue("scheme", reader.getScheme());
    packKeyValue("hasHierarchicalUris", reader.getHasHierarchicalUris());
    packKeyValue("isGlobbable", reader.isGlobbable());
  }

  private void packPathElement(PathElement pathElement) throws IOException {
    packer.packMapHeader(2);
    packKeyValue("name", pathElement.getName());
    packKeyValue("isDirectory", pathElement.isDirectory());
  }

  private void packProject(Project project) throws IOException {
    packer.packMapHeader(2);
    packKeyValue("projectFileUri", project.getProjectFileUri().toString());
    packer.packString("dependencies");
    packDependencies(project.getDependencies());
  }

  private void packHttp(Http http) throws IOException {
    packMapHeader(0, http.getCaCertificates(), http.getProxy());
    packKeyValue("caCertificates", http.getCaCertificates());
    var proxy = http.getProxy();
    if (proxy != null) {
      packer.packString("proxy");
      packMapHeader(0, proxy.address(), proxy.noProxy());
      packKeyValueString("address", proxy.address(), URI::toString);
      packKeyValue("noProxy", proxy.noProxy());
    }
  }

  private void packDependencies(Map<String, Dependency> dependencies) throws IOException {
    packer.packMapHeader(dependencies.size());
    for (var entry : dependencies.entrySet()) {
      packer.packString(entry.getKey());
      var dep = entry.getValue();
      if (dep instanceof Project proj) {
        packMapHeader(3, proj.getPackageUri());
        packKeyValue("type", proj.getType().getType());
        packKeyValueString("packageUri", proj.getPackageUri(), URI::toString);
        packKeyValue("projectFileUri", proj.getProjectFileUri().toString());
        packer.packString("dependencies");
        packDependencies(proj.getDependencies());
      } else if (dep instanceof RemoteDependency rdep) {
        packMapHeader(1, rdep.getPackageUri(), rdep.getChecksums());
        packKeyValue("type", dep.getType().getType());
        packKeyValueString("packageUri", dep.getPackageUri(), URI::toString);
        if (rdep.getChecksums() != null) {
          packer.packString("checksums");
          packChecksums(rdep.getChecksums());
        }
      }
    }
  }

  private void packChecksums(Checksums checksums) throws IOException {
    packer.packMapHeader(1);
    packKeyValue("sha256", checksums.getSha256());
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

  private void packKeyValue(String name, byte[] value) throws IOException {
    if (value.length == 0) {
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

  @Override
  public void encode(Message msg) throws IOException {
    packer.packArrayHeader(2);
    packer.packInt(msg.getType().getCode());

    switch (msg.getType()) {
      case CREATE_EVALUATOR_REQUEST -> {
        var m = (CreateEvaluatorRequest) msg;
        packMapHeader(
            1,
            m.getAllowedModules(),
            m.getAllowedResources(),
            m.getClientModuleReaders(),
            m.getClientResourceReaders(),
            m.getModulePaths(),
            m.getEnv(),
            m.getProperties(),
            m.getTimeout(),
            m.getRootDir(),
            m.getCacheDir(),
            m.getOutputFormat(),
            m.getProject(),
            m.getHttp());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("allowedModules", m.getAllowedModules(), Pattern::toString);
        packKeyValue("allowedResources", m.getAllowedResources(), Pattern::toString);
        if (m.getClientModuleReaders() != null) {
          packer.packString("clientModuleReaders");
          packer.packArrayHeader(m.getClientModuleReaders().size());
          for (var moduleReader : m.getClientModuleReaders()) {
            packModuleReaderSpec(moduleReader);
          }
        }
        if (m.getClientResourceReaders() != null) {
          packer.packString("clientResourceReaders");
          packer.packArrayHeader(m.getClientResourceReaders().size());
          for (var resourceReader : m.getClientResourceReaders()) {
            packResourceReaderSpec(resourceReader);
          }
        }
        packKeyValue("modulePaths", m.getModulePaths(), Path::toString);
        packKeyValue("env", m.getEnv());
        packKeyValue("properties", m.getProperties());
        packKeyValueLong("timeoutSeconds", m.getTimeout(), Duration::getSeconds);
        packKeyValueString("rootDir", m.getRootDir(), Path::toString);
        packKeyValueString("cacheDir", m.getCacheDir(), Path::toString);
        packKeyValue("outputFormat", m.getOutputFormat());
        if (m.getProject() != null) {
          packer.packString("project");
          packProject(m.getProject());
        }
        if (m.getHttp() != null) {
          packer.packString("http");
          packHttp(m.getHttp());
        }
      }
      case CREATE_EVALUATOR_RESPONSE -> {
        var m = (CreateEvaluatorResponse) msg;
        packMapHeader(1, m.getEvaluatorId(), m.getError());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("error", m.getError());
      }
      case CLOSE_EVALUATOR -> {
        var m = (CloseEvaluator) msg;
        packer.packMapHeader(1);
        packKeyValue("evaluatorId", m.getEvaluatorId());
      }
      case EVALUATE_REQUEST -> {
        var m = (EvaluateRequest) msg;
        packMapHeader(3, m.getModuleText(), m.getExpr());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("moduleUri", m.getModuleUri().toString());
        packKeyValue("moduleText", m.getModuleText());
        packKeyValue("expr", m.getExpr());
      }
      case EVALUATE_RESPONSE -> {
        var m = (EvaluateResponse) msg;
        packMapHeader(2, m.getResult(), m.getError());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("result", m.getResult());
        packKeyValue("error", m.getError());
      }
      case LOG_MESSAGE -> {
        var m = (LogMessage) msg;
        packer.packMapHeader(4);
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("level", m.getLevel());
        packKeyValue("message", m.getMessage());
        packKeyValue("frameUri", m.getFrameUri());
      }
      case READ_RESOURCE_REQUEST -> {
        var m = (ReadResourceRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("uri", m.getUri().toString());
      }
      case READ_RESOURCE_RESPONSE -> {
        var m = (ReadResourceResponse) msg;
        packMapHeader(2, m.getContents(), m.getError());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("contents", m.getContents());
        packKeyValue("error", m.getError());
      }
      case READ_MODULE_REQUEST -> {
        var m = (ReadModuleRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("uri", m.getUri().toString());
      }
      case READ_MODULE_RESPONSE -> {
        var m = (ReadModuleResponse) msg;
        packMapHeader(2, m.getContents(), m.getError());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("contents", m.getContents());
        packKeyValue("error", m.getError());
      }
      case LIST_RESOURCES_REQUEST -> {
        var m = (ListResourcesRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("uri", m.getUri().toString());
      }
      case LIST_RESOURCES_RESPONSE -> {
        var m = (ListResourcesResponse) msg;
        packMapHeader(2, m.getPathElements(), m.getError());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        if (m.getPathElements() != null) {
          packer.packString("pathElements");
          packer.packArrayHeader(m.getPathElements().size());
          for (var pathElement : m.getPathElements()) {
            packPathElement(pathElement);
          }
        }
        packKeyValue("error", m.getError());
      }
      case LIST_MODULES_REQUEST -> {
        var m = (ListModulesRequest) msg;
        packer.packMapHeader(3);
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        packKeyValue("uri", m.getUri().toString());
      }
      case LIST_MODULES_RESPONSE -> {
        var m = (ListModulesResponse) msg;
        packMapHeader(2, m.getPathElements(), m.getError());
        packKeyValue("requestId", m.getRequestId());
        packKeyValue("evaluatorId", m.getEvaluatorId());
        if (m.getPathElements() != null) {
          packer.packString("pathElements");
          packer.packArrayHeader(m.getPathElements().size());
          for (var pathElement : m.getPathElements()) {
            packPathElement(pathElement);
          }
        }
        packKeyValue("error", m.getError());
      }
    }

    packer.flush();
  }
}
