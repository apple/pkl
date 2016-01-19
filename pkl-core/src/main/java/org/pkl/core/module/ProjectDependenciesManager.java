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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.GuardedBy;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.packages.Dependency;
import org.pkl.core.packages.DependencyMetadata;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.project.CanonicalPackageUri;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.project.Project;
import org.pkl.core.project.ProjectDeps;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.json.Json.JsonParseException;

public class ProjectDependenciesManager {
  public static final String PKL_PROJECT_FILENAME = "PklProject";

  public static final String PKL_PROJECT_DEPS_FILENAME = "PklProject.deps.json";

  private final DeclaredDependencies declaredDependencies;
  private final Path projectDir;

  @GuardedBy("lock")
  private ProjectDeps projectDeps;

  @GuardedBy("lock")
  private Map<String, Dependency> myDependencies = null;

  @GuardedBy("lock")
  private EconomicMap<PackageUri, Map<String, Dependency>> localPackageDependencies = null;

  @GuardedBy("lock")
  private EconomicMap<PackageUri, Map<String, Dependency>> packageDependencies =
      EconomicMaps.create();

  private final Object lock = new Object();

  public ProjectDependenciesManager(DeclaredDependencies declaredDependencies) {
    this.declaredDependencies = declaredDependencies;
    this.projectDir = Path.of(declaredDependencies.getProjectFileUri()).getParent();
  }

  public boolean hasPath(Path path) {
    return path.startsWith(projectDir);
  }

  private void ensureDependenciesInitialized() {
    synchronized (lock) {
      if (myDependencies != null) {
        return;
      }
      var projectDeps = getProjectDeps();
      myDependencies = doBuildResolvedDependenciesForProject(declaredDependencies, projectDeps);
      localPackageDependencies = EconomicMaps.create();
      for (var localPkg : declaredDependencies.getLocalDependencies().values()) {
        ensureLocalProjectDependencyInitialized(localPkg, projectDeps);
      }
    }
  }

  private void ensureLocalProjectDependencyInitialized(Project project, ProjectDeps projectDeps) {
    assert localPackageDependencies != null;
    var pkg = project.getPackage();
    assert pkg != null;
    // turn `package:` scheme into `projectpackage`: scheme
    var uri = PackageUri.create("project" + pkg.getUri());
    if (localPackageDependencies.containsKey(uri)) {
      return;
    }
    var resolvedDeps =
        doBuildResolvedDependenciesForProject(project.getDependencies(), projectDeps);
    localPackageDependencies.put(uri, resolvedDeps);
    // TODO: check circular imports (should not be possible)
    for (var localPkg : project.getDependencies().getLocalDependencies().values()) {
      ensureLocalProjectDependencyInitialized(localPkg, projectDeps);
    }
  }

  private void checkProjectDependencyOutOfDate(
      URI projectFileUri, PackageUri declaredPackage, Dependency resolvedDependency) {
    if (resolvedDependency.getVersion().compareTo(declaredPackage.getVersion()) < 0) {
      throw new PackageLoadError(
          ErrorMessages.create(
              "projectDependenciesOutOfDateInProject",
              projectFileUri,
              declaredPackage.getDisplayName(),
              resolvedDependency.getPackageUri().getDisplayName()));
    }
  }

