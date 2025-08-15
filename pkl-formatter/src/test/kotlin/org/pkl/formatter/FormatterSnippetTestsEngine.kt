/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import kotlin.reflect.KClass
import org.pkl.commons.test.InputOutputTestEngine
import org.pkl.parser.ParserError

abstract class AbstractFormatterSnippetTestsEngine : InputOutputTestEngine() {

  private val snippetsDir: Path =
    rootProjectDir.resolve("pkl-formatter/src/test/files/FormatterSnippetTests")

  private val expectedOutputDir: Path = snippetsDir.resolve("output")

  /** Convenience for development; this selects which snippet test(s) to run. */
  // language=regexp
  internal val selection: String = ""

  override val includedTests: List<Regex> = listOf(Regex(".*$selection\\.pkl"))

  override val inputDir: Path = snippetsDir.resolve("input")

  override val isInputFile: (Path) -> Boolean = { it.isRegularFile() }

  override fun expectedOutputFileFor(inputFile: Path): Path {
    val relativePath = relativize(inputFile, inputDir).toString()
    return expectedOutputDir.resolve(relativePath)
  }

  companion object {
    private fun relativize(path: Path, base: Path): Path {
      if (System.getProperty("os.name").contains("Windows")) {
        if (path.isAbsolute && base.isAbsolute && (path.root != base.root)) {
          return path
        }
      }
      return base.relativize(path)
    }
  }
}

class FormatterSnippetTestsEngine : AbstractFormatterSnippetTestsEngine() {
  override val testClass: KClass<*> = FormatterSnippetTests::class

  override fun generateOutputFor(inputFile: Path): Pair<Boolean, String> {
    val formatter = Formatter()
    val (success, output) =
      try {
        val res = formatter.format(inputFile)
        true to res
      } catch (_: ParserError) {
        false to ""
      }

    return success to output
  }
}
