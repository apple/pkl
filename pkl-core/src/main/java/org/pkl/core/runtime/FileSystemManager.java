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
package org.pkl.core.runtime;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.util.EconomicMaps;

/**
 * Manages file systems, potentially across multiple evaluator instances.
 *
 * <p>File systems are only closed when the last usage of it closes.
 */
public final class FileSystemManager {
  private FileSystemManager() {}

  private static final EconomicMap<URI, FileSystem> fileSystems = EconomicMaps.create();

  private static final Map<FileSystem, Integer> counts = new IdentityHashMap<>();

  private static final List<FileSystem> externalFileSystems = new ArrayList<>();

  public static synchronized FileSystem getFileSystem(URI uri) throws IOException {
    var fs = fileSystems.get(uri);
    if (fs != null) {
      counts.put(fs, counts.get(fs) + 1);
      return fs;
    }
    try {
      fs = new Handle(FileSystems.newFileSystem(uri, new HashMap<>()));
      fileSystems.put(uri, fs);
      counts.put(fs, 1);
      return fs;
    } catch (FileSystemAlreadyExistsException e) {
      fs = new Handle(FileSystems.getFileSystem(uri));
      // Something other than Pkl is holding onto this file system.
      // Mark it as external, so Pkl does not close it.
      externalFileSystems.add(fs);
      fileSystems.put(uri, fs);
      counts.put(fs, 1);
      return fs;
    }
  }

  /**
   * Possibly close this file system. Will not close if the file system was initialized externally
   * to Pkl.
   */
  private static synchronized void close(Handle fs) throws IOException {
    var count = counts.get(fs) - 1;
    if (count > 0) {
      counts.put(fs, count);
      return;
    }
    counts.remove(fs);
    var cursor = fileSystems.getEntries();
    while (cursor.advance()) {
      var fileSystem = cursor.getValue();
      if (fileSystem.equals(fs)) {
        var key = cursor.getKey();
        //noinspection resource
        fileSystems.removeKey(key);
        break;
      }
    }
    var isExternal = externalFileSystems.contains(fs);
    if (isExternal) {
      externalFileSystems.remove(fs);
    } else {
      fs.delegate.close();
    }
  }

  private static final class Handle extends FileSystem {

    final FileSystem delegate;

    public Handle(FileSystem delegate) {
      this.delegate = delegate;
    }

    @Override
    public FileSystemProvider provider() {
      return delegate.provider();
    }

    @Override
    public void close() throws IOException {
      FileSystemManager.close(this);
    }

    @Override
    public boolean isOpen() {
      return delegate.isOpen();
    }

    @Override
    public boolean isReadOnly() {
      return delegate.isReadOnly();
    }

    @Override
    public String getSeparator() {
      return delegate.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
      return delegate.getRootDirectories();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
      return delegate.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
      return delegate.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
      return delegate.getPath(first, more);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
      return delegate.getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
      return delegate.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
      return delegate.newWatchService();
    }
  }
}
