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
package org.pkl.core;

import com.oracle.truffle.api.TruffleOptions;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.pkl.core.SecurityManagers.StandardBuilder;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModuleKeyFactory;
import org.pkl.core.module.ModulePathResolver;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.project.Project;
import org.pkl.core.project.Project.EvaluatorSettings;
import org.pkl.core.resource.ResourceReader;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.core.runtime.LoggerImpl;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

/**
 * A builder of {@linkplain Evaluator evaluators}.
 *
 * <p>To create a new builder, use {@link #preconfigured} or {@link #unconfigured}. To build an
 * evaluator from the current builder state, use {@link #build}.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class EvaluatorBuilder {
  private final StandardBuilder securityManagerBuilder = SecurityManagers.standardBuilder();

  private @Nullable SecurityManager securityManager;

  private Logger logger = Loggers.noop();

  private final List<ModuleKeyFactory> moduleKeyFactories = new ArrayList<>();

  private final List<ResourceReader> resourceReaders = new ArrayList<>();

  private final Map<String, String> environmentVariables = new HashMap<>();

  private final Map<String, String> externalProperties = new HashMap<>();

  private @Nullable java.time.Duration timeout;

  private @Nullable Path moduleCacheDir = getDefaultModuleCacheDir();

  private @Nullable String outputFormat;

  private StackFrameTransformer stackFrameTransformer = StackFrameTransformers.empty;

  private @Nullable DeclaredDependencies dependencies;

  private EvaluatorBuilder() {}

  /**
   * Creates a builder with defaults for the following options:
   *
   * <ul>
   *   <li>{@link #setStackFrameTransformer}
   *   <li>{@link #setAllowedModules}
   *   <li>{@link #setAllowedResources}
   *   <li>{@link #setTrustLevels}
   *   <li>{@link #setLogger}
   *   <li>{@link #setModuleCacheDir}
   *   <li>{@link #setModuleKeyFactories}
   *   <li>{@link #setResourceReaders}
   *   <li>{@link #setExternalProperties}
   *   <li>{@link #setEnvironmentVariables}
   * </ul>
   *
   * <p>See each of the above options for its default.
   *
   * @see #unconfigured
   */
  // update defaults documented in set* methods when making any changes here
  public static EvaluatorBuilder preconfigured() {
    EvaluatorBuilder builder = new EvaluatorBuilder();

    builder
        .setStackFrameTransformer(StackFrameTransformers.defaultTransformer)
        .setAllowedModules(SecurityManagers.defaultAllowedModules)
        .setAllowedResources(SecurityManagers.defaultAllowedResources)
        .addResourceReader(ResourceReaders.environmentVariable())
        .addResourceReader(ResourceReaders.externalProperty())
        .addResourceReader(ResourceReaders.file())
        .addResourceReader(ResourceReaders.http())
        .addResourceReader(ResourceReaders.https())
        .addResourceReader(ResourceReaders.pkg())
        .addResourceReader(ResourceReaders.projectpackage())
        .addModuleKeyFactory(ModuleKeyFactories.standardLibrary);

    if (!TruffleOptions.AOT) {
      // AOT does not support class loader API
      var classLoader = EvaluatorBuilder.class.getClassLoader();
      builder
          .addModuleKeyFactory(ModuleKeyFactories.classPath(classLoader))
          .addResourceReader(ResourceReaders.classPath(classLoader));

      // only add system properties when running on JVM
      addSystemProperties(builder);
    }

    builder
        .addModuleKeyFactories(ModuleKeyFactories.fromServiceProviders())
        .addModuleKeyFactory(ModuleKeyFactories.file)
        .addModuleKeyFactory(ModuleKeyFactories.pkg)
        .addModuleKeyFactory(ModuleKeyFactories.projectpackage)
        .addModuleKeyFactory(ModuleKeyFactories.genericUrl)
        .addEnvironmentVariables(System.getenv());

    return builder;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void addSystemProperties(EvaluatorBuilder builder) {
    builder.addExternalProperties((Map) System.getProperties());
  }

  /**
   * Creates a builder with defaults for the following options:
   *
   * <ul>
   *   <li>{@link #setStackFrameTransformer}
   *   <li>{@link #setTrustLevels}
   *   <li>{@link #setLogger}
   *   <li>{@link #setModuleCacheDir}
   * </ul>
   *
   * <p>See each of the above options for its default.
   *
   * <p>To complete the configuration of the returned builder, at least one of the following methods
   * must be called:
   *
   * <ul>
   *   <li>{@link #addAllowedModule}
   *   <li>{@link #setAllowedModules}
   *   <li>{@link #setSecurityManager}
   * </ul>
   *
   * @see #preconfigured
   */
  public static EvaluatorBuilder unconfigured() {
    return new EvaluatorBuilder();
  }

  /**
   * Sets the stack frame transformer that adds information to Pkl stack traces.
   *
   * <p>Stack frame transformers can be composed with {@link Function#andThen}. Class {@link
   * StackFrameTransformers} provides several implementations.
   *
   * <p>Default when {@link #unconfigured}: {@link StackFrameTransformers#empty}
   *
   * <p>Default when {@link #preconfigured}: {@link StackFrameTransformers#defaultTransformer}
   *
   * @see #getStackFrameTransformer
   */
  public EvaluatorBuilder setStackFrameTransformer(StackFrameTransformer stackFrameTransformer) {
    this.stackFrameTransformer = stackFrameTransformer;
    return this;
  }

  /**
   * Returns the stack frame transformer that adds information to Pkl stack traces.
   *
   * <p>For more information, see {@link #setStackFrameTransformer}.
   */
  public @Nullable StackFrameTransformer getStackFrameTransformer() {
    return stackFrameTransformer;
  }

  /**
   * Sets a custom security manager.
   *
   * <p>If {@code null}, a {@link SecurityManagers#standard standard} security manager built from
   * the following options is used:
   *
   * <ul>
   *   <li>{@link #setAllowedModules allowedModules}
   *   <li>{@link #setAllowedResources allowedResources}
   *   <li>{@link #setTrustLevels trustLevels}
   *   <li>{@link #setRootDir rootDir}
   * </ul>
   *
   * <p>Otherwise, the given custom security manager is used, and the above options are ignored.
   *
   * <p>Default: {@code null}
   */
  public EvaluatorBuilder setSecurityManager(@Nullable SecurityManager manager) {
    this.securityManager = manager;
    return this;
  }

  /**
   * Same as {@code setSecurityManager(null)}.
   *
   * <p>For more information, see {@link #setSecurityManager}.
   */
  public EvaluatorBuilder unsetSecurityManager() {
    this.securityManager = null;
    return this;
  }

  /**
   * Returns the custom security manager.
   *
   * <p>For more information, see {@link #setSecurityManager}.
   */
  public @Nullable SecurityManager getSecurityManager() {
    return securityManager;
  }

  /**
   * Adds a URI pattern that determines if a module import is allowed.
   *
   * <p>For more information, see {@link #setAllowedModules}.
   *
   * @see #getAllowedModules
   * @see #setTrustLevels
   */
  public EvaluatorBuilder addAllowedModule(Pattern pattern) {
    requireNullSecurityManager("addAllowedModule");
    securityManagerBuilder.addAllowedModule(pattern);
    return this;
  }

  /**
   * Sets the URI patterns that determine if a module import is allowed.
   *
   * <p>Pkl code can import a module with {@code import "moduleUri"}, {@code amends "moduleUri},
   * {@code extends "moduleUri"}, or an {@code import("moduleUri")} expression. The import is
   * allowed only if {@code moduleUri} matches at least one of the given patterns.
   *
   * <p>Setting a {@linkplain #setSecurityManager custom security manager} causes this option to be
   * ignored.
   *
   * <p>Default when {@link #unconfigured}: empty list
   *
   * <p>Default when {@link #preconfigured}: {@link SecurityManagers#defaultAllowedModules}
   *
   * @throws IllegalStateException if a {@linkplain #setSecurityManager custom security manager} has
   *     been set
   * @see #addAllowedModule
   * @see #getAllowedModules
   * @see #setTrustLevels
   */
  public EvaluatorBuilder setAllowedModules(Collection<Pattern> patterns) {
    requireNullSecurityManager("setAllowedModules");
    securityManagerBuilder.setAllowedModules(patterns);
    return this;
  }

  /**
   * Returns the URI patterns that determine if a module import is allowed.
   *
   * <p>For more information, see {@link #setAllowedModules}.
   *
   * @see #addAllowedModule
   * @see #setTrustLevels
   */
  public List<Pattern> getAllowedModules() {
    return securityManagerBuilder.getAllowedModules();
  }

  /**
   * Adds a URI pattern that determines if a resource read is allowed.
   *
   * <p>For more information, see {@link #setAllowedResources}.
   *
   * @see #getAllowedResources
   */
  public EvaluatorBuilder addAllowedResource(Pattern pattern) {
    requireNullSecurityManager("addAllowedResource");
    securityManagerBuilder.addAllowedResource(pattern);
    return this;
  }

  /**
   * Sets the URI patterns that determine if a resource read is allowed.
   *
   * <p>Pkl code can read a resource with {@code read(resourceUri)}. The read is allowed only if
   * {@code resourceUri} matches at least one of the given patterns.
   *
   * <p>Setting a {@linkplain #setSecurityManager custom security manager} causes this option to be
   * ignored.
   *
   * <p>Default when {@link #unconfigured}: empty list
   *
   * <p>Default when {@link #preconfigured}: {@link SecurityManagers#defaultAllowedResources}
   *
   * <ul>
   *   <li>{@link SecurityManagers#defaultAllowedResources} for a {@link #preconfigured} builder
   *   <li>empty list for an {@link #unconfigured} builder
   * </ul>
   *
   * @throws IllegalStateException if a {@linkplain #setSecurityManager custom security manager} has
   *     been set
   * @see #addAllowedResource
   * @see #getAllowedResources
   */
  public EvaluatorBuilder setAllowedResources(Collection<Pattern> patterns) {
    requireNullSecurityManager("setAllowedResources");
    securityManagerBuilder.setAllowedResources(patterns);
    return this;
  }

  /**
   * Returns the URI patterns that determine if a resource read is allowed.
   *
   * <p>For more information, see {@link #setAllowedResources}.
   *
   * @see #addAllowedResource
   */
  public List<Pattern> getAllowedResources() {
    return securityManagerBuilder.getAllowedResources();
  }

  /**
   * Sets the root directory that file-based module imports and resource reads are restricted to.
   *
   * <p>If {@code null}, file-based module imports and resource reads are not restricted to a root
   * directory. However, they are still subject to other security manager checks.
   *
   * <p>Setting a {@linkplain #setSecurityManager custom security manager} causes this option to be
   * ignored.
   *
   * <p>Default: {@code null}
   *
   * @throws IllegalStateException if a {@linkplain #setSecurityManager custom security manager} has
   *     been set
   * @see #setAllowedModules
   * @see #setAllowedResources
   * @see #setTrustLevels
   * @see #getRootDir
   */
  public EvaluatorBuilder setRootDir(@Nullable Path rootDir) {
    requireNullSecurityManager("setRootDir");
    securityManagerBuilder.setRootDir(rootDir);
    return this;
  }

  /**
   * Returns the root directory that file-based module imports and resource reads are restricted to.
   *
   * <p>For more information, see {@link #setRootDir}.
   */
  public @Nullable Path getRootDir() {
    return securityManagerBuilder.getRootDir();
  }

  /**
   * Sets the module trust levels that determine if a module import is allowed.
   *
   * <p>Pkl code can import a module with {@code import "moduleUri"}, {@code amends "moduleUri},
   * {@code extends "moduleUri"}, or an {@code import("moduleUri")} expression. The import is
   * allowed only if the importing module has a higher trust level than the imported module.
   *
   * <p>Setting a {@linkplain #setSecurityManager custom security manager} causes this option to be
   * ignored.
   *
   * <p>Default: {@link SecurityManagers#defaultTrustLevels}
   *
   * @throws IllegalStateException if a custom security manager has been set
   * @see #getTrustLevels
   */
  public EvaluatorBuilder setTrustLevels(Function<URI, Integer> trustLevels) {
    requireNullSecurityManager("setTrustLevels");
    securityManagerBuilder.setTrustLevels(trustLevels);
    return this;
  }

  /**
   * Returns the module trust levels that determine if a module import is allowed.
   *
   * <p>For more information, see {@link #setTrustLevels}.
   */
  public Function<URI, Integer> getTrustLevels() {
    return securityManagerBuilder.getTrustLevels();
  }

  /**
   * Sets the logger for trace and warn messages.
   *
   * <p>Default: {@link Loggers#noop}
   *
   * @see #getLogger
   */
  public EvaluatorBuilder setLogger(Logger logger) {
    this.logger = logger;
    return this;
  }

  /**
   * Returns the logger for trace and warn messages.
   *
   * <p>For more information, see {@link #setLogger}.
   */
  public Logger getLogger() {
    return logger;
  }

  /**
   * Adds a {@code ModuleKeyFactory} object that handles module imports.
   *
   * <p>For more information, see {@link #setModuleKeyFactories}.
   *
   * @see #addModuleKeyFactories
   * @see #getModuleKeyFactories
   */
  public EvaluatorBuilder addModuleKeyFactory(ModuleKeyFactory factory) {
    moduleKeyFactories.add(factory);
    return this;
  }

  /**
   * Adds {@code ModuleKeyFactory} objects that handle module imports.
   *
   * <p>For more information, see {@link #setModuleKeyFactories}.
   *
   * @see #addModuleKeyFactory
   * @see #getModuleKeyFactories
   */
  public EvaluatorBuilder addModuleKeyFactories(Collection<ModuleKeyFactory> factories) {
    moduleKeyFactories.addAll(factories);
    return this;
  }

  /**
   * Sets the {@code ModuleKeyFactory} objects that handle module imports.
   *
   * <p>Pkl code can import a module with {@code import "moduleUri"}, {@code amends "moduleUri},
   * {@code extends "moduleUri"}, or an {@code import("moduleUri")} expression. The import is
   * handled by the first element in {@code factories} (according to iteration order) that can
   * handle {@code moduleUri}.
   *
   * <p>Default when {@link #unconfigured}: empty list
   *
   * <p>Default when {@link #preconfigured}:
   *
   * <ul>
   *   <li>{@link ModuleKeyFactories#standardLibrary}
   *   <li>{@link ModuleKeyFactories#classPath}
   *   <li>{@link ModuleKeyFactories#fromServiceProviders}
   *   <li>{@link ModuleKeyFactories#file}
   *   <li>{@link ModuleKeyFactories#pkg}
   *   <li>{@link ModuleKeyFactories#projectpackage}
   *   <li>{@link ModuleKeyFactories#genericUrl}
   * </ul>
   *
   * @see #addModuleKeyFactory
   * @see #addModuleKeyFactories
   * @see #getModuleKeyFactories
   */
  public EvaluatorBuilder setModuleKeyFactories(Collection<ModuleKeyFactory> factories) {
    moduleKeyFactories.clear();
    return addModuleKeyFactories(factories);
  }

  /**
   * Returns the {@code ModuleKeyFactory} objects that handle module imports.
   *
   * <p>For more information, see {@link #setModuleKeyFactories}.
   *
   * @see #addModuleKeyFactory
   * @see #addModuleKeyFactories
   */
  public List<ModuleKeyFactory> getModuleKeyFactories() {
    return moduleKeyFactories;
  }

  /**
   * Adds a {@code ResourceReader} object that handles resource reads.
   *
   * <p>For more information, see {@link #setResourceReaders}.
   *
   * @see #addResourceReaders
   * @see #getResourceReaders
   */
  public EvaluatorBuilder addResourceReader(ResourceReader reader) {
    resourceReaders.add(reader);
    return this;
  }

  /**
   * Adds {@code ResourceReader} objects that handle resource reads.
   *
   * <p>For more information, see {@link #setResourceReaders}.
   *
   * @see #addResourceReader
   * @see #getResourceReaders
   */
  public EvaluatorBuilder addResourceReaders(Collection<ResourceReader> readers) {
    resourceReaders.addAll(readers);
    return this;
  }

  /**
   * Sets the {@code ResourceReader} objects that handle resource reads.
   *
   * <p>Pkl code can read a resource with {@code read(resourceUri)}. The read is handled by the
   * {@code ResourceReader} registered for {@code resourceUri}'s scheme. The iteration order of
   * {@code readers} is irrelevant.
   *
   * <p>Default when {@link #unconfigured}: empty list
   *
   * <p>Default when {@link #preconfigured}:
   *
   * <ul>
   *   <li>{@link ResourceReaders#environmentVariable}
   *   <li>{@link ResourceReaders#externalProperty}
   *   <li>{@link ResourceReaders#file}
   *   <li>{@link ResourceReaders#http}
   *   <li>{@link ResourceReaders#https}
   *   <li>{@link ResourceReaders#pkg}
   *   <li>{@link ResourceReaders#projectpackage}
   * </ul>
   *
   * @see #addResourceReader
   * @see #addResourceReaders
   * @see #getResourceReaders
   */
  public EvaluatorBuilder setResourceReaders(Collection<ResourceReader> readers) {
    resourceReaders.clear();
    return addResourceReaders(readers);
  }

  /**
   * Returns the {@code ResourceReader} objects that handle resource reads.
   *
   * <p>For more information, see {@link #setResourceReaders}.
   *
   * @see #addResourceReader
   * @see #addResourceReaders
   */
  public List<ResourceReader> getResourceReaders() {
    return resourceReaders;
  }

  /**
   * Adds an environment variable available to Pkl code.
   *
   * <p>Overrides any equally named environment variable that has been added previously.
   *
   * <p>For more information, see {@link #setResourceReaders}.
   *
   * @see #addEnvironmentVariables
   * @see #getEnvironmentVariables
   */
  public EvaluatorBuilder addEnvironmentVariable(String name, String value) {
    environmentVariables.put(name, value);
    return this;
  }

  /**
   * Adds environment variables available to Pkl code.
   *
   * <p>Overrides any equally named environment variables that have been added previously.
   *
   * <p>For more information, see {@link #setResourceReaders}.
   *
   * @see #addEnvironmentVariable
   * @see #getEnvironmentVariables
   */
  public EvaluatorBuilder addEnvironmentVariables(Map<String, String> envVars) {
    environmentVariables.putAll(envVars);
    return this;
  }

  /**
   * Sets the environment variables available to Pkl code.
   *
   * <p>Pkl code can read environment variables with {@code read("env:<NAME>")}.
   *
   * <p>Default when {@link #unconfigured}: empty map
   *
   * <p>Default when {@link #preconfigured}: {@code System.getenv()}
   *
   * @see #addEnvironmentVariable
   * @see #addEnvironmentVariables
   * @see #getEnvironmentVariables
   */
  public EvaluatorBuilder setEnvironmentVariables(Map<String, String> envVars) {
    environmentVariables.clear();
    return addEnvironmentVariables(envVars);
  }

  /**
   * Returns the environment variables available to Pkl code.
   *
   * <p>For more information, see {@link #setEnvironmentVariables}.
   *
   * @see #addEnvironmentVariable
   * @see #addEnvironmentVariables
   */
  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  /**
   * Adds an external property available to Pkl code.
   *
   * <p>Overrides any equally named external property that has been added previously.
   *
   * <p>For more information, see {@link #setExternalProperties}.
   *
   * @see #addExternalProperties
   * @see #getExternalProperties
   */
  public EvaluatorBuilder addExternalProperty(String name, String value) {
    externalProperties.put(name, value);
    return this;
  }

  /**
   * Adds external properties available to Pkl code.
   *
   * <p>Overrides any equally named external properties that have been added previously.
   *
   * <p>For more information, see {@link #setExternalProperties}.
   *
   * @see #addExternalProperty
   * @see #getExternalProperties
   */
  public EvaluatorBuilder addExternalProperties(Map<String, String> properties) {
    externalProperties.putAll(properties);
    return this;
  }

  /**
   * Sets the external properties available to Pkl code.
   *
   * <p>Pkl code can read external properties with {@code read("prop:<name>")}.
   *
   * <p>Default when {@link #unconfigured}: empty map
   *
   * <p>Default when {@link #preconfigured}: {@code System.getProperties()}
   *
   * @see #addExternalProperty
   * @see #addExternalProperties
   * @see #getExternalProperties
   */
  public EvaluatorBuilder setExternalProperties(Map<String, String> properties) {
    externalProperties.clear();
    return addExternalProperties(properties);
  }

  /**
   * Returns the external properties available to Pkl code.
   *
   * <p>For more information, see {@link #setExternalProperties}.
   *
   * @see #addExternalProperty
   * @see #addExternalProperties
   */
  public Map<String, String> getExternalProperties() {
    return externalProperties;
  }

  /**
   * Sets the duration after which evaluation of a source module is timed out.
   *
   * <p>If {@code null}, no timeout is enforced.
   *
   * <p>Default: {@code null}
   *
   * @see #getTimeout
   */
  public EvaluatorBuilder setTimeout(@Nullable java.time.Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  /**
   * Returns the duration after which evaluation of a source module is timed out.
   *
   * <p>For more information, see {@link #setTimeout}.
   */
  public java.time.@Nullable Duration getTimeout() {
    return timeout;
  }

  /**
   * Sets the directory where {@code package:} modules are cached.
   *
   * <p>If {@code null}, {@code package:} modules are not cached.
   *
   * <p>Default: {@link #getDefaultModuleCacheDir}
   *
   * @see #getDefaultModuleCacheDir
   */
  public EvaluatorBuilder setModuleCacheDir(@Nullable Path moduleCacheDir) {
    this.moduleCacheDir = moduleCacheDir;
    return this;
  }

  /**
   * Returns the directory where {@code package:} modules are cached.
   *
   * <p>For more information, see {@link #setModuleCacheDir}.
   */
  public @Nullable Path getModuleCacheDir() {
    return moduleCacheDir;
  }

  /**
   * Returns the default directory where {@code package:} modules are cached, namely {@code
   * ~/.pkl/cache}.
   */
  public static Path getDefaultModuleCacheDir() {
    return IoUtils.getDefaultModuleCacheDir();
  }

  /**
   * Sets the desired output format.
   *
   * <p>The output formats supported by default are defined in {@link OutputFormat}. Modules may
   * override their {@code output.renderer} property to support other formats or to always render
   * the same format. In particular, most templates always render the same format, ignoring this
   * option.
   *
   * <p>Default: {@code null}
   *
   * @see #getOutputFormat
   * @see #setOutputFormat(OutputFormat)
   */
  public EvaluatorBuilder setOutputFormat(@Nullable String outputFormat) {
    this.outputFormat = outputFormat;
    return this;
  }

  /**
   * Sets the desired output format.
   *
   * <p>For more information, see {@link #setOutputFormat(String)}.
   *
   * @see #getOutputFormat
   */
  public EvaluatorBuilder setOutputFormat(@Nullable OutputFormat outputFormat) {
    this.outputFormat = outputFormat == null ? null : outputFormat.toString();
    return this;
  }

  /**
   * Returns the desired output format.
   *
   * <p>For more information, see {@link #setOutputFormat(String)}.
   *
   * @see #setOutputFormat(OutputFormat)
   */
  public @Nullable String getOutputFormat() {
    return outputFormat;
  }

  /**
   * Sets the project dependencies available to Pkl code.
   *
   * <p>Default: {@code null}
   *
   * @see #getProjectDependencies
   */
  public EvaluatorBuilder setProjectDependencies(@Nullable DeclaredDependencies dependencies) {
    this.dependencies = dependencies;
    return this;
  }

  /**
   * Returns the project dependencies available to Pkl code.
   *
   * <p>For more information, see {@link #setProjectDependencies}.
   */
  public @Nullable DeclaredDependencies getProjectDependencies() {
    return dependencies;
  }

  /**
   * Applies a project's dependencies and evaluator settings.
   *
   * <p>Project dependencies are applied by calling {@link #setProjectDependencies}. Evaluator
   * settings are applied by calling the following builder methods:
   *
   * <ul>
   *   <li>{@link #setExternalProperties}
   *   <li>{@link #setEnvironmentVariables}
   *   <li>{@link #setTimeout}
   *   <li>{@link #setModuleCacheDir}
   *   <li>{@link #addResourceReader} (to access the project's {@linkplain
   *       EvaluatorSettings#getModulePath module path})
   *   <li>{@link #addModuleKeyFactory} (to access the project's {@linkplain
   *       EvaluatorSettings#getModulePath module path})
   *   <li>{@link #setAllowedModules}
   *   <li>{@link #setAllowedResources}
   *   <li>{@link #setRootDir}
   * </ul>
   *
   * <p>The above methods are only called if the project defines a corresponding evaluator setting.
   *
   * <p>To apply project dependencies but not evaluator settings, use {@code
   * setProjectDependencies(project.getDependencies())}.
   *
   * @throws IllegalStateException if a {@linkplain #setSecurityManager custom security manager} has
   *     been set and the given project defines {@link EvaluatorSettings#getAllowedModules
   *     allowedModules}, {@link EvaluatorSettings#getAllowedResources allowedResources}, or {@link
   *     EvaluatorSettings#getRootDir rootDir}
   */
  public EvaluatorBuilder applyFromProject(Project project) {
    this.dependencies = project.getDependencies();
    var settings = project.getSettings();
    requireNullSecurityManager("applyFromProject");
    if (settings.getAllowedModules() != null) {
      setAllowedModules(settings.getAllowedModules());
    }
    if (settings.getAllowedResources() != null) {
      setAllowedResources(settings.getAllowedResources());
    }
    if (settings.getExternalProperties() != null) {
      setExternalProperties(settings.getExternalProperties());
    }
    if (settings.getEnv() != null) {
      setEnvironmentVariables(settings.getEnv());
    }
    if (settings.getTimeout() != null) {
      setTimeout(settings.getTimeout().toJavaDuration());
    }
    if (settings.getModulePath() != null) {
      // indirectly closed by `ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)`
      var modulePathResolver = new ModulePathResolver(settings.getModulePath());
      addResourceReader(ResourceReaders.modulePath(modulePathResolver));
      addModuleKeyFactory(ModuleKeyFactories.modulePath(modulePathResolver));
    }
    if (settings.getRootDir() != null) {
      setRootDir(settings.getRootDir());
    }
    if (Boolean.TRUE.equals(settings.isNoCache())) {
      setModuleCacheDir(null);
    } else if (settings.getModuleCacheDir() != null) {
      setModuleCacheDir(settings.getModuleCacheDir());
    }
    return this;
  }

  /**
   * Builds a new evaluator from this builder's current state. Subsequent changes to the builder's
   * state will not affect previously returned evaluators.
   */
  public Evaluator build() {
    var securityManager =
        this.securityManager != null ? this.securityManager : securityManagerBuilder.build();

    return new EvaluatorImpl(
        stackFrameTransformer,
        securityManager,
        new LoggerImpl(logger, stackFrameTransformer),
        // copy to shield against subsequent modification through builder
        List.copyOf(moduleKeyFactories),
        List.copyOf(resourceReaders),
        Map.copyOf(environmentVariables),
        Map.copyOf(externalProperties),
        timeout,
        moduleCacheDir,
        dependencies,
        outputFormat);
  }

  private void requireNullSecurityManager(String methodName) {
    if (securityManager != null) {
      throw new IllegalStateException(
          "Method `"
              + methodName
              + "` cannot be used "
              + "if a custom security manager has been set with `setSecurityManager`.");
    }
  }
}
