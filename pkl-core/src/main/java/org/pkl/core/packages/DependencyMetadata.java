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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.pkl.core.Version;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.json.Json;
import org.pkl.core.util.json.Json.FormatException;
import org.pkl.core.util.json.Json.JsArray;
import org.pkl.core.util.json.Json.JsObject;
import org.pkl.core.util.json.Json.JsonParseException;
import org.pkl.core.util.json.JsonWriter;

/**
 * Java representation of a package's dependency metadata.
 *
 * <p>Sample metadata:
 *
 * <pre>
 *   <code>
 *     {
 *       "name": "my-proj-name",
 *       "packageUri": "package://example.com/my-proj-name@0.5.3",
 *       "version": "0.5.3",
 *       "packageZipChecksums": {
 *         "sha256": "abc123",
 *       }
 *       "packageZipUrl": "https://example.com/foo/bar@0.5.3.zip",
 *       "sourceCodeUrlScheme": "https://github.com/foo/bar/blob/v0.5.3/%{path}#L%{line}-L%{endLine}",
 *       "documentation": "https://my/docs",
 *       "description": "The description for my package",
 *       "issueTracker": "https://example.com/my/issues",
 *       "sourceCode": "https://github.com/foo/bar",
 *       "license": "Apache-2",
 *       "dependencies": {
 *         "foo": {
 *           "uri": "package://example.com/foo@0.5.3",
 *           "checksums": {
 *             "sha256": "abc123"
 *           }
 *         }
 *       }
 *     }
 *   </code>
 * </pre>
 */
@SuppressWarnings({"JavadocLinkAsPlainText", "unused"})
// incorrectly thinks link within sample metadata is a JavaDoc link
public class DependencyMetadata {

  public static DependencyMetadata parse(String input) throws JsonParseException {
    var parsed = Json.parseObject(input);
    var name = parsed.getString("name");
    var packageUri = parsed.get("packageUri", (it) -> new PackageUri((String) it));
    var version = parsed.getVersion("version");
    var packageZipUrl = parsed.getURI("packageZipUrl");
    var packageZipChecksums = parsed.get("packageZipChecksums", DependencyMetadata::parseChecksums);
    var dependencies = parsed.get("dependencies", DependencyMetadata::parseDependencies);
    var sourceCodeUrlScheme = parsed.getStringOrNull("sourceCodeUrlScheme");
    var sourceCode = parsed.getURIOrNull("sourceCode");
    var documentation = parsed.getURIOrNull("documentation");
    var license = parsed.getStringOrNull("license");
    var licenseText = parsed.getStringOrNull("licenseText");
    var authors = parsed.getNullable("authors", DependencyMetadata::parseAuthors);
    var issueTracker = parsed.getURIOrNull("issueTracker");
    var description = parsed.getStringOrNull("description");
    return new DependencyMetadata(
        name,
        packageUri,
        version,
        packageZipUrl,
        packageZipChecksums,
        dependencies,
        sourceCodeUrlScheme,
        sourceCode,
        documentation,
        license,
        licenseText,
        authors,
        issueTracker,
        description);
  }

  private static Map<String, RemoteDependency> parseDependencies(Object deps)
      throws JsonParseException {
    if (!(deps instanceof JsObject)) {
      throw new FormatException("object", deps.getClass());
    }
    var dependencies = (JsObject) deps;
    var ret = new HashMap<String, RemoteDependency>(dependencies.size());
    for (var key : dependencies.keySet()) {
      var remoteDependency =
          dependencies.get(
              key,
              (dep) -> {
                if (!(dep instanceof JsObject)) {
                  throw new FormatException("object", dep.getClass());
                }
                var obj = (JsObject) dep;
                var checksums = obj.get("checksums", DependencyMetadata::parseChecksums);
                var packageUri = obj.get("uri", PackageUtils::parsePackageUriWithoutChecksums);
                return new RemoteDependency(packageUri, checksums);
              });
      ret.put(key, remoteDependency);
    }
    return ret;
  }

