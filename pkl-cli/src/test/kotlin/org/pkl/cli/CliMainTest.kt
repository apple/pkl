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
package org.pkl.cli

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createSymbolicLinkPointingTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.pkl.cli.commands.EvalCommand
import org.pkl.cli.commands.RootCommand
import org.pkl.commons.writeString
import org.pkl.core.Release

class CliMainTest {
  private val rootCmd = RootCommand()

  @Test
  fun `duplicate CLI option produces meaningful error message`(@TempDir tempDir: Path) {
    val inputFile = tempDir.resolve("test.pkl").writeString("").toString()

    assertThatCode {
        rootCmd.parse(
          arrayOf("eval", "--output-path", "path1", "--output-path", "path2", inputFile)
        )
      }
      .hasMessage("Option cannot be repeated")

    assertThatCode {
        rootCmd.parse(arrayOf("eval", "-o", "path1", "--output-path", "path2", inputFile))
      }
      .hasMessage("Option cannot be repeated")
  }

  @Test
  fun `eval requires at least one file`() {
    assertThatCode { rootCmd.parse(arrayOf("eval")) }
      .isInstanceOf(CliktError::class.java)
      .extracting("paramName")
      .isEqualTo("modules")
  }

  // Can't reliably create symlinks on Windows.
  // Might get errors like "A required privilege is not held by the client".
  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `output to symlinked directory works`(@TempDir tempDir: Path) {
    val code =
      """
      x = 3
      
      output {
        value = x
        renderer = new JsonRenderer {}
      }
    """
        .trimIndent()
    val inputFile = tempDir.resolve("test.pkl").writeString(code).toString()
    val outputFile = makeSymdir(tempDir, "out", "linkOut").resolve("test.pkl").toString()

    assertThatCode { rootCmd.parse(arrayOf("eval", inputFile, "-o", outputFile)) }
      .doesNotThrowAnyException()
  }

  @Test
  fun `cannot have multiple output with -o or -x`(@TempDir tempDir: Path) {
    val testIn = makeInput(tempDir)
    val testOut = tempDir.resolve("test").toString()
    val error = """Option is mutually exclusive with -o, --output-path and -x, --expression."""

    assertThatCode { rootCmd.parse(arrayOf("eval", "-m", testOut, "-x", "x", testIn)) }
      .hasMessage(error)

    assertThatCode { rootCmd.parse(arrayOf("eval", "-m", testOut, "-o", "/tmp/test", testIn)) }
      .hasMessage(error)
  }

  @Test
  fun `showing version works`() {
    assertThatCode { rootCmd.parse(arrayOf("--version")) }.hasMessage(Release.current().versionInfo)
  }

  @Test
  fun `file paths get parsed into URIs`(@TempDir tempDir: Path) {
    val cmd = RootCommand()
    cmd.parse(arrayOf("eval", makeInput(tempDir, "my file.txt")))

    val evalCmd = cmd.registeredSubcommands().filterIsInstance<EvalCommand>().first()
    val modules = evalCmd.baseOptions.baseOptions(evalCmd.modules).normalizedSourceModules
    assertThat(modules).hasSize(1)
    assertThat(modules[0].path).endsWith("my file.txt")
  }

  @Test
  fun `invalid URIs are not accepted`() {
    val ex = assertThrows<BadParameterValue> { rootCmd.parse(arrayOf("eval", "file:my file.txt")) }

    assertThat(ex.message).contains("URI `file:my file.txt` has invalid syntax")
  }

  @Test
  fun `invalid rewrites -- non-HTTP URI`() {
    val ex =
      assertThrows<BadParameterValue> {
        rootCmd.parse(arrayOf("eval", "--http-rewrite", "foo=bar", "mymodule.pkl"))
      }
    assertThat(ex.message)
      .contains("Rewrite rule must start with 'http://' or 'https://', but was 'foo'")
  }

  @Test
  fun `invalid rewrites -- invalid URI`() {
    val ex =
      assertThrows<BadParameterValue> {
        rootCmd.parse(arrayOf("eval", "--http-rewrite", "https://foo bar=baz", "mymodule.pkl"))
      }
    assertThat(ex.message).contains("Rewrite target `https://foo bar` has invalid syntax")
  }

  @Test
  fun `invalid rewrites -- capitalized hostname`() {
    val ex =
      assertThrows<BadParameterValue> {
        rootCmd.parse(
          arrayOf("eval", "--http-rewrite", "https://www.FOO.com/=https://bar.com/", "mymodule.pkl")
        )
      }
    assertThat(ex.message)
      .contains("Rewrite rule must have a lowercased hostname, but was 'www.FOO.com'")
  }

  @Test
  fun `invalid rewrites -- doesn't end with slash`() {
    val ex =
      assertThrows<BadParameterValue> {
        rootCmd.parse(
          arrayOf("eval", "--http-rewrite", "http://foo.com=https://bar.com", "mymodule.pkl")
        )
      }
    assertThat(ex.message).contains("Rewrite rule must end with '/', but was 'http://foo.com'")
  }

  private fun makeInput(tempDir: Path, fileName: String = "test.pkl"): String {
    val code = "x = 1"
    return tempDir.resolve(fileName).writeString(code).toString()
  }

  private fun makeSymdir(baseDir: Path, name: String, linkName: String): Path {
    val dir = baseDir.resolve(name)
    val link = baseDir.resolve(linkName)
    dir.createDirectory()
    link.createSymbolicLinkPointingTo(dir)
    return link
  }
}
