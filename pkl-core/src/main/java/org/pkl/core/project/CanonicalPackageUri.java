/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.project;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.pkl.core.PklBugException;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.util.ErrorMessages;

/**
 * The canonical name of a package dependency within a project.
 *
 * <p>Equivalent to the package's base URI, and the major version number, i.e. {@code
 * package://example.com/foo/bar@0}. Does not include a URI's userinfo, query params or fragment
 * segments.
 */
public class CanonicalPackageUri {
  private final URI baseUri;
  private final int majorVersion;

  public static CanonicalPackageUri fromPackageUri(PackageUri packageUri) {
    var uri = packageUri.getUri();
    URI baseUri;
    try {
      baseUri =
          new URI(
              // make sure scheme is always "package"
              "package",
              // userinfo isn't considered part of the package identifier.
              null,
              uri.getHost(),
              uri.getPort(),
              packageUri.getPathWithoutVersion(),
              // query params aren't considered part of the package identifier.
              null,
              null);
    } catch (URISyntaxException e) {
      throw PklBugException.unreachableCode();
    }
    return new CanonicalPackageUri(baseUri, packageUri.getVersion().getMajor());
  }

  public static CanonicalPackageUri of(String uriStr) throws URISyntaxException {
    var versionIdx = uriStr.lastIndexOf('@');
    if (versionIdx == -1) {
      throw new URISyntaxException(
          uriStr, ErrorMessages.create("missingVersionInPackageUri", uriStr));
    }
    int majorVersion;
    try {
      majorVersion = Integer.parseInt(uriStr.substring(versionIdx + 1));
    } catch (NumberFormatException e) {
      throw new URISyntaxException(uriStr, ErrorMessages.create(""));
    }
    var baseUri = new URI(uriStr.substring(0, versionIdx));
    return new CanonicalPackageUri(baseUri, majorVersion);
  }

  public CanonicalPackageUri(URI baseUri, int majorVersion) {
    this.baseUri = baseUri;
    this.majorVersion = majorVersion;
  }

  @SuppressWarnings("unused")
  public int getMajorVersion() {
    return majorVersion;
  }

  @SuppressWarnings("unused")
  public URI getBaseUri() {
    return baseUri;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CanonicalPackageUri that = (CanonicalPackageUri) o;
    return majorVersion == that.majorVersion && baseUri.equals(that.baseUri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseUri, majorVersion);
  }

  @Override
  public String toString() {
    return baseUri + "@" + majorVersion;
  }
}
