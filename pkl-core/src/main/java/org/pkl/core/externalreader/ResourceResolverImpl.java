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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.MessageTransports;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.module.PathElement;
import org.pkl.core.resource.Resource;

final class ResourceResolverImpl implements ResourceResolver {
  private final MessageTransport transport;
  private final long evaluatorId;
  private final Map<URI, Future<byte[]>> readResponses = new ConcurrentHashMap<>();
  private final Map<URI, Future<List<PathElement>>> listResponses = new ConcurrentHashMap<>();
  private final Random requestIdGenerator = new Random();

  ResourceResolverImpl(MessageTransport transport, long evaluatorId) {
    this.transport = transport;
    this.evaluatorId = evaluatorId;
  }

  public Optional<Object> read(URI uri) throws IOException {
    var result = doRead(uri);
    return Optional.of(new Resource(uri, result));
  }

  public boolean hasElement(SecurityManager securityManager, URI elementUri)
      throws SecurityManagerException {
    securityManager.checkResolveResource(elementUri);
    try {
      doRead(elementUri);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
      throws IOException, SecurityManagerException {
    securityManager.checkResolveResource(baseUri);
    return doListElements(baseUri);
  }

  public List<PathElement> doListElements(URI baseUri) throws IOException {
    return MessageTransports.resolveFuture(
        listResponses.computeIfAbsent(
            baseUri,
            (uri) -> {
              var future = new CompletableFuture<List<PathElement>>();
              var request =
                  new ListResourcesRequest(requestIdGenerator.nextLong(), evaluatorId, uri);
              try {
                transport.send(
                    request,
                    (response) -> {
                      if (response instanceof ListResourcesResponse resp) {
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

  public byte[] doRead(URI baseUri) throws IOException {
    return MessageTransports.resolveFuture(
        readResponses.computeIfAbsent(
            baseUri,
            (uri) -> {
              var future = new CompletableFuture<byte[]>();
              var request =
                  new ReadResourceRequest(requestIdGenerator.nextLong(), evaluatorId, uri);
              try {
                transport.send(
                    request,
                    (response) -> {
                      if (response instanceof ReadResourceResponse resp) {
                        if (resp.error() != null) {
                          future.completeExceptionally(new IOException(resp.error()));
                        } else if (resp.contents() != null) {
                          future.complete(resp.contents());
                        } else {
                          future.complete(new byte[0]);
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
