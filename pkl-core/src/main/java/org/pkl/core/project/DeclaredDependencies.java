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
package org.pkl.core.project;

import java.net.URI;
import java.util.Map;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.util.Nullable;

public record DeclaredDependencies(
    Map<String, RemoteDependency> remoteDependencies,
    Map<String, DeclaredDependencies> localDependencies,
    URI projectFileUri,
    @Nullable PackageUri myPackageUri) {
  /**
   * @deprecated As of 0.28.0, replaced by {@link #localDependencies()}.
   */
  @Deprecated(forRemoval = true)
  public Map<String, DeclaredDependencies> getLocalDependencies() {
    return localDependencies;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #remoteDependencies()}.
   */
  @Deprecated(forRemoval = true)
  public Map<String, RemoteDependency> getRemoteDependencies() {
    return remoteDependencies;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #projectFileUri()}.
   */
  @Deprecated(forRemoval = true)
  public URI getProjectFileUri() {
    return projectFileUri;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #myPackageUri()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable PackageUri getMyPackageUri() {
    return myPackageUri;
  }
}
