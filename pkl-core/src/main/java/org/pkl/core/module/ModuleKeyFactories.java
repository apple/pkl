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
package org.pkl.core.module;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.pkl.core.util.IoUtils;

/** Utilities for obtaining and using module key factories. */
public final class ModuleKeyFactories {
  private ModuleKeyFactories() {}

  /** A factory for standard library module keys. */
  public static final ModuleKeyFactory standardLibrary = new StandardLibrary();

  /** A factory for file based module keys. */
  public static final ModuleKeyFactory file = new File();

  /** A factory for URL based module keys. */
  public static final ModuleKeyFactory genericUrl = new GenericUrl();

  /**
   * Returns factories registered as {@link ServiceLoader service providers} of type {@code
   * org.pkl.core.module.ModuleKeyFactory}.
   */
  public static List<ModuleKeyFactory> fromServiceProviders() {
    return FromServiceProviders.INSTANCE;
  }

  /** A factory for {@code package:} modules. */
  public static final ModuleKeyFactory pkg = new Package();

  /** A factory for {@code projectpackage:} modules. */
  public static final ModuleKeyFactory projectpackage = new ProjectPackage();

  /**
   * Returns a factory for {@code modulepath:} modules resolved on the given module path.
   *
   * <p>NOTE: {@code resolver} needs to be {@link ModulePathResolver#close closed} to avoid resource
   * leaks.
   */
  public static ModuleKeyFactory modulePath(ModulePathResolver resolver) {
    return new ModulePath(resolver);
  }

  /** Returns a factory for {@code modulepath:} modules resolved with the given class loader. */
  public static ModuleKeyFactory classPath(ClassLoader classLoader) {
    return new ClassPath(classLoader);
  }

  /** Closes the given factories, ignoring any exceptions. */
  public static void closeQuietly(Iterable<ModuleKeyFactory> factories) {
    for (ModuleKeyFactory factory : factories) {
      try {
        factory.close();
      } catch (Exception ignored) {
      }
    }
  }

  private static class StandardLibrary implements ModuleKeyFactory {
    private StandardLibrary() {}

    @Override
    public Optional<ModuleKey> create(URI uri) {
      if (!uri.getScheme().equals("pkl")) return Optional.empty();
      return Optional.of(ModuleKeys.standardLibrary(uri));
    }
  }

  private static class ModulePath implements ModuleKeyFactory {
    final ModulePathResolver resolver;

    public ModulePath(ModulePathResolver resolver) {
      this.resolver = resolver;
    }

    @Override
    public Optional<ModuleKey> create(URI uri) {
      if (uri.getScheme().equals("modulepath")) {
        return Optional.of(ModuleKeys.modulePath(uri, resolver));
      }
      if (uri.getScheme().equals("jar")) {
        try {
          // modulepaths that resolve to jar-file URIs will register a FileSystemProvider that
          // such that `Paths.get("jar:file")` returns a path.
          // Otherwise, `FileSystemNotFoundException` gets thrown.
          var path = Paths.get(uri);
          return Optional.of(ModuleKeys.modulePath(URI.create("modulepath:" + path), resolver));
        } catch (FileSystemNotFoundException e) {
          return Optional.empty();
        }
      }
      return Optional.empty();
    }

    @Override
    public void close() {
      resolver.close();
    }
  }

  private static class ClassPath implements ModuleKeyFactory {
    private final ClassLoader classLoader;

    public ClassPath(ClassLoader classLoader) {
      this.classLoader = classLoader;
    }

    @Override
    public Optional<ModuleKey> create(URI uri) {
      if (!uri.getScheme().equals("modulepath")) return Optional.empty();
      return Optional.of(ModuleKeys.classPath(uri, classLoader));
    }
  }

  private static class File implements ModuleKeyFactory {
    @Override
    public Optional<ModuleKey> create(URI uri) {
      Path path;
      try {
        path = Path.of(uri);
      } catch (FileSystemNotFoundException | IllegalArgumentException e) {
        // none of the installed file system providers can handle this URI
        return Optional.empty();
      }

      return Optional.of(ModuleKeys.file(uri, path));
    }
  }

  private static class GenericUrl implements ModuleKeyFactory {
    private GenericUrl() {}

    @Override
    public Optional<ModuleKey> create(URI uri) {
      if (!uri.isAbsolute()) return Optional.empty();
      if (uri.isOpaque() && !"jar".equalsIgnoreCase(uri.getScheme())) return Optional.empty();

      // Blindly accept this URI, assuming ModuleKeys.genericUrl() can handle it.
      // This means that ModuleKeyFactories.GenericUrl must come last in the handler chain.
      return Optional.of(ModuleKeys.genericUrl(uri));
    }
  }

  /**
   * Represents a module from a package.
   *
   * <p>Packages are shareable libraries published to the internet in the form of a zip ball, or
   * optionally, a local project declared as a dependency of the current project.
   */
  private static final class Package implements ModuleKeyFactory {
    public Optional<ModuleKey> create(URI uri) throws URISyntaxException {
      if (uri.getScheme().equalsIgnoreCase("package")) {
        return Optional.of(ModuleKeys.pkg(uri));
      }
      return Optional.empty();
    }
  }

  /**
   * Represents a module from a project-relative package.
   *
   * <p>A project-relative package has the scheme {@code projectpackage}. It can either be a remote
   * dependency, or a local dependency
   */
  private static final class ProjectPackage implements ModuleKeyFactory {
    public Optional<ModuleKey> create(URI uri) throws URISyntaxException {
      if (uri.getScheme().equalsIgnoreCase("projectpackage")) {
        return Optional.of(ModuleKeys.projectpackage(uri));
      }
      return Optional.empty();
    }
  }

  private static class FromServiceProviders {
    private static final List<ModuleKeyFactory> INSTANCE;

    static {
      var loader = IoUtils.createServiceLoader(ModuleKeyFactory.class);
      var factories = new ArrayList<ModuleKeyFactory>();
      loader.forEach(factories::add);
      INSTANCE = Collections.unmodifiableList(factories);
    }
  }
}
