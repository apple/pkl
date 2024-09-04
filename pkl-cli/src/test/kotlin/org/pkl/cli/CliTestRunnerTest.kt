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

import com.github.ajalt.clikt.core.MissingArgument
import com.github.ajalt.clikt.core.subcommands
import java.io.StringWriter
import java.net.URI
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.cli.commands.EvalCommand
import org.pkl.cli.commands.RootCommand
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.CliTestOptions
import org.pkl.commons.readString
import org.pkl.commons.toUri
import org.pkl.commons.writeString
import org.pkl.core.Release

class CliTestRunnerTest {

  @Test
  fun `CliTestRunner succeed test`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      facts {
        ["succeed"] {
          8 == 8
          3 == 3
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(code).toString()
    val out = StringWriter()
    val err = StringWriter()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions()
    val runner = CliTestRunner(opts, testOpts, consoleWriter = out, errWriter = err)
    runner.run()

    assertThat(out.toString().stripFileAndLines(tempDir))
      .isEqualTo(
        """
      module test
        facts
          ✅ succeed
      ✅ 100.0% tests pass [1 passed], 100.0% asserts pass [2 passed]
      

    """
          .trimIndent()
      )
    assertThat(err.toString()).isEqualTo("")
  }

  @Test
  fun `CliTestRunner fail test`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      facts {
        ["fail"] {
          4 == 9
          "foo" != "bar"
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(code).toString()
    val out = StringWriter()
    val err = StringWriter()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions()
    val runner = CliTestRunner(opts, testOpts, consoleWriter = out, errWriter = err)
    assertThatCode { runner.run() }.hasMessage("Tests failed.")

    assertThat(out.toString().stripFileAndLines(tempDir))
      .isEqualTo(
        """
      module test
        facts
          ❌ fail
             4 == 9
      ❌ 0.0% tests pass [1/1 failed], 50.0% asserts pass [1/2 failed]
      

    """
          .trimIndent()
      )
    assertThat(err.toString()).isEqualTo("")
  }

  @Test
  fun `CliTestRunner JUnit reports`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      facts {
        ["foo"] {
          9 == trace(9)
          "foo" == "foo"
        }
        ["bar"] {
          "foo" == "foo"
          5 == 9
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(code).toString()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions(junitDir = tempDir)
    val runner = CliTestRunner(opts, testOpts)
    assertThatCode { runner.run() }.hasMessageContaining("failed")

    val junitReport = tempDir.resolve("test.xml").readString().stripFileAndLines(tempDir)
    assertThat(junitReport)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="UTF-8"?>
      <testsuite name="test" tests="2" failures="1">
          <testcase classname="test.facts" name="foo"></testcase>
          <testcase classname="test.facts" name="bar">
              <failure message="Fact Failure">5 == 9</failure>
          </testcase>
          <system-err><![CDATA[9 = 9
      ]]></system-err>
      </testsuite>
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `CliTestRunner duplicated JUnit reports`(@TempDir tempDir: Path) {
    val foo =
      """
      module foo
      
      amends "pkl:test"

      facts {
        ["foo"] {
          1 == 1
        }
      }
    """
        .trimIndent()

    val bar =
      """
      module foo
      
      amends "pkl:test"

      facts {
        ["foo"] {
          1 == 1
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(foo).toString()
    val input2 = tempDir.resolve("test.pkl").writeString(bar).toString()
    val opts =
      CliBaseOptions(
        sourceModules = listOf(input.toUri(), input2.toUri()),
        settings = URI("pkl:settings")
      )
    val testOpts = CliTestOptions(junitDir = tempDir)
    val runner = CliTestRunner(opts, testOpts)
    assertThatCode { runner.run() }.hasMessageContaining("failed")
  }

  @Test
  fun `no source modules specified has same message as pkl eval`() {
    val e1 = assertThrows<CliException> { CliTestRunner(CliBaseOptions(), CliTestOptions()).run() }
    val e2 =
      assertThrows<MissingArgument> {
        val rootCommand =
          RootCommand("pkl", Release.current().versionInfo(), "").subcommands(EvalCommand(""))
        rootCommand.parse(listOf("eval"))
      }
    assertThat(e1).hasMessageContaining("Missing argument \"<modules>\"")
    assertThat(e1.message!!.replace("test", "eval")).isEqualTo(e2.helpMessage())
  }

  private fun String.stripFileAndLines(tmpDir: Path) =
    replace(tmpDir.toUri().toString(), "/tempDir/").replace(Regex(""" \(.*, line \d+\)"""), "")
}
