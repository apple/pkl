/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pkl.core.Analyzer;
import org.pkl.core.Composite;
import org.pkl.core.Duration;
import org.pkl.core.Evaluator;
import org.pkl.core.EvaluatorBuilder;
import org.pkl.core.ModuleSource;
import org.pkl.core.PClassInfo;
import org.pkl.core.PNull;
import org.pkl.core.PObject;
import org.pkl.core.PklException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagers;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.StackFrameTransformers;
import org.pkl.core.Value;
import org.pkl.core.Version;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.packages.Checksums;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.packages.PackageUtils;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.ImportGraphUtils;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

/** Java representation of module {@code pkl.Project}. */
public final class Project {
  private final @Nullable Package pkg;
  private final DeclaredDependencies dependencies;
  private final PklEvaluatorSettings evaluatorSettings;
  private final URI projectFileUri;
  private final URI projectBaseUri;
  private final List<URI> tests;
  private final Map<String, Project> localProjectDependencies;
  private final List<PObject> annotations;

  /**
   * Loads Project data from the given {@link Path}.
   *
   * <p>Evaluates a module's {@code output.value} to allow for embedding a project within a
   * template.
   *
   * @throws PklException if an error occurred while evaluating the project file.
   */
  public static Project loadFromPath(
      Path path,
      SecurityManager securityManager,
      @Nullable java.time.Duration timeout,
      StackFrameTransformer stackFrameTransformer,
      Map<String, String> envVars) {
    try (var evaluator =
        EvaluatorBuilder.unconfigured()
            .setSecurityManager(securityManager)
            .setStackFrameTransformer(stackFrameTransformer)
            .addModuleKeyFactory(ModuleKeyFactories.standardLibrary)
            .addModuleKeyFactory(ModuleKeyFactories.file)
            .addResourceReader(ResourceReaders.environmentVariable())
            .addResourceReader(ResourceReaders.file())
            .addEnvironmentVariables(envVars)
            .setTimeout(timeout)
            .build()) {
      return load(evaluator, ModuleSource.path(path));
    }
  }

  /** Convenience method to load a project with the default stack frame transformer. */
  public static Project loadFromPath(
      Path path, SecurityManager securityManager, @Nullable java.time.Duration timeout) {
    return loadFromPath(
        path, securityManager, timeout, StackFrameTransformers.defaultTransformer, System.getenv());
  }

  /**
   * Convenience method to load a project with the default security manager, no timeout, and the
   * default stack frame transformer.
   */
  public static Project loadFromPath(Path path) {
    return loadFromPath(path, SecurityManagers.defaultManager, null);
  }

  /** Loads a project from the given source. */
  public static Project load(ModuleSource moduleSource) {
    try (var evaluator = evaluatorBuilder().build()) {
      return load(evaluator, moduleSource);
    }
  }

  public static Project load(Evaluator evaluator, ModuleSource moduleSource) {
    try {
      var output = evaluator.evaluateOutputValueAs(moduleSource, PClassInfo.Project);
      return Project.parseProject(output);
    } catch (StackOverflowError e) {
      var cycles = findImportCycle(moduleSource);
      VmException vmException;
      if (!cycles.isEmpty()) {
        if (cycles.size() == 1) {
          vmException =
              new VmExceptionBuilder()
                  .evalError(
                      "cannotHaveCircularProjectDependenciesSingle",
                      renderCycle(cycles.stream().toList().get(0)))
                  .withCause(e)
                  .build();
        } else {
          var renderedCycles = renderMultipleCycles(cycles);
          vmException =
              new VmExceptionBuilder()
                  .evalError("cannotHaveCircularProjectDependenciesMultiple", renderedCycles)
                  .withCause(e)
                  .build();
        }
        // stack frame transformer never used; this exception has no stack frames.
        throw vmException.toPklException(StackFrameTransformers.defaultTransformer, false);
      }
      throw e;
    } catch (URISyntaxException e) {
      throw new PklException(e.getMessage(), e);
    }
  }

  private static String renderMultipleCycles(List<List<URI>> cycles) {
    var sb = new StringBuilder();
    var i = 0;
    for (var cycle : cycles) {
      if (i > 0) {
        sb.append('\n');
      }
      sb.append("Cycle ").append(i + 1).append(":\n");
      renderCycle(sb, cycle);
      sb.append('\n');
      i++;
    }
    return sb.toString();
  }

  private static void renderCycle(StringBuilder sb, List<URI> cycle) {
    sb.append("┌─>");
    var isFirst = true;
    for (URI uri : cycle) {
      if (isFirst) {
        isFirst = false;
      } else {
        sb.append("\n│");
      }
      sb.append("\n│  ");
      sb.append(uri.toString());
    }
    sb.append("\n└─");
  }

