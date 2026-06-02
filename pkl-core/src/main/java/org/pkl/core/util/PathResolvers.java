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

import org.pkl.core.util.PathResolver.PosixPathResolver;
import org.pkl.core.util.PathResolver.WindowsPathResolver;

public final class PathResolvers {
  private PathResolvers() {}

  private static final PathResolver WINDOWS = new WindowsPathResolver();

  private static final PathResolver POSIX = new PosixPathResolver();

  public static PathResolver forWindows() {
    return WINDOWS;
  }

  public static PathResolver forPosix() {
    return POSIX;
  }
}
