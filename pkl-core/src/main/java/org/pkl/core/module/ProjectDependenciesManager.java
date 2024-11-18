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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.PklBugException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.packages.Dependency;
import org.pkl.core.packages.DependencyMetadata;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.project.CanonicalPackageUri;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.project.ProjectDeps;
import org.pkl.core.runtime.ModuleResolver;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.json.Json.JsonParseException;

public final class ProjectDependenciesManager {
  public static final String PKL_PROJECT_FILENAME = "PklProject";

  public static final String PKL_PROJECT_DEPS_FILENAME = "PklProject.deps.json";

  private final DeclaredDependencies declaredDependencies;
  private final URI projectBaseUri;
  private final ModuleResolver moduleResolver;
  private final SecurityManager securityManager;

  @GuardedBy("lock")
  private ProjectDeps projectDeps;

  @GuardedBy("lock")
  private Map<String, Dependency> myDependencies = null;

  @GuardedBy("lock")
  private final EconomicMap<PackageUri, Map<String, Dependency>> localPackageDependencies =
      EconomicMaps.create();

  @GuardedBy("lock")
  private final EconomicMap<PackageUri, Map<String, Dependency>> packageDependencies =
      EconomicMaps.create();

  private final Object lock = new Object();

  public ProjectDependenciesManager(
      DeclaredDependencies declaredDependencies,
      ModuleResolver moduleResolver,
      SecurityManager securityManager) {
    this.declaredDependencies = declaredDependencies;
    // new URI("scheme://host/a/b/c.txt").resolve(".") == new URI("scheme://host/a/b/")
    this.projectBaseUri = IoUtils.resolve(declaredDependencies.projectFileUri(), ".");
    this.moduleResolver = moduleResolver;
    this.securityManager = securityManager;
  }

  public boolean hasUri(URI uri) {
    return projectBaseUri.getScheme().equals(uri.getScheme())
        && Objects.equals(projectBaseUri.getAuthority(), uri.getAuthority())
        && uri.getPath().startsWith(projectBaseUri.getPath());
  }

  private void ensureDependenciesInitialized() {
    synchronized (lock) {
      if (myDependencies != null) {
        return;
      }
      var projectDeps = getProjectDeps();
      myDependencies = doBuildResolvedDependenciesForProject(declaredDependencies, projectDeps);
      for (var localPkg : declaredDependencies.localDependencies().values()) {
        ensureLocalProjectDependencyInitialized(localPkg, projectDeps);
      }
    }
  }

  private void ensureLocalProjectDependencyInitialized(
      DeclaredDependencies localProjectDependencies, ProjectDeps projectDeps) {
    // turn `package:` scheme into `projectpackage`: scheme
    var uri = PackageUri.create("project" + localProjectDependencies.myPackageUri());
    if (localPackageDependencies.containsKey(uri)) {
      return;
    }
    var resolvedDeps = doBuildResolvedDependenciesForProject(localProjectDependencies, projectDeps);
    localPackageDependencies.put(uri, resolvedDeps);
    // TODO: check circular imports (should not be possible)
    for (var declaredDeps : localProjectDependencies.localDependencies().values()) {
      ensureLocalProjectDependencyInitialized(declaredDeps, projectDeps);
    }
  }

  private void checkProjectDependencyOutOfDate(
      URI projectFileUri, PackageUri declaredPackage, Dependency resolvedDependency) {
    if (resolvedDependency.getVersion().compareTo(declaredPackage.getVersion()) < 0) {
      throw new PackageLoadError(
          "projectDependenciesOutOfDateInProject",
          projectFileUri,
          declaredPackage.getDisplayName(),
          resolvedDependency.getPackageUri().getDisplayName());
    }
  }

  private Map<String, Dependency> doBuildResolvedDependenciesForProject(
      DeclaredDependencies declaredDeps, ProjectDeps resolvedProjectDeps) {
    var ret =
        new HashMap<String, Dependency>(
            declaredDeps.remoteDependencies().size() + declaredDeps.localDependencies().size());
    for (var entry : declaredDeps.localDependencies().entrySet()) {
      var localDeclaredDependencies = entry.getValue();
      var packageUri = localDeclaredDependencies.myPackageUri();
      assert packageUri != null;
      var canonicalPackageUri =
          CanonicalPackageUri.fromPackageUri(localDeclaredDependencies.myPackageUri());
      var resolvedDep = resolvedProjectDeps.get(canonicalPackageUri);
      if (resolvedDep == null) {
        throw new PackageLoadError("unresolvedProjectDependency", packageUri);
      }
      checkProjectDependencyOutOfDate(declaredDeps.projectFileUri(), packageUri, resolvedDep);
      ret.put(entry.getKey(), resolvedDep);
    }
    for (var entry : declaredDeps.remoteDependencies().entrySet()) {
      var remoteDep = entry.getValue();
      var packageUri = CanonicalPackageUri.fromPackageUri(remoteDep.getPackageUri());
      var resolvedDep = resolvedProjectDeps.get(packageUri);
      if (resolvedDep == null) {
        throw new PackageLoadError("unresolvedProjectDependency", entry.getValue().getPackageUri());
      }
      checkProjectDependencyOutOfDate(
          declaredDeps.projectFileUri(), remoteDep.getPackageUri(), resolvedDep);
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
            throw new PackageLoadError("unresolvedProjectDependency", packageDependency);
          }
          if (resolvedDep.getVersion().compareTo(packageDependency.getVersion()) < 0) {
            throw new PackageLoadError(
                "projectDependenciesOutOfDateInPackage",
                packageUri.getDisplayName(),
                packageDependency.getPackageUri().getDisplayName(),
                resolvedDep.getPackageUri().getDisplayName());
          }
          resolvedDeps.put(entry.getKey(), resolvedDep);
        }
        packageDependencies.put(packageUri, resolvedDeps);
      }
      return packageDependencies.get(packageUri);
    }
  }

  public DeclaredDependencies getDeclaredDependencies() {
    return declaredDependencies;
  }

  public Dependency getResolvedDependency(PackageUri packageUri) {
    var dep = getProjectDeps().get(CanonicalPackageUri.fromPackageUri(packageUri));
    if (dep == null) {
      throw new PackageLoadError("unresolvedProjectDependency", packageUri);
    }
    return dep;
  }

  public URI getProjectBaseUri() {
    return projectBaseUri;
  }

  public URI getProjectDepsFileUri() {
    return IoUtils.resolve(projectBaseUri, PKL_PROJECT_DEPS_FILENAME);
  }

  public URI getProjectFileUri() {
    return declaredDependencies.projectFileUri();
  }

  private ProjectDeps getProjectDeps() {
    synchronized (lock) {
      if (projectDeps == null) {
        var depsUri = getProjectDepsFileUri();
        var moduleKey = moduleResolver.resolve(depsUri);
        try {
          // treat PklProject.deps.json as a module read, rather than introduce a new API.
          var depsJson = moduleKey.resolve(securityManager).loadSource();
          projectDeps = ProjectDeps.parse(depsJson);
        } catch (IOException e) {
          throw new VmExceptionBuilder()
              .evalError("cannotLoadProjectDepsJson", depsUri)
              .withCause(e)
              .withHint(e.getMessage() != null ? e.getMessage() : ("Encountered error: " + e))
              .build();
        } catch (JsonParseException e) {
          throw new VmExceptionBuilder()
              .evalError("invalidProjectDepsJson", depsUri, e.getMessage())
              .build();
        } catch (SecurityManagerException e) {
          throw PklBugException.unreachableCode();
        }
      }
      return projectDeps;
    }
  }
}
