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
package org.pkl.core.resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.externalreader.ExternalReaderProcess;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.externalreader.ExternalResourceResolver;
import org.pkl.core.messaging.Messages.*;
import org.pkl.core.module.FileResolver;
import org.pkl.core.module.ModulePathResolver;
import org.pkl.core.module.PathElement;
import org.pkl.core.module.ProjectDependenciesManager;
import org.pkl.core.packages.Dependency;
import org.pkl.core.packages.Dependency.LocalDependency;
import org.pkl.core.packages.PackageAssetUri;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.HttpUtils;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

/** Predefined resource readers for OS environment variables and external properties. */
public final class ResourceReaders {
  private ResourceReaders() {}

  /**
   * A resource reader for OS environment variables. If this resource reader is present, Pkl code
   * can read environment variable {@code FOO_BAR} with {@code read("env:FOO_BAR")}, provided that
   * resource URI {@code env:FOO_BAR} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReader environmentVariable() {
    return EnvironmentVariable.INSTANCE;
  }

  /**
   * A resource reader for external properties. If this resource reader is present, Pkl code can
   * read external property {@code foo.bar} with {@code read("prop:foo.bar")}, provided that
   * resource URI {@code prop:foo.bar} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReader externalProperty() {
    return ExternalProperty.INSTANCE;
  }

  public static ResourceReader file() {
    return FileResource.INSTANCE;
  }

  /**
   * A resource reader for HTTP resources. If this resource reader is present, Pkl code can read
   * HTTP resource {@code http://apple.com/foo/bar.txt} with {@code
   * read("http://apple.com/foo/bar.txt")}, provided that resource URI {@code
   * "http://apple.com/foo/bar.txt"} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReader http() {
    return HttpResource.INSTANCE;
  }

  /**
   * A resource reader for HTTPS resources. If this resource reader is present, Pkl code can read
   * HTTPS resource {@code https://apple.com/foo/bar.txt} with {@code
   * read("https://apple.com/foo/bar.txt")}, provided that resource URI {@code
   * "https://apple.com/foo/bar.txt"} matches an entry in the resource allowlist ({@code
   * --allowed-resources}).
   */
  public static ResourceReader https() {
    return HttpsResource.INSTANCE;
  }

  /**
   * A resource reader for JVM class path resources. If this resource reader is present, Pkl code
   * can read class path resource {@code /foo/bar.txt} with {@code read("modulepath:foo/bar.txt")},
   * provided that resource URI {@code "modulepath:foo/bar.txt"} matches an entry in the resource
   * allowlist ({@code --allowed-resources}).
   */
  public static ResourceReader classPath(ClassLoader classLoader) {
    return new ClassPathResource(classLoader);
  }

  /**
   * A resource reader for Pkl module path ({@code --module-path}) resources. If this resource
   * reader is present, Pkl code can read module path resource {@code /foo/bar.txt} with {@code
   * read("modulepath:foo/bar.txt")}, provided that resource URI {@code "modulepath:foo/bar.txt"}
   * matches an entry in the resource allowlist ({@code --allowed-resources}).
   */
  public static ResourceReader modulePath(ModulePathResolver resolver) {
    return new ModulePathResource(resolver);
  }

  /**
   * A resource reader for {@code package:} resources. If this resource reader is present, Pkl code
   * can read resources from within packages with {@code
   * read("package://example.com/foo@1.0.0#/foo.txt")}, or using dependency notation with {@code
   * read("@foo/foo.txt")} assuming that the Pkl module is within a project that declares a
   * dependency named {@code foo}.
   */
  public static ResourceReader pkg() {
    return PackageResource.INSTANCE;
  }

  public static ResourceReader projectpackage() {
    return ProjectPackageResource.INSTANCE;
  }

  /**
   * Returns resource readers registered as {@link ServiceLoader service providers} of type {@code
   * org.pkl.core.resource.ResourceReader}.
   */
  public static List<ResourceReader> fromServiceProviders() {
    return FromServiceProviders.INSTANCE;
  }

  public static ResourceReader external(
      String scheme, ExternalReaderProcess externalReaderProcess) {
    return new External(scheme, externalReaderProcess, 0);
  }

  public static ResourceReader external(
      String scheme, ExternalReaderProcess externalReaderProcess, long evaluatorId) {
    return new External(scheme, externalReaderProcess, evaluatorId);
  }

  public static ResourceReader messageTransport(
      ResourceReaderSpec spec, ExternalResourceResolver resolver) {
    return new MessageTransport(spec, resolver);
  }

