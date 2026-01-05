/*
 * Copyright © 2025Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.nio.file.Path
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliException
import org.pkl.core.util.StringBuilderWriter
import org.pkl.formatter.GrammarVersion

class CliFormatterTest {
  @Test
  fun `no double newline when writing to stdout`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("foo.pkl").also { it.writeText("foo = 1") }
    val sb = StringBuilder()
    val writer = StringBuilderWriter(sb)
    val cmd =
      CliFormatterCommand(
        listOf(file),
        GrammarVersion.latest(),
        overwrite = false,
        diffNameOnly = false,
        silent = false,
        consoleWriter = writer,
      )
    try {
      cmd.run()
    } catch (_: CliException) {}
    assertThat(sb.toString()).isEqualTo("foo = 1\n")
  }
}
