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
package org.pkl.core;

import com.oracle.truffle.api.TruffleOptions;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.pkl.core.SecurityManagers.StandardBuilder;
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader;
import org.pkl.core.externalreader.ExternalReaderProcess;
import org.pkl.core.http.HttpClient;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModuleKeyFactory;
import org.pkl.core.module.ModulePathResolver;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.project.Project;
import org.pkl.core.resource.ResourceReader;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.core.runtime.LoggerImpl;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

/** A builder for an {@link Evaluator}. Can be reused to build multiple evaluators. */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class EvaluatorBuilder {
  private final StandardBuilder securityManagerBuilder = SecurityManagers.standardBuilder();

  private @Nullable SecurityManager securityManager;

  // Default to a client with a fixed set of built-in certificates.
  // Make it lazy to avoid creating a client unnecessarily.
  private HttpClient httpClient = HttpClient.builder().buildLazily();

  private Logger logger = Loggers.noop();

  private final List<ModuleKeyFactory> moduleKeyFactories = new ArrayList<>();

  private final List<ResourceReader> resourceReaders = new ArrayList<>();

  private final Map<String, String> environmentVariables = new HashMap<>();

  private final Map<String, String> externalProperties = new HashMap<>();

  private @Nullable java.time.Duration timeout;

  private @Nullable Path moduleCacheDir = IoUtils.getDefaultModuleCacheDir();

  private @Nullable String outputFormat;

  private boolean color = false;

  private @Nullable StackFrameTransformer stackFrameTransformer;

  private @Nullable DeclaredDependencies dependencies;

  private EvaluatorBuilder() {}

  /**
   * Creates a builder preconfigured with:
   *
   * <ul>
   *   <li>{@link SecurityManagers#defaultAllowedModules}
   *   <li>{@link SecurityManagers#defaultAllowedResources}
   *   <li>{@link Loggers#noop()}
   *   <li>{@link ModuleKeyFactories#standardLibrary}
   *   <li>{@link ModuleKeyFactories#classPath}
   *   <li>{@link ModuleKeyFactories#fromServiceProviders}
   *   <li>{@link ModuleKeyFactories#file}
   *   <li>{@link ModuleKeyFactories#pkg}
   *   <li>{@link ModuleKeyFactories#projectpackage}
   *   <li>{@link ModuleKeyFactories#genericUrl}
   *   <li>{@link ResourceReaders#environmentVariable}
   *   <li>{@link ResourceReaders#externalProperty}
   *   <li>{@link ResourceReaders#classPath}
   *   <li>{@link ResourceReaders#file}
   *   <li>{@link ResourceReaders#http}
   *   <li>{@link ResourceReaders#https}
   *   <li>{@link ResourceReaders#pkg}
   *   <li>{@link ResourceReaders#projectpackage}
   *   <li>{@link ResourceReaders#fromServiceProviders}
   *   <li>{@link System#getProperties}
   * </ul>
   */
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
        .addResourceReaders(ResourceReaders.fromServiceProviders())
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
        .addModuleKeyFactory(ModuleKeyFactories.http)
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
   * Creates a builder that is unconfigured. At a minimum, a security manager will need to be set
   * before building an instance.
   */
  public static EvaluatorBuilder unconfigured() {
    return new EvaluatorBuilder();
  }

  /** Sets the option to render errors in ANSI color. */
  public EvaluatorBuilder setColor(boolean color) {
    this.color = color;
    return this;
  }

  /** Returns the current setting of the option to render errors in ANSI color. */
  public boolean getColor() {
    return color;
  }

  /** Sets the given stack frame transformer, replacing any previously set transformer. */
  public EvaluatorBuilder setStackFrameTransformer(StackFrameTransformer stackFrameTransformer) {
    this.stackFrameTransformer = stackFrameTransformer;
    return this;
  }

  /** Returns the currently set stack frame transformer. */
  public @Nullable StackFrameTransformer getStackFrameTransformer() {
    return stackFrameTransformer;
  }

  /** Sets the given security manager, replacing any previously set security manager. */
  public EvaluatorBuilder setSecurityManager(@Nullable SecurityManager manager) {
    this.securityManager = manager;
    return this;
  }

  public EvaluatorBuilder unsetSecurityManager() {
    this.securityManager = null;
    return this;
  }

  /** Returns the currently set security manager. */
  public @Nullable SecurityManager getSecurityManager() {
    return securityManager;
  }

  /**
   * Sets the set of URI patterns to be allowed when importing modules.
   *
   * @throws IllegalStateException if {@link #setSecurityManager(SecurityManager)} was also called.
   */
  public EvaluatorBuilder setAllowedModules(Collection<Pattern> patterns) {
    if (securityManager != null) {
      throw new IllegalStateException(
          "Cannot call both `setSecurityManager` and `setAllowedModules`, because both define security manager settings.");
    }
    securityManagerBuilder.setAllowedModules(patterns);
    return this;
  }

  /** Returns the set of patterns to be allowed when importing modules. */
  public List<Pattern> getAllowedModules() {
    return securityManagerBuilder.getAllowedModules();
  }

  /**
   * Sets the set of URI patterns to be allowed when reading resources.
   *
   * @throws IllegalStateException if {@link #setSecurityManager(SecurityManager)} was also called.
   */
  public EvaluatorBuilder setAllowedResources(Collection<Pattern> patterns) {
    if (securityManager != null) {
      throw new IllegalStateException(
          "Cannot call both `setSecurityManager` and `setAllowedResources`, because both define security manager settings.");
    }
    securityManagerBuilder.setAllowedResources(patterns);
    return this;
  }

  /** Returns the set of patterns to be allowed when reading resources. */
  public List<Pattern> getAllowedResources() {
    return securityManagerBuilder.getAllowedResources();
  }

  /**
   * Sets the root directory, which restricts access to file-based modules and resources located
   * under this directory.
   */
  public EvaluatorBuilder setRootDir(@Nullable Path rootDir) {
    securityManagerBuilder.setRootDir(rootDir);
    return this;
  }

  /** Returns the currently set root directory, if set. */
  public @Nullable Path getRootDir() {
    return securityManagerBuilder.getRootDir();
  }

  /** Sets the given logger, replacing any previously set logger. */
  public EvaluatorBuilder setLogger(Logger logger) {
    this.logger = logger;
    return this;
  }

  /** Returns the currently set logger. */
  public Logger getLogger() {
    return logger;
  }

  /**
   * Sets the HTTP client to be used.
   *
   * <p>Defaults to {@code HttpClient.builder().buildLazily()}.
   */
  public EvaluatorBuilder setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
    return this;
  }

  /** Returns the currently set HTTP client. */
  public HttpClient getHttpClient() {
    return httpClient;
  }

  /**
   * Adds the given module key factory. Factories will be asked to resolve module keys in the order
   * they have been added to this builder.
   */
  public EvaluatorBuilder addModuleKeyFactory(ModuleKeyFactory factory) {
    moduleKeyFactories.add(factory);
    return this;
  }

  /**
   * Adds the given module key factories. Factories will be asked to resolve module keys in the
   * order they have been added to this builder.
   */
  public EvaluatorBuilder addModuleKeyFactories(Collection<ModuleKeyFactory> factories) {
    moduleKeyFactories.addAll(factories);
    return this;
  }

  /** Removes any existing module key factories, then adds the given factories. */
  public EvaluatorBuilder setModuleKeyFactories(Collection<ModuleKeyFactory> factories) {
    moduleKeyFactories.clear();
    return addModuleKeyFactories(factories);
  }

  /** Returns the currently set module key factories. */
  public List<ModuleKeyFactory> getModuleKeyFactories() {
    return moduleKeyFactories;
  }

  public EvaluatorBuilder addResourceReader(ResourceReader reader) {
    resourceReaders.add(reader);
    return this;
  }

  public EvaluatorBuilder addResourceReaders(Collection<ResourceReader> readers) {
    resourceReaders.addAll(readers);
    return this;
  }

  public EvaluatorBuilder setResourceReaders(Collection<ResourceReader> readers) {
    resourceReaders.clear();
    return addResourceReaders(readers);
  }

  /** Returns the currently set resource readers. */
  public List<ResourceReader> getResourceReaders() {
    return resourceReaders;
  }

  /**
   * Adds the given environment variable, overriding any environment variable previously added under
   * the same name.
   *
   * <p>Pkl code can read environment variables with {@code read("env:<NAME>")}.
   */
  public EvaluatorBuilder addEnvironmentVariable(String name, String value) {
    environmentVariables.put(name, value);
    return this;
  }

  /**
   * Adds the given environment variables, overriding any environment variables previously added
   * under the same name.
   *
   * <p>Pkl code can read environment variables with {@code read("env:<NAME>")}.
   */
  public EvaluatorBuilder addEnvironmentVariables(Map<String, String> envVars) {
    environmentVariables.putAll(envVars);
    return this;
  }

  /** Removes any existing environment variables, then adds the given environment variables. */
  public EvaluatorBuilder setEnvironmentVariables(Map<String, String> envVars) {
    environmentVariables.clear();
    return addEnvironmentVariables(envVars);
  }

  /** Returns the currently set environment variables. */
  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  /**
   * Adds the given external property, overriding any property previously set under the same name.
   *
   * <p>Pkl code can read external properties with {@code read("prop:<name>")}.
   */
  public EvaluatorBuilder addExternalProperty(String name, String value) {
    externalProperties.put(name, value);
    return this;
  }

  /**
   * Adds the given external properties, overriding any properties previously set under the same
   * name.
   *
   * <p>Pkl code can read external properties with {@code read("prop:<name>")}.
   */
  public EvaluatorBuilder addExternalProperties(Map<String, String> properties) {
    externalProperties.putAll(properties);
    return this;
  }

  /** Removes any existing external properties, then adds the given properties. */
  public EvaluatorBuilder setExternalProperties(Map<String, String> properties) {
    externalProperties.clear();
    return addExternalProperties(properties);
  }

  /** Returns the currently set external properties. */
  public Map<String, String> getExternalProperties() {
    return externalProperties;
  }

  /**
   * Sets an evaluation timeout to be enforced by the {@link Evaluator}'s {@code evaluate} methods.
   */
  public EvaluatorBuilder setTimeout(@Nullable java.time.Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  /** Returns the currently set evaluation timeout. */
  public java.time.@Nullable Duration getTimeout() {
    return timeout;
  }

  /**
   * Sets the directory where `package:` modules are cached.
   *
   * <p>If {@code null}, the module cache is disabled.
   */
  public EvaluatorBuilder setModuleCacheDir(@Nullable Path moduleCacheDir) {
    this.moduleCacheDir = moduleCacheDir;
    return this;
  }

  /**
   * Returns the directory where `package:` modules are cached. If {@code null}, the module cache is
   * disabled.
   */
  public @Nullable Path getModuleCacheDir() {
    return moduleCacheDir;
  }

  /**
   * Sets the desired output format, if any.
   *
   * <p>By default, modules support the formats described by {@link OutputFormat}. and fall back to
   * {@link OutputFormat#PCF} if no format is specified.
   *
   * <p>Modules that override {@code output.renderer} in their source code may ignore this option or
   * may support formats other than those described by {@link OutputFormat}. In particular, most
   * templates ignore this option and always render the same format.
   */
  public EvaluatorBuilder setOutputFormat(@Nullable String outputFormat) {
    this.outputFormat = outputFormat;
    return this;
  }

  /**
   * Sets the desired output format, if any.
   *
   * <p>By default, modules support the formats described by {@link OutputFormat}. and fall back to
   * {@link OutputFormat#PCF} if no format is specified.
   *
   * <p>Modules that override {@code output.renderer} in their source code may ignore this option or
   * may support formats other than those described by {@link OutputFormat}. In particular, most
   * templates ignore this option and always render the same format.
   */
  public EvaluatorBuilder setOutputFormat(@Nullable OutputFormat outputFormat) {
    this.outputFormat = outputFormat == null ? null : outputFormat.toString();
    return this;
  }

  /** Returns the currently set output format, if any. */
  public @Nullable String getOutputFormat() {
    return outputFormat;
  }

  /** Sets the project dependencies for the evaluator. */
  public EvaluatorBuilder setProjectDependencies(DeclaredDependencies dependencies) {
    this.dependencies = dependencies;
    return this;
  }

  public @Nullable DeclaredDependencies getProjectDependencies() {
    return this.dependencies;
  }

  /**
   * Given a project, sets its dependencies, and also applies any evaluator settings if set.
   *
   * @throws IllegalStateException if {@link #setSecurityManager(SecurityManager)} was also called.
   */
  public EvaluatorBuilder applyFromProject(Project project) {
    this.dependencies = project.getDependencies();
    var settings = project.getEvaluatorSettings();
    if (securityManager != null) {
      throw new IllegalStateException(
          "Cannot call both `setSecurityManager` and `setProject`, because both define security manager settings. Call `setProjectOnly` if the security manager is desired.");
    }
    if (settings.allowedModules() != null) {
      setAllowedModules(settings.allowedModules());
    }
    if (settings.allowedResources() != null) {
      setAllowedResources(settings.allowedResources());
    }
    if (settings.externalProperties() != null) {
      setExternalProperties(settings.externalProperties());
    }
    if (settings.env() != null) {
      setEnvironmentVariables(settings.env());
    }
    if (settings.timeout() != null) {
      setTimeout(settings.timeout().toJavaDuration());
    }
    if (settings.modulePath() != null) {
      // indirectly closed by `ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)`
      var modulePathResolver = new ModulePathResolver(settings.modulePath());
      addResourceReader(ResourceReaders.modulePath(modulePathResolver));
      addModuleKeyFactory(ModuleKeyFactories.modulePath(modulePathResolver));
    }
    if (settings.rootDir() != null) {
      setRootDir(settings.rootDir());
    }
    if (settings.color() != null) {
      setColor(settings.color().hasColor());
    }
    if (Boolean.TRUE.equals(settings.noCache())) {
      setModuleCacheDir(null);
    } else if (settings.moduleCacheDir() != null) {
      setModuleCacheDir(settings.moduleCacheDir());
    }

    // this isn't ideal as project and non-project ExternalReaderProcess instances can be dupes
    var procs = new HashMap<ExternalReader, ExternalReaderProcess>();
    if (settings.externalModuleReaders() != null) {
      for (var entry : settings.externalModuleReaders().entrySet()) {
        addModuleKeyFactory(
            ModuleKeyFactories.externalProcess(
                entry.getKey(),
                procs.computeIfAbsent(entry.getValue(), ExternalReaderProcess::of)));
      }
    }
    if (settings.externalResourceReaders() != null) {
      for (var entry : settings.externalResourceReaders().entrySet()) {
        addResourceReader(
            ResourceReaders.externalProcess(
                entry.getKey(),
                procs.computeIfAbsent(entry.getValue(), ExternalReaderProcess::of)));
      }
    }
    if (settings.http() != null) {
      var httpClientBuilder = HttpClient.builder();
      if (settings.http().proxy() != null) {
        var noProxy = settings.http().proxy().noProxy();
        if (noProxy == null) {
          noProxy = Collections.emptyList();
        }
        httpClientBuilder.setProxy(settings.http().proxy().address(), noProxy);
      }
      if (settings.http().rewrites() != null) {
        httpClientBuilder.setRewrites(settings.http().rewrites());
      }
      setHttpClient(httpClientBuilder.buildLazily());
    }
    return this;
  }

  public Evaluator build() {
    if (securityManager == null) {
      securityManager = securityManagerBuilder.build();
    }

    if (stackFrameTransformer == null) {
      throw new IllegalStateException("No stack frame transformer set.");
    }

    return new EvaluatorImpl(
        stackFrameTransformer,
        color,
        securityManager,
        httpClient,
        new LoggerImpl(logger, stackFrameTransformer),
        // copy to shield against subsequent modification through builder
        new ArrayList<>(moduleKeyFactories),
        new ArrayList<>(resourceReaders),
        new HashMap<>(environmentVariables),
        new HashMap<>(externalProperties),
        timeout,
        moduleCacheDir,
        dependencies,
        outputFormat);
  }
}
