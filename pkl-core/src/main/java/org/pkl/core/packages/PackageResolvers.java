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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipInputStream;
import javax.annotation.concurrent.GuardedBy;
import javax.net.ssl.HttpsURLConnection;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.Release;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.module.FileResolver;
import org.pkl.core.module.PathElement;
import org.pkl.core.module.PathElement.TreePathElement;
import org.pkl.core.runtime.FileSystemManager;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.ByteArrayUtils;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;
import org.pkl.core.util.json.Json.JsonParseException;

class PackageResolvers {
  abstract static class AbstractPackageResolver implements PackageResolver {
    private static final String USER_AGENT;

    static {
      var release = Release.current();
      USER_AGENT = "Pkl/" + release.version() + " (" + release.os() + " " + release.flavor() + ")";
    }

    @GuardedBy("lock")
    private final EconomicMap<PackageUri, DependencyMetadata> cachedDependencyMetadata;

    private final SecurityManager securityManager;

    private final AtomicBoolean isClosed = new AtomicBoolean();

    protected final Object lock = new Object();

    protected AbstractPackageResolver(SecurityManager securityManager) {
      this.securityManager = securityManager;
      cachedDependencyMetadata = EconomicMaps.create();
    }

    /** Retrieves a dependency's metadata file. */
    public DependencyMetadata getDependencyMetadata(PackageUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      checkNotClosed();
      synchronized (lock) {
        var metadata = cachedDependencyMetadata.get(uri);
        if (metadata == null) {
          metadata = doGetDependencyMetadata(uri, checksums);
          cachedDependencyMetadata.put(uri, metadata);
        }
        return metadata;
      }
    }

    @Override
    public Pair<DependencyMetadata, Checksums> getDependencyMetadataAndComputeChecksum(
        PackageUri packageUri) throws IOException, SecurityManagerException {
      var requestUri = packageUri.getMetadataRequestUri();
      var inputStream = openExternalUri(requestUri);
      return readDependencyMetadataAndComputeChecksum(packageUri, inputStream);
    }

    protected Pair<DependencyMetadata, Checksums> readDependencyMetadataAndComputeChecksum(
        PackageUri packageUri, InputStream inputStream) throws IOException {
      try (var in = newDigestInputStream(inputStream)) {
        var bytes = in.readAllBytes();
        var dependencyMetadata =
            DependencyMetadata.parse(new String(bytes, StandardCharsets.UTF_8));
        var checksums = new Checksums(ByteArrayUtils.toHex(in.getMessageDigest().digest()));
        return Pair.of(dependencyMetadata, checksums);
      } catch (JsonParseException e) {
        throw new PackageLoadError(
            e,
            "invalidDependencyMetadata",
            packageUri.getDisplayName(),
            packageUri.getMetadataRequestUri(),
            e.getMessage());
      }
    }

    @Override
    public List<PathElement> listElements(PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      checkNotClosed();
      return doListElements(uri, checksums);
    }

    @Override
    public boolean hasElement(PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      checkNotClosed();
      return doHasElement(uri, checksums);
    }

    @Override
    public void close() throws IOException {
      if (!isClosed.getAndSet(true)) {
        synchronized (lock) {
          cachedDependencyMetadata.clear();
        }
      }
    }

    protected DigestInputStream newDigestInputStream(InputStream in) {
      try {
        var md = MessageDigest.getInstance("SHA-256");
        return new DigestInputStream(in, md);
      } catch (NoSuchAlgorithmException e) {
        // All JDK's ship with SHA-256
        throw new VmExceptionBuilder().unreachableCode().build();
      }
    }

    protected void verifyPackageZipBytes(
        PackageUri packageUri, DependencyMetadata dependencyMetadata, byte[] computedChecksum) {
      var checksum = ByteArrayUtils.toHex(computedChecksum);
      var expectedChecksum = dependencyMetadata.getPackageZipChecksums().getSha256();
      if (!checksum.equals(expectedChecksum)) {
        throw new PackageLoadError(
            "invalidPackageZipChecksum",
            packageUri.getDisplayName(),
            checksum,
            expectedChecksum,
            dependencyMetadata.getPackageZipUrl());
      }
    }

