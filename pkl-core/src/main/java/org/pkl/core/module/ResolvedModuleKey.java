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

import java.io.IOException;
import java.net.URI;
import org.pkl.core.SecurityManager;

/** SPI for identifying a resolved module and loading its source code. */
public interface ResolvedModuleKey {
  ModuleKey getOriginal();

  /**
   * Returns the URI of the resolved module. This URI identifies the location that the module's
   * source code will be loaded from. If {@link ModuleKey#isCached()} is {@code true}, this URI will
   * also be used as a cache key for the module.
   */
  URI getUri();

  /**
   * Loads module(s) from {@link #getUri()}. All necessary {@link SecurityManager} checks have
   * already been performed before invoking this method.
   *
   * <p>In some cases, a module's source code is already loaded as part of resolving the module,
   * typically for performance reasons. In such a case, this method will not need to perform any
   * additional I/O.
   *
   * @throws IOException if an I/O error occurs while loading the source code
   */
  String loadSource() throws IOException;
}
