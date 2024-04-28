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
package org.pkl.core.packages;

import java.net.URI;
import java.net.URISyntaxException;
import org.pkl.core.PklBugException;
import org.pkl.core.Version;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

public final class PackageUri {
  private final URI uri;
  private final Version version;
  private final String pathWithoutVersion;
  private @Nullable Checksums checksums;

  public static PackageUri create(String baseUri) {
    try {
      return new PackageUri(baseUri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public PackageUri(String baseUri) throws URISyntaxException {
    this(new URI(baseUri));
  }

  public PackageUri(URI uri) throws URISyntaxException {
    if (uri.isOpaque()) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("invalidModuleUriMissingSlash", uri, "package"));
    }
    var scheme = uri.getScheme();
    if (scheme == null || !(scheme.equals("package") || scheme.equals("projectpackage"))) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("invalidSchemeInPackageUri", scheme));
    }
    var authority = uri.getAuthority();
    if (authority == null || authority.isEmpty()) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("missingAuthorityInPackageUri", uri));
    }
    var path = uri.getPath();
    if (path == null || path.isEmpty()) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("missingPathInPackageUri", uri));
    }
    var versionIdx = path.lastIndexOf('@');
    if (versionIdx == -1) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("missingVersionInPackageUri", path));
    }
    this.uri = IoUtils.stripFragment(uri);
    this.pathWithoutVersion = path.substring(0, versionIdx);
    var checksumIdx = path.indexOf("::");
    var versionPart = path.substring(versionIdx + 1);
    if (checksumIdx > versionIdx) {
      var checksumPart = path.substring(checksumIdx + 2);
      versionPart = path.substring(versionIdx + 1, checksumIdx);
      this.checksums = parseChecksumPart(checksumPart);
    }
    try {
      this.version = Version.parse(versionPart);
    } catch (IllegalArgumentException e) {
      throw new URISyntaxException(uri.toString(), e.getMessage());
    }
  }

  public URI getUri() {
    return uri;
  }

  public URI getMetadataRequestUri() {
    if (this.checksums != null) {
      var schemeSpecificPart = uri.getSchemeSpecificPart();
      var checksumIdx = schemeSpecificPart.lastIndexOf("::");
      var effectiveSchemeSpecificPart = schemeSpecificPart.substring(0, checksumIdx);
      return URI.create("https:" + effectiveSchemeSpecificPart);
    }
    return URI.create("https:" + uri.getSchemeSpecificPart());
  }

  public Version getVersion() {
    return version;
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
    PackageUri that = (PackageUri) o;
    return getUri().equals(that.getUri());
  }

  public PackageAssetUri toPackageAssetUri(String path) {
    return new PackageAssetUri(this, path);
  }

  public String getDisplayName() {
    var str = toExternalPackageUri().toString();
    if (checksums != null) {
      var checksumsIdx = str.lastIndexOf("::");
      return str.substring(0, checksumsIdx);
    }
    return str;
  }

  public PackageUri toExternalPackageUri() {
    if (uri.getScheme().equals("package")) {
      return this;
    }
    try {
      return new PackageUri(
          new URI(
              "package",
              uri.getUserInfo(),
              uri.getHost(),
              uri.getPort(),
              uri.getPath(),
              uri.getQuery(),
              uri.getFragment()));
    } catch (URISyntaxException e) {
      throw PklBugException.unreachableCode();
    }
  }

  public PackageUri toProjectPackageUri() {
    if (uri.getScheme().equals("projectpackage")) {
      return this;
    }
    try {
      return new PackageUri(
          new URI(
              "projectpackage",
              uri.getUserInfo(),
              uri.getHost(),
              uri.getPort(),
              uri.getPath(),
              uri.getQuery(),
              uri.getFragment()));
    } catch (URISyntaxException e) {
      throw PklBugException.unreachableCode();
    }
  }

  public String getPathWithoutVersion() {
    return pathWithoutVersion;
  }

  public @Nullable Checksums getChecksums() {
    return checksums;
  }

  private Checksums parseChecksumPart(String checksumPart) throws URISyntaxException {
    var parts = checksumPart.split(":");
    if (parts.length != 2) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("invalidPackageUriChecksum", checksumPart));
    }
    var algorithm = parts[0];
    var checksum = parts[1];
    if (!algorithm.equals("sha256")) {
      throw new URISyntaxException(
          uri.toString(), ErrorMessages.create("unknownChecksumAlgorithm", algorithm));
    }
    return new Checksums(checksum);
  }
}
