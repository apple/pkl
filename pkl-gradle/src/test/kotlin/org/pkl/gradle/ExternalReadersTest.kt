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
package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf

class ExternalReadersTest : AbstractTest() {
  companion object {
    // Adjust paths on Windows to prevent unexpected character escapes.
    private fun getPathSafeSystemProperty(name: String): String? =
      System.getProperty(name)?.replace('\\', '/')

    private val externalReaderJar: String? by lazy {
      getPathSafeSystemProperty("pklGradle.externalReaderJar")
    }

    private val javaExecutable: String? by lazy {
      getPathSafeSystemProperty("pklGradle.javaExecutable")
    }

    @JvmStatic
    fun systemPropertiesAreNotSet(): Boolean {
      return externalReaderJar == null || javaExecutable == null
    }
  }

  @Test
  fun `external module readers DSL is accepted`() {
    writeBuildFile(
      externalModuleReaders =
        """
        externalModuleReaders {
          myscheme {
            executable = "/nonexistent/my-reader"
            arguments = ["--arg1", "--arg2"]
          }
        }
        """
          .trimIndent()
    )
    writePklFile()
    runTask("evalTest")
  }

  @Test
  fun `external resource readers DSL is accepted`() {
    writeBuildFile(
      externalResourceReaders =
        """
        externalResourceReaders {
          myscheme {
            executable = "/nonexistent/my-resource-reader"
            arguments = ["--resource"]
          }
        }
        """
          .trimIndent()
    )
    writePklFile()
    runTask("evalTest")
  }

  @Test
  fun `multiple external readers can be configured`() {
    writeBuildFile(
      externalModuleReaders =
        """
        externalModuleReaders {
          scheme1 {
            executable = "/nonexistent/reader1"
            arguments = ["--mod"]
          }
          scheme2 {
            executable = "/nonexistent/reader2"
            arguments = []
          }
        }
        """
          .trimIndent(),
      externalResourceReaders =
        """
        externalResourceReaders {
          scheme3 {
            executable = "/nonexistent/reader3"
            arguments = ["--res", "--verbose"]
          }
          scheme4 {
            executable = "/nonexistent/reader4"
            arguments = []
          }
        }
        """
          .trimIndent(),
    )
    writePklFile()
    runTask("evalTest")
  }

  @Test
  fun `external module reader with invalid executable produces error`() {
    writeBuildFile(
      externalModuleReaders =
        """
        externalModuleReaders {
          myscheme {
            executable = "/nonexistent/my-reader"
            arguments = []
          }
        }
        """
          .trimIndent(),
      additionalContents =
        """
        allowedModules = ["repl:", "file:", "modulepath:", "https:", "pkl:", "package:", "projectpackage:", "myscheme:"]
        """
          .trimIndent(),
    )
    writePklFile(
      """
      import "myscheme:/something"
      result = 1
      """
        .trimIndent()
    )
    val result = runTask("evalTest", expectFailure = true)
    assertThat(result.output).contains("/nonexistent/my-reader")
  }

  @Test
  fun `external resource reader with invalid executable produces error`() {
    writeBuildFile(
      externalResourceReaders =
        """
        externalResourceReaders {
          myscheme {
            executable = "/nonexistent/my-resource-reader"
            arguments = []
          }
        }
        """
          .trimIndent(),
      additionalContents =
        """
        allowedResources = ["env:", "prop:", "file:", "modulepath:", "https:", "package:", "myscheme:"]
        """
          .trimIndent(),
    )
    writePklFile(
      """
      result = read("myscheme:/something")
      """
        .trimIndent()
    )
    val result = runTask("evalTest", expectFailure = true)
    assertThat(result.output).contains("/nonexistent/my-resource-reader")
  }

