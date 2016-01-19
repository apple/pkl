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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

/** A provider for {@link SecurityManager}s. */
public final class SecurityManagers {
  private SecurityManagers() {}

  /**
   * Returns the list of module patterns that the {@link #defaultManager default security manager}
   * will use to determine if a module import may be resolved.
   */
  public static final List<Pattern> defaultAllowedModules =
      List.of(
          Pattern.compile("repl:"),
          Pattern.compile("file:"),
          // for evaluating URLs returned by `Class(Loader).getResource()`
          Pattern.compile("jar:file:"),
          Pattern.compile("modulepath:"),
          Pattern.compile("https:"),
          Pattern.compile("pkl:"),
          Pattern.compile("package:"),
          Pattern.compile("projectpackage:"));

  /**
   * Returns the list of resource patterns that the {@link #defaultManager default security manager}
   * will use to determine if an external resource may be read.
   */
  public static final List<Pattern> defaultAllowedResources =
      List.of(
          Pattern.compile("prop:"),
          Pattern.compile("env:"),
          Pattern.compile("file:"),
          Pattern.compile("modulepath:"),
          Pattern.compile("package:"),
          Pattern.compile("projectpackage:"),
          Pattern.compile("https:"));

  /**
   * Returns the mapping from module URIs to trust levels used by the {@link #defaultManager default
   * security manager}.
   *
   * <p>Trust levels are used to determine whether a module may import another module. Only modules
   * with the same or a lower trust level may be imported.
   *
   * <p>This mapping supports a fixed set of module URI schemes. Local modules are given a higher
   * trust level than remote modules. For example, a local file may import a remote file, but not
   * the other way around.
   */
  public static final Function<URI, Integer> defaultTrustLevels =
      SecurityManagers::getDefaultTrustLevel;

  private static int getDefaultTrustLevel(URI uri) {
    switch (uri.getScheme()) {
      case "repl":
        return 40;
      case "file":
        return uri.getHost() == null ? 30 : 10;
      case "jar":
        // use trust level of embedded URL
        return getDefaultTrustLevel(URI.create(uri.toString().substring(4)));
      case "modulepath":
        return 20;
      case "pkl":
        return 0;
      default:
        return 10;
    }
  }

  /**
   * Returns a {@link #standard standard security manager} with {@link #defaultAllowedModules
   * default allowed modules}, {@link #defaultAllowedResources default allowed resources}, {@link
   * #defaultTrustLevels default trust levels}, and no root directory for modules and resources.
   */
  public static final SecurityManager defaultManager =
      new Standard(defaultAllowedModules, defaultAllowedResources, defaultTrustLevels, null);

  /**
   * Creates a {@link SecurityManager} that determines whether a module can be resolved based on the
   * given list of module URI patterns, whether an external resources can be read based on the given
   * list of resource URI patterns, and whether a module can import another module based on the
   * given module trust levels. A module can only import modules with the same or a lower trust
   * level.
   *
   * <p>If {@code rootDir} is non-null, access to file-based modules and resources is restricted to
   * those located under {@code rootDir}. Any symlinks are resolved before this check is performed.
   */
  public static SecurityManager standard(
      List<Pattern> allowedModules,
      List<Pattern> allowedResources,
      Function<URI, Integer> trustLevels,
      @Nullable Path rootDir) {
    return new Standard(allowedModules, allowedResources, trustLevels, rootDir);
  }

  /** Creates an unconfigured builder for setting up a standard {@link SecurityManager}. */
  public static StandardBuilder standardBuilder() {
    return new StandardBuilder();
  }

  private static class Standard implements SecurityManager {
    private final List<Pattern> allowedModules;
    private final List<Pattern> allowedResources;
    private final Function<URI, Integer> trustLevels;
    private final @Nullable Path rootDir;

    Standard(
        List<Pattern> allowedModules,
        List<Pattern> allowedResources,
        Function<URI, Integer> trustLevels,
        @Nullable Path rootDir) {
      this.allowedModules = allowedModules;
      this.allowedResources = allowedResources;
      this.trustLevels = trustLevels;
      this.rootDir = normalizePath(rootDir);
    }

