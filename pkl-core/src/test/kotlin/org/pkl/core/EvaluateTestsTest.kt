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
package org.pkl.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.createTempFile
import org.pkl.commons.writeString
import org.pkl.core.ModuleSource.*

class EvaluateTestsTest {

  private val evaluator = Evaluator.preconfigured()

  @Test
  fun `test successful module`() {
    val results =
      evaluator.evaluateTest(
        text(
          """
      amends "pkl:test"

      facts {
        ["should pass"] {
          1 == 1
          "foo" == "foo"
        }
      }
    """
            .trimIndent()
        ),
        true
      )

    assertThat(results.moduleName).isEqualTo("text")
    assertThat(results.displayUri).isEqualTo("repl:text")
    assertThat(results.totalTests()).isEqualTo(1)
    assertThat(results.failed()).isFalse
    assertThat(results.facts.results[0].name).isEqualTo("should pass")
    assertThat(results.err.isBlank()).isTrue
  }

  @Test
  fun `test module failure`() {
    val results =
      evaluator.evaluateTest(
        text(
          """
        amends "pkl:test"
  
        facts {
          ["should fail"] {
            1 == 2
            "foo" == "bar"
          }
        }
        """
            .trimIndent()
        ),
        true
      )

    assertThat(results.totalTests()).isEqualTo(1)
    assertThat(results.totalFailures()).isEqualTo(1)
    assertThat(results.failed()).isTrue

    val res = results.facts.results[0]
    assertThat(res.name).isEqualTo("should fail")
    assertThat(results.facts.hasError()).isFalse
    assertThat(res.failures.size).isEqualTo(2)

    val fail1 = res.failures[0]
    assertThat(fail1.rendered).isEqualTo("1 == 2 (repl:text)")

    val fail2 = res.failures[1]
    assertThat(fail2.rendered).isEqualTo(""""foo" == "bar" (repl:text)""")
  }

  @Test
  fun `test module error`() {
    val results =
      evaluator.evaluateTest(
        text(
          """
        amends "pkl:test"
  
        facts {
          ["should fail"] {
            1 == 2
            throw("got an error")
          }
        }
        """
            .trimIndent()
        ),
        true
      )

    assertThat(results.totalTests()).isEqualTo(1)
    assertThat(results.totalFailures()).isEqualTo(1)
    assertThat(results.failed()).isTrue

    val res = results.facts
    assertThat(res.results).isEmpty()
    assertThat(res.hasError()).isTrue

    val error = res.error
    assertThat(error.message).isEqualTo("got an error")
    assertThat(error.exception.message)
      .isEqualTo(
        """
      –– Pkl Error ––
      got an error

      6 | throw("got an error")
          ^^^^^^^^^^^^^^^^^^^^^
      at text#facts["should fail"][#2] (repl:text)

      3 | facts {
          ^^^^^^^
      at text#facts (repl:text)

    """
          .trimIndent()
      )
  }

  @Test
  fun `test successful example`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile(prefix = "example", suffix = ".pkl")
    Files.writeString(
      file,
      """
      amends "pkl:test"
      
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    Files.writeString(
      createExpected(file),
      """
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    val results = evaluator.evaluateTest(path(file), false)
    assertThat(results.moduleName).startsWith("example")
    assertThat(results.displayUri).startsWith("file:///").endsWith(".pkl")
    assertThat(results.totalTests()).isEqualTo(1)
    assertThat(results.failed()).isFalse
    assertThat(results.examples.results[0].name).isEqualTo("user")
  }

  @Test
  fun `test fact failures with successful example`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile(prefix = "example", suffix = ".pkl")
    Files.writeString(
      file,
      """
      amends "pkl:test"
      
      facts {
        ["should fail"] {
          1 == 2
          "foo" == "bar"
        }
      }
      
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    Files.writeString(
      createExpected(file),
      """
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    val results = evaluator.evaluateTest(path(file), false)
    assertThat(results.moduleName).startsWith("example")
    assertThat(results.displayUri).startsWith("file:///").endsWith(".pkl")
    assertThat(results.totalTests()).isEqualTo(2)
    assertThat(results.totalFailures()).isEqualTo(1)
    assertThat(results.failed()).isTrue

    assertThat(results.facts.results[0].name).isEqualTo("should fail")
    assertThat(results.facts.results[0].failures.size).isEqualTo(2)
    assertThat(results.examples.results[0].name).isEqualTo("user")
  }

