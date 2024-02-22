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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.packages.Dependency;
import org.pkl.core.packages.Dependency.LocalDependency;
import org.pkl.core.packages.PackageAssetUri;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.HttpUtils;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

/** Utilities for creating and using {@link ModuleKey}s. */
public final class ModuleKeys {
  private ModuleKeys() {}

  /**
   * Tells if the given module is a standard library module. Standard library modules ship with the
   * language and have a URI of the form {@code pkl:<simple module name>}.
   */
  public static boolean isStdLibModule(ModuleKey module) {
    return module instanceof StandardLibrary;
  }

  /** Tells if the given module is the standard library module with URI {@code pkl:base}. */
  @TruffleBoundary
  public static boolean isBaseModule(ModuleKey module) {
    return isStdLibModule(module) && module.getUri().getSchemeSpecificPart().equals("base");
  }

  /**
   * Creates a module key identified by the given URI and backed by the given source code. Shorthand
   * for {@code synthetic(uri, uri, uri, sourceText, false}.
   */
  public static ModuleKey synthetic(URI uri, String sourceText) {
    return new Synthetic(uri, uri, uri, sourceText, false);
  }

  /**
   * Creates a module key identified by the given URI and backed by the given source code. Module
   * imports will be resolved against the given {@code importBaseUri}. If {@code isCached} is {@code
   * true}, the resulting module will be cached with {@code uri} and {@code resolvedUri} used as
   * cache keys.
   */
  public static ModuleKey synthetic(
      URI uri, URI importBaseUri, URI resolvedUri, String sourceText, boolean isCached) {
    return new Synthetic(uri, importBaseUri, resolvedUri, sourceText, isCached);
  }

  /**
   * Creates a module key for the standard library module with the given URI. The URI for a standard
   * library module is {@code pkl:<simple module name>}. For example, the URI for the base library
   * module is {@code pkl:base}.
   */
  public static ModuleKey standardLibrary(URI uri) {
    return new StandardLibrary(uri);
  }

  /** Creates a module key for a {@code file:} module. */
  public static ModuleKey file(URI uri, Path path) {
    return new File(uri, path);
  }

  /**
   * Creates a module key for a {@code modulepath:} module to be resolved with the given resolver.
   */
  public static ModuleKey modulePath(URI uri, ModulePathResolver resolver) {
    return new ModulePath(uri, resolver);
  }

  /**
   * Creates a module key for a {@code modulepath:} module to be resolved with the given class
   * loader.
   */
  public static ModuleKey classPath(URI uri, ClassLoader classLoader) {
    return new ClassPath(uri, classLoader);
  }

  /** Creates a module key for the module with the given URL. */
  public static ModuleKey genericUrl(URI url) {
    return new GenericUrl(url);
  }

  /** Creates a module key for the given package. */
  public static ModuleKey pkg(URI uri) throws URISyntaxException {
    var assetUri = new PackageAssetUri(uri);
    return new Package(assetUri);
  }

  public static ModuleKey projectpackage(URI uri) throws URISyntaxException {
    var assetUri = new PackageAssetUri(uri);
    return new ProjectPackage(assetUri);
  }

  /**
   * Creates a module key that behaves like {@code delegate}, except that it returns {@code text} as
   * its loaded source.
   */
  public static ModuleKey cached(ModuleKey delegate, String text) {
    return new CachedModuleKey(delegate, text);
  }

  private static class CachedModuleKey implements ModuleKey, ResolvedModuleKey {
    private final ModuleKey delegate;
    private final String text;

    public CachedModuleKey(ModuleKey delegate, String text) {
      this.delegate = delegate;
      this.text = text;
    }

    @Override
    public ModuleKey getOriginal() {
      return this;
    }

    @Override
    public URI getUri() {
      return delegate.getUri();
    }

    @Override
    public String loadSource() throws IOException {
      return text;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      return this;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return delegate.hasHierarchicalUris();
    }

    @Override
    public boolean isLocal() {
      return delegate.isLocal();
    }

    @Override
    public boolean isGlobbable() {
      return delegate.isGlobbable();
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI uri)
        throws IOException, SecurityManagerException {
      return delegate.hasElement(securityManager, uri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      return delegate.listElements(securityManager, baseUri);
    }
  }

  private static class Synthetic implements ModuleKey {
    final URI uri;
    final URI importBaseUri;
    final boolean isCached;
    final ResolvedModuleKey resolvedKey;

