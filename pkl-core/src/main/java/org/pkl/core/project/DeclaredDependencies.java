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
package org.pkl.core.project;

import java.net.URI;
import java.util.Map;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.util.Nullable;

public final class DeclaredDependencies {
  private final Map<String, RemoteDependency> remoteDependencies;
  private final Map<String, DeclaredDependencies> localDependencies;
  private final URI projectFileUri;
  private final @Nullable PackageUri myPackageUri;

  public DeclaredDependencies(
      Map<String, RemoteDependency> remoteDependencies,
      Map<String, DeclaredDependencies> localDependencies,
      URI projectFileUri,
      @Nullable PackageUri myPackageUri) {
    this.remoteDependencies = remoteDependencies;
    this.localDependencies = localDependencies;
    this.projectFileUri = projectFileUri;
    this.myPackageUri = myPackageUri;
  }

  public Map<String, DeclaredDependencies> getLocalDependencies() {
    return localDependencies;
  }

  public Map<String, RemoteDependency> getRemoteDependencies() {
    return remoteDependencies;
  }

  public URI getProjectFileUri() {
    return projectFileUri;
  }

  public @Nullable PackageUri getMyPackageUri() {
    return myPackageUri;
  }
}