  public static Checksums parseChecksums(Object obj) throws JsonParseException {
    if (!(obj instanceof JsObject)) {
      throw new FormatException("object", obj.getClass());
    }
    var jsObj = (JsObject) obj;
    var sha256 = jsObj.getString("sha256");
    return new Checksums(sha256);
  }

  public static List<String> parseAuthors(Object obj) throws JsonParseException {
    if (!(obj instanceof JsArray)) {
      throw new FormatException("array", obj.getClass());
    }
    var arr = (JsArray) obj;
    var ret = new ArrayList<String>(arr.size());
    for (var elem : arr) {
      if (!(elem instanceof String)) {
        throw new FormatException("string", elem.getClass());
      }
      ret.add((String) elem);
    }
    return ret;
  }

  private final String name;
  private final PackageUri packageUri;
  private final Version version;
  private final URI packageZipUrl;
  private final Checksums packageZipChecksums;
  private final Map<String, RemoteDependency> dependencies;
  private final @Nullable String sourceCodeUrlScheme;
  private final @Nullable URI sourceCode;
  private final @Nullable URI documentation;
  private final @Nullable String license;
  private final @Nullable String licenseText;
  private final @Nullable List<String> authors;
  private final @Nullable URI issueTracker;
  private final @Nullable String description;

