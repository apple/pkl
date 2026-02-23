/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.commons.cli

import com.github.ajalt.clikt.core.parse
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.core.SecurityManagers

@OptIn(ExperimentalPathApi::class)
class CliCommandTest {

  class CliTest(options: CliBaseOptions) : CliCommand(options) {
    override fun doRun() = Unit

    val myAllowedResources = allowedResources
    val myAllowedModules = allowedModules
    val myResolvedSourceModules = resolvedSourceModules
  }

  private val cmd =
    object : BaseCommand("test", "") {
      override fun run() = Unit

      override val helpString: String = ""
    }

  @Test
  fun `--external-resource-reader and --external-module-reader populate allowed modules and resources`() {
    cmd.parse(
      arrayOf(
        "--external-module-reader",
        "scheme3=reader3",
        "--external-module-reader",
        "scheme4=reader4 with args",
        "--external-module-reader",
        "scheme+ext=reader5 with args",
        "--external-resource-reader",
        "scheme1=reader1",
        "--external-resource-reader",
        "scheme2=reader2 with args",
        "--external-resource-reader",
        "scheme+ext=reader5 with args",
      )
    )
    val opts = cmd.baseOptions.baseOptions(emptyList(), testMode = true)
    val cliTest = CliTest(opts)
    assertThat(cliTest.myAllowedModules.map { it.pattern() })
      .isEqualTo(
        SecurityManagers.defaultAllowedModules.map { it.pattern() } +
          listOf("\\Qscheme3:\\E", "\\Qscheme4:\\E", "\\Qscheme+ext:\\E")
      )
    assertThat(cliTest.myAllowedResources.map { it.pattern() })
      .isEqualTo(
        SecurityManagers.defaultAllowedResources.map { it.pattern() } +
          listOf("\\Qscheme1:\\E", "\\Qscheme2:\\E", "\\Qscheme+ext:\\E")
      )
  }

  @Test
  fun `@-notation package URIs - treated as relative paths when no project present`(
    @TempDir tempDir: Path
  ) {
    cmd.parse(arrayOf("--working-dir=$tempDir"))
    val opts = cmd.baseOptions.baseOptions(listOf(URI("@foo/bar.pkl")), testMode = true)
    val cliTest = CliTest(opts)
    assertThat(cliTest.myResolvedSourceModules)
      .isEqualTo(listOf(tempDir.toUri().resolve("@foo/bar.pkl")))
  }

  @Test
  fun `@-notation package URIs - empty paths are rejected`(@TempDir tempDir: Path) {
    tempDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"
    """
          .trimIndent()
      )
    cmd.parse(arrayOf("--working-dir=$tempDir"))
    val opts = cmd.baseOptions.baseOptions(listOf(URI("@no.slash")), testMode = true)
    val exc = assertThrows<CliException> { CliTest(opts) }
    assertThat(exc.message).isEqualTo("Invalid project dependency URI `@no.slash`.")
  }

  @Test
  fun `@-notation package URIs - missing dependencies are rejected`(@TempDir tempDir: Path) {
    tempDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"
    """
          .trimIndent()
      )
    cmd.parse(arrayOf("--working-dir=$tempDir"))
    val opts = cmd.baseOptions.baseOptions(listOf(URI("@foo/bar.pkl")), testMode = true)
    val exc = assertThrows<CliException> { CliTest(opts) }
    assertThat(exc.message).isEqualTo("Project does not contain dependency `@foo`.")
  }

  @Test
  fun `@-notation package URIs - local dependencies are rejected`(
    @TempDir tempDir: Path,
    @TempDir depDir: Path,
  ) {
    depDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"
      
      package {
        name = "foo"
        baseUri = "package://example.com/foo"
        version = "0.0.1"
        packageZipUrl = "https://example.com/foo@\(version).zip"
      }
    """
          .trimIndent()
      )

    tempDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"

      dependencies {
        ["foo"] = import("$depDir/PklProject")
      }
    """
          .trimIndent()
      )
    cmd.parse(arrayOf("--working-dir=$tempDir"))
    val opts = cmd.baseOptions.baseOptions(listOf(URI("@foo/bar.pkl")), testMode = true)
    val exc = assertThrows<CliException> { CliTest(opts) }
    assertThat(exc.message)
      .isEqualTo(
        "Only remote project dependencies may be referenced using @-notation. Dependency `@foo` is a local dependency."
      )
  }

  @Test
  fun `@-notation package URIs - remote dependencies are resolved`(@TempDir tempDir: Path) {
    tempDir
      .resolve("PklProject")
      .writeText(
        """
      amends "pkl:Project"

      dependencies {
        ["foo"] {
          uri = "package://example.com/foo@1.2.3"
        }
      }
    """
          .trimIndent()
      )
    cmd.parse(arrayOf("--working-dir=$tempDir"))
    val opts = cmd.baseOptions.baseOptions(listOf(URI("@foo/bar.pkl")), testMode = true)
    val cliTest = CliTest(opts)
    assertThat(cliTest.myResolvedSourceModules)
      .isEqualTo(listOf(tempDir.toUri().resolve("package://example.com/foo@1.2.3#/bar.pkl")))
  }
}
