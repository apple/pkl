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
package org.pkl.core.resource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.runtime.ReaderBase;

/**
 * SPI for reading external resources from Pkl. Once a resource reader has been registered, Pkl code
 * can read its resources with {@code read("<URI>")}, provided that resource URIs match an entry in
 * the resource allowlist ({@code --allowed-resources}).
 *
 * <p>See {@link ResourceReaders} for predefined resource readers.
 */
public interface ResourceReader extends ReaderBase {
  /** The URI scheme associated with resources read by this resource reader. */
  String getUriScheme();

  /**
   * Reads the resource with the given URI. Returns {@code Optional.empty()} if a resource with the
   * given URI cannot be found.
   *
   * <p>Supported resource types are:
   *
   * <ul>
   *   <li>{@link String}
   *   <li>{@link Resource}
   * </ul>
   *
   * Throws:
   *
   * <ul>
   *   <li>{@link IOException} — if an error occurred while reading the resource, outside of the
   *       resource not being found.
   *   <li>{@link URISyntaxException} — if the URI format is invalid for the resource.
   *   <li>{@link SecurityManagerException} — If the resource read is invalid per the security
   *       manager.
   * </ul>
   */
  Optional<Object> read(URI uri) throws IOException, URISyntaxException, SecurityManagerException;
}
