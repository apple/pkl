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
package org.pkl.core.module;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.GuardedBy;
import org.pkl.core.module.PathElement.TreePathElement;
import org.pkl.core.runtime.FileSystemManager;
import org.pkl.core.util.LateInit;

/**
 * Resolves {@code modulepath} URIs from ZIP or JAR files, or from directory paths.
 *
 * <p>NOTE: Do not initialize two resolvers for the same jar or zip file. Instead, share the same
 * resolver for that jar or zip file.
 */
public class ModulePathResolver implements AutoCloseable {
  private final Iterable<Path> modulePath;

  private final Object lock = new Object();

  @LateInit
  @GuardedBy("lock")
  private Map<String, Path> fileCache;

  @LateInit
  @GuardedBy("lock")
  private List<FileSystem> zipFileSystems;

  @LateInit
  @GuardedBy("lock")
  private TreePathElement cachedPathElementRoot;

  private @GuardedBy("lock") boolean isClosed = false;

  private static final ModulePathResolver EMPTY = new ModulePathResolver(Collections.emptyList());

  public static ModulePathResolver empty() {
    return EMPTY;
  }

  public ModulePathResolver(Iterable<Path> modulePath) {
    this.modulePath = modulePath;
  }

  private void populateCaches() throws IOException {
    fileCache = new HashMap<>();
    zipFileSystems = new ArrayList<>();
    cachedPathElementRoot = new TreePathElement("", false);
    for (var entry : modulePath) {
      if (Files.isDirectory(entry)) {
        populateFileCache(entry);
      } else if (isJarOrZipFile(entry)) {
        // Use the constructor that accepts a jar-file URI because
        // this enables `Paths.get("jar:file...")` API calls.
        var zipFileSystem = FileSystemManager.getFileSystem(URI.create("jar:" + entry.toUri()));
        zipFileSystems.add(zipFileSystem);
        for (var root : zipFileSystem.getRootDirectories()) {
          populateFileCache(root);
        }
      }
      // ignore
    }
  }

  private Map<String, Path> getFileCache() throws IOException {
    synchronized (lock) {
      if (isClosed) {
        throw new IllegalStateException("Module path loader has already been closed.");
      }

      // create cache lazily so that we only pay the cost if/when a modulepath: URI is first
      // resolved
      if (fileCache == null) {
        populateCaches();
      }

      return fileCache;
    }
  }

  public Path resolve(URI uri) throws IOException {
    var modulePath = getModulePath(uri);
    var result = getFileCache().get(modulePath);
    if (result != null) return result;

    throw new FileNotFoundException();
  }

  public boolean hasElement(URI elementUri) {
    var path = elementUri.getPath();
    try {
      assert path.charAt(0) == '/';
      return getFileCache().containsKey(path.substring(1));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (isClosed || fileCache == null) return;

      for (var fileSystem : zipFileSystems) {
        try {
          fileSystem.close();
        } catch (IOException ignored) {
        }
      }
      fileCache = null;
      cachedPathElementRoot = null;
      zipFileSystems = null;
      isClosed = true;
    }
  }

  private void populateFileCache(Path basePath) throws IOException {
    try (var stream =
        Files.find(
            basePath,
            Integer.MAX_VALUE,
            // reduce file cache size by filtering out .class files
            // (currently `read()` only supports text resources, no known use case for accessing
            // .class files from Pkl code)
            (path, attributes) ->
                attributes.isRegularFile() && !path.toString().endsWith(".class"))) {
      // in case of duplicate path, first entry wins (cf. class loader)
      stream.forEach(
          (path) -> {
            var relativized = basePath.relativize(path);
            fileCache.putIfAbsent(relativized.toString(), path);
            var element = cachedPathElementRoot;
            for (var i = 0; i < relativized.getNameCount(); i++) {
              var name = relativized.getName(i).toString();
              var isDirectory = i < (relativized.getNameCount() - 1);
              element = element.putIfAbsent(name, new TreePathElement(name, isDirectory));
            }
          });
    }
  }

  private static boolean isJarOrZipFile(Path path) {
    if (!Files.isRegularFile(path)) return false;

    if (path.endsWith(".jar") || path.endsWith(".zip")) return true;

    byte[] buffer;
    try (var fis = new FileInputStream(path.toFile())) {
      buffer = fis.readNBytes(39);
    } catch (IOException e) {
      return false;
    }
    // file starts with zip header
    if (buffer[0] == 0x50 && buffer[1] == 0x4b && buffer[2] == 0x03 && buffer[3] == 0x04) {
      return true;
    }
    // executable jar, e.g. jpkl
    return Arrays.equals(
        buffer, "#!/bin/sh\n      exec java  -jar $0 \"$@\"".getBytes(StandardCharsets.UTF_8));
  }

  private static String getModulePath(URI moduleUri) {
    var path = moduleUri.getPath();
    assert path.charAt(0) == '/';
    return path.substring(1);
  }
}
