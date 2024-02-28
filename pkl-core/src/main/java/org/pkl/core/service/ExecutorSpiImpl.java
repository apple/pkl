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
package org.pkl.core.service;

import static org.pkl.core.module.ProjectDependenciesManager.PKL_PROJECT_FILENAME;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pkl.core.*;
import org.pkl.core.http.HttpClient;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModulePathResolver;
import org.pkl.core.project.Project;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.executor.spi.v1.ExecutorSpi;
import org.pkl.executor.spi.v1.ExecutorSpiException;
import org.pkl.executor.spi.v1.ExecutorSpiOptions;
import org.pkl.executor.spi.v2.ExecutorSpi2;
import org.pkl.executor.spi.v2.ExecutorSpiException2;
import org.pkl.executor.spi.v2.ExecutorSpiOptions2;

public class ExecutorSpiImpl implements ExecutorSpi, ExecutorSpi2 {
  private static final int MAX_HTTP_CLIENTS = 3;

  // Don't create a new HTTP client for every executor request.
  // Instead, keep a cache of up to MAX_HTTP_CLIENTS clients.
  // A cache size of 1 should be common.
  private final Map<HttpClientKey, HttpClient> httpClients;

  private final String pklVersion = Release.current().version().toString();

  public ExecutorSpiImpl() {
    // only LRU cache available in JDK
    var map =
        new LinkedHashMap<HttpClientKey, HttpClient>(8, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Entry<HttpClientKey, HttpClient> eldest) {
            return size() > MAX_HTTP_CLIENTS;
          }
        };
    httpClients = Collections.synchronizedMap(map);
  }

  @Override
  public String getPklVersion() {
    return pklVersion;
  }

  @Override
  public String evaluatePath(Path modulePath, ExecutorSpiOptions options) {
    var builder =
        createEvaluatorBuilder(
            options.getAllowedModules(),
            options.getAllowedResources(),
            options.getRootDir(),
            options.getModulePath(),
            options.getEnvironmentVariables(),
            options.getExternalProperties(),
            options.getTimeout(),
            options.getOutputFormat(),
            options.getModuleCacheDir(),
            options.getProjectDir(),
            List.of(),
            List.of());

    try (var evaluator = builder.build()) {
      return evaluator.evaluateOutputText(ModuleSource.path(modulePath));
    } catch (PklException e) {
      throw new ExecutorSpiException(e.getMessage(), e.getCause());
    } finally {
      ModuleKeyFactories.closeQuietly(builder.getModuleKeyFactories());
    }
  }

  @Override
  public String evaluatePath(Path modulePath, ExecutorSpiOptions2 options) {
    var builder =
        createEvaluatorBuilder(
            options.getAllowedModules(),
            options.getAllowedResources(),
            options.getRootDir(),
            options.getModulePath(),
            options.getEnvironmentVariables(),
            options.getExternalProperties(),
            options.getTimeout(),
            options.getOutputFormat(),
            options.getModuleCacheDir(),
            options.getProjectDir(),
            options.getCertificateFiles(),
            options.getCertificateUris());

    try (var evaluator = builder.build()) {
      return evaluator.evaluateOutputText(ModuleSource.path(modulePath));
    } catch (PklException e) {
      throw new ExecutorSpiException2(e.getMessage(), e.getCause());
    } finally {
      ModuleKeyFactories.closeQuietly(builder.getModuleKeyFactories());
    }
  }

  private EvaluatorBuilder createEvaluatorBuilder(
      List<String> allowedModules,
      List<String> allowedResources,
      Path rootDir,
      List<Path> modulePath,
      Map<String, String> environmentVariables,
      Map<String, String> externalProperties,
      java.time.Duration timeout,
      String outputFormat,
      Path moduleCacheDir,
      Path projectDir,
      List<Path> certificateFiles,
      List<URI> certificateUris) {
    var allowedModulePatterns =
        allowedModules.stream().map(Pattern::compile).collect(Collectors.toList());

    var allowedResourcePatterns =
        allowedResources.stream().map(Pattern::compile).collect(Collectors.toList());

    var securityManager =
        SecurityManagers.standard(
            allowedModulePatterns,
            allowedResourcePatterns,
            SecurityManagers.defaultTrustLevels,
            rootDir);

    var transformer = StackFrameTransformers.defaultTransformer;
    if (rootDir != null) {
      transformer =
          transformer.andThen(StackFrameTransformers.relativizeModuleUri(rootDir.toUri()));
    }

    var resolver = new ModulePathResolver(modulePath);
    var builder =
        EvaluatorBuilder.unconfigured()
            .setStackFrameTransformer(transformer)
            .setSecurityManager(securityManager)
            .setHttpClient(getOrCreateHttpClient(certificateFiles, certificateUris))
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
            .setEnvironmentVariables(environmentVariables)
            .setExternalProperties(externalProperties)
            .setTimeout(timeout)
            .setOutputFormat(outputFormat)
            .setModuleCacheDir(moduleCacheDir);

    if (projectDir != null) {
      var project = Project.loadFromPath(projectDir.resolve(PKL_PROJECT_FILENAME));
      builder.setProjectDependencies(project.getDependencies());
    }

    return builder;
  }

  private HttpClient getOrCreateHttpClient(List<Path> certificateFiles, List<URI> certificateUris) {
    var clientKey = new HttpClientKey(certificateFiles, certificateUris);
    return httpClients.computeIfAbsent(
        clientKey,
        (key) -> {
          var builder = HttpClient.builder();
          for (var file : key.certificateFiles) {
            builder.addCertificates(file);
          }
          for (var uri : key.certificateUris) {
            builder.addCertificates(uri);
          }
          // If the above didn't add any certificates,
          // builder will use the JVM's default SSL context.
          return builder.buildLazily();
        });
  }

  private static final class HttpClientKey {
    final Set<Path> certificateFiles;
    final Set<URI> certificateUris;

    HttpClientKey(List<Path> certificateFiles, List<URI> certificateUris) {
      // also serve as defensive copies
      this.certificateFiles = Set.copyOf(certificateFiles);
      this.certificateUris = Set.copyOf(certificateUris);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      HttpClientKey that = (HttpClientKey) obj;
      return certificateFiles.equals(that.certificateFiles)
          && certificateUris.equals(that.certificateUris);
    }

    @Override
    public int hashCode() {
      return Objects.hash(certificateFiles, certificateUris);
    }
  }
}
