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
package org.pkl.config.java;

import java.io.IOException;
import java.util.List;
import org.pkl.core.externalReader.ExternalModuleReader;
import org.pkl.core.externalReader.ExternalReaderMessages.*;
import org.pkl.core.externalReader.ExternalResourceReader;
import org.pkl.core.messaging.Message.Type;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.util.Nullable;

/** An implementation of the client side of the external reader flow */
public class ExternalReaderRuntime {

  private final List<ExternalModuleReader> moduleReaders;
  private final List<ExternalResourceReader> resourceReaders;
  private final MessageTransport transport;

  public ExternalReaderRuntime(
      List<ExternalModuleReader> moduleReaders,
      List<ExternalResourceReader> resourceReaders,
      MessageTransport transport) {
    this.moduleReaders = moduleReaders;
    this.resourceReaders = resourceReaders;
    this.transport = transport;
  }

  /** Close the runtime and its transport. */
  public void close() {
    transport.close();
  }

  private @Nullable ExternalModuleReader findModuleReader(String scheme) {
    for (var moduleReader : moduleReaders) {
      if (moduleReader.getScheme().equalsIgnoreCase(scheme)) {
        return moduleReader;
      }
    }
    return null;
  }

  private @Nullable ExternalResourceReader findResourceReader(String scheme) {
    for (var resourceReader : resourceReaders) {
      if (resourceReader.getScheme().equalsIgnoreCase(scheme)) {
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
        (msg) -> {
          if (msg.getType() == Type.CLOSE_EXTERNAL_PROCESS) {
            close();
          } else {
            throw new ProtocolException("Unexpected incoming one-way message: " + msg);
          }
        },
        (msg) -> {
          switch (msg.getType()) {
            case INITIALIZE_MODULE_READER_REQUEST -> {
              var req = (InitializeModuleReaderRequest) msg;
              var reader = findModuleReader(req.getScheme());
              @Nullable ModuleReaderSpec spec = null;
              if (reader != null) {
                spec = reader.getSpec();
              }
              transport.send(new InitializeModuleReaderResponse(req.getRequestId(), spec));
            }
            case INITIALIZE_RESOURCE_READER_REQUEST -> {
              var req = (InitializeResourceReaderRequest) msg;
              var reader = findResourceReader(req.getScheme());
              @Nullable ResourceReaderSpec spec = null;
              if (reader != null) {
                spec = reader.getSpec();
              }
              transport.send(new InitializeResourceReaderResponse(req.getRequestId(), spec));
            }
            case LIST_MODULES_REQUEST -> {
              var req = (ListModulesRequest) msg;
              var reader = findModuleReader(req.getUri().getScheme());
              if (reader == null) {
                transport.send(
                    new ListModulesResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        null,
                        "No module reader found for scheme " + req.getUri().getScheme()));
                return;
              }
              try {
                transport.send(
                    new ListModulesResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        reader.listElements(req.getUri()),
                        null));
              } catch (Exception e) {
                transport.send(
                    new ListModulesResponse(
                        req.getRequestId(), req.getEvaluatorId(), null, e.toString()));
              }
            }
            case LIST_RESOURCES_REQUEST -> {
              var req = (ListResourcesRequest) msg;
              var reader = findModuleReader(req.getUri().getScheme());
              if (reader == null) {
                transport.send(
                    new ListResourcesResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        null,
                        "No resource reader found for scheme " + req.getUri().getScheme()));
                return;
              }
              try {
                transport.send(
                    new ListResourcesResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        reader.listElements(req.getUri()),
                        null));
              } catch (Exception e) {
                transport.send(
                    new ListResourcesResponse(
                        req.getRequestId(), req.getEvaluatorId(), null, e.toString()));
              }
            }
            case READ_MODULE_REQUEST -> {
              var req = (ReadModuleRequest) msg;
              var reader = findModuleReader(req.getUri().getScheme());
              if (reader == null) {
                transport.send(
                    new ReadModuleResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        null,
                        "No module reader found for scheme " + req.getUri().getScheme()));
                return;
              }
              try {
                transport.send(
                    new ReadModuleResponse(
                        req.getRequestId(), req.getEvaluatorId(), reader.read(req.getUri()), null));
              } catch (Exception e) {
                transport.send(
                    new ReadModuleResponse(
                        req.getRequestId(), req.getEvaluatorId(), null, e.toString()));
              }
            }
            case READ_RESOURCE_REQUEST -> {
              var req = (ReadResourceRequest) msg;
              var reader = findResourceReader(req.getUri().getScheme());
              if (reader == null) {
                transport.send(
                    new ReadResourceResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        null,
                        "No resource reader found for scheme " + req.getUri().getScheme()));
                return;
              }
              try {
                transport.send(
                    new ReadResourceResponse(
                        req.getRequestId(),
                        req.getEvaluatorId(),
                        new Bytes(reader.read(req.getUri())),
                        null));
              } catch (Exception e) {
                transport.send(
                    new ReadResourceResponse(
                        req.getRequestId(), req.getEvaluatorId(), null, e.toString()));
              }
            }
            default -> throw new ProtocolException("Unexpected incoming request message: " + msg);
          }
        });
  }
}
