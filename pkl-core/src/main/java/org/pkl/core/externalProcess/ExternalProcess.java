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
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.Messages.ModuleReaderSpec;
import org.pkl.core.messaging.Messages.ResourceReaderSpec;
import org.pkl.core.util.Nullable;

/** An interface for interacting with external module/resource processes. */
public interface ExternalProcess extends AutoCloseable {

  /** Obtain the process's underlying [MessageTransport] for sending reader-specific message */
  MessageTransport getTransport() throws ExternalProcessException;

  /** Retrieve the spec, if available, of the process's module reader with the given scheme. */
  @Nullable
  ModuleReaderSpec getModuleReaderSpec(String scheme) throws IOException;

  /** Retrieve the spec, if available, of the process's resource reader with the given scheme. */
  @Nullable
  ResourceReaderSpec getResourceReaderSpec(String scheme) throws IOException;

  /**
   * Close the external process, cleaning up any resources.
   *
   * <p>This should be called when the evaluator managing this process is closed. Will close the
   * underlying transport and terminate any child processes.
   */
  @Override
  void close();
}
