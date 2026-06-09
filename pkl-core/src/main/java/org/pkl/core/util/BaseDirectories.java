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

public final class BaseDirectories {
  public static final BaseDirectory config =
      new BaseDirectory(
          "XDG_CONFIG_HOME",
          "XDG_CONFIG_DIRS",
          "APPDATA",
          null,
          ".config",
          new String[] {"/etc/xdg"});

  public static final BaseDirectory cache =
      new BaseDirectory("XDG_CACHE_HOME", null, "LOCALAPPDATA", "Cache", ".cache", null);

  public static final BaseDirectory state =
      new BaseDirectory("XDG_STATE_HOME", null, "LOCALAPPDATA", null, ".local/state", null);
}
