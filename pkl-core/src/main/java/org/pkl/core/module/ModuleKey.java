/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.runtime.ReaderBase;
import org.pkl.core.util.Nullable;

/**
 * SPI for identifying, resolving, caching, and resolving a Pkl module. Standard implementations can
 * be created using {@link ModuleKeys}.
 */
public interface ModuleKey extends ReaderBase {
  /**
   * Returns the absolute URI of this module. This URI is used for identifying the module in user
   * facing messages, for example stack traces. Typically, this URI contains all information
   * necessary for resolving and loading the module.
   */
  URI getUri();

  /**
   * Resolves this module to a canonical form suitable for loading and caching the module. This may
   * involve I/O. Throws {@link FileNotFoundException} if this module cannot be found.
   */
  ResolvedModuleKey resolve(SecurityManager securityManager)
      throws IOException, SecurityManagerException;

  default URI resolveUri(URI uri) throws IOException, SecurityManagerException {
    return resolveUri(getUri(), uri);
  }

  /**
   * Tells if this module should be cached in memory.
   *
   * <p>Caching a module means caching its entire evaluation state, not just its resolved URI or
   * source code.
   *
   * <p>Turning off module caching is mostly useful for synthetic modules that cannot be referenced
   * (imported) from other modules. An example for this is a module representing code typed in a
   * REPL.
   */
  default boolean isCached() {
    return true;
  }

  /**
   * Tells if the modules represented by this module key is local to the environment.
   *
   * <p>A module that is local, and also {@link #hasHierarchicalUris()}, supports triple-dot
   * imports.
   *
   * <p>As a best practice, a module key should be considered local if its source can be loaded in a
   * low latency environment (for example, from disk or from memory). On the flip-side, a module
   * loaded from a remove server should not be considered local.
   */
  default boolean isLocal() {
    return false;
  }

  /**
   * The relative file cache path for this module, or {@code null} if this module should not be
   * cached on the file system.
   */
  default @Nullable Path getFileCacheLocation() {
    return null;
  }
}
