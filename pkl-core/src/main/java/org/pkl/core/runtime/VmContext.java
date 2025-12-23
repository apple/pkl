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
package org.pkl.core.runtime;

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.Node;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.pkl.core.Logger;
import org.pkl.core.SecurityManager;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.evaluatorSettings.TraceMode;
import org.pkl.core.http.HttpClient;
import org.pkl.core.module.ProjectDependenciesManager;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public final class VmContext {
  private static final ContextReference<VmContext> REFERENCE =
      ContextReference.create(VmLanguage.class);
  private final VmValueTrackerFactory valueTrackerFactory;

  public VmContext(VmLanguage vmLanguage, Env env) {
    this.valueTrackerFactory = new VmValueTrackerFactory(env.lookup(Instrumenter.class));
  }

  @LateInit private Holder holder;

  public static final class Holder {
    private static final String OUTPUT_FORMAT_KEY = "pkl.outputFormat";

    private final StackFrameTransformer frameTransformer;
    private final SecurityManager securityManager;
    private final HttpClient httpClient;
    private final ModuleResolver moduleResolver;
    private final ResourceManager resourceManager;
    private final Logger logger;
    private final Map<String, String> environmentVariables;
    private final Path moduleCacheDir;
    private final Map<String, String> externalProperties;
    private final ModuleCache moduleCache;
    private final @Nullable PackageResolver packageResolver;
    private final @Nullable ProjectDependenciesManager projectDependenciesManager;
    private final TraceMode traceMode;

    public Holder(
        StackFrameTransformer frameTransformer,
        SecurityManager securityManager,
        HttpClient httpClient,
        ModuleResolver moduleResolver,
        ResourceManager resourceManager,
        Logger logger,
        Map<String, String> environmentVariables,
        Map<String, String> externalProperties,
        @Nullable Path moduleCacheDir,
        @Nullable String outputFormat,
        @Nullable PackageResolver packageResolver,
        @Nullable ProjectDependenciesManager projectDependenciesManager,
        TraceMode traceMode) {

      this.frameTransformer = frameTransformer;
      this.securityManager = securityManager;
      this.httpClient = httpClient;
      this.moduleResolver = moduleResolver;
      this.resourceManager = resourceManager;
      this.logger = logger;
      this.environmentVariables = environmentVariables;
      this.moduleCacheDir = moduleCacheDir;

      // treat outputFormat as an external property from here on, at least for now
      var props = new HashMap<>(externalProperties);
      if (outputFormat != null) {
        props.put(OUTPUT_FORMAT_KEY, outputFormat);
      }
      this.externalProperties = props;

      moduleCache = new ModuleCache();
      this.packageResolver = packageResolver;
      this.projectDependenciesManager = projectDependenciesManager;
      this.traceMode = traceMode;
    }
  }

  public static VmContext get(@Nullable Node node) {
    return REFERENCE.get(node);
  }

  public void initialize(Holder holder) {
    assert this.holder == null;
    this.holder = holder;
  }

  public ModuleCache getModuleCache() {
    return holder.moduleCache;
  }

  public @Nullable Path getModuleCacheDir() {
    return holder.moduleCacheDir;
  }

  public StackFrameTransformer getFrameTransformer() {
    return holder.frameTransformer;
  }

  public SecurityManager getSecurityManager() {
    return holder.securityManager;
  }

  public HttpClient getHttpClient() {
    return holder.httpClient;
  }

  public ModuleResolver getModuleResolver() {
    return holder.moduleResolver;
  }

  public ResourceManager getResourceManager() {
    return holder.resourceManager;
  }

  public Logger getLogger() {
    return holder.logger;
  }

  public Map<String, String> getEnvironmentVariables() {
    return holder.environmentVariables;
  }

  public Map<String, String> getExternalProperties() {
    return holder.externalProperties;
  }

  public @Nullable PackageResolver getPackageResolver() {
    return holder.packageResolver;
  }

  public @Nullable ProjectDependenciesManager getProjectDependenciesManager() {
    return holder.projectDependenciesManager;
  }

  public TraceMode getTraceMode() {
    return holder.traceMode;
  }

  public VmValueTrackerFactory getValueTrackerFactory() {
    return valueTrackerFactory;
  }
}