  private static String renderCycle(List<URI> cycle) {
    var sb = new StringBuilder();
    renderCycle(sb, cycle);
    return sb.toString();
  }

  private static List<List<URI>> findImportCycle(ModuleSource moduleSource) {
    var builder = evaluatorBuilder();
    var analyzer =
        new Analyzer(
            StackFrameTransformers.defaultTransformer,
            builder.getColor(),
            SecurityManagers.defaultManager,
            builder.getModuleKeyFactories(),
            builder.getModuleCacheDir(),
            builder.getProjectDependencies(),
            builder.getHttpClient(),
            builder.getPrettyTraces());
    var importGraph = analyzer.importGraph(moduleSource.getUri());
    var ret = ImportGraphUtils.findImportCycles(importGraph);
    // we only care about cycles in the same scheme as `moduleSource`
    return ret.stream()
        .filter(
            (cycle) ->
                cycle.stream()
                    .anyMatch(
                        (uri) ->
                            uri.getScheme().equalsIgnoreCase(moduleSource.getUri().getScheme())))
        .collect(Collectors.toList());
  }

  private static EvaluatorBuilder evaluatorBuilder() {
    return EvaluatorBuilder.unconfigured()
        .setSecurityManager(SecurityManagers.defaultManager)
        .setStackFrameTransformer(StackFrameTransformers.defaultTransformer)
        .addModuleKeyFactory(ModuleKeyFactories.standardLibrary)
        .addModuleKeyFactory(ModuleKeyFactories.file)
        .addModuleKeyFactory(ModuleKeyFactories.classPath(Project.class.getClassLoader()))
        .addResourceReader(ResourceReaders.environmentVariable())
        .addResourceReader(ResourceReaders.file());
  }

  private static DeclaredDependencies parseDependencies(
      PObject module, URI projectFileUri, @Nullable PackageUri packageUri)
      throws URISyntaxException {
    var remoteDependencies = new HashMap<String, RemoteDependency>();
    var localDependencies = new HashMap<String, DeclaredDependencies>();
    //noinspection unchecked
    var dependencies = (Map<String, PObject>) module.getProperty("dependencies");
    for (var entry : dependencies.entrySet()) {
      var value = entry.getValue();
      if (value.getClassInfo().equals(PClassInfo.Project)) {
        var localProjectFileUri = URI.create((String) value.getProperty("projectFileUri"));
        var localPkgUri =
            PackageUri.create((String) ((PObject) value.getProperty("package")).getProperty("uri"));
        localDependencies.put(
            entry.getKey(), parseDependencies(value, localProjectFileUri, localPkgUri));
      } else {
        remoteDependencies.put(entry.getKey(), parseRemoteDependency(value));
      }
    }
    return new DeclaredDependencies(
        remoteDependencies, localDependencies, projectFileUri, packageUri);
  }

  private static RemoteDependency parseRemoteDependency(PObject object) throws URISyntaxException {
    var packageUri = new PackageUri((String) object.getProperty("uri"));
    PackageUtils.checkHasNoChecksumComponent(packageUri);
    var objChecksum = object.getProperty("checksums");
    Checksums checksums = null;
    if (objChecksum instanceof PObject pObject) {
      var sha256 = (String) pObject.get("sha256");
      assert sha256 != null;
      checksums = new Checksums(sha256);
    }
    return new RemoteDependency(packageUri, checksums);
  }

  @SuppressWarnings("unchecked")
  private static List<PObject> parseAnnotations(PObject module) {
    return (List<PObject>) getProperty(module, "annotations");
  }

  public static Project parseProject(PObject module) throws URISyntaxException {
    var pkgObj = getNullableProperty(module, "package");
    var projectFileUri = URI.create((String) module.getProperty("projectFileUri"));
    var dependencies = parseDependencies(module, projectFileUri, null);
    var projectBaseUri = IoUtils.resolve(projectFileUri, ".");
    Package pkg = null;
    if (pkgObj != null) {
      pkg = parsePackage((PObject) pkgObj);
    }
    var evaluatorSettings =
        getProperty(
            module,
            "evaluatorSettings",
            (settings) ->
                PklEvaluatorSettings.parse(
                    (Value) settings, (it, name) -> resolveNullablePath(it, projectBaseUri, name)));
    @SuppressWarnings("unchecked")
    var testPathStrs = (List<String>) getProperty(module, "tests");
    var tests =
        testPathStrs.stream()
            .map((it) -> projectBaseUri.resolve(it).normalize())
            .collect(Collectors.toList());
    var localProjectDependencies = parseLocalProjectDependencies(module);
    var annotations = parseAnnotations(module);
    return new Project(
        pkg,
        dependencies,
        evaluatorSettings,
        projectFileUri,
        projectBaseUri,
        tests,
        localProjectDependencies,
        annotations);
  }

