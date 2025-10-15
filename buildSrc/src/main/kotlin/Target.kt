/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
@file:Suppress("MemberVisibilityCanBePrivate")

/**
 * A build target when building native libraries or executables.
 *
 * Pkl only builds for the following listed targets.
 */
enum class Target(val os: OS, val arch: Arch, val musl: Boolean) {
  MacosAarch64(os = OS.MacOS, arch = Arch.AARCH64, musl = false),
  MacosAmd64(os = OS.MacOS, arch = Arch.AMD64, musl = false),
  LinuxAarch64(os = OS.Linux, arch = Arch.AARCH64, musl = false),
  LinuxAmd64(os = OS.Linux, arch = Arch.AMD64, musl = false),
  AlpineLinuxAmd64(os = OS.Linux, arch = Arch.AMD64, musl = true),
  WindowsAmd64(os = OS.Windows, arch = Arch.AMD64, musl = false);

  companion object {
    fun from(os: OS, arch: Arch, musl: Boolean): Target {
      for (target in entries) {
        if (target.os == os && target.arch == arch && target.musl == musl) {
          return target
        }
      }
      throw IllegalArgumentException("Cannot build for $os-$arch with musl: $musl")
    }
  }

  val targetName: String
    get() {
      return if (musl) {
        assert(os == OS.Linux)
        "alpine-linux-$arch"
      } else "$os-$arch"
    }

  enum class Arch(
    /** What we call this arch */
    val simpleName: String,
    /** What the C compiler calls this arch */
    val cCompilerName: String,
  ) {
    AARCH64("aarch64", "arm64"),
    AMD64("amd64", "x86_64");

    override fun toString() = simpleName

    companion object {
      fun fromName(name: String): Arch =
        when (name) {
          "aarch64" -> AARCH64
          "amd64" -> AMD64
          else -> throw IllegalArgumentException("Unknown arch: $name")
        }
    }
  }

  enum class OS(
    val simpleName: String,
    val sharedLibraryExtension: String,
    val staticLibraryExtension: String,
    val objectFileExtension: String,
    val displayName: String,
  ) {
    MacOS("macos", "dylib", "a", "o", "macOS"),
    Linux("linux", "so", "a", "o", "Linux"),
    Windows("windows", "dll", "lib", "object", "Windows");

    override fun toString(): String = simpleName

    val isWindows: Boolean
      get() = this == Windows

    val isMacOS: Boolean
      get() = this == MacOS

    val isLinux: Boolean
      get() = this == Linux
  }
}
