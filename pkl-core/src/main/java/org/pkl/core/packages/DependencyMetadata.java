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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.pkl.core.DataSize;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.Duration;
import org.pkl.core.DurationUnit;
import org.pkl.core.PClassInfo;
import org.pkl.core.PNull;
import org.pkl.core.PObject;
import org.pkl.core.Pair;
import org.pkl.core.PklException;
import org.pkl.core.Version;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.json.Json;
import org.pkl.core.util.json.Json.FormatException;
import org.pkl.core.util.json.Json.JsArray;
import org.pkl.core.util.json.Json.JsObject;
import org.pkl.core.util.json.Json.JsonParseException;
import org.pkl.core.util.json.Json.MissingFieldException;
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
 *       },
 *       "annotations": [
 *         {
 *           "moduleName": "pkl.base",
 *           "class": "Unlisted",
 *           "moduleUri": "pkl:base",
 *           "properties": {}
 *         },
 *         {
 *           "moduleName": "pkl.base",
 *           "class": "Deprecated",
 *           "moduleUri": "pkl:base",
 *           "properties": {
 *             "since": "0.26.1",
 *             "message": "don't use",
 *             "replaceWith": null
 *           }
 *         }
 *       ]
 *     }
 *   </code>
 * </pre>
 */
@SuppressWarnings({"JavadocLinkAsPlainText", "unused"})
// incorrectly thinks link within sample metadata is a JavaDoc link
public final class DependencyMetadata {

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
    var annotations = parsed.getNullable("annotations", DependencyMetadata::parseAnnotations);
    if (annotations == null) annotations = List.of();
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
        description,
        annotations);
  }

  private static Map<String, RemoteDependency> parseDependencies(Object deps)
      throws JsonParseException {
    if (!(deps instanceof JsObject dependencies)) {
      throw new FormatException("object", deps.getClass());
    }
    var ret = new HashMap<String, RemoteDependency>(dependencies.size());
    for (var key : dependencies.keySet()) {
      var remoteDependency =
          dependencies.get(
              key,
              (dep) -> {
                if (!(dep instanceof JsObject obj)) {
                  throw new FormatException("object", dep.getClass());
                }
                var checksums = obj.get("checksums", DependencyMetadata::parseChecksums);
                var packageUri = obj.get("uri", PackageUtils::parsePackageUriWithoutChecksums);
                return new RemoteDependency(packageUri, checksums);
              });
      ret.put(key, remoteDependency);
    }
    return ret;
  }

  private static List<PObject> parseAnnotations(Object ann)
      throws JsonParseException, URISyntaxException {
    if (!(ann instanceof JsArray arr)) {
      throw new FormatException("array", ann.getClass());
    }
    var annotations = new ArrayList<PObject>(arr.size());
    for (var annotation : arr) {
      var obj = parsePObject(annotation);
      if (!(obj instanceof PObject pObject)) {
        throw new PklException("Could not read annotation. Invalid object: " + obj);
      }
      annotations.add(pObject);
    }
    return annotations;
  }

  private static Object parsePObject(@Nullable Object obj)
      throws JsonParseException, URISyntaxException {
    if (obj == null) {
      return PNull.getInstance();
    } else if (obj instanceof String string) {
      return string;
    } else if (obj instanceof Boolean bool) {
      return bool;
    } else if (obj instanceof Integer integer) {
      return integer.longValue();
    } else if (obj instanceof Long aLong) {
      return aLong;
    } else if (obj instanceof Float aFloat) {
      return aFloat.doubleValue();
    } else if (obj instanceof Double aDouble) {
      return aDouble;
    } else if (obj instanceof JsArray array) {
      var list = new ArrayList<>(array.size());
      for (var element : array) {
        list.add(parsePObject(element));
      }
      return list;
    } else if (obj instanceof JsObject jsObj) {
      var type = jsObj.getString("type");
      switch (type) {
        case "Set" -> {
          var value = jsObj.getArray("value");
          var set = new HashSet<>(value.size());
          for (var element : value) {
            set.add(parsePObject(element));
          }
          return set;
        }
        case "Map" -> {
          var value = jsObj.getObject("value");
          var map = new HashMap<>();
          for (var kv : value.entrySet()) {
            map.put(kv.getKey(), parsePObject(kv.getValue()));
          }
          return map;
        }
        case "PObject" -> {
          var classInfoObj = jsObj.getObject("classInfo");
          var moduleName = classInfoObj.getString("moduleName");
          var className = classInfoObj.getString("class");
          var moduleUri = classInfoObj.getString("moduleUri");
          var props = jsObj.getObject("properties");
          var classInfo = PClassInfo.get(moduleName, className, new URI(moduleUri));
          var properties = new HashMap<String, Object>();
          for (var kv : props.entrySet()) {
            properties.put(kv.getKey(), parsePObject(kv.getValue()));
          }
          return new PObject(classInfo, properties);
        }
        case "Pattern" -> {
          var value = jsObj.getString("value");
          return Pattern.compile(value);
        }
        case "DataSize" -> {
          var symbol = jsObj.getString("unit");
          var value = jsObj.get("value");
          if (value == null) {
            throw new MissingFieldException(jsObj, "value");
          }
          var unit = DataSizeUnit.parse(symbol);
          if (unit == null) {
            throw new PklException("Invalid DataSize unit symbol: " + symbol);
          }
          if (!(value instanceof Double num)) {
            throw new FormatException("double", value.getClass());
          }
          return new DataSize(num, unit);
        }
        case "Duration" -> {
          var symbol = jsObj.getString("unit");
          var value = jsObj.get("value");
          if (value == null) {
            throw new MissingFieldException(jsObj, "value");
          }
          var unit = DurationUnit.parse(symbol);
          if (unit == null) {
            throw new PklException("Invalid Duration unit symbol: " + symbol);
          }
          if (!(value instanceof Double num)) {
            throw new FormatException("double", value.getClass());
          }
          return new Duration(num, unit);
        }
        case "Pair" -> {
          var first = parsePObject(jsObj.get("first"));
          var second = parsePObject(jsObj.get("second"));
          return new Pair<>(first, second);
        }
      }
    }
    // should never be reached
    throw new PklException("Could not read annotation. Invalid object type: " + obj.getClass());
  }

  public static Checksums parseChecksums(Object obj) throws JsonParseException {
    if (!(obj instanceof JsObject jsObj)) {
      throw new FormatException("object", obj.getClass());
    }
    var sha256 = jsObj.getString("sha256");
    return new Checksums(sha256);
  }

  public static List<String> parseAuthors(Object obj) throws JsonParseException {
    if (!(obj instanceof JsArray arr)) {
      throw new FormatException("array", obj.getClass());
    }
    var ret = new ArrayList<String>(arr.size());
    for (var elem : arr) {
      if (!(elem instanceof String string)) {
        throw new FormatException("string", elem.getClass());
      }
      ret.add(string);
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
  private final List<PObject> annotations;

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
      @Nullable String description,
      List<PObject> annotations) {
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
    this.annotations = annotations;
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

  public List<PObject> getAnnotations() {
    return annotations;
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
        && Objects.equals(description, that.description)
        && Objects.equals(annotations, that.annotations);
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
        description,
        annotations);
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
        + ", annotations="
        + annotations
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
      if (!dependencyMetadata.annotations.isEmpty()) {
        jsonWriter.name("annotations");
        writeAnnotations();
      }
      jsonWriter.endObject();
      jsonWriter.close();
    }

    private void writeAnnotations() throws IOException {
      jsonWriter.beginArray();
      for (var annotation : dependencyMetadata.annotations) {
        writePObject(annotation);
      }
      jsonWriter.endArray();
    }

    private void writePClassInfo(PClassInfo<?> pClassInfo) throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("moduleName").value(pClassInfo.getModuleName());
      jsonWriter.name("class").value(pClassInfo.getSimpleName());
      jsonWriter.name("moduleUri").value(pClassInfo.getModuleUri().toString());
      jsonWriter.endObject();
    }

    private void writePObject(PObject object) throws IOException {
      jsonWriter.beginObject();
      jsonWriter.name("type").value("PObject");
      jsonWriter.name("classInfo");
      writePClassInfo(object.getClassInfo());
      jsonWriter.name("properties");
      jsonWriter.beginObject();
      for (var kv : object.getProperties().entrySet()) {
        jsonWriter.name(kv.getKey());
        writeGenericObject(kv.getValue());
      }
      jsonWriter.endObject();
      jsonWriter.endObject();
    }

    private void writeGenericObject(Object value) throws IOException {
      if (value instanceof PNull) {
        jsonWriter.nullValue();
      } else if (value instanceof PObject pObject) {
        writePObject(pObject);
      } else if (value instanceof String string) {
        jsonWriter.value(string);
      } else if (value instanceof Boolean bool) {
        jsonWriter.value(bool);
      } else if (value instanceof Integer num) {
        jsonWriter.value(num);
      } else if (value instanceof Long num) {
        jsonWriter.value(num);
      } else if (value instanceof Float num) {
        jsonWriter.value(num);
      } else if (value instanceof Double num) {
        jsonWriter.value(num);
      } else if (value instanceof List<?> list) {
        jsonWriter.beginArray();
        for (var v : list) {
          writeGenericObject(v);
        }
        jsonWriter.endArray();
      } else if (value instanceof Set<?> set) {
        jsonWriter.beginObject();
        jsonWriter.name("type").value("Set");
        jsonWriter.name("value");
        jsonWriter.beginArray();
        for (var v : set) {
          writeGenericObject(v);
        }
        jsonWriter.endArray();
        jsonWriter.endObject();
      } else if (value instanceof Map<?, ?> map) {
        jsonWriter.beginObject();
        jsonWriter.name("type").value("Map");
        jsonWriter.name("value");
        jsonWriter.beginObject();
        for (var kv : map.entrySet()) {
          var key = kv.getKey();
          if (key instanceof String s) {
            jsonWriter.name(s);
          } else {
            throw new PklException(
                "Error serializing annotation for PklProject:\n"
                    + "  cannot render map with non-string key: "
                    + key);
          }
          writeGenericObject(kv.getValue());
        }
        jsonWriter.endObject();
        jsonWriter.endObject();
      } else if (value instanceof Pattern pattern) {
        jsonWriter.beginObject();
        jsonWriter.name("type").value("Pattern");
        jsonWriter.name("value").value(pattern.pattern());
        jsonWriter.endObject();
      } else if (value instanceof DataSize dataSize) {
        jsonWriter.beginObject();
        jsonWriter.name("type").value("DataSize");
        jsonWriter.name("unit").value(dataSize.getUnit().getSymbol());
        jsonWriter.name("value").value(dataSize.getValue());
        jsonWriter.endObject();
      } else if (value instanceof Duration duration) {
        jsonWriter.beginObject();
        jsonWriter.name("type").value("Duration");
        jsonWriter.name("unit").value(duration.getUnit().getSymbol());
        jsonWriter.name("value").value(duration.getValue());
        jsonWriter.endObject();
      } else if (value instanceof Pair<?, ?> pair) {
        jsonWriter.beginObject();
        jsonWriter.name("type").value("Pair");
        jsonWriter.name("first");
        writeGenericObject(pair.getFirst());
        jsonWriter.name("second");
        writeGenericObject(pair.getSecond());
        jsonWriter.endObject();
      } else {
        // PClass and TypeAlias are not supported
        throw new PklException(
            "Error serializing annotation for PklProject:\n:"
                + "  cannot render value with unexpected type: "
                + value.getClass());
      }
    }
  }
}
