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
package org.pkl.core.util

import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BaseDirectoryTest {
  private val subject = BaseDirectories.config

  @Test
  fun `firstExistingPath() - finds a file within the XDG-configured home`(@TempDir tempDir: Path) {
    val xdgHome = tempDir.resolve("xdg-home").createDirectories()
    xdgHome.resolve("pkl/settings.pkl").createParentDirectories().createFile()
    val envVars = mapOf("XDG_CONFIG_HOME" to xdgHome.toString())

    assertThat(subject.firstExistingPath("settings.pkl", envVars, false))
      .isEqualTo(xdgHome.resolve("pkl/settings.pkl"))
  }

  @Test
  fun `firstExistingPath() - prefers home over dirs when both contain the file`(
    @TempDir tempDir: Path
  ) {
    val xdgHome = tempDir.resolve("xdg-home").createDirectories()
    val dir1 = tempDir.resolve("dir1").createDirectories()
    xdgHome.resolve("pkl/settings.pkl").createParentDirectories().createFile()
    dir1.resolve("settings.pkl").createFile()
    val envVars =
      mapOf("XDG_CONFIG_HOME" to xdgHome.toString(), "XDG_CONFIG_DIRS" to dir1.toString())

    assertThat(subject.firstExistingPath("settings.pkl", envVars, false))
      .isEqualTo(xdgHome.resolve("pkl/settings.pkl"))
  }

  @Test
  fun `firstExistingPath() - falls back to dirs when home does not contain the file`(
    @TempDir tempDir: Path
  ) {
    val xdgHome = tempDir.resolve("xdg-home").createDirectories()
    val dir1 = tempDir.resolve("dir1").createDirectories()
    dir1.resolve("settings.pkl").createFile()
    val envVars =
      mapOf("XDG_CONFIG_HOME" to xdgHome.toString(), "XDG_CONFIG_DIRS" to dir1.toString())

    assertThat(subject.firstExistingPath("settings.pkl", envVars, false))
      .isEqualTo(dir1.resolve("settings.pkl"))
  }

  @Test
  fun `firstExistingPath() - searches multiple dirs in order`(@TempDir tempDir: Path) {
    val xdgHome = tempDir.resolve("xdg-home").createDirectories()
    val dir1 = tempDir.resolve("dir1").createDirectories()
    val dir2 = tempDir.resolve("dir2").createDirectories()
    val expected =
      dir2.resolve("pkl/settings.pkl").also {
        it.createParentDirectories()
        it.createFile()
      }
    val envVars =
      mapOf(
        "XDG_CONFIG_HOME" to xdgHome.toString(),
        "XDG_CONFIG_DIRS" to "$dir1${File.pathSeparator}$dir2",
      )

    assertThat(subject.firstExistingPath("settings.pkl", envVars, false)).isEqualTo(expected)
  }

  @Test
  fun `firstExistingPath() - returns null when the file exists nowhere in the search hierarchy`(
    @TempDir tempDir: Path
  ) {
    val xdgHome = tempDir.resolve("xdg-home").createDirectories()
    val dir1 = tempDir.resolve("dir1").createDirectories()
    val envVars =
      mapOf("XDG_CONFIG_HOME" to xdgHome.toString(), "XDG_CONFIG_DIRS" to dir1.toString())

    assertThat(subject.firstExistingPath("missing.pkl", envVars, false)).isNull()
  }

  @Test
  fun `firstExistingPath() - XDG env var wins over the Windows env var even when isWindows is true`(
    @TempDir tempDir: Path
  ) {
    val xdgHome = tempDir.resolve("xdg-home").createDirectories()
    val appData = tempDir.resolve("app-data").createDirectories()
    xdgHome.resolve("pkl/settings.pkl").createParentDirectories().createFile()
    appData.resolve("pkl/settings.pkl").createParentDirectories().createFile()
    val envVars = mapOf("XDG_CONFIG_HOME" to xdgHome.toString(), "AppData" to appData.toString())

    assertThat(subject.firstExistingPath("settings.pkl", envVars, true))
      .isEqualTo(xdgHome.resolve("pkl/settings.pkl"))
  }

  @Test
  fun `firstExistingPath() - uses the Windows env var when the XDG env var is unset and isWindows is true`(
    @TempDir tempDir: Path
  ) {
    val appData = tempDir.resolve("app-data").createDirectories()
    appData.resolve("pkl/settings.pkl").createParentDirectories().createFile()
    val envVars = mapOf("APPDATA" to appData.toString())

    assertThat(subject.firstExistingPath("settings.pkl", envVars, true))
      .isEqualTo(appData.resolve("pkl/settings.pkl"))
  }

  @Test
  fun `firstExistingPath() - ignores the Windows env var when isWindows is false`(
    @TempDir tempDir: Path
  ) {
    val appData = tempDir.resolve("app-data").createDirectories()
    appData.resolve("pkl/settings.pkl").createParentDirectories().createFile()
    val envVars = mapOf("APPDATA" to appData.toString())

    assertThat(subject.firstExistingPath("settings.pkl", envVars, false)).isNull()
  }

  @Test
  fun `firstExistingPath() - appends the Windows subpath after 'pkl' when configured`(
    @TempDir tempDir: Path
  ) {
    val localAppData = tempDir.resolve("local-app-data").createDirectories()
    localAppData.resolve("pkl/Cache/cache.db").createParentDirectories().createFile()
    val envVars = mapOf("LOCALAPPDATA" to localAppData.toString())

    assertThat(BaseDirectories.cache.firstExistingPath("cache.db", envVars, true))
      .isEqualTo(localAppData.resolve("pkl/Cache/cache.db"))
  }

  @Test
  fun `firstExistingPath() - returns null when falling back to defaults that do not contain the file`() {
    // Doesn't touch the real filesystem: this subpath is not expected to exist under the real
    // `~/.config` or `/etc/xdg`, so the defaults are exercised without creating any real files.
    val subpath = "base-directory-test/definitely-does-not-exist.txt"
    assertThat(subject.firstExistingPath(subpath, emptyMap(), false)).isNull()
    assertThat(subject.firstExistingPath(subpath, emptyMap(), true)).isNull()
  }

  @Test
  fun `getHome() - appends 'pkl' to the default home directory when no env vars are set`() {
    val expected = Path.of(System.getProperty("user.home")).resolve(".config").resolve("pkl")

    assertThat(subject.getHome(emptyMap(), false)).isEqualTo(expected)
    // Same fallback applies on Windows when the Windows env var is also unset.
    assertThat(subject.getHome(emptyMap(), true)).isEqualTo(expected)
  }

  @Test
  fun `getHome() - appends 'pkl' to the default cache home when no env vars are set`() {
    assertThat(BaseDirectories.cache.getHome(emptyMap(), false))
      .isEqualTo(Path.of(System.getProperty("user.home")).resolve(".cache").resolve("pkl"))
  }

  @Test
  fun `getHome() - appends 'pkl' to the default state home when no env vars are set`() {
    assertThat(BaseDirectories.state.getHome(emptyMap(), false))
      .isEqualTo(Path.of(System.getProperty("user.home")).resolve(".local/state").resolve("pkl"))
  }

  @Test
  fun `getHome() - treats an empty XDG env var as unset`() {
    val expected = Path.of(System.getProperty("user.home")).resolve(".config").resolve("pkl")

    assertThat(subject.getHome(mapOf("XDG_CONFIG_HOME" to ""), false)).isEqualTo(expected)
  }

  @Test
  fun `getHome() - treats an empty Windows env var as unset`() {
    val expected = Path.of(System.getProperty("user.home")).resolve(".config").resolve("pkl")

    assertThat(subject.getHome(mapOf("APPDATA" to ""), true)).isEqualTo(expected)
  }

  @Test
  fun `getHome() - an empty XDG env var falls through to a configured Windows env var`(
    @TempDir tempDir: Path
  ) {
    val appData = tempDir.resolve("app-data")
    val envVars = mapOf("XDG_CONFIG_HOME" to "", "APPDATA" to appData.toString())

    assertThat(subject.getHome(envVars, true)).isEqualTo(appData.resolve("pkl"))
  }

  @Test
  fun `getDirs() - treats an empty XDG_CONFIG_DIRS as unset, falling back to defaults`() {
    assertThat(subject.getDirs(mapOf("XDG_CONFIG_DIRS" to "")))
      .containsExactly(Path.of("/etc/xdg").resolve("pkl"))
  }

  @Test
  fun `getDirs() - skips empty entries within an otherwise non-empty XDG_CONFIG_DIRS list`(
    @TempDir tempDir: Path
  ) {
    val dir1 = tempDir.resolve("dir1")
    val dir2 = tempDir.resolve("dir2")
    // A double separator produces a literal empty segment (`"/a::/b".split(":")` keeps the
    // middle `""`, unlike a trailing separator, which java.lang.String#split drops).
    val envVars = mapOf("XDG_CONFIG_DIRS" to "$dir1${File.pathSeparator}${File.pathSeparator}$dir2")

    assertThat(subject.getDirs(envVars)).containsExactly(dir1.resolve("pkl"), dir2.resolve("pkl"))
  }

  @Test
  fun `firstExistingPath() - does not crash when XDG_CONFIG_DIRS contains a leading empty segment`(
    @TempDir tempDir: Path
  ) {
    val dir1 = tempDir.resolve("dir1").createDirectories()
    val envVars = mapOf("XDG_CONFIG_DIRS" to "${File.pathSeparator}$dir1")

    assertThat(subject.firstExistingPath("missing.pkl", envVars, false)).isNull()
  }
}