  @Test
  fun `test fact error with successful example`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile(prefix = "example", suffix = ".pkl")
    Files.writeString(
      file,
      """
      amends "pkl:test"
      
      facts {
        ["should fail"] {
          throw("exception")
        }
      }
      
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    Files.writeString(
      createExpected(file),
      """
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    val results = evaluator.evaluateTest(path(file), false)
    assertThat(results.moduleName).startsWith("example")
    assertThat(results.displayUri).startsWith("file:///").endsWith(".pkl")
    assertThat(results.totalTests()).isEqualTo(2)
    assertThat(results.totalFailures()).isEqualTo(1)
    assertThat(results.failed()).isTrue

    assertThat(results.facts.results).isEmpty()
    assertThat(results.facts.hasError()).isTrue
    assertThat(results.examples.results[0].name).isEqualTo("user")
  }

  @Test
  fun `test example failure`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile(prefix = "example", suffix = ".pkl")
    Files.writeString(
      file,
      """
      amends "pkl:test"
      
      examples {
        ["user"] {
          new {
            name = "Bob"
            age = 33
          }
        }
      }
    """
        .trimIndent()
    )

    Files.writeString(
      createExpected(file),
      """
      examples {
        ["user"] {
          new {
            name = "Alice"
            age = 45
          }
        }
      }
    """
        .trimIndent()
    )

    val results = evaluator.evaluateTest(path(file), false)
    assertThat(results.moduleName).startsWith("example")
    assertThat(results.displayUri).startsWith("file:///").endsWith(".pkl")
    assertThat(results.totalTests()).isEqualTo(1)
    assertThat(results.failed()).isTrue
    assertThat(results.totalFailures()).isEqualTo(1)

    val res = results.examples.results[0]
    assertThat(res.name).isEqualTo("user")
    assertFalse(results.examples.hasError())

    val fail1 = res.failures[0]
    assertThat(fail1.rendered.stripFileAndLines(tempDir))
      .isEqualTo(
        """
      (/tempDir/example.pkl)
      Expected: (/tempDir/example.pkl-expected.pcf)
      new {
        name = "Alice"
        age = 45
      }
      Actual: (/tempDir/example.pkl-actual.pcf)
      new {
        name = "Bob"
        age = 33
      }
    """
          .trimIndent()
      )
  }

  @Test
  fun `written examples use custom string delimiters`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile(prefix = "example", suffix = ".pkl")
    Files.writeString(
      file,
      """
      amends "pkl:test"
      
      examples {
        ["myStr"] {
          "my \"string\""
        }
      }
    """
        .trimIndent()
    )
    evaluator.evaluateTest(path(file), false)
    val expectedFile = file.parent.resolve(file.fileName.toString() + "-expected.pcf")
    assertThat(expectedFile).exists()
    assertThat(expectedFile)
      .hasContent(
        """
      examples {
        ["myStr"] {
          #"my "string""#
        }
      }

    """
          .trimIndent()
      )
  }

  // test for backwards compatibility
  @Test
  fun `examples that don't use custom string delimiters still pass`(@TempDir tempDir: Path) {
    val file = tempDir.createTempFile(prefix = "example", suffix = ".pkl")
    Files.writeString(
      file,
      """
      amends "pkl:test"
      
      examples {
        ["myStr"] {
          "my \"string\""
        }
      }
    """
        .trimIndent()
    )
    createExpected(file)
      .writeString(
        """
      examples {
        ["myStr"] {
          "my \"string\""
        }
      }

    """
          .trimIndent()
      )
    val result = evaluator.evaluateTest(path(file), false)
    assertFalse(result.failed())
  }

  companion object {
    private fun createExpected(path: Path): Path {
      return path.parent.resolve(path.fileName.toString() + "-expected.pcf").createFile()
    }

    private fun String.stripFileAndLines(tmpDir: Path) =
      replace(tmpDir.toUri().toString(), "/tempDir/")
        .replace(Regex("example\\d+"), "example")
        .replace(Regex("line \\d+"), "line x")
  }
}
