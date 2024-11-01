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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.pkl.core.messaging.Message.*;
import org.pkl.core.module.ExternalModuleResolver;
import org.pkl.core.module.PathElement;
import org.pkl.core.resource.ExternalResourceResolver;
import org.pkl.core.util.Nullable;

public final class Messages {
  private Messages() {}

  public record ModuleReaderSpec(
      String scheme, boolean hasHierarchicalUris, boolean isLocal, boolean isGlobbable)
      implements ExternalModuleResolver.Spec {}

  public record ResourceReaderSpec(String scheme, boolean hasHierarchicalUris, boolean isGlobbable)
      implements ExternalResourceResolver.Spec {}

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

  public record ReadResourceRequest(long requestId, long evaluatorId, URI uri)
      implements Message.Request {
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
    public boolean equals(Object o) {
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

  public record ReadModuleRequest(long requestId, long evaluatorId, URI uri)
      implements Message.Request {
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
}
