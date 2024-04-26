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
package org.pkl.executor.spi.v1;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ExecutorSpiOptions2 extends ExecutorSpiOptions {
  private final List<Path> certificateFiles;

  private final int testPort;

  public ExecutorSpiOptions2(
      List<String> allowedModules,
      List<String> allowedResources,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      List<Path> modulePath,
      Path rootDir,
      Duration timeout,
      String outputFormat,
      Path moduleCacheDir,
      Path projectDir,
      List<Path> certificateFiles,
      int testPort) {
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
    this.testPort = testPort;
  }

  public List<Path> getCertificatePaths() {
    return certificateFiles;
  }

  public int getTestPort() {
    return testPort;
  }
}
