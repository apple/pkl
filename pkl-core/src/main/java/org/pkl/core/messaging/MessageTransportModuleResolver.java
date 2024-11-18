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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.messaging.Messages.ListModulesRequest;
import org.pkl.core.messaging.Messages.ListModulesResponse;
import org.pkl.core.messaging.Messages.ReadModuleRequest;
import org.pkl.core.messaging.Messages.ReadModuleResponse;
import org.pkl.core.module.ExternalModuleResolver;
import org.pkl.core.module.PathElement;

public class MessageTransportModuleResolver implements ExternalModuleResolver {
  private final MessageTransport transport;
  private final long evaluatorId;
  private final Map<URI, Future<String>> readResponses = new ConcurrentHashMap<>();
  private final Map<URI, Future<List<PathElement>>> listResponses = new ConcurrentHashMap<>();
  private final Random requestIdGenerator = new Random();

  public MessageTransportModuleResolver(MessageTransport transport, long evaluatorId) {
    this.transport = transport;
    this.evaluatorId = evaluatorId;
  }

  public List<PathElement> listElements(SecurityManager securityManager, URI uri)
      throws IOException, SecurityManagerException {
    securityManager.checkResolveModule(uri);
    return doListElements(uri);
  }

  public boolean hasElement(SecurityManager securityManager, URI uri)
      throws SecurityManagerException {
    securityManager.checkResolveModule(uri);
    try {
      doReadModule(uri);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public String resolveModule(SecurityManager securityManager, URI uri)
      throws IOException, SecurityManagerException {
    securityManager.checkResolveModule(uri);
    return doReadModule(uri);
  }

  private String doReadModule(URI moduleUri) throws IOException {
    return MessageTransports.resolveFuture(
        readResponses.computeIfAbsent(
            moduleUri,
            (uri) -> {
              var future = new CompletableFuture<String>();
              var request = new ReadModuleRequest(requestIdGenerator.nextLong(), evaluatorId, uri);
              try {
                transport.send(
                    request,
                    (response) -> {
                      if (response instanceof ReadModuleResponse resp) {
                        if (resp.error() != null) {
                          future.completeExceptionally(new IOException(resp.error()));
                        } else if (resp.contents() != null) {
                          future.complete(resp.contents());
                        } else {
                          future.complete("");
                        }
                      } else {
                        future.completeExceptionally(new ProtocolException("unexpected response"));
                      }
                    });
              } catch (ProtocolException | IOException e) {
                future.completeExceptionally(e);
              }
              return future;
            }));
  }

  private List<PathElement> doListElements(URI baseUri) throws IOException {
    return MessageTransports.resolveFuture(
        listResponses.computeIfAbsent(
            baseUri,
            (uri) -> {
              var future = new CompletableFuture<List<PathElement>>();
              var request = new ListModulesRequest(requestIdGenerator.nextLong(), evaluatorId, uri);
              try {
                transport.send(
                    request,
                    (response) -> {
                      if (response instanceof ListModulesResponse resp) {
                        if (resp.error() != null) {
                          future.completeExceptionally(new IOException(resp.error()));
                        } else {
                          future.complete(
                              Objects.requireNonNullElseGet(resp.pathElements(), List::of));
                        }
                      } else {
                        future.completeExceptionally(new ProtocolException("unexpected response"));
                      }
                    });
              } catch (ProtocolException | IOException e) {
                future.completeExceptionally(e);
              }
              return future;
            }));
  }
}
