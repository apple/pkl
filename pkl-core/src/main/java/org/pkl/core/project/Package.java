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
package org.pkl.core.project;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.pkl.core.Version;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.util.Nullable;

/** Java representation of class {@code pkl.Project#Package} */
@SuppressWarnings("unused")
public record Package(
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
  /**
   * @deprecated As of 0.28.0, replaced by {@link #name()}.
   */
  @Deprecated(forRemoval = true)
  public String getName() {
    return name;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #uri()}.
   */
  @Deprecated(forRemoval = true)
  public PackageUri getUri() {
    return uri;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #version()}.
   */
  @Deprecated(forRemoval = true)
  public Version getVersion() {
    return version;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #packageZipUrl()}.
   */
  @Deprecated(forRemoval = true)
  public URI getPackageZipUrl() {
    return packageZipUrl;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #description()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getDescription() {
    return description;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #authors()}.
   */
  @Deprecated(forRemoval = true)
  public List<String> getAuthors() {
    return authors;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #website()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable URI getWebsite() {
    return website;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #documentation()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable URI getDocumentation() {
    return documentation;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #sourceCode()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable URI getSourceCode() {
    return sourceCode;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #sourceCodeUrlScheme()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getSourceCodeUrlScheme() {
    return sourceCodeUrlScheme;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #licenseText()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getLicenseText() {
    return licenseText;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #license()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getLicense() {
    return license;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #issueTracker()}.
   */
  @Deprecated(forRemoval = true)
  public @Nullable URI getIssueTracker() {
    return issueTracker;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #apiTests()}.
   */
  @Deprecated(forRemoval = true)
  public List<Path> getApiTests() {
    return apiTests;
  }

  /**
   * @deprecated As of 0.28.0, replaced by {@link #exclude()}.
   */
  @Deprecated(forRemoval = true)
  public List<String> getExclude() {
    return exclude;
  }
}
