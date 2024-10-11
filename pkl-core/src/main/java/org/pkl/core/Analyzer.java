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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.pkl.core.http.HttpClient;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ModuleKeyFactory;
import org.pkl.core.module.ProjectDependenciesManager;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.runtime.ModuleResolver;
import org.pkl.core.runtime.ResourceManager;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmImportAnalyzer;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

/** Utility library for static analysis of Pkl programs. */
public class Analyzer {
  private final StackFrameTransformer transformer;
  private final SecurityManager securityManager;
  private final @Nullable Path moduleCacheDir;
  private final @Nullable DeclaredDependencies projectDependencies;
  private final ModuleResolver moduleResolver;
  private final HttpClient httpClient;

  public Analyzer(
      StackFrameTransformer transformer,
      SecurityManager securityManager,
      Collection<ModuleKeyFactory> moduleKeyFactories,
      @Nullable Path moduleCacheDir,
      @Nullable DeclaredDependencies projectDependencies,
      HttpClient httpClient) {
    this.transformer = transformer;
    this.securityManager = securityManager;
    this.moduleCacheDir = moduleCacheDir;
    this.projectDependencies = projectDependencies;
    this.moduleResolver = new ModuleResolver(moduleKeyFactories);
    this.httpClient = httpClient;
  }

  /**
   * Builds a graph of imports from the provided source modules.
   *
   * <p>For details, see {@link ImportGraph}.
   */
  public ImportGraph importGraph(URI... sources) {
    var context = createContext();
    try {
      context.enter();
      var vmContext = VmContext.get(null);
      var results = VmImportAnalyzer.analyze(sources, vmContext);
      return new ImportGraph(results.first, results.second);
    } catch (SecurityManagerException
        | IOException
        | URISyntaxException
        | PackageLoadError
        | HttpClientInitException e) {
      throw new PklException(e.getMessage(), e);
    } catch (PklException err) {
      throw err;
    } catch (VmException err) {
      throw err.toPklException(transformer);
    } catch (Exception e) {
      throw new PklBugException(e);
    } finally {
      context.leave();
      context.close();
    }
  }

  private Context createContext() {
    var packageResolver =
        PackageResolver.getInstance(
            securityManager, HttpClient.builder().buildLazily(), moduleCacheDir);
    return VmUtils.createContext(
        () -> {
          VmContext vmContext = VmContext.get(null);
          vmContext.initialize(
              new VmContext.Holder(
                  transformer,
                  securityManager,
                  httpClient,
                  moduleResolver,
                  new ResourceManager(securityManager, List.of()),
                  Loggers.noop(),
                  Map.of(),
                  Map.of(),
                  moduleCacheDir,
                  null,
                  packageResolver,
                  projectDependencies == null
                      ? null
                      : new ProjectDependenciesManager(
                          projectDependencies, moduleResolver, securityManager)));
        });
  }
}
