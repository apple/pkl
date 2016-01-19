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
package org.pkl.core.project;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.pkl.core.Version;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.util.Nullable;

/** Java representation of class {@code pkl.Project#Package} */
@SuppressWarnings("unused")
public final class Package {

  private final String name;
  private final PackageUri uri;
  private final Version version;
  private final URI packageZipUrl;
  private final @Nullable String description;
  private final List<String> authors;
  private final @Nullable URI website;
  private final @Nullable URI documentation;
  private final @Nullable URI sourceCode;
  private final @Nullable String sourceCodeUrlScheme;
  private final @Nullable String license;
  private final @Nullable String licenseText;
  private final @Nullable URI issueTracker;
  private final List<Path> apiTests;
  private final List<String> exclude;

  public Package(
      String name,
      PackageUri uri,
      Version version,
      URI packageZipUrl,
      @Nullable String description,
      List<String> authors,
      @Nullable URI website,
      @Nullable URI documentation,
      @Nullable URI sourceCode,
      @Nullable String sourceCodeUrlScheme,
      @Nullable String license,
      @Nullable String licenseText,
      @Nullable URI issueTracker,
      List<Path> apiTests,
      List<String> exclude) {
    this.name = name;
    this.uri = uri;
    this.version = version;
    this.packageZipUrl = packageZipUrl;
    this.description = description;
    this.authors = authors;
    this.website = website;
    this.documentation = documentation;
    this.sourceCode = sourceCode;
    this.sourceCodeUrlScheme = sourceCodeUrlScheme;
    this.license = license;
    this.licenseText = licenseText;
    this.issueTracker = issueTracker;
    this.apiTests = apiTests;
    this.exclude = exclude;
  }

  public String getName() {
    return name;
  }

  public PackageUri getUri() {
    return uri;
  }

  public Version getVersion() {
    return version;
  }

  public URI getPackageZipUrl() {
    return packageZipUrl;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public List<String> getAuthors() {
    return authors;
  }

  public @Nullable URI getWebsite() {
    return website;
  }

  public @Nullable URI getDocumentation() {
    return documentation;
  }

  public @Nullable URI getSourceCode() {
    return sourceCode;
  }

  public @Nullable String getSourceCodeUrlScheme() {
    return sourceCodeUrlScheme;
  }

  public @Nullable String getLicenseText() {
    return licenseText;
  }

  public @Nullable String getLicense() {
    return license;
  }

  public @Nullable URI getIssueTracker() {
    return issueTracker;
  }

  public List<Path> getApiTests() {
    return apiTests;
  }

  public List<String> getExclude() {
    return exclude;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Package aPackage = (Package) o;
    return name.equals(aPackage.name)
        && uri.equals(aPackage.uri)
        && version.equals(aPackage.version)
        && Objects.equals(description, aPackage.description)
        && authors.equals(aPackage.authors)
        && Objects.equals(website, aPackage.website)
        && Objects.equals(documentation, aPackage.documentation)
        && Objects.equals(sourceCode, aPackage.sourceCode)
        && Objects.equals(sourceCodeUrlScheme, aPackage.sourceCodeUrlScheme)
        && Objects.equals(license, aPackage.license)
        && Objects.equals(licenseText, aPackage.licenseText)
        && Objects.equals(issueTracker, aPackage.issueTracker)
        && Objects.equals(apiTests, aPackage.apiTests)
        && exclude.equals(aPackage.exclude);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        uri,
        version,
        description,
        authors,
        website,
        documentation,
        sourceCode,
        sourceCodeUrlScheme,
        license,
        licenseText,
        issueTracker,
        apiTests,
        exclude);
  }

  @Override
  public String toString() {
    return "Package{"
        + "name="
        + name
        + ", uri="
        + uri
        + ", version='"
        + version
        + '\''
        + ", description='"
        + description
        + '\''
        + ", authors="
        + authors
        + ", website='"
        + website
        + ", documentation='"
        + documentation
        + '\''
        + ", sourceCode='"
        + sourceCode
        + '\''
        + ", sourceCodeUrlScheme='"
        + sourceCodeUrlScheme
        + '\''
        + ", license='"
        + license
        + '\''
        + ", licenseText='"
        + licenseText
        + '\''
        + ", issueTracker='"
        + issueTracker
        + '\''
        + ", apiTests='"
        + apiTests
        + '\''
        + ", exclude='"
        + exclude
        + '\''
        + '}';
  }
}