  private static Map<String, Project> parseLocalProjectDependencies(PObject module)
      throws URISyntaxException {
    //noinspection unchecked
    var dependencies = (Map<String, PObject>) module.getProperty("dependencies");
    var result = new HashMap<String, Project>();
    for (var entry : dependencies.entrySet()) {
      var value = entry.getValue();
      if (value.getClassInfo().equals(PClassInfo.Project)) {
        result.put(entry.getKey(), parseProject(entry.getValue()));
      }
    }
    return result;
  }

  private static Object getProperty(PObject settings, String propertyName) {
    return settings.getProperty(propertyName);
  }

  private static <T> T getProperty(PObject settings, String propertyName, Function<Object, T> f) {
    return Objects.requireNonNull(getNullableProperty(settings, propertyName, f));
  }

  private static @Nullable Object getNullableProperty(Composite object, String propertyName) {
    var result = object.getPropertyOrNull(propertyName);
    if (result instanceof PNull || result == null) {
      return null;
    }
    return result;
  }

  private static @Nullable <T> T getNullableProperty(
      Composite object, String propertyName, Function<Object, T> f) {
    var value = object.getPropertyOrNull(propertyName);
    if (value instanceof PNull || value == null) {
      return null;
    }
    return f.apply(value);
  }

  private static @Nullable URI getNullableURI(Composite object, String propertyName)
      throws URISyntaxException {
    var value = object.getPropertyOrNull(propertyName);
    if (value instanceof PNull || value == null) {
      return null;
    }
    return new URI((String) value);
  }

  /**
   * Resolve a path string against projectBaseUri.
   *
   * @throws PackageLoadError if projectBaseUri is not a {@code file:} URI.
   */
  private static @Nullable Path resolveNullablePath(
      @Nullable String path, URI projectBaseUri, String propertyName) {
    if (path == null) {
      return null;
    }
    try {
      return Path.of(projectBaseUri).resolve(path).normalize();
    } catch (FileSystemNotFoundException e) {
      throw new PackageLoadError(
          "relativePathPropertyDefinedByProjectFromNonFileUri", projectBaseUri, propertyName);
    }
  }

  @SuppressWarnings("unchecked")
  private static Package parsePackage(PObject pObj) throws URISyntaxException {
    var name = (String) pObj.getProperty("name");
    var uri = new PackageUri((String) pObj.getProperty("uri"));
    var version = Version.parse((String) getProperty(pObj, "version"));
    var packageZipUrl = new URI((String) getProperty(pObj, "packageZipUrl"));
    var description = (String) getNullableProperty(pObj, "description");
    var authors = (List<String>) getProperty(pObj, "authors");
    var website = getNullableURI(pObj, "website");
    var documentation = getNullableURI(pObj, "documentation");
    var sourceCode = getNullableURI(pObj, "sourceCode");
    var sourceCodeUrlScheme = (String) getNullableProperty(pObj, "sourceCodeUrlScheme");
    var license = (String) getNullableProperty(pObj, "license");
    var licenseText = (String) getNullableProperty(pObj, "licenseText");
    var issueTracker = getNullableURI(pObj, "issueTracker");
    var apiTestStrs = (List<String>) getProperty(pObj, "apiTests");
    var apiTests = apiTestStrs.stream().map(Path::of).collect(Collectors.toList());
    var exclude = (List<String>) getProperty(pObj, "exclude");

    return new Package(
        name,
        uri,
        version,
        packageZipUrl,
        description,
        authors,
        website,
        documentation,
        sourceCode,
        sourceCodeUrlScheme,
        license,
        licenseText,
        issueTracker,
        apiTests,
        exclude);
  }

  private Project(
      @Nullable Package pkg,
      DeclaredDependencies dependencies,
      PklEvaluatorSettings evaluatorSettings,
      URI projectFileUri,
      URI projectBaseUri,
      List<URI> tests,
      Map<String, Project> localProjectDependencies,
      List<PObject> annotations) {
    this.pkg = pkg;
    this.dependencies = dependencies;
    this.evaluatorSettings = evaluatorSettings;
    this.projectFileUri = projectFileUri;
    this.projectBaseUri = projectBaseUri;
    this.tests = tests;
    this.localProjectDependencies = localProjectDependencies;
    this.annotations = annotations;
  }

  public @Nullable Package getPackage() {
    return pkg;
  }

