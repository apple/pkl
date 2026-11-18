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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.pkl.messaging.Message.Client;
import org.pkl.messaging.Message.Request;
import org.pkl.messaging.Message.Server;
import org.pkl.messaging.externalreader.ExternalReader;
import org.pkl.messaging.externalreader.ModuleReaderSpec;
import org.pkl.messaging.externalreader.ResourceReaderSpec;

public final class Messages {
  private Messages() {}

  public record ListResourcesRequest(long requestId, long evaluatorId, URI uri)
      implements Server.Request {
    public Type type() {
      return Type.LIST_RESOURCES_REQUEST;
    }
  }

  public record ListResourcesResponse(
      long requestId,
      long evaluatorId,
      @Nullable List<PathElement> pathElements,
      @Nullable String error)
      implements Client.Response {
    public Type type() {
      return Type.LIST_RESOURCES_RESPONSE;
    }
  }

  public record ListModulesRequest(long requestId, long evaluatorId, URI uri)
      implements Server.Request {
    public Type type() {
      return Type.LIST_MODULES_REQUEST;
    }
  }

  public record ListModulesResponse(
      long requestId,
      long evaluatorId,
      @Nullable List<PathElement> pathElements,
      @Nullable String error)
      implements Client.Response {
    public Type type() {
      return Type.LIST_MODULES_RESPONSE;
    }
  }

  public record ReadResourceRequest(long requestId, long evaluatorId, URI uri) implements Request {
    public Type type() {
      return Type.READ_RESOURCE_REQUEST;
    }
  }

  public record ReadResourceResponse(
      long requestId, long evaluatorId, byte @Nullable [] contents, @Nullable String error)
      implements Client.Response {

    public Type type() {
      return Type.READ_RESOURCE_RESPONSE;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ReadResourceResponse that = (ReadResourceResponse) o;
      return requestId == that.requestId
          && evaluatorId == that.evaluatorId
          && Objects.equals(error, that.error)
          && Arrays.equals(contents, that.contents);
    }

    @Override
    public int hashCode() {
      return Objects.hash(requestId, evaluatorId, Arrays.hashCode(contents), error);
    }
  }

  public record ReadModuleRequest(long requestId, long evaluatorId, URI uri) implements Request {
    public Type type() {
      return Type.READ_MODULE_REQUEST;
    }
  }

  public record ReadModuleResponse(
      long requestId, long evaluatorId, @Nullable String contents, @Nullable String error)
      implements Client.Response {
    public Type type() {
      return Type.READ_MODULE_RESPONSE;
    }
  }

  public record CreateEvaluatorRequest(
      long requestId,
      @Nullable List<String> allowedModules,
      @Nullable List<String> allowedResources,
      @Nullable List<ModuleReaderSpec> clientModuleReaders,
      @Nullable List<ResourceReaderSpec> clientResourceReaders,
      @Nullable List<Path> modulePaths,
      @Nullable Map<String, String> env,
      @Nullable Map<String, String> properties,
      @Nullable Duration timeout,
      @Nullable Path rootDir,
      @Nullable Path cacheDir,
      @Nullable String outputFormat,
      @Nullable Project project,
      @Nullable Http http,
      @Nullable Map<String, ExternalReader> externalModuleReaders,
      @Nullable Map<String, ExternalReader> externalResourceReaders,
      // Name of a trace mode, e.g. "compact" or "pretty".
      @Nullable String traceMode)
      implements Client.Request {
    public Type type() {
      return Type.CREATE_EVALUATOR_REQUEST;
    }
  }

  public record Proxy(@Nullable URI address, @Nullable List<String> noProxy) {}

  /**
   * @param caCertificates PEM-format CA certificates as raw bytes
   * @param proxy proxy settings
   * @param rewrites HTTP rewrites
   * @param headers HTTP headers
   */
  public record Http(
      byte @Nullable [] caCertificates,
      @Nullable Proxy proxy,
      @Nullable Map<URI, URI> rewrites,
      @Nullable Map<String, Map<String, List<String>>> headers) {
    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Http that)) {
        return false;
      }
      return Arrays.equals(caCertificates, that.caCertificates)
          && Objects.equals(proxy, that.proxy)
          && Objects.equals(rewrites, that.rewrites)
          && Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
      return Objects.hash(Arrays.hashCode(caCertificates), proxy, rewrites, headers);
    }
  }

  public enum DependencyType {
    LOCAL("local"),
    REMOTE("remote");

    private final String value;

    DependencyType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public record Checksums(String sha256) {}

  public sealed interface Dependency permits RemoteDependency, Project {
    DependencyType type();

    @Nullable URI packageUri();
  }

  public record RemoteDependency(URI packageUri, @Nullable Checksums checksums)
      implements Dependency {
    @Override
    public DependencyType type() {
      return DependencyType.REMOTE;
    }
  }

  public record Project(
      URI projectFileUri, @Nullable URI packageUri, Map<String, Dependency> dependencies)
      implements Dependency {
    @Override
    public DependencyType type() {
      return DependencyType.LOCAL;
    }
  }

  public record CreateEvaluatorResponse(
      long requestId, @Nullable Long evaluatorId, @Nullable String error)
      implements Server.Response {
    public Type type() {
      return Type.CREATE_EVALUATOR_RESPONSE;
    }
  }

  public record CloseEvaluator(long evaluatorId) implements Client.OneWay {
    public Type type() {
      return Type.CLOSE_EVALUATOR;
    }
  }

  public record EvaluateRequest(
      long requestId,
      long evaluatorId,
      URI moduleUri,
      @Nullable String moduleText,
      @Nullable String expr)
      implements Client.Request {
    public Type type() {
      return Type.EVALUATE_REQUEST;
    }
  }

  public record EvaluateResponse(
      long requestId, long evaluatorId, byte @Nullable [] result, @Nullable String error)
      implements Server.Response {
    public Type type() {
      return Type.EVALUATE_RESPONSE;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof EvaluateResponse that)) {
        return false;
      }
      return requestId == that.requestId
          && evaluatorId == that.evaluatorId
          && Arrays.equals(result, that.result)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      return Objects.hash(requestId, evaluatorId, Arrays.hashCode(result), error);
    }
  }

  public record LogMessage(long evaluatorId, int level, String message, String frameUri)
      implements Server.OneWay {
    public Type type() {
      return Type.LOG_MESSAGE;
    }
  }

  public record InitializeModuleReaderRequest(long requestId, String scheme)
      implements Server.Request {
    public Type type() {
      return Type.INITIALIZE_MODULE_READER_REQUEST;
    }
  }

  public record InitializeResourceReaderRequest(long requestId, String scheme)
      implements Server.Request {
    public Type type() {
      return Type.INITIALIZE_RESOURCE_READER_REQUEST;
    }
  }

  public record InitializeModuleReaderResponse(long requestId, @Nullable ModuleReaderSpec spec)
      implements Client.Response {
    public Type type() {
      return Type.INITIALIZE_MODULE_READER_RESPONSE;
    }
  }

  public record InitializeResourceReaderResponse(long requestId, @Nullable ResourceReaderSpec spec)
      implements Client.Response {
    public Type type() {
      return Type.INITIALIZE_RESOURCE_READER_RESPONSE;
    }
  }

  public record CloseExternalProcess() implements Server.OneWay {
    public Type type() {
      return Type.CLOSE_EXTERNAL_PROCESS;
    }
  }
}
