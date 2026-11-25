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
package org.pkl.messaging.externalreader;

import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.pkl.messaging.Message;
import org.pkl.messaging.MessageTransport;
import org.pkl.messaging.Messages.*;
import org.pkl.messaging.ProtocolException;

/** An implementation of the client side of the external reader flow. */
public final class ExternalReaderClient {
  private final List<ExternalModuleReader> externalModuleReaders;
  private final List<ExternalResourceReader> externalResourceReaders;
  private final MessageTransport transport;

  public ExternalReaderClient(
      List<ExternalModuleReader> externalModuleReaders,
      List<ExternalResourceReader> externalResourceReaders,
      MessageTransport transport) {
    this.externalModuleReaders = externalModuleReaders;
    this.externalResourceReaders = externalResourceReaders;
    this.transport = transport;
  }

  /** Close the runtime and its transport. */
  public void close() {
    transport.close();
  }

  private @Nullable ExternalModuleReader findModuleReader(String scheme) {
    for (var moduleReader : externalModuleReaders) {
      if (moduleReader.scheme().equalsIgnoreCase(scheme)) {
        return moduleReader;
      }
    }
    return null;
  }

  private @Nullable ExternalResourceReader findResourceReader(String scheme) {
    for (var resourceReader : externalResourceReaders) {
      if (resourceReader.scheme().equalsIgnoreCase(scheme)) {
        return resourceReader;
      }
    }
    return null;
  }

  /**
   * Start the runtime so it can respond to incoming messages on its transport.
   *
   * <p>Blocks until the underlying transport is closed.
   */
  public void run() throws ProtocolException, IOException {
    transport.start(
        msg -> {
          if (msg.type() == Message.Type.CLOSE_EXTERNAL_PROCESS) {
            close();
          } else {
            throw new ProtocolException("Unexpected incoming one-way message: " + msg);
          }
        },
        this::handleRequest);
  }

  private void handleRequest(Message.Request msg) throws ProtocolException {
    switch (msg.type()) {
      case INITIALIZE_MODULE_READER_REQUEST -> {
        var req = (InitializeModuleReaderRequest) msg;
        var reader = findModuleReader(req.scheme());
        send(
            new InitializeModuleReaderResponse(
                req.requestId(), reader == null ? null : reader.spec()));
      }
      case INITIALIZE_RESOURCE_READER_REQUEST -> {
        var req = (InitializeResourceReaderRequest) msg;
        var reader = findResourceReader(req.scheme());
        send(
            new InitializeResourceReaderResponse(
                req.requestId(), reader == null ? null : reader.spec()));
      }
      case LIST_MODULES_REQUEST -> {
        var req = (ListModulesRequest) msg;
        var reader = findModuleReader(req.uri().getScheme());
        if (reader == null) {
          send(
              new ListModulesResponse(
                  req.requestId(),
                  req.evaluatorId(),
                  null,
                  "No module reader found for scheme " + req.uri().getScheme()));
          return;
        }
        try {
          send(
              new ListModulesResponse(
                  req.requestId(), req.evaluatorId(), reader.listElements(req.uri()), null));
        } catch (ProtocolException e) {
          throw e;
        } catch (Exception e) {
          send(new ListModulesResponse(req.requestId(), req.evaluatorId(), null, e.toString()));
        }
      }
      case LIST_RESOURCES_REQUEST -> {
        var req = (ListResourcesRequest) msg;
        var reader = findResourceReader(req.uri().getScheme());
        if (reader == null) {
          send(
              new ListResourcesResponse(
                  req.requestId(),
                  req.evaluatorId(),
                  null,
                  "No resource reader found for scheme " + req.uri().getScheme()));
          return;
        }
        try {
          send(
              new ListResourcesResponse(
                  req.requestId(), req.evaluatorId(), reader.listElements(req.uri()), null));
        } catch (ProtocolException e) {
          throw e;
        } catch (Exception e) {
          send(new ListResourcesResponse(req.requestId(), req.evaluatorId(), null, e.toString()));
        }
      }
      case READ_MODULE_REQUEST -> {
        var req = (ReadModuleRequest) msg;
        var reader = findModuleReader(req.uri().getScheme());
        if (reader == null) {
          send(
              new ReadModuleResponse(
                  req.requestId(),
                  req.evaluatorId(),
                  null,
                  "No module reader found for scheme " + req.uri().getScheme()));
          return;
        }
        try {
          send(
              new ReadModuleResponse(
                  req.requestId(), req.evaluatorId(), reader.read(req.uri()), null));
        } catch (ProtocolException e) {
          throw e;
        } catch (Exception e) {
          send(new ReadModuleResponse(req.requestId(), req.evaluatorId(), null, e.toString()));
        }
      }
      case READ_RESOURCE_REQUEST -> {
        var req = (ReadResourceRequest) msg;
        var reader = findResourceReader(req.uri().getScheme());
        if (reader == null) {
          send(
              new ReadResourceResponse(
                  req.requestId(),
                  req.evaluatorId(),
                  new byte[0],
                  "No resource reader found for scheme " + req.uri().getScheme()));
          return;
        }
        try {
          send(
              new ReadResourceResponse(
                  req.requestId(), req.evaluatorId(), reader.read(req.uri()), null));
        } catch (ProtocolException e) {
          throw e;
        } catch (Exception e) {
          send(
              new ReadResourceResponse(
                  req.requestId(), req.evaluatorId(), new byte[0], e.toString()));
        }
      }
      default -> throw new ProtocolException("Unexpected incoming request message: " + msg);
    }
  }

  // Request handlers can only throw `ProtocolException`.
  private void send(Message.Response response) throws ProtocolException {
    try {
      transport.send(response);
    } catch (IOException e) {
      throw new ProtocolException("Failed to send " + response.type() + ": " + e, e);
    }
  }
}
