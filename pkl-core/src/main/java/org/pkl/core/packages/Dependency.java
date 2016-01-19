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

import java.nio.file.Path;
import java.util.Objects;
import org.pkl.core.Version;
import org.pkl.core.util.Nullable;

public abstract class Dependency {

  protected final PackageUri packageUri;

  Dependency(PackageUri packageUri) {
    this.packageUri = packageUri;
  }

  public PackageUri getPackageUri() {
    return packageUri;
  }

  public Version getVersion() {
    return packageUri.getVersion();
  }

  public static final class LocalDependency extends Dependency {
    private final Path path;

    public LocalDependency(PackageUri packageUri, Path path) {
      super(packageUri);
      this.path = path;
    }

    public Path getPath() {
      return path;
    }

    public Path resolveAssetPath(Path projectDir, PackageAssetUri packageAssetUri) {
      // drop 1 to remove leading `/`
      var assetPath = packageAssetUri.getAssetPath().toString().substring(1);
      return projectDir.resolve(path).resolve(assetPath);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LocalDependency that = (LocalDependency) o;
      return packageUri.equals(that.packageUri) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
      return Objects.hash(packageUri, path);
    }

    @Override
    public String toString() {
      return "LocalDependency{" + "path=" + path + ", packageUri=" + packageUri + '}';
    }
  }

  /** Java representation of {@code pkl.Project#RemoteDependency}. */
  public static final class RemoteDependency extends Dependency {
    private @Nullable Checksums checksums;

    public RemoteDependency(PackageUri packageUri, @Nullable Checksums checksums) {
      super(packageUri);
      this.checksums = checksums;
    }

    public @Nullable Checksums getChecksums() {
      return checksums;
    }

    public void setChecksums(Checksums checksums) {
      this.checksums = checksums;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RemoteDependency that = (RemoteDependency) o;
      return packageUri.equals(that.packageUri) && Objects.equals(checksums, that.checksums);
    }

    @Override
    public int hashCode() {
      return Objects.hash(packageUri, checksums);
    }

    @Override
    public String toString() {
      return "RemoteDependency{" + "checksums=" + checksums + ", packageUri=" + packageUri + '}';
    }
  }
}
