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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.PathElement;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.resource.Resource;
import org.pkl.core.resource.ResourceReader;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.GlobResolver.ResolvedGlobElement;
import org.pkl.core.util.Nullable;

public final class ResourceManager {
  private final Map<String, ResourceReader> resourceReaders = new HashMap<>();
  private final SecurityManager securityManager;
  private final VmObjectFactory<Resource> resourceFactory;

  // cache resources indefinitely to make resource reads deterministic
  private final Map<URI, Optional<Object>> resources = new HashMap<>();

  public ResourceManager(SecurityManager securityManager, Collection<ResourceReader> readers) {
    this.securityManager = securityManager;

    for (var reader : readers) {
      resourceReaders.put(reader.getUriScheme(), reader);
    }

    resourceFactory =
        new VmObjectFactory<Resource>(BaseModule::getResourceClass)
            .addProperty("uri", resource -> resource.getUri().toString())
            .addProperty("text", Resource::getText)
            .addProperty("base64", Resource::getBase64);
  }

  @TruffleBoundary
  public ResourceReader getReader(URI resourceUri, Node readNode) {
    var reader = resourceReaders.get(resourceUri.getScheme());
    if (reader == null) {
      throw new VmExceptionBuilder()
          .withLocation(readNode)
          .evalError("noResourceReaderRegistered", resourceUri.getScheme())
          .build();
    }
    return reader;
  }

  @TruffleBoundary
  public Optional<Object> read(URI resourceUri, @Nullable Node readNode) {
    return resources.computeIfAbsent(
        resourceUri.normalize(),
        uri -> {
          try {
            securityManager.checkReadResource(uri);
          } catch (SecurityManagerException e) {
            throw new VmExceptionBuilder().withCause(e).withOptionalLocation(readNode).build();
          }

          var reader = resourceReaders.get(uri.getScheme());
          if (reader == null) {
            throw new VmExceptionBuilder()
                .withOptionalLocation(readNode)
                .evalError("noResourceReaderRegistered", resourceUri.getScheme())
                .build();
          }

          Optional<Object> resource;
          try {
            resource = reader.read(uri);
          } catch (IOException e) {
            throw new VmExceptionBuilder()
                .evalError("ioErrorReadingResource", uri)
                .withCause(e)
                .withOptionalLocation(readNode)
                .build();
          } catch (URISyntaxException e) {
            throw new VmExceptionBuilder()
                .evalError("invalidResourceUri", resourceUri)
                .withHint(e.getReason())
                .withOptionalLocation(readNode)
                .build();
          } catch (SecurityManagerException | PackageLoadError | HttpClientInitException e) {
            throw new VmExceptionBuilder().withCause(e).withOptionalLocation(readNode).build();
          }
          if (resource.isEmpty()) return resource;

          var res = resource.get();
          if (res instanceof String) return resource;

          if (res instanceof Resource r) {
            return Optional.of(resourceFactory.create(r));
          }

          throw new VmExceptionBuilder()
              .evalError("unsupportedResourceType", reader.getClass().getName(), res.getClass())
              .withOptionalLocation(readNode)
              .build();
        });
  }

  /**
   * Used by ResourceReaders.ProjectPackageResource to resolve resources from projects that may not
   * be on the local filesystem
   */
  public List<PathElement> listElements(URI baseUri) throws IOException, SecurityManagerException {
    var reader = resourceReaders.get(baseUri.getScheme());
    if (reader == null) {
      throw new VmExceptionBuilder()
          .evalError("noResourceReaderRegistered", baseUri.getScheme())
          .build();
    }

    return reader.listElements(securityManager, baseUri);
  }

  /**
   * Used by ResourceReaders.ProjectPackageResource to resolve resources from projects that may not
   * be on the local filesystem
   */
  public boolean hasElement(URI elementUri) throws IOException, SecurityManagerException {
    var reader = resourceReaders.get(elementUri.getScheme());
    if (reader == null) {
      throw new VmExceptionBuilder()
          .evalError("noResourceReaderRegistered", elementUri.getScheme())
          .build();
    }

    return reader.hasElement(securityManager, elementUri);
  }
}
