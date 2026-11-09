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
package org.pkl.gradle

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.readString

abstract class AbstractTest {
  companion object {
    /**
     * Gradle output message emitted when the configuration cache is populated for the first time.
     * Extracted as a constant so that version-specific phrasing changes only need one update.
     */
    const val CONFIG_CACHE_STORED = "Configuration cache entry stored"

    /**
     * Gradle output message emitted when a prior configuration cache entry is reused. Extracted as
     * a constant so that version-specific phrasing changes only need one update.
     */
    const val CONFIG_CACHE_REUSED = "Reusing configuration cache"
  }

  private val gradleVersion: String? = System.getProperty("testGradleVersion")

  private val gradleDistributionUrl: String? = System.getProperty("testGradleDistributionUrl")

  @TempDir protected lateinit var testProjectDir: Path

  protected fun runTask(taskName: String, expectFailure: Boolean = false): BuildResult {

    val runner =
      GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withArguments("--stacktrace", "--no-build-cache", taskName)
        .withPluginClasspath()
        .withDebug(true)

    if (gradleVersion != null) {
      runner.withGradleVersion(gradleVersion)
    }
    if (gradleDistributionUrl != null) {
      runner.withGradleDistribution(URI(gradleDistributionUrl))
    }

    return try {
      if (expectFailure) runner.buildAndFail() else runner.build()
    } catch (e: UnexpectedBuildFailure) {
      throw AssertionError(e.buildResult.output)
    }
  }

  /**
   * Runs the given task with the Gradle configuration cache enabled. Uses forked mode
   * (withDebug=false) because the configuration cache is not supported in debug/in-process mode.
   *
   * Runs the task twice: once to populate the configuration cache, and once to verify the cache is
   * reused.
   */
  protected fun runTaskWithConfigurationCache(taskName: String): Pair<BuildResult, BuildResult> {
    fun createRunner(): GradleRunner {
      val runner =
        GradleRunner.create()
          .withProjectDir(testProjectDir.toFile())
          .withArguments("--stacktrace", "--no-build-cache", "--configuration-cache", taskName)
          .withPluginClasspath()
          // Configuration cache requires forked mode (not in-process debug mode)
          .withDebug(false)

      if (gradleVersion != null) {
        runner.withGradleVersion(gradleVersion)
      }
      if (gradleDistributionUrl != null) {
        runner.withGradleDistribution(URI(gradleDistributionUrl))
      }
      return runner
    }

    val firstRun =
      try {
        createRunner().build()
      } catch (e: UnexpectedBuildFailure) {
        throw AssertionError("Configuration cache: first run failed\n${e.buildResult.output}")
      }

    val secondRun =
      try {
        createRunner().build()
      } catch (e: UnexpectedBuildFailure) {
        throw AssertionError(
          "Configuration cache: second run (reuse) failed\n${e.buildResult.output}"
        )
      }

    return Pair(firstRun, secondRun)
  }

  protected fun writeFile(fileName: String, contents: String): Path {
    return testProjectDir.resolve(fileName).apply {
      createParentDirectories()
      writeText(contents.trimIndent())
    }
  }

  protected fun checkFileContents(file: Path, contents: String) {
    assertThat(file).exists()
    assertThat(file.readString().trim()).isEqualTo(contents.trim())
  }

  protected fun checkTextContains(text: String, vararg contents: String) {
    for (content in contents) {
      try {
        assertThat(text).contains(content.trimMargin())
      } catch (e: AssertionError) {
        // to get diff output in IDE
        assertThat(text).isEqualTo(content.trimMargin())
      }
    }
  }
}
