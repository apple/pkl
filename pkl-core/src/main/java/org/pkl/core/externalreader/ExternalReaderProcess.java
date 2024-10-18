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
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.Messages.ModuleReaderSpec;
import org.pkl.core.messaging.Messages.ResourceReaderSpec;
import org.pkl.core.util.Nullable;

/** An interface for interacting with external module/resource processes. */
public interface ExternalReaderProcess extends AutoCloseable {

  /**
   * Obtain the process's underlying {@link MessageTransport} for sending reader-specific message
   *
   * <p>May allocate resources upon first call, including spawning a child process. Must not be
   * called after {@link ExternalReaderProcess#close} has been called.
   */
  MessageTransport getTransport() throws ExternalReaderProcessException;

  /** Retrieve the spec, if available, of the process's module reader with the given scheme. */
  @Nullable
  ModuleReaderSpec getModuleReaderSpec(String scheme) throws IOException;

  /** Retrieve the spec, if available, of the process's resource reader with the given scheme. */
  @Nullable
  ResourceReaderSpec getResourceReaderSpec(String scheme) throws IOException;

  /**
   * Close the external process, cleaning up any resources.
   *
   * <p>The {@link MessageTransport} is sent the {@link ExternalReaderMessages.CloseExternalProcess}
   * message to request a graceful stop. A bespoke (empty) message type is used here instead of an
   * OS mechanism like signals to avoid forcing external reader implementers needing to handle many
   * OS-specific mechanisms. Implementations may then forcibly clean up resources after a timeout.
   * Must be safe to call multiple times.
   */
  @Override
  void close();
}
