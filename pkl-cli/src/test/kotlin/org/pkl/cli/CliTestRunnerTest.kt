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

import com.github.ajalt.clikt.testing.test
import java.io.StringWriter
import java.io.Writer
import java.net.URI
import java.nio.file.Path
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.cli.commands.RootCommand
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.CliTestOptions
import org.pkl.commons.readString
import org.pkl.commons.toUri
import org.pkl.commons.writeString

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
          ✔ succeed
      
      100.0% tests pass [1 passed], 100.0% asserts pass [2 passed]

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
            ✘ fail
               4 == 9 (/tempDir/test.pkl, line xx)

        0.0% tests pass [1/1 failed], 50.0% asserts pass [1/2 failed]

        """
          .trimIndent()
      )
    assertThat(err.toString()).isEqualTo("")
  }

  @Test
  fun `CliTestRunner with thrown error in facts`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      facts {
        ["fail"] {
          throw("uh oh")
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
      .isEqualToNormalizingNewlines(
        """
      module test
        facts
          ✘ fail
             –– Pkl Error ––
             uh oh

             5 | throw("uh oh")
                 ^^^^^^^^^^^^^^
             at test#facts["fail"][#1] (/tempDir/test.pkl, line xx)

      0.0% tests pass [1/1 failed], 0.0% asserts pass [1/1 failed]

    """
          .trimIndent()
      )
    assertThat(err.toString()).isEqualTo("")
  }

  @Test
  fun `CliTestRunner with thrown error in examples -- no expected output`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      examples {
        ["fail"] {
          throw("uh oh")
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
        examples
          ✘ fail
             –– Pkl Error ––
             uh oh

             5 | throw("uh oh")
                 ^^^^^^^^^^^^^^
             at test#examples["fail"][#1] (/tempDir/test.pkl, line xx)

      0.0% tests pass [1/1 failed], 0.0% asserts pass [1/1 failed]

    """
          .trimIndent()
      )
    assertThat(err.toString()).isEqualTo("")
  }

  @Test
  fun `CliTestRunner with thrown error in examples -- existing expected output`(
    @TempDir tempDir: Path
  ) {
    val code =
      """
      amends "pkl:test"

      examples {
        ["fail"] {
          throw("uh oh")
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(code).toString()
    tempDir
      .resolve("test.pkl-expected.pcf")
      .writeString(
        """
      examples {
        ["fail"] {
          "never compared to"
        }
      }
    """
          .trimIndent()
      )
    val out = StringWriter()
    val err = StringWriter()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions()
    val runner = CliTestRunner(opts, testOpts, consoleWriter = out, errWriter = err)
    assertThatCode { runner.run() }.hasMessage("Tests failed.")

    assertThat(out.toString().stripFileAndLines(tempDir))
      .isEqualToNormalizingNewlines(
        """
      module test
        examples
          ✘ fail
             –– Pkl Error ––
             uh oh
      
             5 | throw("uh oh")
                 ^^^^^^^^^^^^^^
             at test#examples["fail"][#1] (/tempDir/test.pkl, line xx)

      0.0% tests pass [1/1 failed], 0.0% asserts pass [1/1 failed]

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
    val noopWriter = noopWriter()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions(junitDir = tempDir)
    val runner = CliTestRunner(opts, testOpts, noopWriter, noopWriter)
    assertThatCode { runner.run() }.hasMessageContaining("failed")

    val junitReport = tempDir.resolve("test.xml").readString().stripFileAndLines(tempDir)
    assertThat(junitReport)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="UTF-8"?>
      <testsuite name="test" tests="2" failures="1">
          <testcase classname="test.facts" name="foo"></testcase>
          <testcase classname="test.facts" name="bar">
              <failure message="Fact Failure">5 == 9 (/tempDir/test.pkl, line xx)</failure>
          </testcase>
          <system-err><![CDATA[9 = 9
      ]]></system-err>
      </testsuite>
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `CliTestRunner JUnit reports, with thrown error`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      facts {
        ["foo"] {
          9 == trace(9)
          "foo" == "foo"
        }
        ["fail"] {
          throw("uh oh")
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(code).toString()
    val noopWriter = noopWriter()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions(junitDir = tempDir)
    val runner = CliTestRunner(opts, testOpts, noopWriter, noopWriter)
    assertThatCode { runner.run() }.hasMessageContaining("failed")

    val junitReport = tempDir.resolve("test.xml").readString().stripFileAndLines(tempDir)
    assertThat(junitReport)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="UTF-8"?>
      <testsuite name="test" tests="2" failures="1">
          <testcase classname="test.facts" name="foo"></testcase>
          <testcase classname="test.facts" name="fail">
              <error message="uh oh">–– Pkl Error ––
      uh oh
      
      9 | throw(&quot;uh oh&quot;)
          ^^^^^^^^^^^^^^
      at test#facts[&quot;fail&quot;][#1] (/tempDir/test.pkl, line xx)
      </error>
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
    val noopWriter = noopWriter()
    val opts =
      CliBaseOptions(
        sourceModules = listOf(input.toUri(), input2.toUri()),
        settings = URI("pkl:settings"),
      )
    val testOpts = CliTestOptions(junitDir = tempDir)
    val runner = CliTestRunner(opts, testOpts, noopWriter, noopWriter)
    assertThatCode { runner.run() }.hasMessageContaining("failed")
  }

  @Test
  fun `CliTestRunner test per module`(@TempDir tempDir: Path) {
    val code1 =
      """
      amends "pkl:test"

      facts {
        ["foo"] {
          true
        }
      }
    """
        .trimIndent()

    val code2 =
      """
      amends "pkl:test"

      facts {
        ["bar"] {
          true
        }
      }
    """
        .trimIndent()
    val input1 = tempDir.resolve("test1.pkl").writeString(code1).toString()
    val input2 = tempDir.resolve("test2.pkl").writeString(code2).toString()
    val noopWriter = noopWriter()
    val opts =
      CliBaseOptions(
        sourceModules = listOf(input1.toUri(), input2.toUri()),
        settings = URI("pkl:settings"),
      )
    val testOpts = CliTestOptions(junitDir = tempDir)
    val runner = CliTestRunner(opts, testOpts, noopWriter, noopWriter)
    runner.run()

    assertThat(tempDir.resolve("test1.xml")).isNotEmptyFile()
    assertThat(tempDir.resolve("test2.xml")).isNotEmptyFile()
  }

  @Test
  fun `CliTestRunner test aggregate`(@TempDir tempDir: Path) {
    val code1 =
      """
      amends "pkl:test"

      facts {
        ["foo"] {
          true
        }
        ["bar"] {
          5 == 9
        }
      }
    """
        .trimIndent()

    val code2 =
      """
      amends "pkl:test"

      facts {
        ["xxx"] {
          false
        }
        ["yyy"] {
          false
        }
        ["zzz"] {
          true
        }
      }
    """
        .trimIndent()
    val input1 = tempDir.resolve("test1.pkl").writeString(code1).toString()
    val input2 = tempDir.resolve("test2.pkl").writeString(code2).toString()
    val noopWriter = noopWriter()
    val opts =
      CliBaseOptions(
        sourceModules = listOf(input1.toUri(), input2.toUri()),
        settings = URI("pkl:settings"),
      )
    val testOpts = CliTestOptions(junitDir = tempDir, junitAggregateReports = true)
    val runner = CliTestRunner(opts, testOpts, noopWriter, noopWriter)
    assertThatCode { runner.run() }.hasMessageContaining("failed")

    assertThat(tempDir.resolve("pkl-tests.xml")).isNotEmptyFile()
    assertThat(tempDir.resolve("test1.xml")).doesNotExist()
    assertThat(tempDir.resolve("test2.xml")).doesNotExist()

    val junitReport = tempDir.resolve("pkl-tests.xml").readString().stripFileAndLines(tempDir)

    assertThat(junitReport)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="UTF-8"?>
      <testsuites name="pkl-tests" tests="5" failures="3">
          <testsuite name="test1" tests="2" failures="1">
              <testcase classname="test1.facts" name="foo"></testcase>
              <testcase classname="test1.facts" name="bar">
                  <failure message="Fact Failure">5 == 9 (/tempDir/test1.pkl, line xx)</failure>
              </testcase>
          </testsuite>
          <testsuite name="test2" tests="3" failures="2">
              <testcase classname="test2.facts" name="xxx">
                  <failure message="Fact Failure">false (/tempDir/test2.pkl, line xx)</failure>
              </testcase>
              <testcase classname="test2.facts" name="yyy">
                  <failure message="Fact Failure">false (/tempDir/test2.pkl, line xx)</failure>
              </testcase>
              <testcase classname="test2.facts" name="zzz"></testcase>
          </testsuite>
      </testsuites>
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `CliTestRunner test aggregate suite name`(@TempDir tempDir: Path) {
    val code1 =
      """
      amends "pkl:test"

      facts {
        ["foo"] {
          true
        }
      }
    """
        .trimIndent()

    val code2 =
      """
      amends "pkl:test"

      facts {
        ["bar"] {
          true
        }
      }
    """
        .trimIndent()
    val input1 = tempDir.resolve("test1.pkl").writeString(code1).toString()
    val input2 = tempDir.resolve("test2.pkl").writeString(code2).toString()
    val noopWriter = noopWriter()
    val opts =
      CliBaseOptions(
        sourceModules = listOf(input1.toUri(), input2.toUri()),
        settings = URI("pkl:settings"),
      )
    val testOpts =
      CliTestOptions(
        junitDir = tempDir,
        junitAggregateReports = true,
        junitAggregateSuiteName = "custom",
      )
    val runner = CliTestRunner(opts, testOpts, noopWriter, noopWriter)
    runner.run()

    assertThat(tempDir.resolve("custom.xml")).isNotEmptyFile()
    assertThat(tempDir.resolve("pkl-tests.xml")).doesNotExist()
    assertThat(tempDir.resolve("test1.xml")).doesNotExist()
    assertThat(tempDir.resolve("test2.xml")).doesNotExist()
  }

  @Test
  fun `no source modules specified has same message as pkl eval`() {
    val e1 = assertThrows<CliException> { CliTestRunner(CliBaseOptions(), CliTestOptions()).run() }
    val e2 = RootCommand().test("eval")
    assertThat(e1).hasMessageContaining("missing argument <modules>")
    assertThat(e1.message!!.replace("test", "eval") + "\n").isEqualTo(e2.stderr)
  }

  @Test
  fun `example length mismatch`(@TempDir tempDir: Path) {
    val code =
      """
      amends "pkl:test"

      examples {
        ["nums"] {
          1
          2
        }
      }
    """
        .trimIndent()
    val input = tempDir.resolve("test.pkl").writeString(code).toString()
    tempDir
      .resolve("test.pkl-expected.pcf")
      .writeString(
        """
      examples {
        ["nums"] {
          1
        }
      }
    """
          .trimIndent()
      )
    val out = StringWriter()
    val err = StringWriter()
    val opts = CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
    val testOpts = CliTestOptions()
    val runner = CliTestRunner(opts, testOpts, consoleWriter = out, errWriter = err)
    assertThatCode { runner.run() }.hasMessage("Tests failed.")

    assertThat(out.toString().stripFileAndLines(tempDir))
      .isEqualToNormalizingNewlines(
        """
        module test
          examples
            ✘ nums
               (/tempDir/test.pkl, line xx)
               Output mismatch: Expected "nums" to contain 1 examples, but found 2

        0.0% tests pass [1/1 failed], 0.0% asserts pass [1/1 failed]

        """
          .trimIndent()
      )
  }

  @Test
  fun `only written examples`(@TempDir tempDir: Path) {

    val code =
      """
      amends "pkl:test"

      examples {
        ["nums"] {
          1
          2
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
    val exception = assertThrows<CliException> { runner.run() }
    assertThat(exception.exitCode).isEqualTo(10)
    assertThat(out.toString())
      .isEqualTo(
        """
          module test
            examples
              ✍️ nums

          1 examples written
          
          """
          .trimIndent()
      )
  }

  @Test
  fun `CliTestRunner locale independence test`(@TempDir tempDir: Path) {
    val originalLocale = Locale.getDefault()
    Locale.setDefault(Locale.GERMANY)

    try {
      val code =
        """
        amends "pkl:test"

        facts {
            ["localeTest"] {
                1 == 1
            }
        }
        """
          .trimIndent()
      val input = tempDir.resolve("test.pkl").writeString(code).toString()
      val out = StringWriter()
      val err = StringWriter()
      val opts =
        CliBaseOptions(sourceModules = listOf(input.toUri()), settings = URI("pkl:settings"))
      val testOpts = CliTestOptions()
      val runner = CliTestRunner(opts, testOpts, consoleWriter = out, errWriter = err)
      runner.run()

      assertThat(out.toString().stripFileAndLines(tempDir))
        .isEqualTo(
          """
            module test
              facts
                ✔ localeTest

            100.0% tests pass [1 passed], 100.0% asserts pass [1 passed]

            """
            .trimIndent()
        )
      assertThat(err.toString()).isEqualTo("")
    } finally {
      Locale.setDefault(originalLocale)
    }
  }

  private fun String.stripFileAndLines(tmpDir: Path): String {
    // handle platform differences in handling of file URIs
    // (file:/// on *nix vs. file:/ on Windows)
    return replace(tmpDir.toFile().toURI().toString(), "/tempDir/")
      .replace(tmpDir.toUri().toString(), "/tempDir/")
      .replace(Regex("line \\d+"), "line xx")
  }

  private fun noopWriter(): Writer =
    object : Writer() {
      override fun close() {}

      override fun flush() {}

      override fun write(cbuf: CharArray, off: Int, len: Int) {}
    }
}
