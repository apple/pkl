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
import java.util.function.Function
import kotlin.io.path.createDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.util.IoUtils

class ExecutorOptionsTest {
  // `ExecutorOptions.defaultModuleCacheDir()` inlines the XDG/legacy fallback because pkl-executor
  // cannot depend on pkl-core. This guards against drift from `IoUtils.getDefaultModuleCacheDir()`.
  @Test
  fun `defaultModuleCacheDir stays in sync with pkl-core across home states`(@TempDir home: Path) {
    val original = System.getProperty("user.home")
    try {
      System.setProperty("user.home", home.toString())

      // fresh home -> XDG location
      assertThat(ExecutorOptions.defaultModuleCacheDir())
        .isEqualTo(home.resolve(".cache").resolve("pkl"))
      assertThat(ExecutorOptions.defaultModuleCacheDir())
        .isEqualTo(IoUtils.getDefaultModuleCacheDir())

      // pre-existing legacy cache -> legacy location
      home.resolve(".pkl").resolve("cache").createDirectories()
      assertThat(ExecutorOptions.defaultModuleCacheDir())
        .isEqualTo(home.resolve(".pkl").resolve("cache"))
      assertThat(ExecutorOptions.defaultModuleCacheDir())
        .isEqualTo(IoUtils.getDefaultModuleCacheDir())
    } finally {
      System.setProperty("user.home", original)
    }
  }

  private fun env(vararg entries: Pair<String, String?>): Function<String, String?> {
    val map = entries.toMap()
    return Function { name -> map[name] }
  }

  @Test
  fun `defaultModuleCacheDir on Windows uses LOCALAPPDATA when set`(@TempDir home: Path) {
    val localAppData = home.resolve("LocalAppData").createDirectories()
    assertThat(
        ExecutorOptions.defaultModuleCacheDir(
          home,
          true,
          env("LOCALAPPDATA" to localAppData.toString()),
        )
      )
      .isEqualTo(localAppData.resolve("pkl").resolve("cache"))
  }

  @Test
  fun `defaultModuleCacheDir on Windows falls back to Unix layout when LOCALAPPDATA is unset`(
    @TempDir home: Path
  ) {
    assertThat(ExecutorOptions.defaultModuleCacheDir(home, true, env()))
      .isEqualTo(home.resolve(".cache").resolve("pkl"))
  }

  @Test
  fun `defaultModuleCacheDir on Windows treats empty LOCALAPPDATA like unset`(@TempDir home: Path) {
    assertThat(ExecutorOptions.defaultModuleCacheDir(home, true, env("LOCALAPPDATA" to "")))
      .isEqualTo(home.resolve(".cache").resolve("pkl"))
  }

  @Test
  fun `defaultModuleCacheDir on Windows still falls back to legacy ~_pkl_cache`(
    @TempDir home: Path
  ) {
    val localAppData = home.resolve("LocalAppData").createDirectories()
    home.resolve(".pkl").resolve("cache").createDirectories()
    assertThat(
        ExecutorOptions.defaultModuleCacheDir(
          home,
          true,
          env("LOCALAPPDATA" to localAppData.toString()),
        )
      )
      .isEqualTo(home.resolve(".pkl").resolve("cache"))
  }
}
