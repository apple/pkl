/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Utility library for accessing files in base directories.
 *
 * <p>On macOS and Linux, follows the XDG base directory specification.
 *
 * <p>On Windows, follows {@code $APPDATA} and {@code $LOCALAPPDATA} conventions, but can be
 * overridden by {@code XDG} style env vars.
 */
class BaseDirectory {
  private final String xdgHomeEnvVar;
  private final @Nullable String xdgDirsEnvVar;
  private final String windowsEnvVar;
  private final @Nullable String windowsSubpath;
  private final String homeDefault;
  private final String @Nullable [] dirsDefault;

  BaseDirectory(
      String xdgHomeEnvVar,
      @Nullable String xdgDirsEnvVar,
      String windowsEnvVar,
      @Nullable String windowsSubpath,
      String homeDefault,
      String @Nullable [] dirsDefault) {
    this.xdgHomeEnvVar = xdgHomeEnvVar;
    this.xdgDirsEnvVar = xdgDirsEnvVar;
    this.windowsEnvVar = windowsEnvVar;
    this.windowsSubpath = windowsSubpath;
    this.homeDefault = homeDefault;
    this.dirsDefault = dirsDefault;
  }

  @Nullable Path getHome() {
    return getHome(System.getenv(), IoUtils.isWindows());
  }

  /** Returns the first file within the search hierarchy that exists. */
  @Nullable Path firstMatchingPath(String subpath, Predicate<Path> predicate) {
    return firstMatchingPath(subpath, System.getenv(), IoUtils.isWindows(), predicate);
  }

  /** Returns the subpath within the {@code home} of this base directory type. */
  @Nullable Path resolveHome(String subpath) {
    var homeDir = getHome(System.getenv(), IoUtils.isWindows());
    if (homeDir != null) {
      return homeDir.resolve(subpath);
    }
    return null;
  }

  // for testing only
  @Nullable Path firstMatchingPath(
      String subpath, Map<String, String> envVars, boolean isWindows, Predicate<Path> predicate) {
    var home = getHome(envVars, isWindows);
    Path candidate;
    if (home != null) {
      candidate = home.resolve(subpath);
      if (predicate.test(candidate)) {
        return candidate;
      }
    }
    var dirs = getDirs(envVars);
    if (dirs != null) {
      for (var dir : dirs) {
        candidate = dir.resolve(subpath);
        if (predicate.test(candidate)) {
          return candidate;
        }
      }
    }
    return null;
  }

  // possibly null if $HOME is not set.
  private static final @Nullable Path homeDir;

  static {
    var userHome = System.getProperty("user.home");
    if (userHome != null) {
      homeDir = Path.of(userHome);
    } else {
      homeDir = null;
    }
  }

  private static Path @Nullable [] getConfiguredPaths(String envVar, Map<String, String> envVars) {
    try {
      var value = envVars.get(envVar);
      if (value == null || value.isEmpty()) {
        return null;
      }
      var strs = value.split(File.pathSeparator);
      var ret = new Path[strs.length];
      for (var i = 0; i < strs.length; i++) {
        var dir = strs[i];
        if (dir.isEmpty()) {
          continue;
        }
        ret[i] = Path.of(dir).resolve("pkl");
      }
      return Arrays.stream(ret).filter(Objects::nonNull).toArray(Path[]::new);
    } catch (InvalidPathException e) {
      // can't use org.pkl.core.Logger here; logger isn't yet available
      // (can't call `VmContext.get()`).
      // do the next best thing and just write to stderr.
      System.err.println(
          "[org.pkl.core.util.BaseDirectory] '"
              + envVar
              + "' env var contains an invalid path: "
              + e.getMessage());
      return null;
    }
  }

  private static @Nullable Path getConfiguredPath(String envVar, Map<String, String> envVars) {
    try {
      var value = envVars.get(envVar);
      if (value == null || value.isEmpty()) {
        return null;
      }
      return Path.of(value);
    } catch (InvalidPathException e) {
      // can't use org.pkl.core.Logger here; logger isn't yet available
      // (can't call `VmContext.get()`).
      // do the next best thing and just write to stderr.
      System.err.println(
          "[org.pkl.core.util.BaseDirectory] '"
              + envVar
              + "' env var is an invalid path: "
              + e.getMessage());
      return null;
    }
  }

  @Nullable Path getHome(Map<String, String> envVars, boolean isWindows) {
    var configuredHome = getConfiguredPath(xdgHomeEnvVar, envVars);
    if (configuredHome != null) {
      return configuredHome.resolve("pkl");
    }
    if (isWindows) {
      configuredHome = getConfiguredPath(windowsEnvVar, envVars);
      if (configuredHome != null) {
        configuredHome = configuredHome.resolve("pkl");
        if (windowsSubpath != null) {
          configuredHome = configuredHome.resolve(windowsSubpath);
        }
        return configuredHome;
      }
    }
    if (homeDir != null) {
      return homeDir.resolve(homeDefault).resolve("pkl");
    }
    return null;
  }

  Path @Nullable [] getDirs(Map<String, String> envVars) {
    if (xdgDirsEnvVar != null) {
      var paths = getConfiguredPaths(xdgDirsEnvVar, envVars);
      if (paths != null) {
        return paths;
      }
    }
    if (dirsDefault == null) {
      return null;
    }
    var ret = new Path[dirsDefault.length];
    for (var i = 0; i < dirsDefault.length; i++) {
      var dir = dirsDefault[i];
      if (!dir.startsWith("/")) {
        if (homeDir == null) {
          return null;
        }
        ret[i] = homeDir.resolve(dir).resolve("pkl");
      } else {
        ret[i] = Path.of(dir).resolve("pkl");
      }
    }
    return ret;
  }
}