    Synthetic(URI uri, URI importBaseUri, URI resolvedUri, String sourceText, boolean isCached) {
      this.uri = uri;
      this.importBaseUri = importBaseUri;
      this.isCached = isCached;
      resolvedKey = ResolvedModuleKeys.virtual(this, resolvedUri, sourceText, isCached);
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return false;
    }

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws SecurityManagerException {
      securityManager.checkResolveModule(uri);
      return resolvedKey;
    }

    @Override
    public boolean isCached() {
      return isCached;
    }
  }

  private static class StandardLibrary implements ModuleKey, ResolvedModuleKey {
    final URI uri;

    StandardLibrary(URI uri) {
      if (!uri.getScheme().equals("pkl")) {
        throw new IllegalArgumentException("Expected URI with scheme `pkl`, but got: " + uri);
      }
      this.uri = uri;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return false;
    }

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws SecurityManagerException {
      securityManager.checkResolveModule(uri);
      return this;
    }

    @Override
    public boolean isCached() {
      return true;
    }

    @Override
    public ModuleKey getOriginal() {
      return this;
    }

    @Override
    public String loadSource() throws IOException {
      return IoUtils.readClassPathResourceAsString(
          getClass(), "/org/pkl/core/stdlib/" + uri.getSchemeSpecificPart() + ".pkl");
    }
  }

  private static class File extends DependencyAwareModuleKey {
    final URI uri;
    final Path path;

    File(URI uri, Path path) {
      super(uri);
      this.uri = uri;
      this.path = path;
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI uri)
        throws SecurityManagerException {
      securityManager.checkResolveModule(uri);
      return FileResolver.hasElement(uri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(baseUri);
      return FileResolver.listElements(baseUri);
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(uri);
      var realPath = path.toRealPath();
      var resolvedUri = realPath.toUri();
      securityManager.checkResolveModule(resolvedUri);
      return ResolvedModuleKeys.file(this, resolvedUri, realPath);
    }

    @Override
    protected Map<String, ? extends Dependency> getDependencies() {
      var projectDepsManager = VmContext.get(null).getProjectDependenciesManager();
      if (projectDepsManager == null || !projectDepsManager.hasPath(path)) {
        throw new PackageLoadError("cannotResolveDependencyNoProject");
      }
      return projectDepsManager.getDependencies();
    }

    @Override
    protected PackageLoadError cannotFindDependency(String name) {
      return new PackageLoadError("cannotFindDependencyInProject", name);
    }
  }

  private static final class ModulePath implements ModuleKey {
    final URI uri;
    final ModulePathResolver resolver;

    ModulePath(URI uri, ModulePathResolver resolver) {
      if (uri.getPath() == null) {
        throw new IllegalArgumentException(
            ErrorMessages.create("invalidModuleUriMissingSlash", uri, "modulepath"));
      }

      this.uri = uri;
      this.resolver = resolver;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI uri)
        throws SecurityManagerException {
      securityManager.checkResolveModule(uri);
      return resolver.hasElement(uri);
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(uri);

      var path = resolver.resolve(uri).toRealPath();
      return ResolvedModuleKeys.file(this, path.toUri(), path);
    }
  }

  private static final class ClassPath implements ModuleKey {

    final URI uri;

    final ClassLoader classLoader;

    ClassPath(URI uri, ClassLoader classLoader) {
      if (uri.getPath() == null) {
        throw new IllegalArgumentException(
            ErrorMessages.create("invalidModuleUriMissingSlash", uri, "modulepath"));
      }
      this.uri = uri;
      this.classLoader = classLoader;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public boolean hasElement(SecurityManager manager, URI uri) throws SecurityManagerException {
      manager.checkResolveModule(uri);
      var uriPath = uri.getPath();
      assert uriPath.charAt(0) == '/';
      return classLoader.getResource(uriPath.substring(1)) != null;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(uri);
      var url = classLoader.getResource(getResourcePath());
      if (url == null) throw new FileNotFoundException();
      try {
        return ResolvedModuleKeys.url(this, url.toURI(), url);
      } catch (URISyntaxException e) {
        throw new AssertionError(e);
      }
    }

    private String getResourcePath() {
      String path = uri.getPath();
      assert path.charAt(0) == '/';
      return path.substring(1);
    }
  }