    protected void verifyPackageMetadataBytes(
        PackageUri packageUri, URI requestUri, Checksums checksums, byte[] computedChecksum) {
      var expectedChecksum = checksums.getSha256();
      var checksum = ByteArrayUtils.toHex(computedChecksum);
      // Qualify of life improvement: we have a lot of projects in our language snippet tests.
      // To avoid having to update checksum values in their PklProject.deps.json files, every time
      // a package changes, we set their checksum value to "$skipChecksumVerification".
      // We keep two tests that do test checksum verification.
      if (IoUtils.isTestMode() && expectedChecksum.equals("$skipChecksumVerification")) {
        return;
      }
      if (!checksum.equals(expectedChecksum)) {
        throw new PackageLoadError(
            "invalidPackageMetadataChecksum",
            packageUri.getDisplayName(),
            checksum,
            expectedChecksum,
            requestUri);
      }
    }

    protected InputStream openExternalUri(URI uri) throws SecurityManagerException, IOException {
      // treat package assets as resources instead of modules
      securityManager.checkReadResource(uri);
      var connection = (HttpsURLConnection) uri.toURL().openConnection();
      connection.setRequestProperty("User-Agent", USER_AGENT);
      int responseCode;
      try {
        responseCode = connection.getResponseCode();
        if (responseCode != 200) {
          throw new PackageLoadError("badHttpStatusCode", responseCode, uri);
        }
      } catch (IOException e) {
        throw new PackageLoadError(e, "ioErrorMakingHttpGet", uri, e.getMessage());
      }
      return connection.getInputStream();
    }

    protected IOException fileIsADirectory() {
      // Sync with error message from `Files#readString(Path)`
      return new IOException("Is a directory");
    }

