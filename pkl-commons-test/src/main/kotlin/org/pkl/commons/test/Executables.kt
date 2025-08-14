/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.commons.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.pkl.commons.test.FileTestUtils.rootProjectDir

sealed class ExecutablePaths(protected val gradleProject: String) {
  abstract val allNative: List<Path>

  val existingNative: List<Path>
    get() = allNative.filter(Files::exists)

  val firstExistingNative: Path
    get() =
      existingNative.firstOrNull()
        ?: throw AssertionError(
          "Native executable not found on system. " +
            "To fix this problem, run `./gradlew $gradleProject:assembleNative`."
        )

  protected fun executable(name: String): Path =
    rootProjectDir.resolve("$gradleProject/build/executable").resolve(name)

  protected fun javaExecutable(name: String): Path {
    val isWindows = System.getProperty("os.name").startsWith("Windows")
    val effectiveName = if (isWindows) "$name.bat" else name
    return rootProjectDir.resolve("$gradleProject/build/executable").resolve(effectiveName).also {
      path ->
      if (!path.exists()) {
        throw AssertionError(
          "Java executable not found on system. " +
            "To fix this problem, run `./gradlew $gradleProject:javaExecutable`."
        )
      }
    }
  }
}

@Suppress("ClassName")
object Executables {

  object pkl : ExecutablePaths("pkl-cli") {
    val macAarch64: Path = executable("pkl-macos-aarch64")
    val macAmd64: Path = executable("pkl-macos-amd64")
    val linuxAarch64: Path = executable("pkl-linux-aarch64")
    val linuxAmd64: Path = executable("pkl-linux-amd64")
    val alpineAmd64: Path = executable("pkl-alpine-linux-amd64")
    val windowsAmd64: Path = executable("pkl-windows-amd64.exe")

    // order (aarch64 before amd64, linux before alpine) affects [firstExisting]
    override val allNative: List<Path> =
      listOf(macAarch64, macAmd64, linuxAarch64, linuxAmd64, alpineAmd64, windowsAmd64)
  }

  object pkldoc : ExecutablePaths("pkl-doc") {
    val macAarch64: Path = executable("pkldoc-macos-aarch64")
    val macAmd64: Path = executable("pkldoc-macos-amd64")
    val linuxAarch64: Path = executable("pkldoc-linux-aarch64")
    val linuxAmd64: Path = executable("pkldoc-linux-amd64")
    val alpineAmd64: Path = executable("pkldoc-alpine-linux-amd64")
    val windowsAmd64: Path = executable("pkldoc-windows-amd64.exe")

    val javaExecutable: Path by lazy { javaExecutable("jpkldoc") }

    // order (aarch64 before amd64, linux before alpine) affects [firstExisting]
    override val allNative: List<Path> =
      listOf(macAarch64, macAmd64, linuxAarch64, linuxAmd64, alpineAmd64, windowsAmd64)
  }
}
