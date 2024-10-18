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

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import javax.annotation.concurrent.GuardedBy;
import org.pkl.core.Duration;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader;
import org.pkl.core.externalreader.ExternalReaderMessages.*;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.MessageTransports;
import org.pkl.core.messaging.Messages.ModuleReaderSpec;
import org.pkl.core.messaging.Messages.ResourceReaderSpec;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public class ExternalReaderProcessImpl implements ExternalReaderProcess {

  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(3);

  private final ExternalReader spec;
  private final @Nullable String logPrefix;
  private final Map<String, Future<@Nullable ModuleReaderSpec>> initializeModuleReaderResponses =
      new ConcurrentHashMap<>();
  private final Map<String, Future<@Nullable ResourceReaderSpec>>
      initializeResourceReaderResponses = new ConcurrentHashMap<>();

  private @GuardedBy("this") boolean closed = false;

  @LateInit
  @GuardedBy("this")
  private Process process;

  @LateInit
  @GuardedBy("this")
  private MessageTransport transport;

  private void log(String msg) {
    if (logPrefix != null) {
      System.err.println(logPrefix + msg);
    }
  }

  public ExternalReaderProcessImpl(ExternalReader spec) {
    this.spec = spec;
    logPrefix =
        Objects.equals(System.getenv("PKL_DEBUG"), "1")
            ? "[pkl-core][external-process][" + spec.executable() + "] "
            : null;
  }

  @Override
  public synchronized MessageTransport getTransport() throws ExternalReaderProcessException {
    if (closed) {
      throw new ExternalReaderProcessException("ExternalProcessImpl has already been closed");
    }
    if (process != null) {
      if (!process.isAlive()) {
        throw new ExternalReaderProcessException("ExternalProcessImpl process is no longer alive");
      }

      return transport;
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

    var rxThread = new Thread(this::runTransport, "ExternalProcessImpl rxThread for " + spec);
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
  public synchronized void close() {
    closed = true;
    if (process == null || !process.isAlive()) {
      return;
    }

    try {
      if (transport != null) {
        transport.send(new CloseExternalProcess());
        transport.close();
      }

      // forcefully stop the process after the timeout
      // note that both transport.close() and process.destroy() are safe to call multiple times
      new Timer()
          .schedule(
              new TimerTask() {
                @Override
                public void run() {
                  if (process != null) {
                    transport.close();
                    process.destroyForcibly();
                  }
                }
              },
              CLOSE_TIMEOUT.inWholeMillis());

      // block on process exit
      process.onExit().get();
    } catch (Exception e) {
      transport.close();
      process.destroyForcibly();
    } finally {
      process = null;
      transport = null;
    }
  }

  @Override
  public @Nullable ModuleReaderSpec getModuleReaderSpec(String uriScheme) throws IOException {
    return MessageTransports.resolveFuture(
        initializeModuleReaderResponses.computeIfAbsent(
            uriScheme,
            (scheme) -> {
              var future = new CompletableFuture<@Nullable ModuleReaderSpec>();
              var request = new InitializeModuleReaderRequest(new Random().nextLong(), scheme);
              try {
                getTransport()
                    .send(
                        request,
                        (response) -> {
                          if (response instanceof InitializeModuleReaderResponse resp) {
                            future.complete(resp.getSpec());
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
  public @Nullable ResourceReaderSpec getResourceReaderSpec(String uriScheme) throws IOException {
    return MessageTransports.resolveFuture(
        initializeResourceReaderResponses.computeIfAbsent(
            uriScheme,
            (scheme) -> {
              var future = new CompletableFuture<@Nullable ResourceReaderSpec>();
              var request = new InitializeResourceReaderRequest(new Random().nextLong(), scheme);
              try {
                getTransport()
                    .send(
                        request,
                        (response) -> {
                          log(response.toString());
                          if (response instanceof InitializeResourceReaderResponse resp) {
                            future.complete(resp.getSpec());
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