    protected abstract DependencyMetadata doGetDependencyMetadata(
        PackageUri packageUri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException;

    protected abstract List<PathElement> doListElements(
        PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException;

    protected abstract boolean doHasElement(PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException;

    protected PackageLoadError invalidPackageZipUrl(
        PackageUri packageUri, DependencyMetadata dependencyMetadata) {
      return new PackageLoadError(
          "invalidPackageZipUrl",
          packageUri.getDisplayName(),
          dependencyMetadata.getPackageZipUrl());
    }

    private void checkNotClosed() {
      if (isClosed.get()) {
        throw new IllegalStateException("Package resolver has already been closed.");
      }
    }
  }

  /**
   * A package resolver that holds entire package contents in memory.
   *
   * <p>This gets used when the cache dir is not set.
   */
  static class InMemoryPackageResolver extends AbstractPackageResolver {
    @GuardedBy("lock")
    private final EconomicMap<PackageUri, EconomicMap<String, ByteBuffer>> cachedEntries =
        EconomicMaps.create();

    @GuardedBy("lock")
    private final EconomicMap<PackageUri, TreePathElement> cachedTreePathElementRoots =
        EconomicMaps.create();

    InMemoryPackageResolver(SecurityManager securityManager) {
      super(securityManager);
    }

    private byte[] getPackageBytes(PackageUri packageUri, DependencyMetadata metadata)
        throws IOException, SecurityManagerException {
      var httpInputStream = openExternalUri(metadata.getPackageZipUrl());
      try (var digestInputStream = newDigestInputStream(httpInputStream)) {
        var packageBytes = digestInputStream.readAllBytes();
        var checksumBytes = digestInputStream.getMessageDigest().digest();
        verifyPackageZipBytes(packageUri, metadata, checksumBytes);
        return packageBytes;
      }
    }

    private void ensurePackageDownloaded(PackageUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      synchronized (lock) {
        if (cachedEntries.containsKey(uri)) {
          return;
        }
        var metadata = getDependencyMetadata(uri, checksums);
        var cachedEntrySet = EconomicMaps.<String, ByteBuffer>create();
        var packageBytes = getPackageBytes(uri, metadata);
        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
          var rootPathElement = new TreePathElement("", true);
          cachedTreePathElementRoots.put(uri, rootPathElement);
          for (var entry = zipInputStream.getNextEntry();
              entry != null;
              entry = zipInputStream.getNextEntry()) {
            var pathElement = rootPathElement;
            var nameParts = entry.getName().split("/");
            var nameCount = nameParts.length;
            for (var i = 0; i < nameCount; i++) {
              var name = nameParts[i];
              var isDirectory = entry.isDirectory() || i < (nameCount - 1);
              pathElement = pathElement.putIfAbsent(name, new TreePathElement(name, isDirectory));
            }
            if (!entry.isDirectory()) {
              var entryBytes = zipInputStream.readAllBytes();
              cachedEntrySet.put("/" + entry.getName(), ByteBuffer.wrap(entryBytes));
            }
          }
        }
        cachedEntries.put(uri, cachedEntrySet);
      }
    }

    // No sense in supporting this in the in-memory package resolver, because it cannot write
    // anything to disk.
    @Override
    public void downloadPackage(
        PackageUri uri, @Nullable Checksums checksums, boolean noTransitive) {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytes(
        PackageAssetUri uri, boolean allowDirectories, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var packageUri = uri.getPackageUri();
      ensurePackageDownloaded(packageUri, checksums);
      var elem = cachedTreePathElementRoots.get(uri.getPackageUri()).getElement(uri.getAssetPath());
      if (elem == null) {
        throw new FileNotFoundException();
      } else if (elem.isDirectory()) {
        if (allowDirectories) {
          var text =
              StreamSupport.stream(elem.getChildren().getKeys().spliterator(), false)
                      .sorted()
                      .collect(Collectors.joining("\n"))
                  + "\n";
          return text.getBytes(StandardCharsets.UTF_8);
        }
        throw fileIsADirectory();
      }
      var entries = cachedEntries.get(packageUri);
      // need to normalize here but not in `doListElments` nor `doHasElement` because
      // `TreePathElement.getElement` does normalization already.
      var path = uri.getAssetPath().normalize().toString();
      assert path.startsWith("/");
      return entries.get(path).array();
    }

    @Override
    public List<PathElement> doListElements(PackageAssetUri uri, Checksums checksums)
        throws IOException, SecurityManagerException {
      var packageUri = uri.getPackageUri();
      ensurePackageDownloaded(packageUri, checksums);
      var element = cachedTreePathElementRoots.get(packageUri).getElement(uri.getAssetPath());
      if (element == null) {
        return Collections.emptyList();
      }
      return element.getChildrenValues();
    }

    @Override
    public boolean doHasElement(PackageAssetUri uri, Checksums checksums)
        throws IOException, SecurityManagerException {
      var packageUri = uri.getPackageUri();
      ensurePackageDownloaded(packageUri, checksums);
      var element = cachedTreePathElementRoots.get(packageUri).getElement(uri.getAssetPath());
      return element != null;
    }

    @Override
    protected DependencyMetadata doGetDependencyMetadata(
        PackageUri packageUri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var requestUri = packageUri.getMetadataRequestUri();
      var inputStream = openExternalUri(requestUri);
      if (checksums != null) {
        inputStream = newDigestInputStream(inputStream);
      }
      try (var in = inputStream) {
        var metadataStr = IoUtils.readString(in);
        if (checksums != null) {
          var digestInputStream = (DigestInputStream) in;
          var checksumBytes = digestInputStream.getMessageDigest().digest();
          verifyPackageMetadataBytes(packageUri, requestUri, checksums, checksumBytes);
        }
        var metadata = DependencyMetadata.parse(metadataStr);
        if (!metadata.getPackageZipUrl().getScheme().equalsIgnoreCase("https")) {
          throw invalidPackageZipUrl(packageUri, metadata);
        }
        return metadata;
      } catch (JsonParseException e) {
        throw new PackageLoadError(
            e,
            "invalidDependencyMetadata",
            packageUri.getDisplayName(),
            requestUri,
            e.getMessage());
      }
    }

    @Override
    public void close() throws IOException {
      super.close();
      synchronized (lock) {
        cachedEntries.clear();
        cachedTreePathElementRoots.clear();
      }
    }
  }

  /**
   * Resolves packages, caching them to disk.
   *
   * <p>Uses the built-in zip file system in {@link jdk.nio.zipfs} for reading files from the zip
   * archive.
   */
  static class DiskCachedPackageResolver extends AbstractPackageResolver {
    private final Path cacheDir;

    private final Path tmpDir;

    private static final String CACHE_DIR_PREFIX = "package-1";

    @GuardedBy("lock")
    private final EconomicMap<PackageUri, FileSystem> fileSystems = EconomicMaps.create();

    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ);

    public DiskCachedPackageResolver(SecurityManager securityManager, Path cacheDir) {
      super(securityManager);
      this.cacheDir = cacheDir;
      this.tmpDir = cacheDir.resolve("tmp");
    }

    private String getEffectivePackageUriPath(PackageUri packageUri) {
      var path = packageUri.getUri().getPath();
      assert path != null;
      if (packageUri.getChecksums() == null) {
        return path;
      }
      var checksumIdx = path.lastIndexOf("::");
      return path.substring(0, checksumIdx);
    }

    private Path getRelativePath(PackageUri uri) {
      return Path.of(
          CACHE_DIR_PREFIX, uri.getUri().getAuthority(), getEffectivePackageUriPath(uri));
    }

    private String getLastSegmentName(PackageUri packageUri) {
      var path = getEffectivePackageUriPath(packageUri);
      var lastSep = path.lastIndexOf("/");
      return path.substring(lastSep + 1);
    }

    private byte[] downloadUriToPathAndComputeChecksum(URI downloadUri, Path path)
        throws IOException, SecurityManagerException {
      Files.createDirectories(path.getParent());
      var inputStream = openExternalUri(downloadUri);
      try (var digestInputStream = newDigestInputStream(inputStream)) {
        Files.copy(digestInputStream, path);
        return digestInputStream.getMessageDigest().digest();
      }
    }

    private void downloadMetadata(
        PackageUri packageUri, URI downloadUri, Path path, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var inputStream = openExternalUri(downloadUri);
      if (checksums != null) {
        inputStream = newDigestInputStream(inputStream);
      }
      Files.createDirectories(path.getParent());
      try (var in = inputStream) {
        Files.copy(in, path);
        if (checksums != null) {
          var digestInputStream = (DigestInputStream) inputStream;
          var checksumBytes = digestInputStream.getMessageDigest().digest();
          verifyPackageMetadataBytes(packageUri, downloadUri, checksums, checksumBytes);
        }
      }
    }

    private Path getMetadataPath(
        PackageUri packageUri, URI requestUri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var metadataFileName = getLastSegmentName(packageUri) + ".json";
      var metadataRelativePath = getRelativePath(packageUri).resolve(metadataFileName);
      var cachePath = cacheDir.resolve(metadataRelativePath);
      if (Files.exists(cachePath)) {
        return cachePath;
      }
      var tmpPath = tmpDir.resolve(metadataRelativePath);
      try {
        downloadMetadata(packageUri, requestUri, tmpPath, checksums);
        Files.createDirectories(cachePath.getParent());
        Files.move(tmpPath, cachePath, StandardCopyOption.ATOMIC_MOVE);
        Files.setPosixFilePermissions(cachePath, FILE_PERMISSIONS);
        return cachePath;
      } finally {
        Files.deleteIfExists(tmpPath);
      }
    }

    /** Retrieves a dependency's metadata file. */
    @Override
    protected DependencyMetadata doGetDependencyMetadata(
        PackageUri packageUri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var requestUri = packageUri.getMetadataRequestUri();
      var metadataPath = getMetadataPath(packageUri, requestUri, checksums);
      var metadataStr = Files.readString(metadataPath, StandardCharsets.UTF_8);
      DependencyMetadata metadata;
      try {
        metadata = DependencyMetadata.parse(metadataStr);
      } catch (JsonParseException e) {
        Files.deleteIfExists(metadataPath);
        throw new PackageLoadError(
            e,
            "invalidDependencyMetadata",
            packageUri.getDisplayName(),
            requestUri,
            e.getMessage());
      }
      if (!metadata.getPackageZipUrl().getScheme().equalsIgnoreCase("https")) {
        Files.deleteIfExists(metadataPath);
        throw invalidPackageZipUrl(packageUri, metadata);
      }
      return metadata;
    }

    private Path getZipFilePath(PackageUri packageUri, DependencyMetadata dependencyMetadata)
        throws IOException, SecurityManagerException {
      var packageZipName = getLastSegmentName(packageUri) + ".zip";
      var relativePath = getRelativePath(packageUri).resolve(packageZipName);
      var cachePath = cacheDir.resolve(relativePath);
      if (Files.exists(cachePath)) {
        return cachePath;
      }
      var tmpPath = tmpDir.resolve(relativePath);
      try {
        var checksumBytes =
            downloadUriToPathAndComputeChecksum(dependencyMetadata.getPackageZipUrl(), tmpPath);
        verifyPackageZipBytes(packageUri, dependencyMetadata, checksumBytes);
        Files.createDirectories(cachePath.getParent());
        Files.move(tmpPath, cachePath, StandardCopyOption.ATOMIC_MOVE);
        Files.setPosixFilePermissions(cachePath, FILE_PERMISSIONS);
        return cachePath;
      } finally {
        Files.deleteIfExists(tmpPath);
      }
    }

    /**
     * Returns a file system that backs the zip archive for a package.
     *
     * <p>Downloads the package if not available within {@link DiskCachedPackageResolver#cacheDir}.
     */
    private FileSystem getZipFileSystem(PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var packageUri = uri.getPackageUri();
      synchronized (lock) {
        var fs = fileSystems.get(packageUri);
        if (fs == null) {
          var metadata = getDependencyMetadata(packageUri, checksums);
          var zipFilePath = getZipFilePath(packageUri, metadata);
          var jarFileUri = URI.create("jar:" + zipFilePath.toUri());
          fs = FileSystemManager.getFileSystem(jarFileUri);
          fileSystems.put(packageUri, fs);
        }
        return fs;
      }
    }

    @Override
    public void downloadPackage(PackageUri uri, @Nullable Checksums checksums, boolean noTransitive)
        throws IOException, SecurityManagerException {
      var metadata = getDependencyMetadata(uri, checksums);
      getZipFilePath(uri, metadata);
      if (noTransitive) {
        return;
      }
      for (var dependency : metadata.getDependencies().values()) {
        downloadPackage(dependency.getPackageUri(), dependency.getChecksums(), false);
      }
    }

    @Override
    public Pair<DependencyMetadata, Checksums> getDependencyMetadataAndComputeChecksum(
        PackageUri packageUri) throws IOException, SecurityManagerException {
      var packageDir = cacheDir.resolve(getRelativePath(packageUri));
      var cacheFile = packageDir.resolve(packageDir.getFileName() + ".json");
      if (Files.exists(cacheFile)) {
        return readDependencyMetadataAndComputeChecksum(
            packageUri, new FileInputStream(cacheFile.toFile()));
      }
      return super.getDependencyMetadataAndComputeChecksum(packageUri);
    }

    /** Reads the byte contents of the resource within a package. */
    @Override
    public byte[] getBytes(
        PackageAssetUri uri, boolean allowDirectories, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var path = getZipFileSystem(uri, checksums).getPath(uri.getUri().getFragment());
      if (Files.isDirectory(path)) {
        if (allowDirectories) {
          // mimic the format that we get when reading a `file:` directory
          try (var paths = Files.list(path)) {
            var text =
                paths
                        .map(it -> it.getFileName().toString())
                        .sorted()
                        .collect(Collectors.joining("\n"))
                    + "\n";
            return text.getBytes(StandardCharsets.UTF_8);
          }
        }
        throw fileIsADirectory();
      }
      try {
        return Files.readAllBytes(path);
      } catch (NoSuchFileException e) {
        throw new FileNotFoundException();
      }
    }

    @Override
    protected List<PathElement> doListElements(PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var path = getZipFileSystem(uri, checksums).getPath(uri.getUri().getFragment());
      return FileResolver.listElements(path);
    }

    @Override
    public boolean doHasElement(PackageAssetUri uri, @Nullable Checksums checksums)
        throws IOException, SecurityManagerException {
      var path = getZipFileSystem(uri, checksums).getPath(uri.getUri().getFragment());
      return FileResolver.hasElement(path);
    }

    @Override
    public void close() throws IOException {
      super.close();
      synchronized (lock) {
        var cursor = fileSystems.getEntries();
        while (cursor.advance()) {
          cursor.getValue().close();
        }
        fileSystems.clear();
      }
    }
  }
}
