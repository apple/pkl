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
package org.pkl.core.module;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.pkl.core.util.IoUtils;

/** Utilities for obtaining and using resolved module keys. */
public final class ResolvedModuleKeys {
  private ResolvedModuleKeys() {}

  /**
   * Creates a resolved module key backed by the given file path. The resulting module will be
   * loaded from that file path and cached using the given URI as cache key.
   */
  public static ResolvedModuleKey file(ModuleKey original, URI uri, Path path) {
    return new File(original, uri, path);
  }

  /**
   * Creates a resolved module key backed by the given URL. The resulting module will be loaded from
   * that URL and cached using the given URI as cache key.
   */
  public static ResolvedModuleKey url(ModuleKey original, URI uri, URL url) {
    return new Url(original, uri, url);
  }

  /**
   * Creates a resolved module key backed by the given source code. If {@code cached} is {@code
   * true}, the resulting module will be cached using the given URI as cache key.
   */
  public static ResolvedModuleKey virtual(
      ModuleKey original, URI uri, String sourceText, boolean cached) {
    return new Virtual(original, uri, sourceText, cached);
  }

  /**
   * Creates a resolved module key that behaves like {@code delegate}, except with {@code original}
   * as its original module key.
   */
  public static ResolvedModuleKey delegated(ResolvedModuleKey delegate, ModuleKey original) {
    return new Delegated(delegate, original);
  }

  private static class File implements ResolvedModuleKey {
    final ModuleKey original;
    final URI uri;
    final Path path;

    File(ModuleKey original, URI uri, Path path) {
      this.original = original;
      this.uri = uri;
      this.path = path;
    }

    @Override
    public ModuleKey getOriginal() {
      return original;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public String loadSource() throws IOException {
      try {
        return Files.readString(path, StandardCharsets.UTF_8);
      } catch (AccessDeniedException e) {
        // Windows throws `AccessDeniedException` when reading directories.
        // Sync error between different OSes.
        if (Files.isDirectory(path)) {
          throw new IOException("Is a directory");
        }
        throw e;
      }
    }
  }

  private static class Url implements ResolvedModuleKey {
    final ModuleKey original;
    final URI uri;
    final URL url;

    Url(ModuleKey original, URI uri, URL url) {
      this.original = original;
      this.uri = uri;
      this.url = url;
    }

    @Override
    public ModuleKey getOriginal() {
      return original;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public String loadSource() throws IOException {
      return IoUtils.readString(url);
    }
  }

  private static class Virtual implements ResolvedModuleKey {
    final ModuleKey original;
    final URI uri;
    final String sourceText;
    final boolean cached;

    Virtual(ModuleKey original, URI uri, String sourceText, boolean cached) {
      this.original = original;
      this.uri = uri;
      this.sourceText = sourceText;
      this.cached = cached;
    }

    @Override
    public ModuleKey getOriginal() {
      return original;
    }

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public String loadSource() {
      return sourceText;
    }
  }

  private static class Delegated implements ResolvedModuleKey {

    private final ResolvedModuleKey delegate;
    private final ModuleKey original;

    Delegated(ResolvedModuleKey delegate, ModuleKey original) {
      this.delegate = delegate;
      this.original = original;
    }

    @Override
    public ModuleKey getOriginal() {
      return original;
    }

    @Override
    public URI getUri() {
      return delegate.getUri();
    }

    @Override
    public String loadSource() throws IOException {
      return delegate.loadSource();
    }
  }
}