  private static final class EnvironmentVariable implements ResourceReader {
    static final ResourceReader INSTANCE = new EnvironmentVariable();

    @Override
    public String getUriScheme() {
      return "env";
    }

    @Override
    public Optional<Object> read(URI uri) {
      assert uri.getScheme().equals("env");

      var context = VmContext.get(null);
      var value = context.getEnvironmentVariables().get(uri.getSchemeSpecificPart());
      return Optional.ofNullable(value);
    }

    @Override
    public boolean hasHierarchicalUris() {
      return false;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws SecurityManagerException {
      securityManager.checkResolveResource(baseUri);
      var context = VmContext.get(null);
      var ret = new ArrayList<PathElement>();
      for (var envVarName : context.getEnvironmentVariables().keySet()) {
        ret.add(PathElement.opaque(envVarName));
      }
      return ret;
    }
  }

  // to clearly separate the capability to read properties
  // from their definition/storage, this class doesn't store
  // properties but looks them up from the language context
  private static final class ExternalProperty implements ResourceReader {
    static final ResourceReader INSTANCE = new ExternalProperty();

    @Override
    public String getUriScheme() {
      return "prop";
    }

    @Override
    public Optional<Object> read(URI uri) {
      assert uri.getScheme().equals("prop");

      var context = VmContext.get(null);
      var value = context.getExternalProperties().get(uri.getSchemeSpecificPart());
      return Optional.ofNullable(value);
    }

    @Override
    public boolean hasHierarchicalUris() {
      return false;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws SecurityManagerException {
      securityManager.checkResolveResource(baseUri);
      var context = VmContext.get(null);
      var ret = new ArrayList<PathElement>();
      for (var propName : context.getExternalProperties().keySet()) {
        ret.add(PathElement.opaque(propName));
      }
      return ret;
    }
  }

  private static final class FileResource extends UrlResource {
    static final ResourceReader INSTANCE = new FileResource();

