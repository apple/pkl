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
package org.pkl.executor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.pkl.executor.spi.v1.ExecutorSpiOptions;
import org.pkl.executor.spi.v1.ExecutorSpiOptions2;

/**
 * Options for {@link Executor#evaluatePath}.
 *
 * <p>To create {@code ExecutorOptions}, use its {@linkplain #builder builder}.
 */
public final class ExecutorOptions {
  private final List<String> allowedModules;

  private final List<String> allowedResources;

  private final Map<String, String> environmentVariables;

  private final Map<String, String> externalProperties;

  private final List<Path> modulePath;

  private final /* @Nullable */ Path rootDir;

  private final /* @Nullable */ Duration timeout;

  private final /* @Nullable */ String outputFormat;

  private final /* @Nullable */ Path moduleCacheDir;

  private final /* @Nullable */ Path projectDir;

  private final List<Path> certificatePaths;

  private final int testPort; // -1 means disabled

  private final int spiOptionsVersion; // -1 means use latest

  /** Returns the module cache dir that the CLI uses by default. */
  public static Path defaultModuleCacheDir() {
    return Path.of(System.getProperty("user.home"), ".pkl", "cache");
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder of {@link ExecutorOptions}.
   *
   * <p>It is safe to create multiple options objects with the same builder.
   */
  public static final class Builder {
    private List<String> allowedModules = List.of();
    private List<String> allowedResources = List.of();
    private Map<String, String> environmentVariables = Map.of();
    private Map<String, String> externalProperties = Map.of();
    private List<Path> modulePath = List.of();
    private /* @Nullable */ Path rootDir;
    private /* @Nullable */ Duration timeout;
    private /* @Nullable */ String outputFormat;
    private /* @Nullable */ Path moduleCacheDir;
    private /* @Nullable */ Path projectDir;
    private List<Path> certificatePaths = List.of();
    private int testPort = -1; // -1 means disabled
    private int spiOptionsVersion = -1; // -1 means use latest

    private Builder() {}

    /** API equivalent of the {@code --allowed-modules} CLI option. */
    public Builder allowedModules(List<String> allowedModules) {
      this.allowedModules = allowedModules;
      return this;
    }

    /** API equivalent of the {@code --allowed-modules} CLI option. */
    public Builder allowedModules(String... allowedModules) {
      this.allowedModules = List.of(allowedModules);
      return this;
    }

    /** API equivalent of the {@code --allowed-resources} CLI option. */
    public Builder allowedResources(List<String> allowedResources) {
      this.allowedResources = allowedResources;
      return this;
    }

    /** API equivalent of the {@code --allowed-resources} CLI option. */
    public Builder allowedResources(String... allowedResources) {
      this.allowedResources = List.of(allowedResources);
      return this;
    }

    /** API equivalent of the repeatable {@code --env-var} CLI option. */
    public Builder environmentVariables(Map<String, String> environmentVariables) {
      this.environmentVariables = environmentVariables;
      return this;
    }

    /** API equivalent of the repeatable {@code --property} CLI option. */
    public Builder externalProperties(Map<String, String> externalProperties) {
      this.externalProperties = externalProperties;
      return this;
    }

    /** API equivalent of the {@code --module-path} CLI option. */
    public Builder modulePath(List<Path> modulePath) {
      this.modulePath = modulePath;
      return this;
    }

    /** API equivalent of the {@code --module-path} CLI option. */
    public Builder modulePath(Path... modulePath) {
      this.modulePath = List.of(modulePath);
      return this;
    }

    /** API equivalent of the {@code --root-dir} CLI option. */
    public Builder rootDir(/*Nullable*/ Path rootDir) {
      this.rootDir = rootDir;
      return this;
    }

    /** API equivalent of the {@code --timeout} CLI option. */
    public Builder timeout(/*Nullable*/ Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /** API equivalent of the {@code --format} CLI option. */
    public Builder outputFormat(/*Nullable*/ String outputFormat) {
      this.outputFormat = outputFormat;
      return this;
    }

    /**
     * API equivalent of the {@code --cache-dir} CLI option. Passing {@code null} is equivalent to
     * {@code --no-cache}.
     */
    public Builder moduleCacheDir(/*Nullable*/ Path moduleCacheDir) {
      this.moduleCacheDir = moduleCacheDir;
      return this;
    }

    /**
     * API equivalent of the {@code --project-dir} CLI option.
     *
     * <p>Unlike the CLI, this option only sets project dependencies. It does not set evaluator
     * settings.
     */
    public Builder projectDir(/*Nullable*/ Path projectDir) {
      this.projectDir = projectDir;
      return this;
    }

    /** API equivalent of the {@code --ca-certificates} CLI option. */
    public Builder certificatePaths(List<Path> certificateFiles) {
      this.certificatePaths = certificateFiles;
      return this;
    }

    /** API equivalent of the {@code --ca-certificates} CLI option. */
    public Builder certificatePaths(Path... certificateFiles) {
      this.certificatePaths = List.of(certificateFiles);
      return this;
    }

    /** Internal test option. -1 means disabled. */
    Builder testPort(int testPort) {
      this.testPort = testPort;
      return this;
    }

    /** Internal test option. -1 means use latest. */
    Builder spiOptionsVersion(int version) {
      this.spiOptionsVersion = version;
      return this;
    }

    public ExecutorOptions build() {
      return new ExecutorOptions(
          allowedModules,
          allowedResources,
          environmentVariables,
          externalProperties,
          modulePath,
          rootDir,
          timeout,
          outputFormat,
          moduleCacheDir,
          projectDir,
          certificatePaths,
          testPort,
          spiOptionsVersion);
    }
  }

  /**
   * Constructs an options object.
   *
   * @deprecated use {@link #builder} instead
   * @param allowedModules API equivalent of the {@code --allowed-modules} CLI option
   * @param allowedResources API equivalent of the {@code --allowed-resources} CLI option
   * @param environmentVariables API equivalent of the repeatable {@code --env-var} CLI option
   * @param externalProperties API equivalent of the repeatable {@code --property} CLI option
   * @param modulePath API equivalent of the {@code --module-path} CLI option
   * @param rootDir API equivalent of the {@code --root-dir} CLI option
   * @param timeout API equivalent of the {@code --timeout} CLI option
   * @param outputFormat API equivalent of the {@code --format} CLI option
   * @param moduleCacheDir API equivalent of the {@code --cache-dir} CLI option. Passing {@link
   *     #defaultModuleCacheDir()} is equivalent to omitting {@code --cache-dir}. Passing {@code
   *     null} is equivalent to {@code --no-cache}.
   * @param projectDir API equivalent of the {@code --project-dir} CLI option.
   */
  @Deprecated(forRemoval = true)
  public ExecutorOptions(
      List<String> allowedModules,
      List<String> allowedResources,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      List<Path> modulePath,
      /* @Nullable */ Path rootDir,
      /* @Nullable */ Duration timeout,
      /* @Nullable */ String outputFormat,
      /* @Nullable */ Path moduleCacheDir,
      /* @Nullable */ Path projectDir) {

    this(
        allowedModules,
        allowedResources,
        environmentVariables,
        externalProperties,
        modulePath,
        rootDir,
        timeout,
        outputFormat,
        moduleCacheDir,
        projectDir,
        List.of(),
        -1,
        -1);
  }

  private ExecutorOptions(
      List<String> allowedModules,
      List<String> allowedResources,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      List<Path> modulePath,
      /* @Nullable */ Path rootDir,
      /* @Nullable */ Duration timeout,
      /* @Nullable */ String outputFormat,
      /* @Nullable */ Path moduleCacheDir,
      /* @Nullable */ Path projectDir,
      List<Path> certificatePaths,
      int testPort,
      int spiOptionsVersion) {

    this.allowedModules = List.copyOf(allowedModules);
    this.allowedResources = List.copyOf(allowedResources);
    this.environmentVariables = Map.copyOf(environmentVariables);
    this.externalProperties = Map.copyOf(externalProperties);
    this.modulePath = modulePath;
    this.rootDir = rootDir;
    this.timeout = timeout;
    this.outputFormat = outputFormat;
    this.moduleCacheDir = moduleCacheDir;
    this.projectDir = projectDir;
    this.certificatePaths = List.copyOf(certificatePaths);
    this.testPort = testPort;
    this.spiOptionsVersion = spiOptionsVersion;
  }

  /** API equivalent of the {@code --allowed-modules} CLI option. */
  public List<String> getAllowedModules() {
    return allowedModules;
  }

  /** API equivalent of the {@code --allowed-resources} CLI option. */
  public List<String> getAllowedResources() {
    return allowedResources;
  }

  /** API equivalent of the repeatable {@code --env-var} CLI option. */
  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  /** API equivalent of the repeatable {@code --property} CLI option. */
  public Map<String, String> getExternalProperties() {
    return externalProperties;
  }

  /** API equivalent of the {@code --module-path} CLI option. */
  public List<Path> getModulePath() {
    return modulePath;
  }

  /** API equivalent of the {@code --root-dir} CLI option. */
  public /* @Nullable */ Path getRootDir() {
    return rootDir;
  }

  /** API equivalent of the {@code --timeout} CLI option. */
  public Duration getTimeout() {
    return timeout;
  }

  /** API equivalent of the {@code --format} CLI option. */
  public /* @Nullable */ String getOutputFormat() {
    return outputFormat;
  }

  /**
   * API equivalent of the {@code --cache-dir} CLI option. {@code null} is equivalent to {@code
   * --no-cache}.
   */
  public /* @Nullable */ Path getModuleCacheDir() {
    return moduleCacheDir;
  }

  /**
   * API equivalent of the {@code --project-dir} CLI option.
   *
   * <p>Unlike the CLI, this option only sets project dependencies. It does not set evaluator
   * settings.
   */
  public /* @Nullable */ Path getProjectDir() {
    return projectDir;
  }

  /** API equivalent of the {@code --ca-certificates} CLI option. */
  public List<Path> getCertificatePaths() {
    return certificatePaths;
  }

  @Override
  public boolean equals(/* @Nullable */ Object obj) {
    if (this == obj) return true;
    if (obj.getClass() != ExecutorOptions.class) return false;

    var other = (ExecutorOptions) obj;
    return allowedModules.equals(other.allowedModules)
        && allowedResources.equals(other.allowedResources)
        && environmentVariables.equals(other.environmentVariables)
        && externalProperties.equals(other.externalProperties)
        && modulePath.equals(other.modulePath)
        && Objects.equals(rootDir, other.rootDir)
        && Objects.equals(timeout, other.timeout)
        && Objects.equals(outputFormat, other.outputFormat)
        && Objects.equals(moduleCacheDir, other.moduleCacheDir)
        && Objects.equals(projectDir, other.projectDir)
        && Objects.equals(certificatePaths, other.certificatePaths)
        && testPort == other.testPort
        && spiOptionsVersion == other.spiOptionsVersion;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        allowedModules,
        allowedResources,
        environmentVariables,
        externalProperties,
        modulePath,
        rootDir,
        timeout,
        outputFormat,
        moduleCacheDir,
        projectDir,
        certificatePaths,
        testPort,
        spiOptionsVersion);
  }

  @Override
  public String toString() {
    return "ExecutorOptions{"
        + "allowedModules="
        + allowedModules
        + ", allowedResources="
        + allowedResources
        + ", environmentVariables="
        + environmentVariables
        + ", externalProperties="
        + externalProperties
        + ", modulePath="
        + modulePath
        + ", rootDir="
        + rootDir
        + ", timeout="
        + timeout
        + ", outputFormat="
        + outputFormat
        + ", cacheDir="
        + moduleCacheDir
        + ", projectDir="
        + projectDir
        + ", certificatePaths="
        + certificatePaths
        + ", testPort="
        + testPort
        + ", spiOptionsVersion="
        + spiOptionsVersion
        + '}';
  }

  ExecutorSpiOptions toSpiOptions() {
    return switch (spiOptionsVersion) {
      case -1, 2 ->
          new ExecutorSpiOptions2(
              allowedModules,
              allowedResources,
              environmentVariables,
              externalProperties,
              modulePath,
              rootDir,
              timeout,
              outputFormat,
              moduleCacheDir,
              projectDir,
              certificatePaths,
              testPort);
      case 1 -> // for testing only
          new ExecutorSpiOptions(
              allowedModules,
              allowedResources,
              environmentVariables,
              externalProperties,
              modulePath,
              rootDir,
              timeout,
              outputFormat,
              moduleCacheDir,
              projectDir);
      default ->
          throw new AssertionError("Unknown ExecutorSpiOptions version: " + spiOptionsVersion);
    };
  }
}
