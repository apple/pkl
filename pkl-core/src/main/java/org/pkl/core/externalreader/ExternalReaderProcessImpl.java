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
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;
import java.time.Duration;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader;
import org.pkl.core.externalreader.ExternalReaderMessages.*;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.MessageTransportModuleResolver;
import org.pkl.core.messaging.MessageTransportResourceResolver;
import org.pkl.core.messaging.MessageTransports;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.module.ExternalModuleResolver;
import org.pkl.core.resource.ExternalResourceResolver;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

final class ExternalReaderProcessImpl implements ExternalReaderProcess {

  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(3);

  private final ExternalReader spec;
  private final @Nullable String logPrefix;
  private final Map<String, Future<ExternalModuleResolver.@Nullable Spec>>
      initializeModuleReaderResponses = new ConcurrentHashMap<>();
  private final Map<String, Future<ExternalResourceResolver.@Nullable Spec>>
      initializeResourceReaderResponses = new ConcurrentHashMap<>();
  private final Random requestIdGenerator = new Random();

  private final Object lock = new Object();
  private @GuardedBy("lock") boolean closed = false;

  @LateInit
  @GuardedBy("lock")
  private Process process;

  @LateInit
  @GuardedBy("lock")
  private MessageTransport transport;

  private void log(String msg) {
    if (logPrefix != null) {
      System.err.println(logPrefix + msg);
    }
  }

  ExternalReaderProcessImpl(ExternalReader spec) {
    this.spec = spec;
    logPrefix =
        Objects.equals(System.getenv("PKL_DEBUG"), "1")
            ? "[pkl-core][external-process][" + spec.executable() + "] "
            : null;
  }

  @Override
  public ExternalModuleResolver getModuleResolver(long evaluatorId)
      throws ExternalReaderProcessException {
    return new MessageTransportModuleResolver(getTransport(), evaluatorId);
  }

  @Override
  public ExternalResourceResolver getResourceResolver(long evaluatorId)
      throws ExternalReaderProcessException {
    return new MessageTransportResourceResolver(getTransport(), evaluatorId);
  }

  private MessageTransport getTransport() throws ExternalReaderProcessException {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("External reader process has already been closed.");
      }
      if (process != null) {
        if (!process.isAlive()) {
          throw new ExternalReaderProcessException(
              ErrorMessages.create("externalReaderAlreadyTerminated"));
        }

        return transport;
      }
    }

    // This relies on Java/OS behavior around PATH resolution, absolute/relative paths, etc.
    var command = new ArrayList<String>();
    command.add(spec.executable());
    command.addAll(spec.arguments());

    var builder = new ProcessBuilder(command);
    builder.redirectError(Redirect.INHERIT); // inherit stderr from this pkl process
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new ExternalReaderProcessException(e);
    }
    transport =
        MessageTransports.stream(
            new ExternalReaderMessagePackDecoder(process.getInputStream()),
            new ExternalReaderMessagePackEncoder(process.getOutputStream()),
            this::log);

    var rxThread = new Thread(this::runTransport, "ExternalReaderProcessImpl rxThread for " + spec);
    rxThread.setDaemon(true);
    rxThread.start();

    return transport;
  }

  /**
   * Runs the underlying message transport so it can receive responses from the child process.
   *
   * <p>Blocks until the underlying transport is closed.
   */
  private void runTransport() {
    try {
      transport.start(
          (msg) -> {
            throw new ProtocolException("Unexpected incoming one-way message: " + msg);
          },
          (msg) -> {
            throw new ProtocolException("Unexpected incoming request message: " + msg);
          });
    } catch (ProtocolException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (closed) return;
      closed = true;
      
      try {
        if (transport != null && process != null && process.isAlive()) {
          transport.send(new CloseExternalProcess());
          process.waitFor(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
      } catch (Exception ignored) {
      } finally {
        if (process != null) {
          // no-op unless process is alive
          process.destroyForcibly();
        }
        if (transport != null) {
          transport.close();
        }
      }
    }
  }

  @Override
  public ExternalModuleResolver.@Nullable Spec getModuleReaderSpec(String uriScheme)
      throws IOException {
    return MessageTransports.resolveFuture(
        initializeModuleReaderResponses.computeIfAbsent(
            uriScheme,
            (scheme) -> {
              var future = new CompletableFuture<ExternalModuleResolver.@Nullable Spec>();
              var request =
                  new InitializeModuleReaderRequest(requestIdGenerator.nextLong(), scheme);
              try {
                getTransport()
                    .send(
                        request,
                        (response) -> {
                          if (response instanceof InitializeModuleReaderResponse resp) {
                            future.complete(resp.spec());
                          } else {
                            future.completeExceptionally(
                                new ProtocolException("unexpected response"));
                          }
                        });
              } catch (ProtocolException | IOException | ExternalReaderProcessException e) {
                future.completeExceptionally(e);
              }
              return future;
            }));
  }

  @Override
  public ExternalResourceResolver.@Nullable Spec getResourceReaderSpec(String uriScheme)
      throws IOException {
    return MessageTransports.resolveFuture(
        initializeResourceReaderResponses.computeIfAbsent(
            uriScheme,
            (scheme) -> {
              var future = new CompletableFuture<ExternalResourceResolver.@Nullable Spec>();
              var request =
                  new InitializeResourceReaderRequest(requestIdGenerator.nextLong(), scheme);
              try {
                getTransport()
                    .send(
                        request,
                        (response) -> {
                          log(response.toString());
                          if (response instanceof InitializeResourceReaderResponse resp) {
                            future.complete(resp.spec());
                          } else {
                            future.completeExceptionally(
                                new ProtocolException("unexpected response"));
                          }
                        });
              } catch (ProtocolException | IOException | ExternalReaderProcessException e) {
                future.completeExceptionally(e);
              }
              return future;
            }));
  }
}
