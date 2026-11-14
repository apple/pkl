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
package org.pkl.cli

import java.nio.file.Path
import kotlin.io.path.exists
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Runs `pkl project package` using the packaged executable jar (or native executable) as a
 * subprocess.
 */
class SubprocessProjectPackagerTest {
  private fun Path.writeProject(name: String) {
    writeFile(
      "$name/PklProject",
      """
      amends "pkl:Project"

      package {
        name = "$name"
        baseUri = "package://example.com/$name"
        version = "1.0.0"
        packageZipUrl = "https://example.com/$name@\(version).zip"
        apiTests { "test.pkl" }
      }
      """
        .trimIndent(),
    )
    writeFile(
      "$name/test.pkl",
      """
      amends "pkl:test"

      facts {
        ["ok"] { true }
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `multiple projects with apiTests`(@TempDir tempDir: Path) {
    tempDir.writeProject("project1")
    tempDir.writeProject("project2")

    val process =
      ProcessBuilder(
          *subprocessLauncher(),
          "project",
          "package",
          "--skip-publish-check",
          "--output-path",
          ".out",
          "project1",
          "project2",
        )
        .directory(tempDir.toFile())
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    assertThat(exitCode).withFailMessage { "process failed with output:\n$output" }.isZero()
    assertThat(tempDir.resolve(".out/project1@1.0.0.zip").exists()).isTrue
    assertThat(tempDir.resolve(".out/project2@1.0.0.zip").exists()).isTrue
  }
}
