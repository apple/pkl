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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings;
import org.pkl.core.module.PathElement;
import org.pkl.core.packages.Checksums;
import org.pkl.core.util.Nullable;

public sealed interface Message {

  Type getType();

  enum Type {
    CREATE_EVALUATOR_REQUEST(0x20),
    CREATE_EVALUATOR_RESPONSE(0x21),
    CLOSE_EVALUATOR(0x22),
    EVALUATE_REQUEST(0x23),
    EVALUATE_RESPONSE(0x24),
    LOG_MESSAGE(0x25),
    READ_RESOURCE_REQUEST(0x26),
    READ_RESOURCE_RESPONSE(0x27),
    READ_MODULE_REQUEST(0x28),
    READ_MODULE_RESPONSE(0x29),
    LIST_RESOURCES_REQUEST(0x2a),
    LIST_RESOURCES_RESPONSE(0x2b),
    LIST_MODULES_REQUEST(0x2c),
    LIST_MODULES_RESPONSE(0x2d);

    private final int code;

    Type(int code) {
      this.code = code;
    }

    public static Type fromInt(int val) throws IllegalArgumentException {
      for (Type t : Type.values()) {
        if (t.code == val) {
          return t;
        }
      }

      throw new IllegalArgumentException("Unknown Message.Type code");
    }

    public int getCode() {
      return code;
    }
  }

  sealed interface OneWay extends Message {}

  sealed interface Request extends Message {

    long getRequestId();
  }

  sealed interface Response extends Message {

    long getRequestId();
  }

  abstract sealed class Client implements Message {

    private final Type type;

    protected Client(Type type) {
      this.type = type;
    }

    @Override
    public Type getType() {
      return type;
    }

    public abstract static sealed class Request extends Client implements Message.Request {

      private final long requestId;

      public Request(Type type, long requestId) {
        super(type);
        this.requestId = requestId;
      }

      public long getRequestId() {
        return this.requestId;
      }
    }

    public abstract static sealed class Response extends Client implements Message.Response {

      private final long requestId;

      public Response(Type type, long requestId) {
        super(type);
        this.requestId = requestId;
      }

      public long getRequestId() {
        return this.requestId;
      }
    }

    public abstract static sealed class OneWay extends Client implements Message.OneWay {

      protected OneWay(Type type) {
        super(type);
      }
    }
  }

  abstract sealed class Server implements Message {

    private final Type type;

    protected Server(Type type) {
      this.type = type;
    }

    @Override
    public Type getType() {
      return type;
    }

    public abstract static sealed class Request extends Server implements Message.Request {

      private final long requestId;

      public Request(Type type, long requestId) {
        super(type);
        this.requestId = requestId;
      }

      public long getRequestId() {
        return this.requestId;
      }
    }

    public abstract static sealed class Response extends Server implements Message.Response {

      private final long requestId;

      public Response(Type type, long requestId) {
        super(type);
        this.requestId = requestId;
      }

      public long getRequestId() {
        return this.requestId;
      }
    }

    public abstract static sealed class OneWay extends Server implements Message.OneWay {

      protected OneWay(Type type) {
        super(type);
      }
    }
  }

  final class ModuleReaderSpec {

    private final String scheme;
    private final boolean hasHierarchicalUris;
    private final boolean isLocal;
    private final boolean isGlobbable;

    public ModuleReaderSpec(
        String scheme, boolean hasHierarchicalUris, boolean isLocal, boolean isGlobbable) {
      this.scheme = scheme;
      this.hasHierarchicalUris = hasHierarchicalUris;
      this.isLocal = isLocal;
      this.isGlobbable = isGlobbable;
    }

    public String getScheme() {
      return scheme;
    }

    public boolean getHasHierarchicalUris() {
      return hasHierarchicalUris;
    }

    public boolean isLocal() {
      return isLocal;
    }

    public boolean isGlobbable() {
      return isGlobbable;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ModuleReaderSpec that)) {
        return false;
      }

