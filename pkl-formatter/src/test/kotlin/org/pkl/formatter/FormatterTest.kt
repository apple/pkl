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
package org.pkl.formatter

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.useDirectoryEntries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.readString
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.failWithDiff
import org.pkl.parser.GenericParserError

class FormatterTest {

  @Test
  fun `multiline string - wrong start quote`() {
    val ex =
      assertThrows<GenericParserError> {
        format(
          """
        foo = ""${'"'}line1
          line 2
          ""${'"'}
      """
        )
      }

    assertThat(ex.message).contains("The content of a multi-line string must begin on a new line")
  }

  @Test
  fun `multiline string - wrong end quote`() {
    val ex =
      assertThrows<GenericParserError> {
        format(
          """
        foo = ""${'"'}
          line1
          line 2""${'"'}
      """
        )
      }

    assertThat(ex.message)
      .contains("The closing delimiter of a multi-line string must begin on a new line")
  }

  @Test
  fun `multiline string - wrong indentation`() {
    val ex =
      assertThrows<GenericParserError> {
        format(
          """
        foo = ""${'"'}
          line1
        line 2
          ""${'"'}
      """
        )
      }

    assertThat(ex.message)
      .contains("Line must match or exceed indentation of the String's last line.")
  }

  private fun format(code: String): String {
    return Formatter().format(code.trimIndent())
  }

  @Test
  fun `snippet test output must be stable`() {
    val outputDir =
      FileTestUtils.rootProjectDir.resolve(
        "pkl-formatter/src/test/files/FormatterSnippetTests/output"
      )
    val formatter = Formatter()
    fun walkDir(dir: Path) {
      dir.useDirectoryEntries { children ->
        for (child in children) {
          if (child.isRegularFile()) {
            val expected = child.readString()
            val formatted = formatter.format(expected)
            if (expected != formatted) {
              failWithDiff(
                "Formatter output not stable: ${child.relativeTo(outputDir)}",
                expected,
                formatted,
              )
            }
          } else {
            walkDir(child)
          }
        }
      }
    }

    walkDir(outputDir)
  }

  @Test
  fun `whitespace only`() {
    for (src in listOf(";;;", "\n", "\n\n\n", "\t")) {
      assertThat(format(src)).isEqualTo("\n")
    }
  }


@Test
fun `multi line comments - no extra empty lines`() {
  val input =
    """
      import "pkl:json" // used for doc comments
      import "pkl:jsonnet"
      import "pkl:math" // used for doc comments
      import "pkl:pklbinary"
      import "pkl:protobuf"
      import "pkl:xml"
      import "pkl:yaml" // used for doc comments
    """

  val expected =
    """
      // used for doc comments
      // used for doc comments
      // used for doc comments
      import "pkl:json"
      import "pkl:jsonnet"
      import "pkl:math"
      import "pkl:pklbinary"
      import "pkl:protobuf"
      import "pkl:xml"
      import "pkl:yaml"
      
    """

  val formatted = format(input)

  assertThat(formatted).isEqualTo(expected.trimIndent())
}

}