  private Map<String, Dependency> doBuildResolvedDependenciesForProject(
      DeclaredDependencies declaredDeps, ProjectDeps resolvedProjectDeps) {
    var ret =
        new HashMap<String, Dependency>(
            declaredDeps.getRemoteDependencies().size()
                + declaredDeps.getLocalDependencies().size());
    for (var entry : declaredDeps.getLocalDependencies().entrySet()) {
      var localProj = entry.getValue();
      var pkg = localProj.getPackage();
      assert pkg != null;
      var packageUri = CanonicalPackageUri.fromPackageUri(pkg.getUri());
      var resolvedDep = resolvedProjectDeps.get(packageUri);
      if (resolvedDep == null) {
        throw new PackageLoadError(
            ErrorMessages.create("unresolvedProjectDependency", pkg.getUri()));
      }
      checkProjectDependencyOutOfDate(declaredDeps.getProjectFileUri(), pkg.getUri(), resolvedDep);
      ret.put(entry.getKey(), resolvedDep);
    }
    for (var entry : declaredDeps.getRemoteDependencies().entrySet()) {
      var remoteDep = entry.getValue();
      var packageUri = CanonicalPackageUri.fromPackageUri(remoteDep.getPackageUri());
      var resolvedDep = resolvedProjectDeps.get(packageUri);
      if (resolvedDep == null) {
        throw new PackageLoadError(
            ErrorMessages.create("unresolvedProjectDependency", entry.getValue().getPackageUri()));
      }
      checkProjectDependencyOutOfDate(
          declaredDeps.getProjectFileUri(), remoteDep.getPackageUri(), resolvedDep);
      ret.put(entry.getKey(), resolvedDep);
    }
    return ret;
  }

  public Map<String, Dependency> getDependencies() {
    ensureDependenciesInitialized();
    return myDependencies;
  }

  public boolean isLocalPackage(PackageUri packageUri) {
    ensureDependenciesInitialized();
    return localPackageDependencies.containsKey(packageUri);
  }

  public Map<String, Dependency> getLocalPackageDependencies(PackageUri packageUri) {
    ensureDependenciesInitialized();
    var dep = localPackageDependencies.get(packageUri);
    assert dep != null;
    return dep;
  }

  public Map<String, Dependency> getResolvedDependenciesForPackage(
      PackageUri packageUri, DependencyMetadata dependencyMetadata) {
    synchronized (lock) {
      if (!packageDependencies.containsKey(packageUri)) {
        var declaredDependencies = dependencyMetadata.getDependencies();
        var resolvedDeps = new HashMap<String, Dependency>(declaredDependencies.size());
        for (var entry : declaredDependencies.entrySet()) {
          var packageDependency = entry.getValue();
          var canonicalPackage =
              CanonicalPackageUri.fromPackageUri(packageDependency.getPackageUri());
          var resolvedDep = projectDeps.get(canonicalPackage);
          if (resolvedDep == null) {
            throw new PackageLoadError(
                ErrorMessages.create("unresolvedProjectDependency", packageDependency));
          }
          if (resolvedDep.getVersion().compareTo(packageDependency.getVersion()) < 0) {
            throw new PackageLoadError(
                ErrorMessages.create(
                    "projectDependenciesOutOfDateInPackage",
                    packageUri.getDisplayName(),
                    packageDependency.getPackageUri().getDisplayName(),
                    resolvedDep.getPackageUri().getDisplayName()));
          }
          resolvedDeps.put(entry.getKey(), resolvedDep);
        }
        packageDependencies.put(packageUri, resolvedDeps);
      }
      return packageDependencies.get(packageUri);
    }
  }

  public Dependency getResolvedDependency(PackageUri packageUri) {
    var dep = getProjectDeps().get(CanonicalPackageUri.fromPackageUri(packageUri));
    if (dep == null) {
      throw new PackageLoadError(ErrorMessages.create("unresolvedProjectDependency", packageUri));
    }
    return dep;
  }

  public Path getProjectDir() {
    return projectDir;
  }

  public Path getProjectDepsFile() {
    return projectDir.resolve(PKL_PROJECT_DEPS_FILENAME);
  }

  private ProjectDeps getProjectDeps() {
    synchronized (lock) {
      if (projectDeps == null) {
        var depsPath = getProjectDepsFile();
        if (!Files.exists(depsPath)) {
          throw new VmExceptionBuilder().evalError("missingProjectDepsJson", projectDir).build();
        }
        try {
          projectDeps = ProjectDeps.parse(depsPath);
        } catch (IOException | URISyntaxException | JsonParseException e) {
          throw new VmExceptionBuilder()
              .evalError("invalidProjectDepsJson", depsPath, e.getMessage())
              .withCause(e)
              .build();
        }
      }
      return projectDeps;
    }
  }
}
