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
package org.pkl.core.resource;

import java.net.URI;
import java.util.Optional;
import java.util.ServiceLoader;
import org.pkl.core.module.ModulePathResolver;
import org.pkl.core.util.IoUtils;

public final class ResourceReaderFactories {

  private ResourceReaderFactories() {}

  /**
   * A factory for OS environment variables. If this resource reader is present, Pkl code can read
   * environment variable {@code FOO_BAR} with {@code read("env:FOO_BAR")}, provided that resource
   * URI {@code env:FOO_BAR} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReaderFactory environmentVariable() {
    return EnvironmentVariable.INSTANCE;
  }

  /**
   * A factory for external properties. If this resource reader is present, Pkl code can read
   * external property {@code foo.bar} with {@code read("prop:foo.bar")}, provided that resource URI
   * {@code prop:foo.bar} matches an entry in the resource allowlist ({@code --allowed-resources}).
   */
  public static ResourceReaderFactory externalProperty() {
    return ExternalProperty.INSTANCE;
  }

  public static ResourceReaderFactory file() {
    return FileResourceFactory.INSTANCE;
  }

  /**
   * A factory for HTTP resources. If this resource reader is present, Pkl code can read HTTP
   * resource {@code http://apple.com/foo/bar.txt} with {@code
   * read("http://apple.com/foo/bar.txt")}, provided that resource URI {@code
   * "http://apple.com/foo/bar.txt"} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReaderFactory http() {
    return HttpResourceFactory.INSTANCE;
  }

  /**
   * A factory for HTTPS resources. If this resource reader is present, Pkl code can read HTTPS
   * resource {@code https://apple.com/foo/bar.txt} with {@code
   * read("https://apple.com/foo/bar.txt")}, provided that resource URI {@code
   * "https://apple.com/foo/bar.txt"} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReaderFactory https() {
    return HttpsResourceFactory.INSTANCE;
  }

  /**
   * A factory for JVM class path resources. If this resource reader is present, Pkl code can read
   * class path resource {@code /foo/bar.txt} with {@code read("modulepath:foo/bar.txt")}, provided
   * that resource URI {@code "modulepath:foo/bar.txt"} matches an entry in the resource allowlist
   * ({@code --allowed-resources}).
   */
  public static ResourceReaderFactory classPath(ClassLoader classLoader) {
    return new ClassPathResourceFactory(classLoader);
  }

  /**
   * A factory for Pkl module path ({@code --module-path}) resources. If this resource reader is
   * present, Pkl code can read module path resource {@code /foo/bar.txt} with {@code
   * read("modulepath:foo/bar.txt")}, provided that resource URI {@code "modulepath:foo/bar.txt"}
   * matches an entry in the resource allowlist ({@code --allowed-resources}).
   */
  public static ResourceReaderFactory modulePath(ModulePathResolver resolver) {
    return new ModulePathResourceFactory(resolver);
  }

  /**
   * A factory for {@code package:} resources. If this resource reader is present, Pkl code can read
   * resources from within packages with {@code read("package://example.com/foo@1.0.0#/foo.txt")},
   * or using dependency notation with {@code read("@foo/foo.txt")} assuming that the Pkl module is
   * within a project that declares a dependency named {@code foo}.
   */
  public static ResourceReaderFactory pkg() {
    return PackageResourceFactory.INSTANCE;
  }

  public static ResourceReaderFactory projectpackage() {
    return ProjectPackageResourceFactory.INSTANCE;
  }

  /**
   * Returns a factory resource readers registered as {@link ServiceLoader service providers} of
   * type {@code org.pkl.core.resource.ResourceReader}.
   */
  public static ResourceReaderFactory fromServiceProviders() {
    return FromServiceProviders.INSTANCE;
  }

  private static final class EnvironmentVariable implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new EnvironmentVariable();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("env")) return Optional.empty();
      return Optional.of(ResourceReaders.environmentVariable());
    }
  }

  private static final class ExternalProperty implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new ExternalProperty();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("prop")) return Optional.empty();
      return Optional.of(ResourceReaders.externalProperty());
    }
  }

  private static final class FileResourceFactory implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new FileResourceFactory();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("file")) return Optional.empty();
      return Optional.of(ResourceReaders.file());
    }
  }

  private static final class HttpResourceFactory implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new HttpResourceFactory();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("http")) return Optional.empty();
      return Optional.of(ResourceReaders.http());
    }
  }

  private static final class HttpsResourceFactory implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new HttpsResourceFactory();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("https")) return Optional.empty();
      return Optional.of(ResourceReaders.https());
    }
  }

  private static final class ClassPathResourceFactory implements ResourceReaderFactory {
    private final ClassLoader classLoader;

    public ClassPathResourceFactory(ClassLoader classLoader) {
      this.classLoader = classLoader;
    }

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("modulepath")) return Optional.empty();
      return Optional.of(ResourceReaders.classPath(classLoader));
    }
  }

  private static final class ModulePathResourceFactory implements ResourceReaderFactory {
    private final ModulePathResolver resolver;

    public ModulePathResourceFactory(ModulePathResolver resolver) {
      this.resolver = resolver;
    }

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("modulepath")) return Optional.empty();
      return Optional.of(ResourceReaders.modulePath(resolver));
    }
  }

  private static final class PackageResourceFactory implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new PackageResourceFactory();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("package")) return Optional.empty();
      return Optional.of(ResourceReaders.pkg());
    }
  }

  private static final class ProjectPackageResourceFactory implements ResourceReaderFactory {
    static final ResourceReaderFactory INSTANCE = new ProjectPackageResourceFactory();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      if (!uri.getScheme().equalsIgnoreCase("projectpackage")) return Optional.empty();
      return Optional.of(ResourceReaders.projectpackage());
    }
  }

  private static class FromServiceProviders implements ResourceReaderFactory {
    private static final FromServiceProviders INSTANCE = new FromServiceProviders();

    @Override
    public Optional<ResourceReader> create(URI uri) {
      var loader = IoUtils.createServiceLoader(ResourceReader.class);
      for (var reader : loader) {
        if (uri.getScheme().equalsIgnoreCase(reader.getUriScheme())) {
          return Optional.of(reader);
        }
      }
      return Optional.empty();
    }
  }
}
