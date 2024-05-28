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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.packages.Checksums;
import org.pkl.core.packages.Dependency;
import org.pkl.core.packages.Dependency.LocalDependency;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.DependencyMetadata;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageUtils;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.json.Json;
import org.pkl.core.util.json.Json.FormatException;
import org.pkl.core.util.json.Json.JsObject;
import org.pkl.core.util.json.Json.JsonParseException;
import org.pkl.core.util.json.JsonWriter;

/**
 * The Java representation of a project's resolved dependency list. Resolved dependencies are stored
 * as JSON as a sibling file to PklProject. Each key in the JSON file records an entry for each
 * dependency via its base URI, and the major version number.
 *
 * <p>A resolved dependency can either be local or remote. A remote dependency will have its
 * checksums recorded, while a local dependency will point to the relative path of the project
 * holding the dependency.
 *
 * <p>Sample structure:
 *
 * <pre>
 * <code>
 * {
 *   "schemaVersion": 1,
 *   "resolvedDependencies": {
 *     "package://example.com/my/package@0": {
 *       "type": "remote",
 *       "uri": "projectpackage://example.com/my/package@0.5.0",
 *       "checksums": {
 *         "sha256": "abc123"
 *       }
 *     },
 *     "package://example.com/other/package@1": {
 *       "type": "local",
 *       "uri": "projectpackage://example.com/other/package@1.5.0",
 *       "path": "../sibling"
 *     }
 *   }
 * }
 * </pre>
 * </code>
 */
public final class ProjectDeps {
  private static final Set<Integer> supportedSchemaVersions = Set.of(1);

  private final EconomicMap<CanonicalPackageUri, Dependency> resolvedDependencies;

  public static ProjectDeps parse(Path path)
      throws IOException, URISyntaxException, JsonParseException {
    var input = Files.readString(path);
    return parse(input);
  }

  public static ProjectDeps parse(String input) throws JsonParseException {
    var parsed = Json.parseObject(input);
    var schemaVersion = parsed.getInt("schemaVersion");
    if (!supportedSchemaVersions.contains(schemaVersion)) {
      throw new PackageLoadError("unsupportedProjectDepsVersion", schemaVersion);
    }
    var resolvedDependencies =
        parsed.get("resolvedDependencies", ProjectDeps::parseResolvedDependencies);
    return new ProjectDeps(resolvedDependencies);
  }

  private static EconomicMap<CanonicalPackageUri, Dependency> parseResolvedDependencies(
      Object object) throws JsonParseException, URISyntaxException {
    if (!(object instanceof JsObject jsObj)) {
      throw new FormatException("resolvedDependencies", "object", object.getClass());
    }
    var ret = EconomicMaps.<CanonicalPackageUri, Dependency>create(jsObj.size());
    for (var entry : jsObj.entrySet()) {
      Dependency resolvedDependency = parseResolvedDependency(entry);
      var canonicalPackageUri = CanonicalPackageUri.of(entry.getKey());
      ret.put(canonicalPackageUri, resolvedDependency);
    }
    return ret;
  }

  private static Dependency parseResolvedDependency(Entry<String, Object> entry)
      throws JsonParseException {
    var input = entry.getValue();
    if (!(input instanceof JsObject obj)) {
      throw new VmExceptionBuilder().evalError("invalid object").build();
    }
    var type = obj.getString("type");
    var uri = obj.get("uri", PackageUtils::parsePackageUriWithoutChecksums);
    if (type.equals("remote")) {
      var checksums = DependencyMetadata.parseChecksums(obj.getObject("checksums"));
      return new RemoteDependency(uri, checksums);
    } else {
      assert type.equals("local");
      var pathStr = obj.getString("path");
      return new Dependency.LocalDependency(uri, Path.of(pathStr));
    }
  }

  public ProjectDeps(EconomicMap<CanonicalPackageUri, Dependency> resolvedDependencies) {
    this.resolvedDependencies = resolvedDependencies;
  }

  /** Given a declared dependency, return the resolved dependency. */
  public @Nullable Dependency get(CanonicalPackageUri canonicalPackageUri) {
    return resolvedDependencies.get(canonicalPackageUri);
  }

  /** Serializes project dependencies to JSON, and writes it to the provided output stream. */
  public void writeTo(OutputStream out) throws IOException {
    new ProjectDepsWriter(out, resolvedDependencies).write();
  }

  @Override
  public String toString() {
    return "ProjectDeps {" + resolvedDependencies + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProjectDeps that = (ProjectDeps) o;
    return EconomicMaps.equals(resolvedDependencies, that.resolvedDependencies);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resolvedDependencies);
  }

  private static final class ProjectDepsWriter {
    private final EconomicMap<CanonicalPackageUri, Dependency> projectDeps;
    private final JsonWriter jsonWriter;

    private ProjectDepsWriter(
        OutputStream out, EconomicMap<CanonicalPackageUri, Dependency> projectDeps) {
      jsonWriter = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      jsonWriter.setIndent("  ");
      this.projectDeps = projectDeps;
    }

    private void writeChecksums(Checksums checksums) throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("sha256").value(checksums.getSha256());
      jsonWriter.endObject();
    }

    private void writeRemoteDependency(RemoteDependency remoteDependency) throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("type").value("remote");
      jsonWriter.name("uri").value(remoteDependency.getPackageUri().toString());
      jsonWriter.name("checksums");
      assert remoteDependency.getChecksums() != null;
      writeChecksums(remoteDependency.getChecksums());
      jsonWriter.endObject();
    }

    private void writeLocalDependency(LocalDependency localDependency) throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("type").value("local");
      jsonWriter.name("uri").value(localDependency.getPackageUri().toString());
      jsonWriter.name("path").value(IoUtils.toNormalizedPathString(localDependency.getPath()));
      jsonWriter.endObject();
    }

    private void write() throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("schemaVersion").value(1);
      jsonWriter.name("resolvedDependencies");
      jsonWriter.beginObject();
      var cursor = projectDeps.getEntries();
      while (cursor.advance()) {
        jsonWriter.name(cursor.getKey().toString());
        var dependency = cursor.getValue();
        if (dependency instanceof LocalDependency localDependency) {
          writeLocalDependency(localDependency);
        } else {
          writeRemoteDependency((RemoteDependency) dependency);
        }
      }
      jsonWriter.endObject();
      jsonWriter.endObject();
      jsonWriter.close();
    }
  }
}
