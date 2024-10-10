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
package org.pkl.core.module;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.externalProcess.ExternalProcessException;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.messaging.MessageTransports;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.messaging.ProtocolException;
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
  public static ModuleKey file(URI uri) {
    return new File(uri);
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

  /** Creates a module key for {@code http:} and {@code https:} uris. */
  public static ModuleKey http(URI url) {
    return new Http(url);
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

  /** Creates a module key for an externally read module. */
  public static ModuleKey external(URI uri, ModuleReaderSpec spec, External.Resolver resolver)
      throws URISyntaxException {
    return new External(uri, spec, resolver);
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
    public String loadSource() {
      return text;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager) {
      return this;
    }

    @Override
    public boolean hasHierarchicalUris() throws IOException, ExternalProcessException {
      return delegate.hasHierarchicalUris();
    }

    @Override
    public boolean isLocal() {
      return delegate.isLocal();
    }

    @Override
    public boolean isGlobbable() throws IOException, ExternalProcessException {
      return delegate.isGlobbable();
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI uri)
        throws IOException, SecurityManagerException, ExternalProcessException {
      return delegate.hasElement(securityManager, uri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException, ExternalProcessException {
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
    public ModuleKey getOriginal() {
      return this;
    }

    @Override
    public String loadSource() throws IOException {
      return IoUtils.readClassPathResourceAsString(
          getClass(), "/org/pkl/core/stdlib/" + uri.getSchemeSpecificPart() + ".pkl");
    }
  }

  private static class File implements ModuleKey {
    final URI uri;

    File(URI uri) {
      this.uri = uri;
    }

    @Override
    public URI getUri() {
      return uri;
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
      // Disallow paths that contain `\\` characters if on Windows
      // (require `/` as the path separator on all OSes)
      var uriPath = uri.getPath();
      if (java.io.File.separatorChar == '\\' && uriPath != null && uriPath.contains("\\")) {
        throw new FileNotFoundException();
      }
      var realPath = IoUtils.pathOf(uri).toRealPath();
      var resolvedUri = realPath.toUri();
      securityManager.checkResolveModule(resolvedUri);
      return ResolvedModuleKeys.file(this, resolvedUri, realPath);
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
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

  private static class Http implements ModuleKey {
    private final URI uri;

    Http(URI uri) {
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
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      var httpClient = VmContext.get(null).getHttpClient();
      var request = HttpRequest.newBuilder(uri).build();
      var response = httpClient.send(request, BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        HttpUtils.checkHasStatusCode200(response);
        securityManager.checkResolveModule(response.uri());
        String text = IoUtils.readString(body);
        // intentionally use uri instead of response.uri()
        return ResolvedModuleKeys.virtual(this, uri, text, true);
      }
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
    public boolean isGlobbable() {
      return false;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveModule(uri);
      var url = IoUtils.toUrl(uri);
      var conn = url.openConnection();
      conn.connect();
      if (conn instanceof JarURLConnection && IoUtils.isWindows()) {
        // On Windows, opening a JarURLConnection prevents the jar file from being deleted, unless
        // cacheing is disabled.
        // See https://bugs.openjdk.org/browse/JDK-8239054
        conn.setUseCaches(false);
      }
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
        // intentionally use uri instead of redirected
        return ResolvedModuleKeys.virtual(this, uri, text, true);
      }
    }
  }

  private abstract static class AbstractPackage implements ModuleKey {
    protected final PackageAssetUri packageAssetUri;

    AbstractPackage(PackageAssetUri packageAssetUri) {
      this.packageAssetUri = packageAssetUri;
    }

    protected abstract Map<String, ? extends Dependency> getDependencies()
        throws IOException, SecurityManagerException;

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean hasFragmentPaths() {
      return true;
    }

    @Override
    public boolean isLocal() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public URI getUri() {
      return packageAssetUri.getUri();
    }

    @Override
    public URI resolveUri(URI baseUri, URI importUri) throws IOException, SecurityManagerException {
      var ssp = importUri.getSchemeSpecificPart();
      if (importUri.isAbsolute() || !ssp.startsWith("@")) {
        return ModuleKey.super.resolveUri(baseUri, importUri);
      }
      var parsed = IoUtils.parseDependencyNotation(ssp);
      var name = parsed.getFirst();
      var path = parsed.getSecond();
      var dependency = getDependencies().get(name);
      if (dependency == null) {
        throw new PackageLoadError(
            "cannotFindDependencyInPackage",
            name,
            packageAssetUri.getPackageUri().getDisplayName());
      }
      return dependency.getPackageUri().toPackageAssetUri(path).getUri();
    }
  }

  /** Represents a module imported via the {@code package} scheme. */
  private static class Package extends AbstractPackage {

    Package(PackageAssetUri packageAssetUri) {
      super(packageAssetUri);
    }

    private PackageResolver getPackageResolver() {
      var packageResolver = VmContext.get(null).getPackageResolver();
      assert packageResolver != null;
      return packageResolver;
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      var uri = packageAssetUri.getUri();
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
    protected Map<String, ? extends Dependency> getDependencies()
        throws IOException, SecurityManagerException {
      return getPackageResolver()
          .getDependencyMetadata(
              packageAssetUri.getPackageUri(), packageAssetUri.getPackageUri().getChecksums())
          .getDependencies();
    }
  }

  /**
   * Represents a module imported via the {@code projectpackage} scheme.
   *
   * <p>The {@code projectpackage} scheme is what project-local dependencies resolve to when
   * imported using dependency notation (for example, {@code import "@foo/bar.pkl"}). This scheme is
   * an internal implementation detail, and we do not expect a module to declare this.
   */
  public static class ProjectPackage extends AbstractPackage {

    ProjectPackage(PackageAssetUri packageAssetUri) {
      super(packageAssetUri);
    }

    private PackageResolver getPackageResolver() {
      var packageResolver = VmContext.get(null).getPackageResolver();
      assert packageResolver != null;
      return packageResolver;
    }

    private ProjectDependenciesManager getProjectDependenciesManager() {
      var projectDepsManager = VmContext.get(null).getProjectDependenciesManager();
      assert projectDepsManager != null;
      return projectDepsManager;
    }

    private @Nullable URI getLocalUri(Dependency dependency) {
      return getLocalUri(dependency, packageAssetUri);
    }

    private @Nullable URI getLocalUri(Dependency dependency, PackageAssetUri assetUri) {
      if (!(dependency instanceof LocalDependency localDependency)) {
        return null;
      }
      return localDependency.resolveAssetUri(
          getProjectDependenciesManager().getProjectBaseUri(), assetUri);
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      var uri = packageAssetUri.getUri();
      securityManager.checkResolveModule(uri);
      var dependency =
          getProjectDependenciesManager().getResolvedDependency(packageAssetUri.getPackageUri());
      var local = getLocalUri(dependency);
      if (local != null) {
        var resolved =
            VmContext.get(null).getModuleResolver().resolve(local).resolve(securityManager);
        return ResolvedModuleKeys.delegated(resolved, this);
      }
      var dep = (Dependency.RemoteDependency) dependency;
      assert dep.getChecksums() != null;
      var bytes = getPackageResolver().getBytes(packageAssetUri, false, dep.getChecksums());
      return ResolvedModuleKeys.virtual(this, uri, new String(bytes, StandardCharsets.UTF_8), true);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException, ExternalProcessException {
      securityManager.checkResolveModule(baseUri);
      var packageAssetUri = PackageAssetUri.create(baseUri);
      var dependency =
          getProjectDependenciesManager().getResolvedDependency(packageAssetUri.getPackageUri());
      var local = getLocalUri(dependency, packageAssetUri);
      if (local != null) {
        var moduleKey = VmContext.get(null).getModuleResolver().resolve(local);
        if (!moduleKey.isGlobbable()) {
          throw new PackageLoadError(
              "cannotResolveInLocalDependencyNotGlobbable", local.getScheme());
        }
        return moduleKey.listElements(securityManager, local);
      }
      var dep = (Dependency.RemoteDependency) dependency;
      assert dep.getChecksums() != null;
      return getPackageResolver().listElements(packageAssetUri, dep.getChecksums());
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException, ExternalProcessException {
      securityManager.checkResolveModule(elementUri);
      var packageAssetUri = PackageAssetUri.create(elementUri);
      var dependency =
          getProjectDependenciesManager().getResolvedDependency(packageAssetUri.getPackageUri());
      var local = getLocalUri(dependency, packageAssetUri);
      if (local != null) {
        var moduleKey = VmContext.get(null).getModuleResolver().resolve(local);
        if (!moduleKey.isGlobbable() && !moduleKey.isLocal()) {
          throw new PackageLoadError(
              "cannotResolveInLocalDependencyNotGlobbableNorLocal", local.getScheme());
        }
        return moduleKey.hasElement(securityManager, local);
      }
      var dep = (Dependency.RemoteDependency) dependency;
      assert dep.getChecksums() != null;
      return getPackageResolver().hasElement(packageAssetUri, dep.getChecksums());
    }

    @Override
    protected Map<String, ? extends Dependency> getDependencies()
        throws IOException, SecurityManagerException {
      var packageUri = packageAssetUri.getPackageUri();
      var projectResolver = getProjectDependenciesManager();
      if (projectResolver.isLocalPackage(packageUri)) {
        return projectResolver.getLocalPackageDependencies(packageUri);
      }
      var dep =
          (Dependency.RemoteDependency)
              getProjectDependenciesManager().getResolvedDependency(packageUri);
      assert dep.getChecksums() != null;
      var dependencyMetadata =
          getPackageResolver().getDependencyMetadata(packageUri, dep.getChecksums());
      return projectResolver.getResolvedDependenciesForPackage(packageUri, dependencyMetadata);
    }
  }

  public static class External implements ModuleKey {
    public static class Resolver {
      private final MessageTransport transport;
      private final long evaluatorId;
      private final Map<URI, Future<String>> readResponses = new ConcurrentHashMap<>();
      private final Map<URI, Future<List<PathElement>>> listResponses = new ConcurrentHashMap<>();

      public Resolver(MessageTransport transport, long evaluatorId) {
        this.transport = transport;
        this.evaluatorId = evaluatorId;
      }

      public List<PathElement> listElements(SecurityManager securityManager, URI uri)
          throws IOException, SecurityManagerException {
        securityManager.checkResolveModule(uri);
        return doListElements(uri);
      }

      public boolean hasElement(SecurityManager securityManager, URI uri)
          throws SecurityManagerException {
        securityManager.checkResolveModule(uri);
        try {
          doReadModule(uri);
          return true;
        } catch (IOException e) {
          return false;
        }
      }

      public String resolveModule(SecurityManager securityManager, URI uri)
          throws IOException, SecurityManagerException {
        securityManager.checkResolveModule(uri);
        return doReadModule(uri);
      }

      private String doReadModule(URI moduleUri) throws IOException {
        return MessageTransports.resolveFuture(
            readResponses.computeIfAbsent(
                moduleUri,
                (uri) -> {
                  var future = new CompletableFuture<String>();
                  var request = new ReadModuleRequest(new Random().nextLong(), evaluatorId, uri);
                  try {
                    transport.send(
                        request,
                        (response) -> {
                          if (response instanceof ReadModuleResponse resp) {
                            if (resp.getError() != null) {
                              future.completeExceptionally(new IOException(resp.getError()));
                            } else if (resp.getContents() != null) {
                              future.complete(resp.getContents());
                            } else {
                              future.complete("");
                            }
                          } else {
                            future.completeExceptionally(
                                new ProtocolException("unexpected response"));
                          }
                        });
                  } catch (ProtocolException | IOException e) {
                    future.completeExceptionally(e);
                  }
                  return future;
                }));
      }

      private List<PathElement> doListElements(URI baseUri) throws IOException {
        return MessageTransports.resolveFuture(
            listResponses.computeIfAbsent(
                baseUri,
                (uri) -> {
                  var future = new CompletableFuture<List<PathElement>>();
                  var request = new ListModulesRequest(new Random().nextLong(), evaluatorId, uri);
                  try {
                    transport.send(
                        request,
                        (response) -> {
                          if (response instanceof ListModulesResponse resp) {
                            if (resp.getError() != null) {
                              future.completeExceptionally(new IOException(resp.getError()));
                            } else {
                              future.complete(
                                  Objects.requireNonNullElseGet(resp.getPathElements(), List::of));
                            }
                          } else {
                            future.completeExceptionally(
                                new ProtocolException("unexpected response"));
                          }
                        });
                  } catch (ProtocolException | IOException e) {
                    future.completeExceptionally(e);
                  }
                  return future;
                }));
      }
    }

    private final URI uri;
    private final ModuleReaderSpec spec;
    private final Resolver resolver;

    public External(URI uri, ModuleReaderSpec spec, Resolver resolver) {
      this.uri = uri;
      this.spec = spec;
      this.resolver = resolver;
    }

    @Override
    public boolean isLocal() {
      return spec.isLocal();
    }

    @Override
    public boolean hasHierarchicalUris() {
      return spec.getHasHierarchicalUris();
    }

    @Override
    public boolean isGlobbable() {
      return spec.isGlobbable();
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      return resolver.listElements(securityManager, baseUri);
    }

    @Override
    public ResolvedModuleKey resolve(SecurityManager securityManager)
        throws IOException, SecurityManagerException {
      var contents = resolver.resolveModule(securityManager, uri);
      return ResolvedModuleKeys.virtual(this, uri, contents, true);
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException {
      return resolver.hasElement(securityManager, elementUri);
    }
  }
}
