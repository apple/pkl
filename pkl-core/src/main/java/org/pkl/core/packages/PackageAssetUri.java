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
package org.pkl.core.packages;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.pkl.core.Version;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.IoUtils;

/**
 * The canonical URI of an asset within a package, i.e., a package URI with a fragment path. For
 * example, {@code package://example.com/my/package@1.0.0#/my/module.pkl}
 */
public final class PackageAssetUri {
  private final URI uri;
  private final PackageUri packageUri;
  private final String assetPath;

  public static PackageAssetUri create(URI uri) {
    try {
      return new PackageAssetUri(uri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  public PackageAssetUri(PackageUri packageUri, String assetPath) {
    this.uri = packageUri.getUri().resolve("#" + assetPath);
    this.packageUri = packageUri;
    this.assetPath = assetPath;
  }

  public PackageAssetUri(String uri) throws URISyntaxException {
    this(new URI(uri));
  }

  public PackageAssetUri(URI uri) throws URISyntaxException {
    this.uri = uri;
    this.packageUri = new PackageUri(uri);
    var fragment = uri.getFragment();
    if (fragment == null) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("invalidUriMissingFragment", uri));
    }
    if (!fragment.startsWith("/")) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("cannotHaveRelativeFragment", fragment, uri));
    }
    this.assetPath = fragment;
  }

  public URI getUri() {
    return uri;
  }

  public PackageUri getPackageUri() {
    return packageUri;
  }

  public String getAssetPath() {
    return assetPath;
  }

  public Version getVersion() {
    return packageUri.getVersion();
  }

  @Override
  public String toString() {
    return uri.toString();
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PackageAssetUri that = (PackageAssetUri) o;
    return getUri().equals(that.getUri());
  }

  public PackageAssetUri resolve(String path) {
    var resolvedPath = IoUtils.toNormalizedPathString(Path.of(assetPath).resolve(path));
    return new PackageAssetUri(packageUri, resolvedPath);
  }
}