  public DependencyMetadata(
      String name,
      PackageUri packageUri,
      Version version,
      URI packageZipUrl,
      Checksums packageZipChecksums,
      Map<String, RemoteDependency> dependencies,
      @Nullable String sourceCodeUrlScheme,
      @Nullable URI sourceCode,
      @Nullable URI documentation,
      @Nullable String license,
      @Nullable String licenseText,
      @Nullable List<String> authors,
      @Nullable URI issueTracker,
      @Nullable String description) {
    this.name = name;
    this.packageUri = packageUri;
    this.version = version;
    this.packageZipUrl = packageZipUrl;
    this.packageZipChecksums = packageZipChecksums;
    this.dependencies = dependencies;
    this.sourceCodeUrlScheme = sourceCodeUrlScheme;
    this.sourceCode = sourceCode;
    this.documentation = documentation;
    this.license = license;
    this.licenseText = licenseText;
    this.authors = authors;
    this.issueTracker = issueTracker;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public Version getVersion() {
    return version;
  }

  public URI getPackageZipUrl() {
    return packageZipUrl;
  }

  public Checksums getPackageZipChecksums() {
    return packageZipChecksums;
  }

  public Map<String, RemoteDependency> getDependencies() {
    return dependencies;
  }

  public @Nullable String getSourceCodeUrlScheme() {
    return sourceCodeUrlScheme;
  }

  public @Nullable URI getSourceCode() {
    return sourceCode;
  }

  public @Nullable URI getDocumentation() {
    return documentation;
  }

  public @Nullable String getLicense() {
    return license;
  }

  public @Nullable String getLicenseText() {
    return licenseText;
  }

  public @Nullable List<String> getAuthors() {
    return authors;
  }

  @Nullable
  public URI getIssueTracker() {
    return issueTracker;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  /** Serializes project dependencies to JSON, and writes it to the provided output stream. */
  public void writeTo(OutputStream out) throws IOException {
    new DependencyMetadataWriter(out, this).write();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DependencyMetadata that = (DependencyMetadata) o;
    return name.equals(that.name)
        && packageUri.equals(that.packageUri)
        && version.equals(that.version)
        && packageZipUrl.equals(that.packageZipUrl)
        && packageZipChecksums.equals(that.packageZipChecksums)
        && dependencies.equals(that.dependencies)
        && Objects.equals(sourceCodeUrlScheme, that.sourceCodeUrlScheme)
        && Objects.equals(sourceCode, that.sourceCode)
        && Objects.equals(documentation, that.documentation)
        && Objects.equals(license, that.license)
        && Objects.equals(licenseText, that.licenseText)
        && Objects.equals(authors, that.authors)
        && Objects.equals(issueTracker, that.issueTracker)
        && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        packageUri,
        version,
        packageZipUrl,
        packageZipChecksums,
        dependencies,
        sourceCodeUrlScheme,
        sourceCode,
        documentation,
        license,
        licenseText,
        authors,
        issueTracker,
        description);
  }

  @Override
  public String toString() {
    return "DependencyMetadata{"
        + "name='"
        + name
        + '\''
        + "packageUri='"
        + packageUri
        + '\''
        + ", version="
        + version
        + ", packageZipUrl="
        + packageZipUrl
        + ", packageZipChecksums="
        + packageZipChecksums
        + ", dependencies="
        + dependencies
        + ", sourceCodeUrlScheme='"
        + sourceCodeUrlScheme
        + '\''
        + ", sourceCode='"
        + sourceCode
        + '\''
        + ", documentation='"
        + documentation
        + '\''
        + ", license='"
        + license
        + '\''
        + ", licenseText='"
        + licenseText
        + '\''
        + ", authors="
        + authors
        + '\''
        + ", issueTracker="
        + issueTracker
        + '\''
        + ", description="
        + description
        + '}';
  }

  private static final class DependencyMetadataWriter {

    private final JsonWriter jsonWriter;
    private final DependencyMetadata dependencyMetadata;

    private DependencyMetadataWriter(
        OutputStream outputStream, DependencyMetadata dependencyMetadata) {
      jsonWriter = new JsonWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
      jsonWriter.setIndent("  ");
      this.dependencyMetadata = dependencyMetadata;
    }

    private void writeChecksums() throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("sha256").value(dependencyMetadata.packageZipChecksums.getSha256());
      jsonWriter.endObject();
    }

    private void writeDependencies() throws IOException {
      jsonWriter.beginObject();
      for (var entry : dependencyMetadata.getDependencies().entrySet()) {
        jsonWriter.name(entry.getKey());
        jsonWriter.beginObject();
        jsonWriter.name("uri").value(entry.getValue().getPackageUri().toString());
        var checksums = entry.getValue().getChecksums();
        if (checksums != null) {
          jsonWriter.name("checksums");
          jsonWriter.beginObject();
          jsonWriter.name("sha256").value(entry.getValue().getChecksums().getSha256());
          jsonWriter.endObject();
        }
        jsonWriter.endObject();
      }
      jsonWriter.endObject();
    }

    private void writeAuthors() throws IOException {
      var authors = dependencyMetadata.authors;
      assert authors != null;
      jsonWriter.beginArray();
      for (var author : authors) {
        jsonWriter.value(author);
      }
      jsonWriter.endArray();
    }

    private void write() throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("name").value(dependencyMetadata.name);
      jsonWriter.name("packageUri").value(dependencyMetadata.packageUri.toString());
      jsonWriter.name("version").value(dependencyMetadata.version.toString());
      jsonWriter.name("packageZipUrl").value(dependencyMetadata.packageZipUrl.toString());
      jsonWriter.name("packageZipChecksums");
      writeChecksums();
      jsonWriter.name("dependencies");
      writeDependencies();
      if (dependencyMetadata.sourceCodeUrlScheme != null) {
        jsonWriter.name("sourceCodeUrlScheme").value(dependencyMetadata.sourceCodeUrlScheme);
      }
      if (dependencyMetadata.sourceCode != null) {
        jsonWriter.name("sourceCode").value(dependencyMetadata.sourceCode.toString());
      }
      if (dependencyMetadata.documentation != null) {
        jsonWriter.name("documentation").value(dependencyMetadata.documentation.toString());
      }
      if (dependencyMetadata.license != null) {
        jsonWriter.name("license").value(dependencyMetadata.license);
      }
      if (dependencyMetadata.licenseText != null) {
        jsonWriter.name("licenseText").value(dependencyMetadata.licenseText);
      }
      if (dependencyMetadata.authors != null) {
        jsonWriter.name("authors");
        writeAuthors();
      }
      if (dependencyMetadata.issueTracker != null) {
        jsonWriter.name("issueTracker").value(dependencyMetadata.issueTracker.toString());
      }
      if (dependencyMetadata.description != null) {
        jsonWriter.name("description").value(dependencyMetadata.description);
      }
      jsonWriter.endObject();
      jsonWriter.close();
    }
  }
}
