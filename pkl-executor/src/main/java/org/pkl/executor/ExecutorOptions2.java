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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.pkl.executor.spi.v1.ExecutorSpiOptions2;

/**
 * Options for {@link Executor#evaluatePath}.
 *
 * <p>This class offers additional options not available in {@code ExecutorOptions}.
 */
public class ExecutorOptions2 extends ExecutorOptions {
  protected final List<Path> certificateFiles;
  protected final List<URI> certificateUris;

  /**
   * Constructs an options object.
   *
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
   * @param certificateFiles API equivalent of the {@code --ca-certificates} CLI option
   * @param certificateUris API equivalent of the {@code --ca-certificates} CLI option
   */
  public ExecutorOptions2(
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
      List<Path> certificateFiles,
      List<URI> certificateUris) {

    super(
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
    this.certificateFiles = certificateFiles;
    this.certificateUris = certificateUris;
  }

  /** API equivalent of the {@code --ca-certificates} CLI option. */
  public List<Path> getCertificateFiles() {
    return certificateFiles;
  }

  /** API equivalent of the {@code --ca-certificates} CLI option. */
  public List<URI> getCertificateUris() {
    return certificateUris;
  }

  @Override
  public boolean equals(/* @Nullable */ Object obj) {
    if (this == obj) return true;
    if (obj.getClass() != ExecutorOptions2.class) return false;

    var other = (ExecutorOptions2) obj;
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
        && Objects.equals(certificateFiles, other.certificateFiles)
        && Objects.equals(certificateUris, other.certificateUris);
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
        certificateFiles,
        certificateUris);
  }

  @Override
  public String toString() {
    return "ExecutorOptions2{"
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
        + ", certificateFiles="
        + certificateFiles
        + ", certificateUris="
        + certificateUris
        + '}';
  }

  ExecutorSpiOptions2 toSpiOptions() {
    return new ExecutorSpiOptions2(
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
        certificateFiles,
        certificateUris);
  }
}
