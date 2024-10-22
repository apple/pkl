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

import java.net.URI
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.writeString
import org.pkl.core.OutputFormat
import org.pkl.core.util.StringBuilderWriter

class CliImportAnalyzerTest {
  @Test
  fun `write to console writer`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("test.pkl").writeString("import \"bar.pkl\"")
    val otherFile = tempDir.resolve("bar.pkl").writeString("")
    val baseOptions = CliBaseOptions(sourceModules = listOf(file.toUri()))
    val sb = StringBuilder()
    val analyzer = CliImportAnalyzer(CliImportAnalyzerOptions(baseOptions), StringBuilderWriter(sb))
    analyzer.run()
    assertThat(sb.toString())
      .isEqualTo(
        """
          imports {
            ["${otherFile.toUri()}"] {}
            ["${file.toUri()}"] {
              "${otherFile.toUri()}"
            }
          }
          resolvedImports {
            ["${otherFile.toUri()}"] = "${otherFile.toRealPath().toUri()}"
            ["${file.toUri()}"] = "${file.toRealPath().toUri()}"
          }

        """
          .trimIndent()
      )
  }

  @Test
  fun `different output format`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("test.pkl").writeString("import \"bar.pkl\"")
    val otherFile = tempDir.resolve("bar.pkl").writeString("")
    val baseOptions = CliBaseOptions(sourceModules = listOf(file.toUri()))
    val sb = StringBuilder()
    val analyzer =
      CliImportAnalyzer(
        CliImportAnalyzerOptions(baseOptions, outputFormat = OutputFormat.JSON.toString()),
        StringBuilderWriter(sb)
      )
    analyzer.run()
    assertThat(sb.toString())
      .isEqualTo(
        """
          {
            "imports": {
              "${otherFile.toUri()}": [],
              "${file.toUri()}": [
                "${otherFile.toUri()}"
              ]
            },
            "resolvedImports": {
              "${otherFile.toUri()}": "${otherFile.toRealPath().toUri()}",
              "${file.toUri()}": "${file.toRealPath().toUri()}"
            }
          }

        """
          .trimIndent()
      )
  }

  @Test
  fun `write to output file`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("test.pkl").writeString("import \"bar.pkl\"")
    val otherFile = tempDir.resolve("bar.pkl").writeString("")
    val outputPath = tempDir.resolve("imports.pcf")
    val baseOptions = CliBaseOptions(sourceModules = listOf(file.toUri()))
    val analyzer = CliImportAnalyzer(CliImportAnalyzerOptions(baseOptions, outputPath = outputPath))
    analyzer.run()
    assertThat(outputPath)
      .hasContent(
        """
          imports {
            ["${otherFile.toUri()}"] {}
            ["${file.toUri()}"] {
              "${otherFile.toUri()}"
            }
          }
          resolvedImports {
            ["${otherFile.toUri()}"] = "${otherFile.toRealPath().toUri()}"
            ["${file.toUri()}"] = "${file.toRealPath().toUri()}"
          }

        """
          .trimIndent()
      )
  }

  @Test
  fun `invalid syntax in module`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("test.pkl").writeString("foo = bar(]")
    assertThatCode {
        CliImportAnalyzer(
            CliImportAnalyzerOptions(
              CliBaseOptions(sourceModules = listOf(file.toUri()), settings = URI("pkl:settings"))
            )
          )
          .run()
      }
      .hasMessageContaining(
        """
          –– Pkl Error ––
          Found a syntax error when parsing module `${file.toUri()}`.
        """
          .trimIndent()
      )
  }
}
