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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Path;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.pkl.core.PklException;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.packages.Checksums;
import org.pkl.core.packages.Dependency;
import org.pkl.core.packages.Dependency.LocalDependency;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.EconomicSets;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

/**
 * Given a project's dependencies, build the dependency list.
 *
 * <p>Resolves all dependencies using the <a
 * href="https://research.swtch.com/vgo-mvs#algorithm_1">Construct Build List</a> algorithm.
 *
 * <p>Resolved dependencies have URI `projectpackage` to indicate that they should be project-local.
 */
public final class ProjectDependenciesResolver {
  private final Project project;
  private final PackageResolver packageResolver;
  private final Writer logWriter;
  private final EconomicMap<CanonicalPackageUri, Dependency> resolvedDependencies =
      EconomicMaps.create();

  private final EconomicSet<PackageUri> alreadyHandledDependencies = EconomicSets.create();

  public ProjectDependenciesResolver(
      Project project, PackageResolver packageResolver, Writer logWriter) {
    this.project = project;
    this.packageResolver = packageResolver;
    this.logWriter = logWriter;
  }

  public ProjectDeps resolve() {
    buildResolvedDependencies(project.getDependencies());
    for (var localProject : project.getDependencies().getLocalDependencies().values()) {
      var packageUri = localProject.getMyPackageUri();
      assert packageUri != null;
      var canonicalUri = CanonicalPackageUri.fromPackageUri(packageUri);
      var resolvedDependency = resolvedDependencies.get(canonicalUri);
      if (!(resolvedDependencies.get(canonicalUri) instanceof LocalDependency)) {
        log(
            String.format(
                "WARN: local dependency `%s` was overridden to remote dependency `%s`.",
                packageUri.getDisplayName(), resolvedDependency.getPackageUri().getDisplayName()));
      }
    }
    return new ProjectDeps(resolvedDependencies);
  }

  private void log(String message) {
    try {
      logWriter.write(message + IoUtils.getLineSeparator());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void buildResolvedDependencies(DeclaredDependencies declaredDependencies) {
    for (var dependency : declaredDependencies.getRemoteDependencies().values()) {
      resolveDependenciesOfPackageUri(
          dependency.getPackageUri().toProjectPackageUri(), dependency.getChecksums());
    }
    for (var localDeclaredDependencies : declaredDependencies.getLocalDependencies().values()) {
      resolveDependencies(localDeclaredDependencies);
    }
  }

  private void resolveDependenciesOfPackageUri(
      PackageUri packageUri, @Nullable Checksums expectedChecksums) {
    try {
      if (alreadyHandledDependencies.contains(packageUri)) {
        return;
      }
      var pair = packageResolver.getDependencyMetadataAndComputeChecksum(packageUri);
      var metadata = pair.first;
      var computedChecksums = pair.second;
      if (expectedChecksums != null) {
        if (!expectedChecksums.getSha256().equals(computedChecksums.getSha256())) {
          throw new PklException(
              ErrorMessages.create(
                  "invalidDeclaredChecksum",
                  packageUri.getDisplayName(),
                  computedChecksums.getSha256(),
                  expectedChecksums.getSha256()));
        }
      }
      var dependencyWithChecksum = new RemoteDependency(packageUri, computedChecksums);
      updateDependency(dependencyWithChecksum);
      EconomicSets.add(alreadyHandledDependencies, packageUri);
      for (var transitiveDependency : metadata.getDependencies().values()) {
        resolveDependenciesOfPackageUri(
            transitiveDependency.getPackageUri().toProjectPackageUri(),
            transitiveDependency.getChecksums());
      }
    } catch (IOException | SecurityManagerException | PackageLoadError e) {
      throw new PklException(e.getMessage(), e);
    }
  }

  private void resolveDependencies(DeclaredDependencies declaredDependencies) {
    var packageUri = declaredDependencies.getMyPackageUri();
    assert packageUri != null;
    var projectDir = Path.of(declaredDependencies.getProjectFileUri()).getParent();
    var relativePath = IoUtils.relativize(projectDir, this.project.getProjectDir());
    var localDependency = new LocalDependency(packageUri.toProjectPackageUri(), relativePath);
    updateDependency(localDependency);
    buildResolvedDependencies(declaredDependencies);
  }

  private void updateDependency(Dependency dependency) {
    var canonicalPackageUri = CanonicalPackageUri.fromPackageUri(dependency.getPackageUri());
    var currentDependency = resolvedDependencies.get(canonicalPackageUri);
    if (currentDependency == null
        || currentDependency.getVersion().compareTo(dependency.getVersion()) < 0) {
      EconomicMaps.put(resolvedDependencies, canonicalPackageUri, dependency);
    }
  }
}
