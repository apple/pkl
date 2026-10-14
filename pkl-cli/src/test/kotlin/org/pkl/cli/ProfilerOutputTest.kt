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
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.net.URI
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliTestOptions
import org.pkl.core.ProfilerOptions

/**
 * Exercises the CPU profiler by calling into the CLI classes in-process.
 *
 * See [SubprocessProfilerOutputTest] for tests that run the packaged executables.
 */
class ProfilerOutputTest {
  private fun profilerOptions(outputFile: Path) =
    ProfilerOptions(ProfilerOptions.Cpu(outputFile, 1))

  private fun fib(name: String) =
    "function $name(n) = if (n < 2) n else $name(n - 1) + $name(n - 2)"

  private fun evalCode(name: String) =
    """
    ${fib(name)}
    x = $name(30)
    """
      .trimIndent()

  private fun testCode(name: String) =
    """
    amends "pkl:test"

    local ${fib(name)}

    facts {
      ["fib"] {
        $name(30) > 0
      }
    }
    """
      .trimIndent()

  private fun runCode(name: String) =
    """
    amends "pkl:Command"

    command {
      name = "do-run"
    }

    local ${fib(name)}

    output {
      text = $name(30).toString()
    }
    """
      .trimIndent()

  private fun baseOptions(source: Path) =
    CliBaseOptions(sourceModules = listOf(source.toUri()), settings = URI("pkl:settings"))

  private fun eval(tempDir: Path, name: String, outputFile: Path) {
    val source = tempDir.resolve("$name.pkl").also { it.writeText(evalCode(name)) }
    val options =
      CliEvaluatorOptions(baseOptions(source), profilerOptions = profilerOptions(outputFile))
    CliEvaluator(options, outputStream = ByteArrayOutputStream()).run()
  }

  private fun test(tempDir: Path, name: String, outputFile: Path) {
    val source = tempDir.resolve("$name.pkl").also { it.writeText(testCode(name)) }
    CliTestRunner(
        baseOptions(source),
        CliTestOptions(),
        consoleWriter = StringWriter(),
        errWriter = StringWriter(),
        profilerOptions = profilerOptions(outputFile),
      )
      .run()
  }

  private fun run(tempDir: Path, name: String, outputFile: Path) {
    val source = tempDir.resolve("$name.pkl").also { it.writeText(runCode(name)) }
    CliCommandRunner(
        baseOptions(source),
        emptySet(),
        emptySet(),
        emptyList(),
        outputStream = ByteArrayOutputStream(),
        errStream = ByteArrayOutputStream(),
        profilerOptions = profilerOptions(outputFile),
      )
      .run()
  }

  private fun readProfile(outputFile: Path): Profile =
    GZIPInputStream(outputFile.inputStream()).use { Profile.parseFrom(it) }

  private fun Profile.functionNames() = functionList.map { stringTableList[it.name.toInt()] }

  /** Smoke test of the profile's structure, and that it contains samples of [fibName]. */
  private fun assertValidProfile(outputFile: Path, fibName: String) {
    val profile = readProfile(outputFile)
    assertThat(profile.stringTableList.first()).isEmpty() // string_table[0] must be ""
    assertThat(profile.sampleTypeList).isNotEmpty
    assertThat(profile.functionList).isNotEmpty
    assertThat(profile.functionNames()).anyMatch { it.contains(fibName) }
  }

  @Test
  fun `pkl eval`(@TempDir tempDir: Path) {
    val outputFile = tempDir.resolve("profile.pb.gz")
    eval(tempDir, "evalFib", outputFile)
    assertValidProfile(outputFile, "evalFib")
  }

  @Test
  fun `pkl test`(@TempDir tempDir: Path) {
    val outputFile = tempDir.resolve("profile.pb.gz")
    test(tempDir, "testFib", outputFile)
    assertValidProfile(outputFile, "testFib")
  }

  @Test
  fun `pkl run`(@TempDir tempDir: Path) {
    val outputFile = tempDir.resolve("profile.pb.gz")
    run(tempDir, "runFib", outputFile)
    assertValidProfile(outputFile, "runFib")
  }

  @Test
  fun `profiler can be restarted, and runs don't share samples`(@TempDir tempDir: Path) {
    val firstOutput = tempDir.resolve("first.pb.gz")
    val secondOutput = tempDir.resolve("second.pb.gz")
    val thirdOutput = tempDir.resolve("third.pb.gz")

    eval(tempDir, "firstFib", firstOutput)
    test(tempDir, "secondFib", secondOutput)
    run(tempDir, "thirdFib", thirdOutput)

    assertValidProfile(firstOutput, "firstFib")
    assertValidProfile(secondOutput, "secondFib")
    assertValidProfile(thirdOutput, "thirdFib")

    // samples from earlier runs must not leak into later ones
    assertThat(readProfile(secondOutput).functionNames()).noneMatch { it.contains("firstFib") }
    assertThat(readProfile(thirdOutput).functionNames()).noneMatch {
      it.contains("firstFib") || it.contains("secondFib")
    }
  }

  @Test
  fun `profiler restarts after a failed evaluation`(@TempDir tempDir: Path) {
    val failingSource = tempDir.resolve("failing.pkl").also { it.writeText("x = throw(\"boom\")") }
    val failingOutput = tempDir.resolve("failing.pb.gz")
    val options =
      CliEvaluatorOptions(
        baseOptions(failingSource),
        profilerOptions = profilerOptions(failingOutput),
      )
    runCatching { CliEvaluator(options, outputStream = ByteArrayOutputStream()).run() }
      .also { assertThat(it.isFailure).isTrue() }

    val outputFile = tempDir.resolve("profile.pb.gz")
    eval(tempDir, "afterFailureFib", outputFile)
    assertValidProfile(outputFile, "afterFailureFib")
  }

  @Test
  fun `no output is written if profiling is not enabled`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("noProfile.pkl").also { it.writeText(evalCode("noProfileFib")) }
    CliEvaluator(CliEvaluatorOptions(baseOptions(source)), outputStream = ByteArrayOutputStream())
      .run()
    assertThat(tempDir.resolve("profile.pb.gz").exists()).isFalse()
  }
}
