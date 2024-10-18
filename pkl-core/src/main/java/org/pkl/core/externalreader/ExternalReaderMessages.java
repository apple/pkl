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
package org.pkl.core.externalreader;

import java.util.Objects;
import org.pkl.core.messaging.Message;
import org.pkl.core.messaging.Messages.ModuleReaderSpec;
import org.pkl.core.messaging.Messages.ResourceReaderSpec;
import org.pkl.core.util.Nullable;

public class ExternalReaderMessages {

  public abstract static class InitializeReaderRequest extends Message.Base.Request
      implements Message.Server.Request {
    private final String scheme;

    public InitializeReaderRequest(Message.Type type, long requestId, String scheme) {
      super(type, requestId);
      this.scheme = scheme;
    }

    public String getScheme() {
      return scheme;
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof InitializeReaderRequest that)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      return scheme.equals(that.scheme);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + scheme.hashCode();
      return result;
    }
  }

  public static class InitializeModuleReaderRequest extends InitializeReaderRequest {
    public InitializeModuleReaderRequest(long requestId, String scheme) {
      super(Type.INITIALIZE_MODULE_READER_REQUEST, requestId, scheme);
    }
  }

  public static class InitializeResourceReaderRequest extends InitializeReaderRequest {
    public InitializeResourceReaderRequest(long requestId, String scheme) {
      super(Type.INITIALIZE_RESOURCE_READER_REQUEST, requestId, scheme);
    }
  }

  public static class InitializeModuleReaderResponse extends Message.Base.Response
      implements Message.Client.Response {

    private final @Nullable ModuleReaderSpec spec;

    public InitializeModuleReaderResponse(long requestId, @Nullable ModuleReaderSpec spec) {
      super(Type.INITIALIZE_MODULE_READER_RESPONSE, requestId);
      this.spec = spec;
    }

    public @Nullable ModuleReaderSpec getSpec() {
      return spec;
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof InitializeModuleReaderResponse that)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      return Objects.equals(spec, that.spec);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Objects.hashCode(spec);
      return result;
    }
  }

  public static class InitializeResourceReaderResponse extends Message.Base.Response
      implements Message.Client.Response {

    private final @Nullable ResourceReaderSpec spec;

    public InitializeResourceReaderResponse(long requestId, @Nullable ResourceReaderSpec spec) {
      super(Type.INITIALIZE_RESOURCE_READER_RESPONSE, requestId);
      this.spec = spec;
    }

    public @Nullable ResourceReaderSpec getSpec() {
      return spec;
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof InitializeResourceReaderResponse that)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      return Objects.equals(spec, that.spec);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Objects.hashCode(spec);
      return result;
    }
  }

  public static class CloseExternalProcess extends Message.Base implements Message.Server.OneWay {
    public CloseExternalProcess() {
      super(Type.CLOSE_EXTERNAL_PROCESS);
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      return o instanceof CloseExternalProcess;
    }
  }
}
