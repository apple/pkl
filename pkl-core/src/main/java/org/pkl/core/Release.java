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
package org.pkl.core;

import com.oracle.truffle.api.TruffleOptions;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Information about the Pkl release that the current program runs on. This class is the Java
 * equivalent of standard library module {@code pkl.release}.
 */
public class Release {
  private static final String SOURCE_CODE_HOMEPAGE = "https://github.com/pkl-lang/pkl/";
  private static final String DOCUMENTATION_HOMEPAGE = "https://pkl.apple.com/main/";

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
    var osVersion = System.getProperty("os.version");
    var flavor = TruffleOptions.AOT ? "native" : "Java " + System.getProperty("java.version");
    var versionInfo = "Pkl " + version + " (" + osName + " " + osVersion + ", " + flavor + ")";
    var commitish = version.isNormal() ? version.toString() : commitId;
    var docsVersion = version.isNormal() ? version.toString() : "latest";
    var docsHomepage = DOCUMENTATION_HOMEPAGE + docsVersion + "/";
    var stdlibModules =
        new LinkedHashSet<>(List.of(properties.getProperty("stdlibModules").split(",")));

    CURRENT =
        new Release(
            version,
            versionInfo,
            commitId,
            new SourceCode(SOURCE_CODE_HOMEPAGE, commitish),
            new Documentation(docsHomepage),
            new StandardLibrary(stdlibModules));
  }

  private final Version version;
  private final String versionInfo;
  private final String commitId;
  private final SourceCode sourceCode;
  private final Documentation documentation;
  private final StandardLibrary standardLibrary;

  /** Constructs a release. */
  public Release(
      Version version,
      String versionInfo,
      String commitId,
      SourceCode sourceCode,
      Documentation documentation,
      StandardLibrary standardLibrary) {
    this.version = version;
    this.versionInfo = versionInfo;
    this.commitId = commitId;
    this.sourceCode = sourceCode;
    this.documentation = documentation;
    this.standardLibrary = standardLibrary;
  }

  /** The Pkl release that the current program runs on. */
  public static Release current() {
    return CURRENT;
  }

  /** The version of this release. */
  public Version version() {
    return version;
  }

  /** The output of {@code pkl --version} for this release. */
  public String versionInfo() {
    return versionInfo;
  }

  /** The Git commit ID of this release. */
  public String commitId() {
    return commitId;
  }

  /** The source code of this release. */
  public SourceCode sourceCode() {
    return sourceCode;
  }

  /** The documentation of this release. */
  public Documentation documentation() {
    return documentation;
  }

  /** The standard library of this release. */
  public StandardLibrary standardLibrary() {
    return standardLibrary;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Release)) return false;

    var other = (Release) obj;
    return version.equals(other.version)
        && versionInfo.equals(other.versionInfo)
        && commitId.equals(other.commitId)
        && sourceCode.equals(other.sourceCode)
        && documentation.equals(other.documentation)
        && standardLibrary.equals(other.standardLibrary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, versionInfo, commitId, sourceCode, documentation, standardLibrary);
  }

  /** The source code of a Pkl release. */
  public static final class SourceCode {
    private final String homepage;
    private final String version;

    /** Constructs a {@link SourceCode}. */
    public SourceCode(String homepage, String version) {
      this.homepage = homepage;
      this.version = version;
    }

    public String getVersion() {
      return version;
    }

    /** The homepage of this source code. */
    public String homepage() {
      return homepage;
    }

    /**
     * Returns the source code page of the file with the given path. <b>Note:</b> Files may be moved
     * or deleted anytime.
     */
    public String getFilePage(String path) {
      return homepage + "blob/" + version + "/" + path;
    }

    /** The source code scheme for the stdlib module. */
    public String getSourceCodeUrlScheme() {
      return homepage + "blob/" + version + "/stdlib%{path}#L%{line}-L%{endLine}";
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof SourceCode)) return false;

      var other = (SourceCode) obj;
      return homepage.equals(other.homepage) && version.equals(other.version);
    }

    @Override
    public int hashCode() {
      return Objects.hash(homepage, version);
    }
  }

  /** The documentation of a Pkl release. */
  public static final class Documentation {
    private final String homepage;

    /** Constructs a {@link Documentation}. */
    public Documentation(String homepage) {
      this.homepage = homepage;
    }

    /** The homepage of this documentation. */
    public String homepage() {
      return homepage;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Documentation)) return false;

      var other = (Documentation) obj;
      return homepage.equals(other.homepage);
    }

    @Override
    public int hashCode() {
      return homepage.hashCode();
    }
  }

  /**
   * The standard library of a Pkl release.
   *
   * @since 0.21.0
   */
  public static final class StandardLibrary {
    private final Set<String> modules;

    /** Constructs a {@link StandardLibrary}. */
    public StandardLibrary(Set<String> modules) {
      this.modules = modules;
    }

    /** The modules of this standard library. */
    public Set<String> modules() {
      return modules;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof StandardLibrary)) return false;

      var other = (StandardLibrary) obj;
      return modules.equals(other.modules);
    }

    @Override
    public int hashCode() {
      return modules.hashCode();
    }
  }
}
