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
package org.pkl.core.packages;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.naming.OperationNotSupportedException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.module.PathElement;
import org.pkl.core.packages.PackageResolvers.DiskCachedPackageResolver;
import org.pkl.core.packages.PackageResolvers.InMemoryPackageResolver;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

public interface PackageResolver extends Closeable {

  static PackageResolver getInstance(SecurityManager securityManager, @Nullable Path cachedDir) {
    return cachedDir == null
        ? new InMemoryPackageResolver(securityManager)
        : new DiskCachedPackageResolver(securityManager, cachedDir);
  }

  DependencyMetadata getDependencyMetadata(PackageUri uri, @Nullable Checksums checksums)
      throws IOException, SecurityManagerException;

  Pair<DependencyMetadata, Checksums> getDependencyMetadataAndComputeChecksum(PackageUri packageUri)
      throws IOException, SecurityManagerException;

  void downloadPackage(PackageUri uri, @Nullable Checksums checksums, boolean noTransitive)
      throws OperationNotSupportedException, IOException, SecurityManagerException;

  /** Reads the byte contents of the resource within a package. */
  byte[] getBytes(PackageAssetUri uri, boolean allowDirectories, @Nullable Checksums checksums)
      throws IOException, SecurityManagerException;

  List<PathElement> listElements(PackageAssetUri uri, @Nullable Checksums checksums)
      throws IOException, SecurityManagerException;

  boolean hasElement(PackageAssetUri uri, @Nullable Checksums checksums)
      throws IOException, SecurityManagerException;
}
