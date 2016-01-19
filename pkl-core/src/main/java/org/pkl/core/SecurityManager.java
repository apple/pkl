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
package org.pkl.core;

import java.net.URI;

/**
 * Enforces a security model during {@link Evaluator evaluation}.
 *
 * <p>Use {@link SecurityManagers} to obtain or construct an instance of this type.
 */
public interface SecurityManager {
  /**
   * Checks if the given module may be resolved. This check is required before any attempt is made
   * to access the given URI.
   */
  void checkResolveModule(URI uri) throws SecurityManagerException;

  /** Checks if the given importing module may import the given imported module. */
  void checkImportModule(URI importingModule, URI importedModule) throws SecurityManagerException;

  /** Checks if the given resource may be read. */
  void checkReadResource(URI resource) throws SecurityManagerException;

  /**
   * Checks if the given resource may be resolved. This check is required before any attempt is made
   * to access the given URI.
   */
  void checkResolveResource(URI resource) throws SecurityManagerException;
}
