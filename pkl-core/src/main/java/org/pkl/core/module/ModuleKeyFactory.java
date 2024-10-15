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
package org.pkl.core.module;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.pkl.core.externalReader.ExternalReaderProcessException;

/** A factory for {@link ModuleKey}s. */
public interface ModuleKeyFactory extends AutoCloseable {
  /**
   * Returns a {@link ModuleKey} for the given absolute normalized URI, or {@code Optional.empty()}
   * if this factory cannot handle the given URI.
   *
   * <p>Implementations must not perform any I/O related to the given URI. For example, they must
   * not check if the module represented by the given URI exists. {@link
   * org.pkl.core.SecurityManager} checks for the returned module will be performed by clients of
   * this method.
   *
   * <p>Throws {@link URISyntaxException} if the given URI has invalid syntax.
   *
   * @param uri an absolute normalized URI
   * @return a module key for the given URI
   */
  Optional<ModuleKey> create(URI uri)
      throws URISyntaxException, ExternalReaderProcessException, IOException;

  /**
   * Closes this factory, releasing any resources held. See the documentation of factory methods in
   * {@link ModuleKeyFactories} for which factories need to be closed.
   */
  @Override
  default void close() {}
}
