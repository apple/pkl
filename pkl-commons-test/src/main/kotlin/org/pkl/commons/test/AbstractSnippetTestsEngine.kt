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
package org.pkl.commons.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

abstract class AbstractSnippetTestsEngine : InputOutputTestEngine() {
  private val lineNumberRegex = Regex("(?m)^(( ║ )*)(\\d+) \\|")
  private val hiddenExtensionRegex = Regex(".*[.]([^.]*)[.]pkl")

  protected abstract val snippetsDir: Path

  private val expectedOutputDir: Path by lazy { snippetsDir.resolve("output") }

  /**
   * Convenience for development; this selects which snippet test(s) to run. There is a
   * (non-language-snippet) test to make sure this is `""` before commit.
   */
  // language=regexp
  val selection: String = ""

  protected val packageServer: PackageServer = PackageServer()

  override val includedTests: List<Regex> = listOf(Regex(".*$selection\\.pkl"))

  override val excludedTests: List<Regex> = listOf(Regex(".*/native/.*"))

  override val inputDir: Path by lazy { snippetsDir.resolve("input") }

  override val isInputFile: (Path) -> Boolean = { it.isRegularFile() }

  protected tailrec fun Path.getProjectDir(): Path? =
    if (Files.exists(this.resolve("PklProject"))) this else parent?.getProjectDir()

  override fun expectedOutputFileFor(inputFile: Path): Path {
    val relativePath = inputDir.relativize(inputFile).toString()
    val stdoutPath =
      if (relativePath.matches(hiddenExtensionRegex)) relativePath.dropLast(4)
      else relativePath.dropLast(3) + "pcf"
    return expectedOutputDir.resolve(stdoutPath)
  }

  override fun afterAll() {
    packageServer.close()
  }

  protected fun String.stripFilePaths() = replace(snippetsDir.toString(), "/\$snippetsDir")

  protected fun String.stripLineNumbers() =
    replace(lineNumberRegex) { result ->
      // replace line number with equivalent number of 'x' characters to keep formatting intact
      (result.groups[1]!!.value) + "x".repeat(result.groups[3]!!.value.length) + " |"
    }
}
