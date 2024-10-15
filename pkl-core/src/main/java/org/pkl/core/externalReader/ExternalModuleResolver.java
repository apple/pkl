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
package org.pkl.core.externalReader;

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
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.MessageTransports;
import org.pkl.core.messaging.Messages.ListModulesRequest;
import org.pkl.core.messaging.Messages.ListModulesResponse;
import org.pkl.core.messaging.Messages.ReadModuleRequest;
import org.pkl.core.messaging.Messages.ReadModuleResponse;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.module.PathElement;

public class ExternalModuleResolver {
  private final MessageTransport transport;
  private final long evaluatorId;
  private final Map<URI, Future<String>> readResponses = new ConcurrentHashMap<>();
  private final Map<URI, Future<List<PathElement>>> listResponses = new ConcurrentHashMap<>();

  public ExternalModuleResolver(MessageTransport transport, long evaluatorId) {
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
              var request = new ReadModuleRequest(new Random().nextLong(), evaluatorId, uri);
              try {
                transport.send(
                    request,
                    (response) -> {
                      if (response instanceof ReadModuleResponse resp) {
                        if (resp.getError() != null) {
                          future.completeExceptionally(new IOException(resp.getError()));
                        } else if (resp.getContents() != null) {
                          future.complete(resp.getContents());
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
              var request = new ListModulesRequest(new Random().nextLong(), evaluatorId, uri);
              try {
                transport.send(
                    request,
                    (response) -> {
                      if (response instanceof ListModulesResponse resp) {
                        if (resp.getError() != null) {
                          future.completeExceptionally(new IOException(resp.getError()));
                        } else {
                          future.complete(
                              Objects.requireNonNullElseGet(resp.getPathElements(), List::of));
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
