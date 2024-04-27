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
package org.pkl.core;

import java.util.*;
import java.util.regex.*;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

/**
 * A <a href="https://semver.org/spec/v2.0.0.html">semantic version</a>.
 *
 * <p>This class guarantees that valid semantic version numbers are handled correctly, but does
 * <em>not</em> guarantee that invalid semantic version numbers are rejected.
 */
// copied by `org.pkl.executor.Version` to avoid dependency on pkl-core
@SuppressWarnings("Duplicates")
public final class Version implements Comparable<Version> {
  // https://semver.org/#backusnaur-form-grammar-for-valid-semver-versions
  private static final Pattern VERSION =
      Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-([^+]+))?(?:\\+(.+))?");

  private static final Pattern NUMERIC_IDENTIFIER = Pattern.compile("(0|[1-9]\\d*)");

  private static final Comparator<Version> COMPARATOR =
      Comparator.comparingInt(Version::getMajor)
          .thenComparingInt(Version::getMinor)
          .thenComparingInt(Version::getPatch)
          .thenComparing(
              (v1, v2) -> {
                if (v1.preRelease == null) return v2.preRelease == null ? 0 : 1;
                if (v2.preRelease == null) return -1;
                var ids1 = v1.getPreReleaseIdentifiers();
                var ids2 = v2.getPreReleaseIdentifiers();
                var minSize = Math.min(ids1.length, ids2.length);
                for (var i = 0; i < minSize; i++) {
                  var result = ids1[i].compareTo(ids2[i]);
                  if (result != 0) return result;
                }
                return Integer.compare(ids1.length, ids2.length);
              });

  private final int major;
  private final int minor;
  private final int patch;
  private final @Nullable String preRelease;
  private final @Nullable String build;

  @LateInit private volatile Identifier[] __preReleaseIdentifiers;

  /** Constructs a semantic version. */
  public Version(
      int major, int minor, int patch, @Nullable String preRelease, @Nullable String build) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.preRelease = preRelease;
    this.build = build;
  }

  /**
   * Parses the given string as a semantic version number.
   *
   * <p>Throws {@link IllegalArgumentException} if the given string could not be parsed as a
   * semantic version number or is too large to fit into a {@link Version}.
   */
  public static Version parse(String version) {
    var result = parseOrNull(version);
    if (result != null) return result;

    if (VERSION.matcher(version).matches()) {
      throw new IllegalArgumentException(
          String.format("`%s` is too large to fit into a Version.", version));
    }

    throw new IllegalArgumentException(
        String.format("`%s` could not be parsed as a semantic version number.", version));
  }

  /**
   * Parses the given string as a semantic version number.
   *
   * <p>Returns {@code null} if the given string could not be parsed as a semantic version number or
   * is too large to fit into a {@link Version}.
   */
  public static @Nullable Version parseOrNull(String version) {
    var matcher = VERSION.matcher(version);
    if (!matcher.matches()) return null;

    try {
      return new Version(
          Integer.parseInt(matcher.group(1)),
          Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3)),
          matcher.group(4),
          matcher.group(5));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Returns a comparator for semantic versions. */
  public static Comparator<Version> comparator() {
    return COMPARATOR;
  }

  /** Returns the major version. */
  public int getMajor() {
    return major;
  }

  /** Returns a copy of this version with the given major version. */
  public Version withMajor(int major) {
    return new Version(major, minor, patch, preRelease, build);
  }

  /** Returns the minor version. */
  public int getMinor() {
    return minor;
  }

  /** Returns a copy of this version with the given minor version. */
  public Version withMinor(int minor) {
    return new Version(major, minor, patch, preRelease, build);
  }

  /** Returns the patch version. */
  public int getPatch() {
    return patch;
  }

  /** Returns a copy of this version with the given patch version. */
  public Version withPatch(int patch) {
    return new Version(major, minor, patch, preRelease, build);
  }

  /** Returns the pre-release version (if any). */
  public @Nullable String getPreRelease() {
    return preRelease;
  }

  /** Returns a copy of this version with the given pre-release version. */
  public Version withPreRelease(@Nullable String preRelease) {
    return new Version(major, minor, patch, preRelease, build);
  }

  /** Returns the build metadata (if any). */
  public @Nullable String getBuild() {
    return build;
  }

  /** Returns a copy of this version with the given build metadata. */
  public Version withBuild(@Nullable String build) {
    return new Version(major, minor, patch, preRelease, build);
  }

  /** Tells if this version has no pre-release version or build metadata. */
  public boolean isNormal() {
    return preRelease == null && build == null;
  }

  /** Tells if this version has a non-zero major version and no pre-release version. */
  public boolean isStable() {
    return major != 0 && preRelease == null;
  }

  /** Strips any pre-release version and build metadata from this version. */
  public Version toNormal() {
    return preRelease == null && build == null
        ? this
        : new Version(major, minor, patch, null, null);
  }

  /** Compares this version to the given version according to semantic versioning rules. */
  @Override
  public int compareTo(Version other) {
    return COMPARATOR.compare(this, other);
  }

  /** Tells if this version is equal to {@code obj} according to semantic versioning rules. */
  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Version other)) return false;
    return major == other.major
        && minor == other.minor
        && patch == other.patch
        && Objects.equals(preRelease, other.preRelease);
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, preRelease);
  }

  @Override
  public String toString() {
    return major
        + "."
        + minor
        + "."
        + patch
        + (preRelease != null ? "-" + preRelease : "")
        + (build != null ? "+" + build : "");
  }

  private Identifier[] getPreReleaseIdentifiers() {
    if (__preReleaseIdentifiers == null) {
      __preReleaseIdentifiers =
          preRelease == null
              ? new Identifier[0]
              : Arrays.stream(preRelease.split("\\."))
                  .map(
                      str ->
                          NUMERIC_IDENTIFIER.matcher(str).matches()
                              ? new Identifier(Long.parseLong(str), null)
                              : new Identifier(-1, str))
                  .toArray(Identifier[]::new);
    }
    return __preReleaseIdentifiers;
  }

  private static final class Identifier implements Comparable<Identifier> {
    private final long numericId;
    private final @Nullable String alphanumericId;

    Identifier(long numericId, @Nullable String alphanumericId) {
      this.numericId = numericId;
      this.alphanumericId = alphanumericId;
    }

    @Override
    public int compareTo(Identifier other) {
      return alphanumericId != null
          ? other.alphanumericId != null ? alphanumericId.compareTo(other.alphanumericId) : 1
          : other.alphanumericId != null ? -1 : Long.compare(numericId, other.numericId);
    }
  }
}