    @Override
    public void checkResolveModule(URI uri) throws SecurityManagerException {
      checkRead(uri, allowedModules, "moduleNotInAllowList");
    }

    @Override
    public void checkResolveResource(URI resource) throws SecurityManagerException {
      checkRead(resource, allowedResources, "resourceNotInAllowList");
    }

    @Override
    public void checkReadResource(URI uri) throws SecurityManagerException {
      checkRead(uri, allowedResources, "resourceNotInAllowList");
    }

    @Override
    public void checkImportModule(URI importingModule, URI importedModule)
        throws SecurityManagerException {
      var importingTrustLevel = trustLevels.apply(importingModule);
      var importedTrustLevel = trustLevels.apply(importedModule);

      if (importingTrustLevel < importedTrustLevel) {
        var message =
            ErrorMessages.create("insufficientModuleTrustLevel", importedModule, importingModule);
        throw new SecurityManagerException(message);
      }
    }

    private @Nullable Path normalizePath(@Nullable Path path) {
      if (path == null) {
        return null;
      }
      try {
        if (Files.exists(path)) {
          return path.toRealPath();
        }
        return path.toAbsolutePath();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private void checkRead(URI uri, List<Pattern> allowedPatterns, String errorMessageKey)
        throws SecurityManagerException {
      for (var pattern : allowedPatterns) {
        if (pattern.matcher(uri.toString()).lookingAt()) {
          checkIsUnderRootDir(uri, errorMessageKey);
          return;
        }
      }

      var message = ErrorMessages.create(errorMessageKey, uri);
      throw new SecurityManagerException(message);
    }

    private void checkIsUnderRootDir(URI uri, String errorMessageKey)
        throws SecurityManagerException {
      if (!uri.isAbsolute()) {
        throw new AssertionError("Expected absolute URI but got: " + uri);
      }

      if (rootDir == null || !uri.getScheme().equals("file")) return;

      var path = Path.of(uri);
      if (Files.exists(path)) {
        try {
          path = path.toRealPath();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      } else {
        // perform check even if file doesn't exist
        // to avoid leaking information on whether files outside root dir exist
        path = path.normalize();
      }

      if (!path.startsWith(rootDir)) {
        var message = ErrorMessages.create(errorMessageKey, uri);
        throw new SecurityManagerException(message);
      }
    }
  }

  public static class StandardBuilder implements SecurityManagerBuilder<StandardBuilder> {

    private final List<Pattern> allowedModules = new ArrayList<>();

    private final List<Pattern> allowedResources = new ArrayList<>();

    private Path rootDir;

    private StandardBuilder() {}

    @Override
    public StandardBuilder addAllowedModule(Pattern pattern) {
      allowedModules.add(pattern);
      return this;
    }

    @Override
    public StandardBuilder addAllowedModules(Collection<Pattern> patterns) {
      allowedModules.addAll(patterns);
      return this;
    }

    @Override
    public StandardBuilder setAllowedModules(Collection<Pattern> patterns) {
      allowedModules.clear();
      allowedModules.addAll(patterns);
      return this;
    }

    @Override
    public List<Pattern> getAllowedModules() {
      return allowedModules;
    }

    @Override
    public StandardBuilder addAllowedResource(Pattern pattern) {
      allowedResources.add(pattern);
      return this;
    }

    @Override
    public StandardBuilder addAllowedResources(Collection<Pattern> patterns) {
      allowedResources.addAll(patterns);
      return this;
    }

    @Override
    public StandardBuilder setAllowedResources(Collection<Pattern> patterns) {
      allowedResources.clear();
      allowedResources.addAll(patterns);
      return this;
    }

    @Override
    public List<Pattern> getAllowedResources() {
      return allowedResources;
    }

    @Override
    public StandardBuilder setRootDir(@Nullable Path rootDir) {
      this.rootDir = rootDir;
      return this;
    }

    @Override
    public @Nullable Path getRootDir() {
      return rootDir;
    }

    @Override
    public SecurityManager build() {
      if (allowedResources.isEmpty() && allowedModules.isEmpty()) {
        throw new IllegalStateException("No security manager set.");
      }

      return new Standard(allowedModules, allowedResources, defaultTrustLevels, rootDir);
    }
  }
}