  /** Use {@link org.pkl.core.project.Project#getEvaluatorSettings()} instead. */
  @Deprecated(forRemoval = true)
  public EvaluatorSettings getSettings() {
    return new EvaluatorSettings(evaluatorSettings);
  }

  public PklEvaluatorSettings getEvaluatorSettings() {
    return evaluatorSettings;
  }

  public URI getProjectFileUri() {
    return projectFileUri;
  }

  public List<Path> getTests() {
    return tests.stream()
        .map(
            (it) -> {
              try {
                return Path.of(it);
              } catch (FileSystemNotFoundException e) {
                throw new PackageLoadError("invalidUsageOfProjectFromNonFileUri");
              }
            })
        .collect(Collectors.toList());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Project project = (Project) o;
    return Objects.equals(pkg, project.pkg)
        && dependencies.equals(project.dependencies)
        && evaluatorSettings.equals(project.evaluatorSettings)
        && projectFileUri.equals(project.projectFileUri)
        && tests.equals(project.tests)
        && annotations.equals(project.annotations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pkg, dependencies, evaluatorSettings, projectFileUri, tests, annotations);
  }

  public DeclaredDependencies getDependencies() {
    return dependencies;
  }

  public Map<String, Project> getLocalProjectDependencies() {
    return localProjectDependencies;
  }

  public URI getProjectBaseUri() {
    return projectBaseUri;
  }

  public Path getProjectDir() {
    assert projectBaseUri.getScheme().equalsIgnoreCase("file");
    return Path.of(projectBaseUri);
  }

  public List<PObject> getAnnotations() {
    return annotations;
  }

  @Deprecated(forRemoval = true)
  public static class EvaluatorSettings {
    private final PklEvaluatorSettings delegate;

    public EvaluatorSettings(PklEvaluatorSettings delegate) {
      this.delegate = delegate;
    }

    public EvaluatorSettings(
        @Nullable Map<String, String> externalProperties,
        @Nullable Map<String, String> env,
        @Nullable List<Pattern> allowedModules,
        @Nullable List<Pattern> allowedResources,
        @Nullable Boolean noCache,
        @Nullable Path moduleCacheDir,
        @Nullable List<Path> modulePath,
        @Nullable Duration timeout,
        @Nullable Path rootDir,
        @Nullable Boolean prettyTraces) {
      this.delegate =
          new PklEvaluatorSettings(
              externalProperties,
              env,
              allowedModules,
              allowedResources,
              null,
              noCache,
              moduleCacheDir,
              modulePath,
              timeout,
              rootDir,
              null,
              null,
              null,
              prettyTraces);
    }

    @Deprecated(forRemoval = true)
    public @Nullable Map<String, String> getExternalProperties() {
      return delegate.externalProperties();
    }

    @Deprecated(forRemoval = true)
    public @Nullable Map<String, String> getEnv() {
      return delegate.env();
    }

    @Deprecated(forRemoval = true)
    public @Nullable List<Pattern> getAllowedModules() {
      return delegate.allowedModules();
    }

    @Deprecated(forRemoval = true)
    public @Nullable List<Pattern> getAllowedResources() {
      return delegate.allowedResources();
    }

    @Deprecated(forRemoval = true)
    public @Nullable Boolean isNoCache() {
      return delegate.noCache();
    }

    @Deprecated(forRemoval = true)
    public @Nullable List<Path> getModulePath() {
      return delegate.modulePath();
    }

    @Deprecated(forRemoval = true)
    public @Nullable Duration getTimeout() {
      return delegate.timeout();
    }

    @Deprecated(forRemoval = true)
    public @Nullable Path getModuleCacheDir() {
      return delegate.moduleCacheDir();
    }

    @Deprecated(forRemoval = true)
    public @Nullable Path getRootDir() {
      return delegate.rootDir();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      return o != null
          && getClass() == o.getClass()
          && Objects.equals(delegate, ((EvaluatorSettings) o).delegate);
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }

    @Override
    public String toString() {
      return "EvaluatorSettings{"
          + "externalProperties="
          + delegate.externalProperties()
          + ", env="
          + delegate.env()
          + ", allowedModules="
          + delegate.allowedModules()
          + ", allowedResources="
          + delegate.allowedResources()
          + ", noCache="
          + delegate.noCache()
          + ", moduleCacheDir="
          + delegate.moduleCacheDir()
          + ", modulePath="
          + delegate.modulePath()
          + ", timeout="
          + delegate.timeout()
          + ", rootDir="
          + delegate.rootDir()
          + ", prettyTraces="
          + delegate.prettyTraces()
          + '}';
    }
  }
}
