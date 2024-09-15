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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/** A factory for {@link ResourceReader}s. */
public interface ResourceReaderFactory {

  /**
   * Returns a {@link ResourceReader} for the given absolute normalized URI, or {@code
   * Optional.empty()} if this factory cannot handle the given URI.
   *
   * <p>Implementations must not perform any I/O related to the given URI. For example, they must
   * not check if the module represented by the given URI exists.
   *
   * <p>Throws {@link URISyntaxException} if the given URI has invalid syntax.
   *
   * @param uri an absolute normalized URI
   * @return a resource for the given URI
   */
  Optional<ResourceReader> create(URI uri);
}
