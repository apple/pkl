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
package org.pkl.core.externalreader;

import org.pkl.core.messaging.Message.*;
import org.pkl.core.messaging.Messages.ModuleReaderSpec;
import org.pkl.core.messaging.Messages.ResourceReaderSpec;
import org.pkl.core.util.Nullable;

public class ExternalReaderMessages {

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
