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
package org.pkl.core.service;

import static org.pkl.core.module.ProjectDependenciesManager.PKL_PROJECT_FILENAME;

import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pkl.core.*;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModulePathResolver;
import org.pkl.core.project.Project;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.executor.spi.v1.ExecutorSpi;
import org.pkl.executor.spi.v1.ExecutorSpiException;
import org.pkl.executor.spi.v1.ExecutorSpiOptions;

public class ExecutorSpiImpl implements ExecutorSpi {
  private final String pklVersion = Release.current().version().toString();

  @Override
  public String getPklVersion() {
    return pklVersion;
  }

  @Override
  public String evaluatePath(Path modulePath, ExecutorSpiOptions options) {
    var allowedModules =
        options.getAllowedModules().stream().map(Pattern::compile).collect(Collectors.toList());

    var allowedResources =
        options.getAllowedResources().stream().map(Pattern::compile).collect(Collectors.toList());

    var securityManager =
        SecurityManagers.standard(
            allowedModules,
            allowedResources,
            SecurityManagers.defaultTrustLevels,
            options.getRootDir());

    var transformer = StackFrameTransformers.defaultTransformer;
    if (options.getRootDir() != null) {
      transformer =
          transformer.andThen(
              StackFrameTransformers.relativizeModuleUri(options.getRootDir().toUri()));
    }

    var resolver = new ModulePathResolver(options.getModulePath());

    var builder =
        EvaluatorBuilder.unconfigured()
            .setStackFrameTransformer(transformer)
            .setSecurityManager(securityManager)
            .addResourceReader(ResourceReaders.environmentVariable())
            .addResourceReader(ResourceReaders.externalProperty())
            .addResourceReader(ResourceReaders.modulePath(resolver))
            .addResourceReader(ResourceReaders.pkg())
            .addResourceReader(ResourceReaders.projectpackage())
            .addResourceReader(ResourceReaders.file())
            .addResourceReader(ResourceReaders.http())
            .addResourceReader(ResourceReaders.https())
            .addModuleKeyFactory(ModuleKeyFactories.standardLibrary)
            .addModuleKeyFactories(ModuleKeyFactories.fromServiceProviders())
            .addModuleKeyFactory(ModuleKeyFactories.modulePath(resolver))
            .addModuleKeyFactory(ModuleKeyFactories.pkg)
            .addModuleKeyFactory(ModuleKeyFactories.projectpackage)
            .addModuleKeyFactory(ModuleKeyFactories.file)
            .addModuleKeyFactory(ModuleKeyFactories.genericUrl)
            .setEnvironmentVariables(options.getEnvironmentVariables())
            .setExternalProperties(options.getExternalProperties())
            .setTimeout(options.getTimeout())
            .setOutputFormat(options.getOutputFormat())
            .setModuleCacheDir(options.getModuleCacheDir());
    if (options.getProjectDir() != null) {
      var project = Project.loadFromPath(options.getProjectDir().resolve(PKL_PROJECT_FILENAME));
      builder.setProjectDependencies(project.getDependencies());
    }

    try (var evaluator = builder.build()) {
      return evaluator.evaluateOutputText(ModuleSource.path(modulePath));
    } catch (PklException e) {
      throw new ExecutorSpiException(e.getMessage(), e.getCause());
    } finally {
      ModuleKeyFactories.closeQuietly(builder.getModuleKeyFactories());
    }
  }
}