    @Override
    public String getUriScheme() {
      return "file";
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws SecurityManagerException {
      securityManager.checkResolveResource(elementUri);
      return FileResolver.hasElement(elementUri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveResource(baseUri);
      return FileResolver.listElements(baseUri);
    }
  }

  private static final class HttpResource extends UrlResource {
    static final ResourceReader INSTANCE = new HttpResource();

    @Override
    public String getUriScheme() {
      return "http";
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }
  }

  private static final class HttpsResource extends UrlResource {
    static final ResourceReader INSTANCE = new HttpsResource();

    @Override
    public String getUriScheme() {
      return "https";
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return false;
    }
  }

  private abstract static class UrlResource implements ResourceReader {
    @Override
    public Optional<Object> read(URI uri) throws IOException {
      if (HttpUtils.isHttpUrl(uri)) {
        var httpClient = VmContext.get(null).getHttpClient();
        var request = HttpRequest.newBuilder(uri).build();
        var response = httpClient.send(request, BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) return Optional.empty();
        HttpUtils.checkHasStatusCode200(response);
        return Optional.of(new Resource(uri, response.body()));
      }

      try {
        var content = IoUtils.readBytes(uri);
        return Optional.of(new Resource(uri, content));
      } catch (FileNotFoundException e) {
        return Optional.empty();
      }
    }
  }

  private static final class ClassPathResource implements ResourceReader {
    private final ClassLoader classLoader;

    public ClassPathResource(ClassLoader classLoader) {
      this.classLoader = classLoader;
    }

    @Override
    public String getUriScheme() {
      return "modulepath";
    }

    @Override
    public Optional<Object> read(URI uri) throws URISyntaxException {
      assert uri.getScheme().equals("modulepath");
      if (uri.getPath() == null) {
        throw new URISyntaxException(
            uri.toString(),
            ErrorMessages.create("invalidModuleUriMissingSlash", uri, "modulepath"));
      }
      var path = getResourcePath(uri);
      try (var stream = classLoader.getResourceAsStream(path)) {
        return stream == null
            ? Optional.empty()
            : Optional.of(new Resource(uri, stream.readAllBytes()));
      } catch (IOException e) {
        return Optional.empty();
      }
    }

    private static String getResourcePath(URI uri) {
      var path = uri.getPath();
      assert path.charAt(0) == '/';
      return path.substring(1);
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
    public boolean hasElement(SecurityManager manager, URI uri) throws SecurityManagerException {
      manager.checkResolveResource(uri);
      var uriPath = uri.getPath();
      assert uriPath.charAt(0) == '/';
      return classLoader.getResource(uriPath.substring(1)) != null;
    }
  }

  private static final class ModulePathResource implements ResourceReader {
    private final ModulePathResolver resolver;

    public ModulePathResource(ModulePathResolver resolver) {
      this.resolver = resolver;
    }

    @Override
    public String getUriScheme() {
      return "modulepath";
    }

    @Override
    public Optional<Object> read(URI uri) throws IOException, URISyntaxException {
      assert uri.getScheme().equals("modulepath");

      if (uri.getPath() == null) {
        throw new URISyntaxException(
            uri.toString(),
            ErrorMessages.create("invalidModuleUriMissingSlash", uri, "modulepath"));
      }

      try {
        var path = resolver.resolve(uri);
        var content = Files.readAllBytes(path);
        return Optional.of(new Resource(uri, content));
      } catch (FileNotFoundException e) {
        return Optional.empty();
      }
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
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws SecurityManagerException {
      securityManager.checkResolveResource(elementUri);
      return resolver.hasElement(elementUri);
    }
  }

  /** Handler for {@code package} schemes */
  private static final class PackageResource implements ResourceReader {
    static final PackageResource INSTANCE = new PackageResource();

    @Override
    public String getUriScheme() {
      return "package";
    }

    @Override
    public Optional<Object> read(URI uri)
        throws IOException, URISyntaxException, SecurityManagerException {
      var assetUri = new PackageAssetUri(uri);
      var bytes = getPackageResolver().getBytes(assetUri, true, null);
      return Optional.of(new Resource(uri, bytes));
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public boolean hasFragmentPaths() {
      return true;
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveResource(baseUri);
      var packageAssetUri = PackageAssetUri.create(baseUri);
      return getPackageResolver()
          .listElements(packageAssetUri, packageAssetUri.getPackageUri().getChecksums());
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException {
      securityManager.checkResolveResource(elementUri);
      var packageAssetUri = PackageAssetUri.create(elementUri);
      return getPackageResolver()
          .hasElement(packageAssetUri, packageAssetUri.getPackageUri().getChecksums());
    }

    private PackageResolver getPackageResolver() {
      var packageResolver = VmContext.get(null).getPackageResolver();
      assert packageResolver != null;
      return packageResolver;
    }
  }

  private static final class ProjectPackageResource implements ResourceReader {
    static final ProjectPackageResource INSTANCE = new ProjectPackageResource();

    @Override
    public String getUriScheme() {
      return "projectpackage";
    }

    @Override
    public Optional<Object> read(URI uri)
        throws IOException, URISyntaxException, SecurityManagerException {
      var assetUri = new PackageAssetUri(uri);
      var dependency = getProjectDepsResolver().getResolvedDependency(assetUri.getPackageUri());
      var local = getLocalUri(dependency, assetUri);
      if (local != null) {
        var resourceManager = VmContext.get(null).getResourceManager();
        var securityManager = VmContext.get(null).getSecurityManager();
        securityManager.checkReadResource(local);
        var reader = resourceManager.getResourceReader(local);
        if (reader == null) {
          throw new VmExceptionBuilder()
              .evalError("noResourceReaderRegistered", uri.getScheme())
              .build();
        }
        return resourceManager.doRead(reader, local, null);
      }
      var remoteDep = (Dependency.RemoteDependency) dependency;
      var bytes = getPackageResolver().getBytes(assetUri, true, remoteDep.getChecksums());
      return Optional.of(new Resource(uri, bytes));
    }

    @Override
    public boolean hasHierarchicalUris() {
      return true;
    }

    @Override
    public boolean isGlobbable() {
      return true;
    }

    @Override
    public boolean hasFragmentPaths() {
      return true;
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException, ExternalReaderProcessException {
      securityManager.checkResolveResource(baseUri);
      var packageAssetUri = PackageAssetUri.create(baseUri);
      var dependency =
          getProjectDepsResolver().getResolvedDependency(packageAssetUri.getPackageUri());
      var local = getLocalUri(dependency, packageAssetUri);
      if (local != null) {
        var reader = VmContext.get(null).getResourceManager().getResourceReader(local);
        if (reader == null) {
          throw new VmExceptionBuilder()
              .evalError("noResourceReaderRegistered", local.getScheme())
              .build();
        }
        return reader.listElements(securityManager, local);
      }
      var remoteDep = (Dependency.RemoteDependency) dependency;
      return getPackageResolver()
          .listElements(PackageAssetUri.create(baseUri), remoteDep.getChecksums());
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException, ExternalReaderProcessException {
      securityManager.checkResolveResource(elementUri);
      var packageAssetUri = PackageAssetUri.create(elementUri);
      var dependency =
          getProjectDepsResolver().getResolvedDependency(packageAssetUri.getPackageUri());
      var local = getLocalUri(dependency, packageAssetUri);
      if (local != null) {
        var reader = VmContext.get(null).getResourceManager().getResourceReader(local);
        if (reader == null) {
          throw new VmExceptionBuilder()
              .evalError("noResourceReaderRegistered", local.getScheme())
              .build();
        }
        return reader.hasElement(securityManager, local);
      }
      var remoteDep = (Dependency.RemoteDependency) dependency;
      return getPackageResolver()
          .hasElement(PackageAssetUri.create(elementUri), remoteDep.getChecksums());
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

    private @Nullable URI getLocalUri(Dependency dependency, PackageAssetUri packageAssetUri) {
      if (!(dependency instanceof LocalDependency localDependency)) {
        return null;
      }
      return localDependency.resolveAssetUri(
          getProjectDepsResolver().getProjectBaseUri(), packageAssetUri);
    }
  }

  private static class FromServiceProviders {

    private static final List<ResourceReader> INSTANCE;

    static {
      var loader = IoUtils.createServiceLoader(ResourceReader.class);
      var readers = new ArrayList<ResourceReader>();
      loader.forEach(readers::add);
      INSTANCE = Collections.unmodifiableList(readers);
    }
  }

  private static final class External implements ResourceReader {
    private final String scheme;
    private final ExternalReaderProcess process;
    private final long evaluatorId;
    private MessageTransport underlying;

    public External(String scheme, ExternalReaderProcess process, long evaluatorId) {
      this.scheme = scheme;
      this.process = process;
      this.evaluatorId = evaluatorId;
    }

    private MessageTransport getUnderlyingReader()
        throws ExternalReaderProcessException, IOException {
      if (underlying != null) {
        return underlying;
      }

      var spec = process.getResourceReaderSpec(scheme);
      if (spec == null) {
        throw new ExternalReaderProcessException(
            ErrorMessages.create("externalReaderDoesNotSupportScheme", "resource", scheme));
      }
      underlying =
          new MessageTransport(
              spec, new ExternalResourceResolver(process.getTransport(), evaluatorId));
      return underlying;
    }

    @Override
    public String getUriScheme() {
      return scheme;
    }

    @Override
    public boolean hasHierarchicalUris() throws ExternalReaderProcessException, IOException {
      return getUnderlyingReader().hasHierarchicalUris();
    }

    @Override
    public boolean isGlobbable() throws ExternalReaderProcessException, IOException {
      return getUnderlyingReader().isGlobbable();
    }

    @Override
    public Optional<Object> read(URI uri) throws IOException, ExternalReaderProcessException {
      return getUnderlyingReader().read(uri);
    }

    @Override
    public boolean hasElement(SecurityManager securityManager, URI elementUri)
        throws IOException, SecurityManagerException, ExternalReaderProcessException {
      return getUnderlyingReader().hasElement(securityManager, elementUri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException, ExternalReaderProcessException {
      return getUnderlyingReader().listElements(securityManager, baseUri);
    }

    @Override
    public void close() {
      process.close();
    }
  }

  private static final class MessageTransport implements ResourceReader {
    private final ResourceReaderSpec readerSpec;
    private final ExternalResourceResolver resolver;

    public MessageTransport(ResourceReaderSpec readerSpec, ExternalResourceResolver resolver) {
      this.readerSpec = readerSpec;
      this.resolver = resolver;
    }

    @Override
    public boolean hasHierarchicalUris() {
      return readerSpec.hasHierarchicalUris();
    }

    @Override
    public boolean isGlobbable() {
      return readerSpec.isGlobbable();
    }

    @Override
    public String getUriScheme() {
      return readerSpec.scheme();
    }

    @Override
    public Optional<Object> read(URI uri) throws IOException {
      return resolver.read(uri);
    }

    @Override
    public boolean hasElement(org.pkl.core.SecurityManager securityManager, URI elementUri)
        throws SecurityManagerException {
      return resolver.hasElement(securityManager, elementUri);
    }

    @Override
    public List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
        throws IOException, SecurityManagerException {
      return resolver.listElements(securityManager, baseUri);
    }
  }
}
