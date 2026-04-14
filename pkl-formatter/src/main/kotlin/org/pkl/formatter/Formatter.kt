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

import java.io.IOException
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.Throws
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.Nodes
import org.pkl.parser.GenericParser

/**
 * A formatter for Pkl files that applies canonical formatting rules.
 *
 * @param grammarVersion grammar compatibility version
 */
class Formatter
@JvmOverloads
constructor(private val grammarVersion: GrammarVersion = GrammarVersion.latest()) {

  /**
   * Formats a Pkl file from the given file path.
   *
   * @param path the path to the Pkl file to format
   * @param grammarVersion grammar compatibility version
   * @return the formatted Pkl source code as a string
   * @throws java.io.IOException if the file cannot be read
   */
  @JvmOverloads
  @Deprecated(message = "use format(path.readText()) instead")
  fun format(path: Path, grammarVersion: GrammarVersion = GrammarVersion.latest()): String {
    return Formatter(grammarVersion).format(Files.readString(path))
  }

  /**
   * Formats the given Pkl source code text.
   *
   * @param text the Pkl source code to format
   * @param grammarVersion grammar compatibility version
   * @return the formatted Pkl source code as a string
   */
  @Deprecated(message = "use Formatter(grammarVersion).format(text) instead")
  fun format(text: String, grammarVersion: GrammarVersion): String {
    return Formatter(grammarVersion).format(text)
  }

  /**
   * Formats the given Pkl source code text.
   *
   * @param text the Pkl source code to format
   * @return the formatted Pkl source code as a string
   */
  fun format(text: String): String {
    return buildString { format(text, this) }
  }

  /**
   * Formats the given Pkl source code text.
   *
   * It is the caller's responsibility to close [input], and, if applicable, [output].
   *
   * @param input the Pkl source code to format
   * @param output the formatted Pkl source code
   * @throws java.io.IOException if an I/O error occurs during reading or writing
   */
  @Throws(IOException::class)
  fun format(input: Reader, output: Appendable) {
    format(input.readText(), output)
  }

  private fun format(input: String, output: Appendable) {
    val ast = GenericParser().parseModule(input)
    val formatAst = Builder(input, grammarVersion).format(ast)
    // force a line at the end of the file
    val nodes = Nodes(listOf(formatAst, ForceLine))
    Generator(output).generate(nodes)
  }
}

/** Grammar compatibility version. */
enum class GrammarVersion(val version: Int, val versionSpan: String) {
  V1(1, "0.25 - 0.29"),
  V2(2, "0.30+");

  companion object {
    @JvmStatic fun latest(): GrammarVersion = entries.maxBy { it.version }
  }
}
