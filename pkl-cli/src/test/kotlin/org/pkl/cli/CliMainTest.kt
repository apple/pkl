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
package org.pkl.cli

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.subcommands
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createSymbolicLinkPointingTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.cli.commands.EvalCommand
import org.pkl.cli.commands.RootCommand
import org.pkl.commons.writeString

class CliMainTest {

    private val evalCmd = EvalCommand("")
    private val cmd = RootCommand("pkl", "pkl version 1", "").subcommands(evalCmd)

    @Test
    fun `duplicate CLI option produces meaningful errror message`(@TempDir tempDir: Path) {
        val inputFile = tempDir.resolve("test.pkl").writeString("").toString()

        assertThatCode {
                cmd.parse(
                    arrayOf("eval", "--output-path", "path1", "--output-path", "path2", inputFile)
                )
            }
            .hasMessage("Invalid value for \"--output-path\": Option cannot be repeated")

        assertThatCode {
                cmd.parse(arrayOf("eval", "-o", "path1", "--output-path", "path2", inputFile))
            }
            .hasMessage("Invalid value for \"--output-path\": Option cannot be repeated")
    }

    @Test
    fun `eval requires at least one file`() {
        assertThatCode { cmd.parse(arrayOf("eval")) }.hasMessage("""Missing argument "<modules>"""")
    }

    @Test
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

        assertThatCode { cmd.parse(arrayOf("eval", inputFile, "-o", outputFile)) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `cannot have multiple output with -o or -x`(@TempDir tempDir: Path) {
        val testIn = makeInput(tempDir)
        val testOut = tempDir.resolve("test").toString()
        val error =
            """Invalid value for "--multiple-file-output-path": Option is mutually exclusive with -o, --output-path and -x, --expression."""

        assertThatCode { cmd.parse(arrayOf("eval", "-m", testOut, "-x", "x", testIn)) }
            .hasMessage(error)

        assertThatCode { cmd.parse(arrayOf("eval", "-m", testOut, "-o", "/tmp/test", testIn)) }
            .hasMessage(error)
    }

    @Test
    fun `showing version works`() {
        assertThatCode { cmd.parse(arrayOf("--version")) }.hasMessage("pkl version 1")
    }

    @Test
    fun `file paths get parsed into URIs`(@TempDir tempDir: Path) {
        cmd.parse(arrayOf("eval", makeInput(tempDir, "my file.txt")))

        val modules = evalCmd.baseOptions.baseOptions(evalCmd.modules).normalizedSourceModules
        assertThat(modules).hasSize(1)
        assertThat(modules[0].path).endsWith("my file.txt")
    }

    @Test
    fun `invalid URIs are not accepted`() {
        val ex = assertThrows<BadParameterValue> { cmd.parse(arrayOf("eval", "file:my file.txt")) }

        assertThat(ex.message).contains("URI `file:my file.txt` has invalid syntax")
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
