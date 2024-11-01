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
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader;
import org.pkl.core.module.ExternalModuleResolver;
import org.pkl.core.resource.ExternalResourceResolver;
import org.pkl.core.util.Nullable;

/** An external process that reads Pkl modules and resources. */
public interface ExternalReaderProcess extends AutoCloseable {
  /**
   * Creates a new {@link ExternalReaderProcess} from the given spec. No resources are allocated at
   * this time.
   */
  static ExternalReaderProcess of(ExternalReader spec) {
    return new ExternalReaderProcessImpl(spec);
  }

  /**
   * Returns a resolver for modules provided via this reader.
   *
   * <p>Upon first call, this method may allocate resources, including spawning a child process.
   *
   * @throws IllegalStateException if this process has already been closed
   */
  ExternalModuleResolver getModuleResolver(long evaluatorId) throws ExternalReaderProcessException;

  /**
   * Returns a resolver for resources provided via this reader.
   *
   * <p>Upon first call, this method may allocate resources, including spawning a child process.
   *
   * @throws IllegalStateException if this process has already been closed
   */
  ExternalResourceResolver getResourceResolver(long evaluatorId)
      throws ExternalReaderProcessException;

  /**
   * Returns the spec, if available, of this process's module reader with the given scheme.
   *
   * @throws IllegalStateException if this process has already been {@linkplain #close closed}
   * @throws IOException if an I/O error occurs
   */
  ExternalModuleResolver.@Nullable Spec getModuleReaderSpec(String scheme) throws IOException;

  /**
   * Returns the spec, if available, of this process's resource reader with the given scheme.
   *
   * @throws IllegalStateException if this process has already been {@linkplain #close closed}
   * @throws IOException if an I/O error occurs
   */
  ExternalResourceResolver.@Nullable Spec getResourceReaderSpec(String scheme) throws IOException;

  /**
   * Closes this process, releasing any associated resources.
   *
   * <p>This method can be safely called multiple times. Subsequent calls have no effect.
   *
   * @implNote Implementers should request a graceful termination by sending a {@link
   *     ExternalReaderMessages.CloseExternalProcess CloseExternalProcess} message to the process
   *     before terminating it forcibly.
   */
  @Override
  void close();
}
