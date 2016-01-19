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

/** Information about the Pkl package index. */
public final class PklInfo {
  // TODO rdar://110376879
  private static final String PACKAGE_INDEX_HOMEPAGE = "https://pkl.apple.com/package-docs/";

  private static final PklInfo CURRENT;
  private final PackageIndex packageIndex;

  static {
    CURRENT = new PklInfo(new PackageIndex(PACKAGE_INDEX_HOMEPAGE));
  }

  /** The current {@link PklInfo}. */
  public static PklInfo current() {
    return CURRENT;
  }

  /** Constructs a {@link PklInfo}. */
  PklInfo(PackageIndex packageIndex) {
    this.packageIndex = packageIndex;
  }

  /** The Pkl package index. */
  public PackageIndex getPackageIndex() {
    return packageIndex;
  }

  /** A Pkl package index. */
  public static final class PackageIndex {
    private final String homepage;

    /** Constructs a {@link PackageIndex}. */
    public PackageIndex(String homepage) {
      this.homepage = homepage;
    }

    /** The homepage of this package index. */
    public String homepage() {
      return homepage;
    }

    /** Returns the homepage of the given package. */
    public String getPackagePage(String packageName, String packageVersion) {
      return homepage + packageName + "/" + packageVersion + "/";
    }
  }
}
