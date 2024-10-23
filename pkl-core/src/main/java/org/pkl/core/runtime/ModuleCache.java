/*
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.pkl.core.Release;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ResolvedModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.util.Nullable;

/**
 * Caches modules by the URI originally specified in the importing module, and also by the resolved
 * URI from which the module was eventually loaded. Caching by original URI avoids any overhead
 * incurred by resolving a module multiple times. Caching by resolved URI avoids any overhead
 * incurred by evaluating a module multiple times, and also avoids any inconsistencies caused by
 * module contents changing during evaluation.
 */
public final class ModuleCache {
  private static final Set<URI> STDLIB_MODULE_URIS =
      Release.current().standardLibrary().modules().stream()
          .map(URI::create)
          .collect(Collectors.toSet());

  public ModuleCache() {}

  public interface ModuleInitializer {
    void initialize(
        ModuleKey moduleKey,
        ResolvedModuleKey resolvedModuleKey,
        ModuleResolver moduleResolver,
        Source source,
        VmTyped emptyModule,
        @Nullable Node importNode);
  }

  // due to eager initialization of (module) classes,
  // loading of module A may be triggered while A is being loaded
  // this is why we can't use (Concurrent)Map.computeIfAbsent() for caching,
  // (duplicate modules wouldn't work correctly because modules and their classes have identity)
  // value type is VmTyped|RuntimeException
  private final Map<URI, Object> modulesByOriginalUri = new HashMap<>();
  private final Map<URI, Object> modulesByResolvedUri = new HashMap<>();

  @TruffleBoundary
  public synchronized VmTyped getOrLoad(
      ModuleKey moduleKey,
      SecurityManager securityManager,
      ModuleResolver moduleResolver,
      Supplier<VmTyped> moduleInstantiator,
      ModuleInitializer moduleInitializer,
      @Nullable Node importNode) {

    if (ModuleKeys.isStdLibModule(moduleKey)) {
      var moduleName = moduleKey.getUri().getSchemeSpecificPart();

      // some standard library modules are cached as static singletons
      // and hence aren't parsed/initialized anew for every evaluator
      switch (moduleName) {
        case "base":
          // always needed
          return BaseModule.getModule();
        case "Benchmark":
          return BenchmarkModule.getModule();
        case "jsonnet":
          return JsonnetModule.getModule();
        case "math":
          return MathModule.getModule();
        case "platform":
          return PlatformModule.getModule();
        case "project":
          return ProjectModule.getModule();
        case "reflect":
          return ReflectModule.getModule();
        case "release":
          return ReleaseModule.getModule();
        case "semver":
          return SemVerModule.getModule();
        case "settings":
          // always needed if ~/.pkl/settings.pkl is present
          return SettingsModule.getModule();
        case "test":
          return TestModule.getModule();
        case "xml":
          return XmlModule.getModule();
        default:
          if (!STDLIB_MODULE_URIS.contains(moduleKey.getUri())) {
            var stdlibModules = String.join("\n", Release.current().standardLibrary().modules());
            throw new VmExceptionBuilder()
                .withOptionalLocation(importNode)
                .evalError("cannotFindStdLibModule", moduleName, stdlibModules)
                .build();
          }
      }
    }

    if (!moduleKey.isCached()) {
      var resolvedKey = resolve(moduleKey, securityManager, importNode);
      return doLoad(
          moduleKey,
          resolvedKey,
          moduleResolver,
          moduleInstantiator,
          moduleInitializer,
          importNode);
    }

    var module1 = modulesByOriginalUri.get(moduleKey.getUri());
    if (module1 != null) {
      if (module1 instanceof VmTyped typed) return typed;

      assert module1 instanceof RuntimeException;
      // would be more accurate/safe to throw a clone with adapted Pkl stack trace
      throw (RuntimeException) module1;
    }

    var resolvedKey = resolve(moduleKey, securityManager, importNode);
    var module2 = modulesByResolvedUri.get(resolvedKey.getUri());
    if (module2 != null) {
      if (module2 instanceof VmTyped typed) return typed;

      assert module2 instanceof RuntimeException;
      // would be more accurate/safe to throw a clone with adapted Pkl stack trace
      throw (RuntimeException) module2;
    }

    return doLoad(
        moduleKey, resolvedKey, moduleResolver, moduleInstantiator, moduleInitializer, importNode);
  }

  private VmTyped doLoad(
      ModuleKey moduleKey,
      ResolvedModuleKey resolvedKey,
      ModuleResolver moduleResolver,
      Supplier<VmTyped> moduleInstantiator,
      ModuleInitializer moduleInitializer,
      @Nullable Node importNode) {

    VmTyped module = moduleInstantiator.get();

    try {
      var result = VmUtils.loadSource(resolvedKey);

      // cache module before initializing it to handle recursive module dependencies (cf. ClassNode)
      modulesByOriginalUri.put(moduleKey.getUri(), module);
      modulesByResolvedUri.put(resolvedKey.getUri(), module);

      moduleInitializer.initialize(
          moduleKey, resolvedKey, moduleResolver, result, module, importNode);
    } catch (Exception e) {
      // handle error deterministically by caching it and rethrowing it when the module is loaded
      // again
      // (shouldn't try to load a module multiple times within the scope of an
      // Evaluator/ModuleCache)
      modulesByOriginalUri.put(moduleKey.getUri(), e);
      modulesByResolvedUri.put(resolvedKey.getUri(), e);
      throw e;
    }

    return module;
  }

  private ResolvedModuleKey resolve(
      ModuleKey module, SecurityManager securityManager, @Nullable Node importNode) {
    try {
      return module.resolve(securityManager);
    } catch (SecurityManagerException | PackageLoadError | HttpClientInitException e) {
      throw new VmExceptionBuilder().withOptionalLocation(importNode).withCause(e).build();
    } catch (FileNotFoundException | NoSuchFileException e) {
      var exceptionBuilder =
          new VmExceptionBuilder()
              .withOptionalLocation(importNode)
              .evalError("cannotFindModule", module.getUri());
      var path = module.getUri().getPath();
      if (path != null && path.contains("\\")) {
        exceptionBuilder.withHint(
            "To resolve modules in nested directories, use `/` as the directory separator.");
      }
      throw exceptionBuilder.build();
    } catch (IOException e) {
      throw new VmExceptionBuilder()
          .withOptionalLocation(importNode)
          .evalError("ioErrorLoadingModule", module.getUri())
          .withCause(e)
          .build();
    }
  }
}