  private static class GenericUrl implements ModuleKey {
    final URI uri;

    GenericUrl(URI uri) {
      this.uri = uri;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isLocal() {
      return false;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(uri);

      if (HttpUtils.isHttpUrl(uri)) {
        var httpClient = VmContext.get(null).getHttpClient();
        var request = HttpRequest.newBuilder(uri).build();
        var response = httpClient.send(request, BodyHandlers.ofInputStream());
        try (var body = response.body()) {
          HttpUtils.checkHasStatusCode200(response);
          securityManager.checkResolveModule(response.uri());
          String text = IoUtils.readString(body);
          return ResolvedModuleKeys.virtual(this, response.uri(), text, true);
        }
      }

      var url = IoUtils.toUrl(uri);
      var conn = url.openConnection();
      conn.connect();
      try (InputStream stream = conn.getInputStream()) {
        URI redirected;
        try {
          redirected = conn.getURL().toURI();
        } catch (URISyntaxException e1) {
          // should never happen because we started from a URI
          throw new AssertionError(e1);
        }
        securityManager.checkResolveModule(redirected);
        var text = IoUtils.readString(stream);
        return ResolvedModuleKeys.virtual(this, redirected, text, true);
      }
    }
  }

  /** Base implementation; knows how to resolve dependencies prefixed with <code>@</code>. */
  private abstract static class DependencyAwareModuleKey implements ModuleKey {

    protected final URI uri;

    DependencyAwareModuleKey(URI uri) {
      this.uri = uri;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    protected Pair<String, String> parseDependencyNotation(String importPath) {
      var idx = importPath.indexOf('/');
      if (idx == -1) {
        // treat named dependency without a subpath as the root path.
        // i.e. resolve to `@foo` to `package://example.com/foo@1.0.0#/`
        return Pair.of(importPath.substring(1), "/");
      }
      return Pair.of(importPath.substring(1, idx), importPath.substring(idx));
    }

    protected abstract Map<String, ? extends Dependency> getDependencies()
        throws IOException, SecurityManagerException;

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    private URI resolveDependencyNotation(String notation)
        throws IOException, SecurityManagerException {
      var parsed = parseDependencyNotation(notation);
      var name = parsed.getFirst();
      var path = parsed.getSecond();
      var dependency = getDependencies().get(name);
      if (dependency == null) {
        throw cannotFindDependency(name);
      }
      return dependency.getPackageUri().toPackageAssetUri(path).getUri();
    }

    @Override
    public URI resolveUri(URI baseUri, URI importUri) throws IOException, SecurityManagerException {
      if (importUri.isAbsolute() || !importUri.getPath().startsWith("@")) {
        return ModuleKey.super.resolveUri(baseUri, importUri);
      }
      return resolveDependencyNotation(importUri.getPath());
    }

    protected abstract PackageLoadError cannotFindDependency(String name);
  }

  /** Represents a module imported via the {@code package} scheme. */
  private static class Package extends DependencyAwareModuleKey {

    private final PackageAssetUri packageAssetUri;

    Package(PackageAssetUri packageAssetUri) {
      super(packageAssetUri.getUri());
      this.packageAssetUri = packageAssetUri;
    }

    private PackageResolver getPackageResolver() {
      var packageResolver = VmContext.get(null).getPackageResolver();
      assert packageResolver != null;
      return packageResolver;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(uri);
      var bytes =
          getPackageResolver()
              .getBytes(packageAssetUri, false, packageAssetUri.getPackageUri().getChecksums());
      return ResolvedModuleKeys.virtual(this, uri, new String(bytes, StandardCharsets.UTF_8), true);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(baseUri);
      var assetUri = PackageAssetUri.create(baseUri);
      return getPackageResolver().listElements(assetUri, assetUri.getPackageUri().getChecksums());
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(elementUri);
      var assetUri = PackageAssetUri.create(elementUri);
      return getPackageResolver().hasElement(assetUri, assetUri.getPackageUri().getChecksums());
    }

    @Override
    public boolean hasFragmentPaths() {
      return true;
    }

    @Override
    protected Map<String, ? extends Dependency> getDependencies()
        throws IOException, SecurityManagerException {
      return getPackageResolver()
          .getDependencyMetadata(
              packageAssetUri.getPackageUri(), packageAssetUri.getPackageUri().getChecksums())
          .getDependencies();
    }