      return hasHierarchicalUris == that.hasHierarchicalUris
          && isLocal == that.isLocal
          && isGlobbable == that.isGlobbable
          && scheme.equals(that.scheme);
    }

    @Override
    public int hashCode() {
      int result = scheme.hashCode();
      result = 31 * result + Boolean.hashCode(hasHierarchicalUris);
      result = 31 * result + Boolean.hashCode(isLocal);
      result = 31 * result + Boolean.hashCode(isGlobbable);
      return result;
    }
  }

  final class ResourceReaderSpec {

    private final String scheme;
    private final boolean hasHierarchicalUris;
    private final boolean isGlobbable;

    public ResourceReaderSpec(String scheme, boolean hasHierarchicalUris, boolean isGlobbable) {
      this.scheme = scheme;
      this.hasHierarchicalUris = hasHierarchicalUris;
      this.isGlobbable = isGlobbable;
    }

    public String getScheme() {
      return scheme;
    }

    public boolean getHasHierarchicalUris() {
      return hasHierarchicalUris;
    }

    public boolean isGlobbable() {
      return isGlobbable;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ResourceReaderSpec that)) {
        return false;
      }

      return hasHierarchicalUris == that.hasHierarchicalUris
          && isGlobbable == that.isGlobbable
          && scheme.equals(that.scheme);
    }

    @Override
    public int hashCode() {
      int result = scheme.hashCode();
      result = 31 * result + Boolean.hashCode(hasHierarchicalUris);
      result = 31 * result + Boolean.hashCode(isGlobbable);
      return result;
    }
  }

  class Dependency {

    private final Type type;
    private final @Nullable URI packageUri;

    public Dependency(Type type, @Nullable URI packageUri) {
      this.type = type;
      this.packageUri = packageUri;
    }

    public Type getType() {
      return type;
    }

    public @Nullable URI getPackageUri() {
      return packageUri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Dependency that)) {
        return false;
      }

      return type == that.type && Objects.equals(packageUri, that.packageUri);
    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + Objects.hashCode(packageUri);
      return result;
    }

    public enum Type {
      LOCAL("local"),
      REMOTE("remote");

      private final String type;

      Type(String type) {
        this.type = type;
      }

      public String getType() {
        return type;
      }
    }
  }

  final class RemoteDependency extends Dependency {

    private final @Nullable Checksums checksums;

    public RemoteDependency(URI packageUri, @Nullable Checksums checksums) {
      super(Type.REMOTE, packageUri);
      this.checksums = checksums;
    }

    @Override
    public URI getPackageUri() {
      return super.getPackageUri();
    }

    public @Nullable Checksums getChecksums() {
      return checksums;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RemoteDependency that)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      return Objects.equals(checksums, that.checksums);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Objects.hashCode(checksums);
      return result;
    }
  }

  final class Project extends Dependency {

    private final URI projectFileUri;
    private final Map<String, Dependency> dependencies;

    public Project(
        URI projectFileUri, @Nullable URI packageUri, Map<String, Dependency> dependencies) {
      super(Type.LOCAL, packageUri);
      this.projectFileUri = projectFileUri;
      this.dependencies = dependencies;
    }

    public URI getProjectFileUri() {
      return projectFileUri;
    }

    public Map<String, Dependency> getDependencies() {
      return dependencies;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Project project)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      return projectFileUri.equals(project.projectFileUri)
          && dependencies.equals(project.dependencies);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + projectFileUri.hashCode();
      result = 31 * result + dependencies.hashCode();
      return result;
    }
  }

  final class Http {

    private final byte[] caCertificates;
    private final @Nullable PklEvaluatorSettings.Proxy proxy;

    public Http(byte[] caCertificates, @Nullable PklEvaluatorSettings.Proxy proxy) {
      this.caCertificates = caCertificates;
      this.proxy = proxy;
    }

    public byte[] getCaCertificates() {
      return caCertificates;
    }

    public @Nullable PklEvaluatorSettings.Proxy getProxy() {
      return proxy;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Http http)) {
        return false;
      }

      return Arrays.equals(caCertificates, http.caCertificates)
          && Objects.equals(proxy, http.proxy);
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(caCertificates);
      result = 31 * result + Objects.hashCode(proxy);
      return result;
    }
  }

  final class CreateEvaluatorRequest extends Client.Request {

    private final @Nullable List<Pattern> allowedModules;
    private final @Nullable List<Pattern> allowedResources;
    private final @Nullable List<ModuleReaderSpec> clientModuleReaders;
    private final @Nullable List<ResourceReaderSpec> clientResourceReaders;
    private final @Nullable List<Path> modulePaths;
    private final @Nullable Map<String, String> env;
    private final @Nullable Map<String, String> properties;
    private final @Nullable Duration timeout;
    private final @Nullable Path rootDir;
    private final @Nullable Path cacheDir;
    private final @Nullable String outputFormat;
    private final @Nullable Project project;
    private final @Nullable Http http;

    public CreateEvaluatorRequest(
        long requestId,
        @Nullable List<Pattern> allowedModules,
        @Nullable List<Pattern> allowedResources,
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
        @Nullable Http http) {
      super(Type.CREATE_EVALUATOR_REQUEST, requestId);
      this.allowedModules = allowedModules;
      this.allowedResources = allowedResources;
      this.clientModuleReaders = clientModuleReaders;
      this.clientResourceReaders = clientResourceReaders;
      this.modulePaths = modulePaths;
      this.env = env;
      this.properties = properties;
      this.timeout = timeout;
      this.rootDir = rootDir;
      this.cacheDir = cacheDir;
      this.outputFormat = outputFormat;
      this.project = project;
      this.http = http;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CreateEvaluatorRequest that)) {
        return false;
      }

      return patternsEqual(allowedModules, that.allowedModules)
          && patternsEqual(allowedResources, that.allowedResources)
          && Objects.equals(clientModuleReaders, that.clientModuleReaders)
          && Objects.equals(clientResourceReaders, that.clientResourceReaders)
          && Objects.equals(modulePaths, that.modulePaths)
          && Objects.equals(env, that.env)
          && Objects.equals(properties, that.properties)
          && Objects.equals(timeout, that.timeout)
          && Objects.equals(rootDir, that.rootDir)
          && Objects.equals(cacheDir, that.cacheDir)
          && Objects.equals(outputFormat, that.outputFormat)
          && Objects.equals(project, that.project)
          && Objects.equals(http, that.http);
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(allowedModules);
      result = 31 * result + Objects.hashCode(allowedResources);
      result = 31 * result + Objects.hashCode(clientModuleReaders);
      result = 31 * result + Objects.hashCode(clientResourceReaders);
      result = 31 * result + Objects.hashCode(modulePaths);
      result = 31 * result + Objects.hashCode(env);
      result = 31 * result + Objects.hashCode(properties);
      result = 31 * result + Objects.hashCode(timeout);
      result = 31 * result + Objects.hashCode(rootDir);
      result = 31 * result + Objects.hashCode(cacheDir);
      result = 31 * result + Objects.hashCode(outputFormat);
      result = 31 * result + Objects.hashCode(project);
      result = 31 * result + Objects.hashCode(http);
      return result;
    }

    public @Nullable List<Pattern> getAllowedModules() {
      return allowedModules;
    }

    public @Nullable List<Pattern> getAllowedResources() {
      return allowedResources;
    }

    public @Nullable List<ModuleReaderSpec> getClientModuleReaders() {
      return clientModuleReaders;
    }

    public @Nullable List<ResourceReaderSpec> getClientResourceReaders() {
      return clientResourceReaders;
    }

    public @Nullable List<Path> getModulePaths() {
      return modulePaths;
    }

    public @Nullable Map<String, String> getEnv() {
      return env;
    }

    public @Nullable Map<String, String> getProperties() {
      return properties;
    }

    public @Nullable Duration getTimeout() {
      return timeout;
    }

    public @Nullable Path getRootDir() {
      return rootDir;
    }

    public @Nullable Path getCacheDir() {
      return cacheDir;
    }

    public @Nullable String getOutputFormat() {
      return outputFormat;
    }

    public @Nullable Project getProject() {
      return project;
    }

    public @Nullable Http getHttp() {
      return http;
    }
  }

  final class CreateEvaluatorResponse extends Server.Response {

    private final @Nullable Long evaluatorId;
    private final @Nullable String error;

    public CreateEvaluatorResponse(
        long requestId, @Nullable Long evaluatorId, @Nullable String error) {
      super(Type.CREATE_EVALUATOR_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.error = error;
    }

    public @Nullable Long getEvaluatorId() {
      return evaluatorId;
    }

    public @Nullable String getError() {
      return error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CreateEvaluatorResponse that)) {
        return false;
      }

      return Objects.equals(evaluatorId, that.evaluatorId) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(evaluatorId);
      result = 31 * result + Objects.hashCode(error);
      return result;
    }

    public CreateEvaluatorResponse withError(String error) {
      return new CreateEvaluatorResponse(getRequestId(), evaluatorId, error);
    }

    public CreateEvaluatorResponse withEvaluatorId(long evaluatorId) {
      return new CreateEvaluatorResponse(getRequestId(), evaluatorId, error);
    }
  }

  final class ListResourcesRequest extends Server.Request {

    private final long evaluatorId;
    private final URI uri;

    public ListResourcesRequest(long requestId, long evaluatorId, URI uri) {
      super(Type.LIST_RESOURCES_REQUEST, requestId);
      this.evaluatorId = evaluatorId;
      this.uri = uri;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public URI getUri() {
      return uri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListResourcesRequest that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId && uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + uri.hashCode();
      return result;
    }
  }

  final class ListResourcesResponse extends Client.Response {

    private final long evaluatorId;
    private final @Nullable List<PathElement> pathElements;
    private final @Nullable String error;

    public ListResourcesResponse(
        long requestId,
        long evaluatorId,
        @Nullable List<PathElement> pathElements,
        @Nullable String error) {
      super(Type.LIST_RESOURCES_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.pathElements = pathElements;
      this.error = error;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public @Nullable List<PathElement> getPathElements() {
      return pathElements;
    }

    public @Nullable String getError() {
      return error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListResourcesResponse that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && Objects.equals(pathElements, that.pathElements)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + Objects.hashCode(pathElements);
      result = 31 * result + Objects.hashCode(error);
      return result;
    }
  }

  final class ListModulesRequest extends Server.Request {

    private final long evaluatorId;
    private final URI uri;

    public ListModulesRequest(long requestId, long evaluatorId, URI uri) {
      super(Type.LIST_MODULES_REQUEST, requestId);
      this.evaluatorId = evaluatorId;
      this.uri = uri;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public URI getUri() {
      return uri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListModulesRequest that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId && uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + uri.hashCode();
      return result;
    }
  }

  final class ListModulesResponse extends Client.Response {

    private final long evaluatorId;
    private final @Nullable List<PathElement> pathElements;
    private final @Nullable String error;

    public ListModulesResponse(
        long requestId,
        long evaluatorId,
        @Nullable List<PathElement> pathElements,
        @Nullable String error) {
      super(Type.LIST_MODULES_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.pathElements = pathElements;
      this.error = error;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public @Nullable List<PathElement> getPathElements() {
      return pathElements;
    }

    public @Nullable String getError() {
      return error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListModulesResponse that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && Objects.equals(pathElements, that.pathElements)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + Objects.hashCode(pathElements);
      result = 31 * result + Objects.hashCode(error);
      return result;
    }
  }

  final class CloseEvaluator extends Client.OneWay {

    private final long evaluatorId;

    public CloseEvaluator(long evaluatorId) {
      super(Type.CLOSE_EVALUATOR);
      this.evaluatorId = evaluatorId;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CloseEvaluator that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId;
    }

    @Override
    public int hashCode() {
      return Long.hashCode(evaluatorId);
    }
  }

  final class EvaluateRequest extends Client.Request {

    private final long evaluatorId;
    private final URI moduleUri;
    private final @Nullable String moduleText;
    private final @Nullable String expr;

    public EvaluateRequest(
        long requestId,
        long evaluatorId,
        URI moduleUri,
        @Nullable String moduleText,
        @Nullable String expr) {
      super(Type.EVALUATE_REQUEST, requestId);
      this.evaluatorId = evaluatorId;
      this.moduleUri = moduleUri;
      this.moduleText = moduleText;
      this.expr = expr;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public URI getModuleUri() {
      return moduleUri;
    }

    public @Nullable String getModuleText() {
      return moduleText;
    }

    public @Nullable String getExpr() {
      return expr;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof EvaluateRequest that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && moduleUri.equals(that.moduleUri)
          && Objects.equals(moduleText, that.moduleText)
          && Objects.equals(expr, that.expr);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + moduleUri.hashCode();
      result = 31 * result + Objects.hashCode(moduleText);
      result = 31 * result + Objects.hashCode(expr);
      return result;
    }
  }

  final class EvaluateResponse extends Server.Response {

    private final long evaluatorId;
    private final byte[] result;
    private final @Nullable String error;

    public EvaluateResponse(
        long requestId, long evaluatorId, byte[] result, @Nullable String error) {
      super(Type.EVALUATE_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.result = result;
      this.error = error;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public byte[] getResult() {
      return result;
    }

    public @Nullable String getError() {
      return error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof EvaluateResponse that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && Arrays.equals(result, that.result)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      int result1 = Long.hashCode(evaluatorId);
      result1 = 31 * result1 + Arrays.hashCode(result);
      result1 = 31 * result1 + Objects.hashCode(error);
      return result1;
    }

    public EvaluateResponse withError(String error) {
      return new EvaluateResponse(getRequestId(), evaluatorId, result, error);
    }

    public EvaluateResponse withResult(byte[] result) {
      return new EvaluateResponse(getRequestId(), evaluatorId, result, error);
    }
  }

  final class LogMessage extends Server.OneWay {

    private final long evaluatorId;
    private final int level;
    private final String message;
    private final String frameUri;

    public LogMessage(long evaluatorId, int level, String message, String frameUri) {
      super(Type.LOG_MESSAGE);
      this.evaluatorId = evaluatorId;
      this.level = level;
      this.message = message;
      this.frameUri = frameUri;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public int getLevel() {
      return level;
    }

    public String getMessage() {
      return message;
    }

    public String getFrameUri() {
      return frameUri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof LogMessage that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && level == that.level
          && message.equals(that.message)
          && frameUri.equals(that.frameUri);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + level;
      result = 31 * result + message.hashCode();
      result = 31 * result + frameUri.hashCode();
      return result;
    }
  }

  final class ReadResourceRequest extends Server.Request {

    private final long evaluatorId;
    private final URI uri;

    public ReadResourceRequest(long requestId, long evaluatorId, URI uri) {
      super(Type.READ_RESOURCE_REQUEST, requestId);
      this.evaluatorId = evaluatorId;
      this.uri = uri;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public URI getUri() {
      return uri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ReadResourceRequest that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId && uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + uri.hashCode();
      return result;
    }
  }

  final class ReadResourceResponse extends Client.Response {

    private final long evaluatorId;
    private final byte[] contents;
    private final @Nullable String error;

    public ReadResourceResponse(
        long requestId, long evaluatorId, byte[] contents, @Nullable String error) {
      super(Type.READ_RESOURCE_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.contents = contents;
      this.error = error;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public byte[] getContents() {
      return contents;
    }

    public @Nullable String getError() {
      return error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ReadResourceResponse that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && Arrays.equals(contents, that.contents)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + Arrays.hashCode(contents);
      result = 31 * result + Objects.hashCode(error);
      return result;
    }
  }

  final class ReadModuleRequest extends Server.Request {

    private final long evaluatorId;
    private final URI uri;

    public ReadModuleRequest(long requestId, long evaluatorId, URI uri) {
      super(Type.READ_MODULE_REQUEST, requestId);
      this.evaluatorId = evaluatorId;
      this.uri = uri;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public URI getUri() {
      return uri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ReadModuleRequest that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId && uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + uri.hashCode();
      return result;
    }
  }

  final class ReadModuleResponse extends Client.Response {

    private final long evaluatorId;
    private final @Nullable String contents;
    private final @Nullable String error;

    public ReadModuleResponse(
        long requestId, long evaluatorId, @Nullable String contents, @Nullable String error) {
      super(Type.READ_MODULE_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.contents = contents;
      this.error = error;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public @Nullable String getContents() {
      return contents;
    }

    public @Nullable String getError() {
      return error;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ReadModuleResponse that)) {
        return false;
      }

      return evaluatorId == that.evaluatorId
          && Objects.equals(contents, that.contents)
          && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
      int result = Long.hashCode(evaluatorId);
      result = 31 * result + Objects.hashCode(contents);
      result = 31 * result + Objects.hashCode(error);
      return result;
    }
  }

  private static boolean patternsEqual(@Nullable List<Pattern> a, @Nullable List<Pattern> b) {
    if (a == null && b == null) {
      return true;
    } else if (a == null || b == null) {
      return false;
    } else if (a.size() != b.size()) {
      return false;
    }

    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).pattern().equals(b.get(i).pattern())) {
        return false;
      }
    }

    return true;
  }
}
