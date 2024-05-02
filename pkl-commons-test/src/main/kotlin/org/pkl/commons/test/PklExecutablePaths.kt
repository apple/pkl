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
package org.pkl.commons.test

import java.nio.file.Files
import java.nio.file.Path
import org.pkl.commons.test.FileTestUtils.rootProjectDir

object PklExecutablePaths {
  val macAarch64: Path = executablePath("pkl-macos-aarch64")
  val macAmd64: Path = executablePath("pkl-macos-amd64")
  val linuxAarch64: Path = executablePath("pkl-linux-aarch64")
  val linuxAmd64: Path = executablePath("pkl-linux-amd64")
  val alpineAmd64: Path = executablePath("pkl-alpine-linux-amd64")
  val windowsAmd64: Path = executablePath("pkl-windows-amd64.exe")

  // order (aarch64 before amd64, linux before alpine) affects [firstExisting]
  val all: List<Path> =
    listOf(macAarch64, macAmd64, linuxAarch64, linuxAmd64, alpineAmd64, windowsAmd64)

  val existing: List<Path>
    get() = all.filter(Files::exists)

  val firstExisting: Path
    get() = existing.first()

  private fun executablePath(name: String): Path =
    rootProjectDir.resolve("pkl-cli/build/executable").resolve(name)
}