    @Override
    protected PackageLoadError cannotFindDependency(String name) {
      return new PackageLoadError(
          "cannotFindDependencyInPackage", name, packageAssetUri.getPackageUri().getDisplayName());
    }
  }

  /**
   * Represents a module imported via the {@code projectpackage} scheme.
   *
   * <p>The {@code projectpackage} scheme is what project-local dependencies resolve to when
   * imported using dependency notation (for example, {@code import "@foo/bar.pkl"}). This scheme is
   * an internal implementation detail, and we do not expect a project to declare this.
   */
  private static class ProjectPackage extends DependencyAwareModuleKey {

    private final PackageAssetUri packageAssetUri;

    ProjectPackage(PackageAssetUri packageAssetUri) {
      super(packageAssetUri.getUri());
      this.packageAssetUri = packageAssetUri;
    }

    private PackageResolver getPackageResolver() {
      var packageResolver = VmContext.get(null).getPackageResolver();
      assert packageResolver != null;
      return packageResolver;
    }

    private ProjectDependenciesManager getProjectDepsResolver() {
      var projectDepsManager = VmContext.get(null).getProjectDependenciesManager();
      assert projectDepsManager != null;
      return projectDepsManager;
    }

    private @Nullable Path getLocalPath(Dependency dependency) {
      if (!(dependency instanceof LocalDependency)) {
        return null;
      }
      return ((LocalDependency) dependency)
          .resolveAssetPath(getProjectDepsResolver().getProjectDir(), packageAssetUri);
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(packageAssetUri.getUri());
      var dependency =
          getProjectDepsResolver().getResolvedDependency(packageAssetUri.getPackageUri());
      var path = getLocalPath(dependency);
      if (path != null) {
        securityManager.checkResolveModule(path.toUri());
        return ResolvedModuleKeys.file(this, path.toUri(), path);
      }
      var dep = (Dependency.RemoteDependency) dependency;
      assert dep.getChecksums() != null;
      var bytes = getPackageResolver().getBytes(packageAssetUri, false, dep.getChecksums());
      return ResolvedModuleKeys.virtual(this, uri, new String(bytes, StandardCharsets.UTF_8), true);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(baseUri);
      var packageAssetUri = PackageAssetUri.create(baseUri);
      var dependency =
          getProjectDepsResolver().getResolvedDependency(packageAssetUri.getPackageUri());
      var path = getLocalPath(dependency);
      if (path != null) {
        securityManager.checkResolveModule(path.toUri());
        return FileResolver.listElements(path);
      }
      var dep = (Dependency.RemoteDependency) dependency;
      assert dep.getChecksums() != null;
      return getPackageResolver().listElements(packageAssetUri, dep.getChecksums());
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(elementUri);
      var packageAssetUri = PackageAssetUri.create(elementUri);
      var dependency =
          getProjectDepsResolver().getResolvedDependency(packageAssetUri.getPackageUri());
      var path = getLocalPath(dependency);
      if (path != null) {
        securityManager.checkResolveModule(path.toUri());
        return FileResolver.hasElement(path);
      }
      var dep = (Dependency.RemoteDependency) dependency;
      assert dep.getChecksums() != null;
      return getPackageResolver().hasElement(packageAssetUri, dep.getChecksums());
    }

    @Override
    public boolean hasFragmentPaths() {
      return true;
    }

    @Override
    protected Map<String, ? extends Dependency> getDependencies()
        throws IOException, SecurityManagerException {
      var packageUri = packageAssetUri.getPackageUri();
      var projectResolver = getProjectDepsResolver();
      if (projectResolver.isLocalPackage(packageUri)) {
        return projectResolver.getLocalPackageDependencies(packageUri);
      }
      var dep =
          (Dependency.RemoteDependency) getProjectDepsResolver().getResolvedDependency(packageUri);
      assert dep.getChecksums() != null;
      var dependencyMetadata =
          getPackageResolver().getDependencyMetadata(packageUri, dep.getChecksums());
      return projectResolver.getResolvedDependenciesForPackage(packageUri, dependencyMetadata);
    }

    @Override
    protected PackageLoadError cannotFindDependency(String name) {
      return new PackageLoadError(
          "cannotFindDependencyInPackage", name, packageAssetUri.getPackageUri().getDisplayName());
    }
  }
}
