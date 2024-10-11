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
package org.pkl.core.externalProcess;

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
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader;
import org.pkl.core.externalProcess.ExternalProcessMessages.*;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.MessageTransports;
import org.pkl.core.messaging.Messages.ModuleReaderSpec;
import org.pkl.core.messaging.Messages.ResourceReaderSpec;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public class ExternalProcessImpl implements ExternalProcess {

  private static final long CLOSE_TIMEOUT = 3000; // 3 seconds

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

  public ExternalProcessImpl(ExternalReader spec) {
    this.spec = spec;
    logPrefix =
        Objects.equals(System.getenv("PKL_DEBUG"), "1")
            ? "[pkl-core][external-process][" + spec.executable() + "] "
            : null;
  }

  /**
   * Returns the transport used for communication with the child process.
   *
   * <p>If the process is not yet running, it will be spawned. If this instance has already been
   * closed an exception is thrown.
   */
  @Override
  public synchronized MessageTransport getTransport() throws ExternalProcessException {
    if (closed) {
      throw new ExternalProcessException("ExternalProcessImpl has already been closed");
    }
    if (process != null) {
      if (!process.isAlive()) {
        throw new ExternalProcessException("ExternalProcessImpl process is no longer alive");
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
      throw new ExternalProcessException(e);
    }
    transport =
        MessageTransports.stream(
            new ExternalProcessMessagePackDecoder(process.getInputStream()),
            new ExternalProcessMessagePackEncoder(process.getOutputStream()),
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

  /**
   * Closes the external process.
   *
   * <p>The process asked nicely to exit by sending the [CloseExternalProcess] message. If the
   * process has not terminated within a timeout of 3 seconds, it is forcefully temrinated. Safe to
   * call multiple times. A bespoke (empty) message type is used here instead of an OS mechanism
   * like signals to avoid forcing external reader implementers needing to handle many OS-specific
   * mechanisms.
   */
  @Override
  public synchronized void close() {
    closed = true;
    if (process == null || !process.isAlive()) {
      return;
    }

    try {
      getTransport().send(new CloseExternalProcess());

      // forcefully stop the process after the timeout
      // note that both transport.close() and process.destroy() are safe to call multiple times
      new Timer()
          .schedule(
              new TimerTask() {
                @Override
                public void run() {
                  if (process != null) {
                    transport.close();
                    process.destroy();
                  }
                }
              },
              CLOSE_TIMEOUT);

      // block on process exit
      process.onExit().get();
    } catch (Exception e) {
      transport.close();
      process.destroy();
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
              } catch (ProtocolException | IOException | ExternalProcessException e) {
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
              } catch (ProtocolException | IOException | ExternalProcessException e) {
                future.completeExceptionally(e);
              }
              return future;
            }));
  }
}
