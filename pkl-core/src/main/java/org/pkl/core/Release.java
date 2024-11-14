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
package org.pkl.core;

import com.oracle.truffle.api.TruffleOptions;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Information about the Pkl release that the current program runs on. This class is the Java
 * equivalent of standard library module {@code pkl.release}.
 *
 * @param version the version of this release
 * @param os the operating system (name and version) this release is running on
 * @param flavor the flavor of this release (native, or Java and JVM version)
 * @param versionInfo the output of {@code pkl --version} for this release
 * @param commitId the Git commit ID of this release
 * @param sourceCode the source code of this release
 * @param documentation the documentation of this release
 * @param standardLibrary the standard library of this release
 */
public record Release(
    Version version,
    String os,
    String flavor,
    String versionInfo,
    String commitId,
    SourceCode sourceCode,
    Documentation documentation,
    StandardLibrary standardLibrary) {
  private static final String SOURCE_CODE_HOMEPAGE = "https://github.com/apple/pkl/";
  private static final String DOCUMENTATION_HOMEPAGE = "https://pkl-lang.org/main/";

  private static final Release CURRENT;

  static {
    var properties = new Properties();

    try (var stream = Release.class.getResourceAsStream("Release.properties")) {
      if (stream == null) {
        throw new AssertionError("Failed to locate `Release.properties`.");
      }
      properties.load(stream);
    } catch (IOException e) {
      throw new AssertionError("Failed to load `Release.properties`.", e);
    }

    var version = Version.parse(properties.getProperty("version"));
    var commitId = properties.getProperty("commitId");
    var osName = System.getProperty("os.name");
    if (osName.equals("Mac OS X")) osName = "macOS";
    if (osName.contains("Windows")) osName = "Windows";
    var osVersion = System.getProperty("os.version");
    var os = osName + " " + osVersion;
    var flavor = TruffleOptions.AOT ? "native" : "Java " + System.getProperty("java.version");
    var versionInfo = "Pkl " + version + " (" + os + ", " + flavor + ")";
    var commitish = version.isNormal() ? version.toString() : commitId;
    var docsVersion = version.isNormal() ? version.toString() : "latest";
    var docsHomepage = DOCUMENTATION_HOMEPAGE + docsVersion + "/";
    var stdlibModules =
        new LinkedHashSet<>(List.of(properties.getProperty("stdlibModules").split(",")));

    CURRENT =
        new Release(
            version,
            os,
            flavor,
            versionInfo,
            commitId,
            new SourceCode(SOURCE_CODE_HOMEPAGE, commitish),
            new Documentation(docsHomepage),
            new StandardLibrary(stdlibModules));
  }

  /** The Pkl release that the current program runs on. */
  public static Release current() {
    return CURRENT;
  }

  /**
   * The source code of a Pkl release.
   *
   * @param homepage the homepage of this source code
   * @param version the version of this source code
   */
  public record SourceCode(String homepage, String version) {
    /**
     * @deprecated As of 0.28.0, replaced by {@link #version()}.
     */
    @Deprecated(forRemoval = true)
    public String getVersion() {
      return version;
    }

    /**
     * Returns the source code page of the file with the given path. <b>Note:</b> Files may be moved
     * or deleted anytime.
     */
    public String getFilePage(String path) {
      return homepage + "blob/" + version + "/" + path;
    }

    /**
     * @deprecated As of 0.28.0, replaced by {@link #sourceCodeUrlScheme()}.
     */
    @Deprecated(forRemoval = true)
    public String getSourceCodeUrlScheme() {
      return sourceCodeUrlScheme();
    }

    /** Returns the source code scheme for the stdlib module. */
    public String sourceCodeUrlScheme() {
      return homepage + "blob/" + version + "/stdlib%{path}#L%{line}-L%{endLine}";
    }
  }

  /**
   * The documentation of a Pkl release.
   *
   * @param homepage the homepage of this documentation
   */
  public record Documentation(String homepage) {}

  /**
   * The standard library of a Pkl release.
   *
   * @since 0.21.0
   * @param modules the modules of this standard library
   */
  public record StandardLibrary(Set<String> modules) {}
}
