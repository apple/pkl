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
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pkl.core.Composite;
import org.pkl.core.Duration;
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
import org.pkl.core.Version;
import org.pkl.core.httpsettings.PklHttpSettings;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.packages.Checksums;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.packages.PackageUtils;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.core.util.Nullable;

/** Java representation of module {@code pkl.Project}. */
public final class Project {
  private final @Nullable Package pkg;
  private final DeclaredDependencies dependencies;
  private final EvaluatorSettings evaluatorSettings;
  private final URI projectFileUri;
  private final Path projectDir;
  private final List<Path> tests;
  private final Map<String, Project> localProjectDependencies;

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
      var output = evaluator.evaluateOutputValueAs(ModuleSource.path(path), PClassInfo.Project);
      return Project.parseProject(output);
    } catch (URISyntaxException e) {
      throw new PklException(e.getMessage(), e);
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

  public static Project parseProject(PObject module) throws URISyntaxException {
    var pkgObj = getNullableProperty(module, "package");
    var projectFileUri = URI.create((String) module.getProperty("projectFileUri"));
    var dependencies = parseDependencies(module, projectFileUri, null);
    var projectDir = Path.of(projectFileUri).getParent();
    Package pkg = null;
    if (pkgObj != null) {
      pkg = parsePackage((PObject) pkgObj);
    }
    var evaluatorSettings =
        getProperty(
            module,
            "evaluatorSettings",
            (settings) -> parseEvaluatorSettings(settings, projectDir));
    @SuppressWarnings("unchecked")
    var testPathStrs = (List<String>) getProperty(module, "tests");
    var tests =
        testPathStrs.stream()
            .map((it) -> projectDir.resolve(it).normalize())
            .collect(Collectors.toList());
    var localProjectDependencies = parseLocalProjectDependencies(module);
    return new Project(
        pkg,
        dependencies,
        evaluatorSettings,
        projectFileUri,
        projectDir,
        tests,
        localProjectDependencies);
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

  @SuppressWarnings("unchecked")
  private static EvaluatorSettings parseEvaluatorSettings(Object settings, Path projectDir) {
    var pSettings = (PObject) settings;
    var externalProperties = getNullableProperty(pSettings, "externalProperties", Project::asMap);
    var env = getNullableProperty(pSettings, "env", Project::asMap);
    var allowedModules = getNullableProperty(pSettings, "allowedModules", Project::asPatternList);
    var allowedResources =
        getNullableProperty(pSettings, "allowedResources", Project::asPatternList);
    var noCache = (Boolean) getNullableProperty(pSettings, "noCache");
    var modulePathStrs = (List<String>) getNullableProperty(pSettings, "modulePath");
    List<Path> modulePath = null;
    if (modulePathStrs != null) {
      modulePath =
          modulePathStrs.stream()
              .map((it) -> projectDir.resolve(it).normalize())
              .collect(Collectors.toList());
    }
    var timeout = (Duration) getNullableProperty(pSettings, "timeout");
    var moduleCacheDir = getNullablePath(pSettings, "moduleCacheDir", projectDir);
    var rootDir = getNullablePath(pSettings, "rootDir", projectDir);
    var httpSettings = getProperty(pSettings, "http", (it) -> PklHttpSettings.parse((PObject) it));
    return new EvaluatorSettings(
        externalProperties,
        env,
        allowedModules,
        allowedResources,
        noCache,
        moduleCacheDir,
        modulePath,
        timeout,
        rootDir,
        httpSettings);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> asMap(Object t) {
    assert t instanceof Map;
    return (Map<String, String>) t;
  }

  @SuppressWarnings("unchecked")
  private static List<Pattern> asPatternList(Object t) {
    return ((List<String>) t).stream().map(Pattern::compile).collect(Collectors.toList());
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

  private static @Nullable Path getNullablePath(
      Composite object, String propertyName, Path projectDir) {
    return getNullableProperty(
        object, propertyName, (obj) -> projectDir.resolve((String) obj).normalize());
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
    var issueTracker = (URI) getNullableURI(pObj, "issueTracker");
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
      EvaluatorSettings evaluatorSettings,
      URI projectFileUri,
      Path projectDir,
      List<Path> tests,
      Map<String, Project> localProjectDependencies) {
    this.pkg = pkg;
    this.dependencies = dependencies;
    this.evaluatorSettings = evaluatorSettings;
    this.projectFileUri = projectFileUri;
    this.projectDir = projectDir;
    this.tests = tests;
    this.localProjectDependencies = localProjectDependencies;
  }

  public @Nullable Package getPackage() {
    return pkg;
  }

  public EvaluatorSettings getSettings() {
    return evaluatorSettings;
  }

  public URI getProjectFileUri() {
    return projectFileUri;
  }

  public List<Path> getTests() {
    return tests;
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
        && tests.equals(project.tests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pkg, dependencies, evaluatorSettings, projectFileUri, tests);
  }

  public DeclaredDependencies getDependencies() {
    return dependencies;
  }

  public Map<String, Project> getLocalProjectDependencies() {
    return localProjectDependencies;
  }

  public Path getProjectDir() {
    return projectDir;
  }

  public static class EvaluatorSettings {
    private final @Nullable Map<String, String> externalProperties;
    private final @Nullable Map<String, String> env;
    private final @Nullable List<Pattern> allowedModules;
    private final @Nullable List<Pattern> allowedResources;
    private final @Nullable Boolean noCache;
    private final @Nullable Path moduleCacheDir;
    private final @Nullable List<Path> modulePath;
    private final @Nullable Duration timeout;
    private final @Nullable Path rootDir;
    private final PklHttpSettings http;

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
        PklHttpSettings http) {
      this.externalProperties = externalProperties;
      this.env = env;
      this.allowedModules = allowedModules;
      this.allowedResources = allowedResources;
      this.noCache = noCache;
      this.moduleCacheDir = moduleCacheDir;
      this.modulePath = modulePath;
      this.timeout = timeout;
      this.rootDir = rootDir;
      this.http = http;
    }

    public @Nullable Map<String, String> getExternalProperties() {
      return externalProperties;
    }

    public @Nullable Map<String, String> getEnv() {
      return env;
    }

    public @Nullable List<Pattern> getAllowedModules() {
      return allowedModules;
    }

    public @Nullable List<Pattern> getAllowedResources() {
      return allowedResources;
    }

    public @Nullable Boolean isNoCache() {
      return noCache;
    }

    public @Nullable List<Path> getModulePath() {
      return modulePath;
    }

    public @Nullable Duration getTimeout() {
      return timeout;
    }

    public @Nullable Path getModuleCacheDir() {
      return moduleCacheDir;
    }

    public @Nullable Path getRootDir() {
      return rootDir;
    }

    private boolean arePatternsEqual(
        @Nullable List<Pattern> myPattern, @Nullable List<Pattern> thatPattern) {
      if (myPattern == null) {
        return thatPattern == null;
      }
      if (thatPattern == null) {
        return false;
      }
      if (myPattern.size() != thatPattern.size()) {
        return false;
      }
      for (var i = 0; i < myPattern.size(); i++) {
        if (!myPattern.get(i).pattern().equals(thatPattern.get(i).pattern())) {
          return false;
        }
      }
      return true;
    }

    private int hashPatterns(@Nullable List<Pattern> patterns) {
      if (patterns == null) {
        return 0;
      }
      var ret = 1;
      for (var pattern : patterns) {
        ret = 31 * ret + pattern.pattern().hashCode();
      }
      return ret;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EvaluatorSettings that = (EvaluatorSettings) o;
      return Objects.equals(externalProperties, that.externalProperties)
          && Objects.equals(env, that.env)
          && arePatternsEqual(allowedModules, that.allowedModules)
          && arePatternsEqual(allowedResources, that.allowedResources)
          && Objects.equals(noCache, that.noCache)
          && Objects.equals(moduleCacheDir, that.moduleCacheDir)
          && Objects.equals(modulePath, that.modulePath)
          && Objects.equals(timeout, that.timeout)
          && Objects.equals(rootDir, that.rootDir);
    }

    @Override
    public int hashCode() {
      var result =
          Objects.hash(
              externalProperties, env, noCache, moduleCacheDir, modulePath, timeout, rootDir);
      result = 31 * result + hashPatterns(allowedModules);
      result = 31 * result + hashPatterns(allowedResources);
      return result;
    }

    @Override
    public String toString() {
      return "EvaluatorSettings{"
          + "externalProperties="
          + externalProperties
          + ", env="
          + env
          + ", allowedModules="
          + allowedModules
          + ", allowedResources="
          + allowedResources
          + ", noCache="
          + noCache
          + ", moduleCacheDir="
          + moduleCacheDir
          + ", modulePath="
          + modulePath
          + ", timeout="
          + timeout
          + ", rootDir="
          + rootDir
          + '}';
    }

    public PklHttpSettings getHttp() {
      return http;
    }
  }
}
