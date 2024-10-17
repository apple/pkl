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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.pkl.core.messaging.Message.Base;
import org.pkl.core.messaging.Message.Client;
import org.pkl.core.messaging.Message.Server;
import org.pkl.core.module.PathElement;
import org.pkl.core.util.Nullable;

public class Messages {

  public static final class ModuleReaderSpec {

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

  public static final class ResourceReaderSpec {

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

  public static final class ListResourcesRequest extends Base.Request implements Server.Request {

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

  public static final class ListResourcesResponse extends Base.Response implements Client.Response {

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

  public static final class ListModulesRequest extends Base.Request implements Message.Request {

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

  public static final class ListModulesResponse extends Base.Response implements Client.Response {

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

  public static final class ReadResourceRequest extends Base.Request implements Message.Request {

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

  /** Java has no boxed byte array type, so we'll bring our own */
  public static class Bytes {
    private final byte[] bytes;

    public Bytes(byte[] bytes) {
      this.bytes = bytes;
    }

    public byte[] getBytes() {
      return bytes;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Bytes bytes1 = (Bytes) o;
      return Arrays.equals(bytes, bytes1.bytes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(bytes);
    }
  }

  public static final class ReadResourceResponse extends Base.Response implements Client.Response {

    private final long evaluatorId;
    private final @Nullable Bytes contents;
    private final @Nullable String error;

    public ReadResourceResponse(
        long requestId, long evaluatorId, @Nullable Bytes contents, @Nullable String error) {
      super(Type.READ_RESOURCE_RESPONSE, requestId);
      this.evaluatorId = evaluatorId;
      this.contents = contents;
      this.error = error;
    }

    public long getEvaluatorId() {
      return evaluatorId;
    }

    public @Nullable Bytes getContents() {
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

  public static final class ReadModuleRequest extends Base.Request implements Message.Request {

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

  public static final class ReadModuleResponse extends Base.Response implements Client.Response {

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
}
