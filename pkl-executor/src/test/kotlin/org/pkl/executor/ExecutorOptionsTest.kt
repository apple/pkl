/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.executor

import java.nio.file.Path
import kotlin.io.path.createDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.util.IoUtils

class ExecutorOptionsTest {
  // `ExecutorOptions.defaultModuleCacheDir()` inlines the XDG/legacy fallback because pkl-executor
  // cannot depend on pkl-core. This guards against drift from `IoUtils.getDefaultModuleCacheDir()`.
  @Test
  fun `defaultModuleCacheDir stays in sync with pkl-core`(@TempDir home: Path) {
    val original = System.getProperty("user.home")
    try {
      System.setProperty("user.home", home.toString())
      assertThat(ExecutorOptions.defaultModuleCacheDir())
        .isEqualTo(home.resolve(".cache").resolve("pkl"))
      assertThat(ExecutorOptions.defaultModuleCacheDir())
        .isEqualTo(IoUtils.getSystemModuleCacheDir())
    } finally {
      System.setProperty("user.home", original)
    }
  }

  @Test
  fun `defaultModuleCacheDir on Windows uses LOCALAPPDATA when set`(@TempDir home: Path) {
    val localAppData = home.resolve("LocalAppData").createDirectories()
    assertThat(
        ExecutorOptions.defaultModuleCacheDir(
          home,
          true,
          mapOf("LOCALAPPDATA" to localAppData.toString()),
        )
      )
      .isEqualTo(localAppData.resolve("pkl").resolve("Cache"))
  }

  @Test
  fun `defaultModuleCacheDir on Windows falls back to Unix layout when LOCALAPPDATA is unset`(
    @TempDir home: Path
  ) {
    assertThat(ExecutorOptions.defaultModuleCacheDir(home, true, mapOf()))
      .isEqualTo(home.resolve(".cache").resolve("pkl"))
  }

  @Test
  fun `defaultModuleCacheDir on Windows still falls XDG style default dir`(@TempDir home: Path) {
    home.resolve(".pkl").resolve("cache").createDirectories()
    assertThat(ExecutorOptions.defaultModuleCacheDir(home, true, mapOf()))
      .isEqualTo(home.resolve(".cache").resolve("pkl"))
  }
}
