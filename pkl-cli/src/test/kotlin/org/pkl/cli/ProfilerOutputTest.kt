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

import com.google.perftools.profiles.ProfileProto.Profile
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises the CPU profiler end-to-end by running the packaged executable jar as a subprocess.
 *
 * This must run out-of-process: enabling the profiler flips global JVM state (system properties
 * read by a singleton polyglot engine, see `VmUtils`), so it can't share a JVM with the rest of the
 * test suite without corrupting it.
 *
 * Run this test using `./gradlew pkl-cli:testProfilerOutput`.
 *
 * To use an IntelliJ JUnit run configuration, configure the VM options and add
 * `-Dorg.pkl.cli.testJar=<path>`.
 */
class ProfilerOutputTest {
  private fun runTest(tempDir: Path, pklCode: String, command: Array<String>) {
    val jar =
      checkNotNull(System.getProperty("org.pkl.cli.testJar")) {
        "system property `org.pkl.cli.testJar` is not set"
      }
    val sourceFile =
      tempDir.resolve("profiled.pkl").also { it.writeText(pklCode) }
    val outputFile = tempDir.resolve("profile.pb.gz")

    val javaBin = ProcessHandle.current().info().command().orElseThrow()
    val process =
      ProcessBuilder(
          javaBin,
          "-jar",
          jar,
          *command,
          "--profile-cpu-output",
          outputFile.absolutePathString(),
          "--profile-cpu-sample-period",
          "1",
          sourceFile.absolutePathString(),
        )
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    assertThat(exitCode).withFailMessage { "process failed with output:\n$output" }.isZero()

    // smoke test only
    val profile = GZIPInputStream(outputFile.inputStream()).use { Profile.parseFrom(it) }
    assertThat(profile.stringTableList.first()).isEmpty() // string_table[0] must be ""
    assertThat(profile.sampleTypeList).isNotEmpty
    assertThat(profile.functionList).isNotEmpty
  }

  @Test
  fun `pkl eval`(@TempDir tempDir: Path) {
    val pklCode =
      """
      function fib(n) = if (n < 2) n else fib(n - 1) + fib(n - 2)
      x = fib(30)
      """
        .trimIndent()
    runTest(tempDir, pklCode, arrayOf("eval"))
  }

  @Test
  fun `pkl test`(@TempDir tempDir: Path) {
    val pklCode =
      """
      amends "pkl:test"

      local function fib(n) = if (n < 2) n else fib(n - 1) + fib(n - 2)

      facts {
        ["fib"] {
          fib(30) > 0
        }
      }
      """
        .trimIndent()
    runTest(tempDir, pklCode, arrayOf("eval"))
  }

  @Test
  fun `pkl run`(@TempDir tempDir: Path) {
    val pklCode =
      """
      amends "pkl:Command"

      local function fib(n) = if (n < 2) n else fib(n - 1) + fib(n - 2)

      output {
        text = fib(30).toString()
      }
      """
        .trimIndent()
    runTest(tempDir, pklCode, arrayOf("run"))
  }
}
