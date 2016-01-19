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
package org.pkl.executor.spi.v1;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ExecutorSpiOptions {
  private final List<String> allowedModules;

  private final List<String> allowedResources;

  private final Map<String, String> environmentVariables;

  private final Map<String, String> externalProperties;

  private final List<Path> modulePath;

  private final Path rootDir;

  private final Duration timeout;

  private final String outputFormat;

  public ExecutorSpiOptions(
      List<String> allowedModules,
      List<String> allowedResources,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      List<Path> modulePath,
      /* @Nullable */ Path rootDir,
      /* @Nullable */ Duration timeout,
      /* @Nullable */ String outputFormat) {

    this.allowedModules = allowedModules;
    this.allowedResources = allowedResources;
    this.environmentVariables = environmentVariables;
    this.externalProperties = externalProperties;
    this.modulePath = modulePath;
    this.rootDir = rootDir;
    this.timeout = timeout;
    this.outputFormat = outputFormat;
  }

  public List<String> getAllowedModules() {
    return allowedModules;
  }

  public List<String> getAllowedResources() {
    return allowedResources;
  }

  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  public Map<String, String> getExternalProperties() {
    return externalProperties;
  }

  public List<Path> getModulePath() {
    return modulePath;
  }

  public /* @Nullable */ Path getRootDir() {
    return rootDir;
  }

  public /* @Nullable */ Duration getTimeout() {
    return timeout;
  }

  public /* @Nullable */ String getOutputFormat() {
    return outputFormat;
  }
}