  @Test
  fun `external module reader scheme must be in allowedModules`() {
    writeBuildFile(
      externalModuleReaders =
        """
        externalModuleReaders {
          myscheme {
            executable = "/nonexistent/my-reader"
            arguments = []
          }
        }
        """
          .trimIndent()
    )
    writePklFile(
      """
      import "myscheme:/something"
      result = 1
      """
        .trimIndent()
    )
    val result = runTask("evalTest", expectFailure = true)
    assertThat(result.output).containsAnyOf("myscheme:/something", "/nonexistent/my-reader")
  }

  @Test
  fun `external resource reader scheme must be in allowedResources`() {
    writeBuildFile(
      externalResourceReaders =
        """
        externalResourceReaders {
          myscheme {
            executable = "/nonexistent/my-resource-reader"
            arguments = []
          }
        }
        """
          .trimIndent()
    )
    writePklFile(
      """
      result = read("myscheme:/something")
      """
        .trimIndent()
    )
    val result = runTask("evalTest", expectFailure = true)
    assertThat(result.output).contains("myscheme:/something")
  }

  @Test
  @DisabledIf("systemPropertiesAreNotSet")
  fun `external resource reader reads and uppercases content`() {
    writeBuildFile(
      externalResourceReaders =
        """
        externalResourceReaders {
          upper {
            executable = "$javaExecutable"
            arguments = ["-jar", "$externalReaderJar"]
          }
        }
        """
          .trimIndent(),
      additionalContents =
        """
        allowedResources = ["env:", "prop:", "file:", "modulepath:", "https:", "package:", "upper:"]
        """
          .trimIndent(),
    )
    writePklFile(
      """
      result = read("upper:hello-world").text
      """
        .trimIndent()
    )
    runTask("evalTest")
    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(outputFile, """result = "HELLO-WORLD"""")
  }

  @Test
  @DisabledIf("systemPropertiesAreNotSet")
  fun `external resource reader handles path-like URI`() {
    writeBuildFile(
      externalResourceReaders =
        """
        externalResourceReaders {
          upper {
            executable = "$javaExecutable"
            arguments = ["-jar", "$externalReaderJar"]
          }
        }
        """
          .trimIndent(),
      additionalContents =
        """
        allowedResources = ["env:", "prop:", "file:", "modulepath:", "https:", "package:", "upper:"]
        """
          .trimIndent(),
    )
    writePklFile(
      """
      result = read("upper:/some/path").text
      """
        .trimIndent()
    )
    runTask("evalTest")
    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(outputFile, """result = "/SOME/PATH"""")
  }

  @Test
  fun `external readers are configuration cache compatible`() {
    writeBuildFile(
      externalModuleReaders =
        """
        externalModuleReaders {
          myscheme {
            executable = "/nonexistent/my-reader"
            arguments = ["--arg1"]
          }
        }
        """
          .trimIndent(),
      externalResourceReaders =
        """
        externalResourceReaders {
          myresscheme {
            executable = "/nonexistent/my-resource-reader"
            arguments = ["--res"]
          }
        }
        """
          .trimIndent(),
    )
    writePklFile()

    val (firstRun, secondRun) = runTaskWithConfigurationCache("evalTest")

    assertThat(firstRun.output).contains(CONFIG_CACHE_STORED)
    assertThat(secondRun.output).contains(CONFIG_CACHE_REUSED)
  }

  private fun writeBuildFile(
    outputFormat: String = "pcf",
    externalModuleReaders: String = "",
    externalResourceReaders: String = "",
    additionalContents: String = "",
  ) {
    writeFile(
      "build.gradle",
      """
        plugins {
          id "org.pkl-lang"
        }

        pkl {
          evaluators {
            evalTest {
              sourceModules = ["test.pkl"]
              outputFormat = "$outputFormat"
              settingsModule = "pkl:settings"
              $additionalContents
              $externalModuleReaders
              $externalResourceReaders
            }
          }
        }
      """,
    )
  }

  private fun writePklFile(
    contents: String =
      """
        person {
          name = "Pigeon"
          age = 20 + 10
        }
      """
  ) {
    writeFile("test.pkl", contents)
  }
}
