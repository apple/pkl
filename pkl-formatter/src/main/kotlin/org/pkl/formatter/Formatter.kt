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

import java.nio.file.Files
import java.nio.file.Path
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.Nodes
import org.pkl.parser.GenericParser

/** A formatter for Pkl files that applies canonical formatting rules. */
class Formatter {
  /**
   * Formats a Pkl file from the given file path.
   *
   * @param path the path to the Pkl file to format
   * @param version grammar compatibility version
   * @return the formatted Pkl source code as a string
   * @throws java.io.IOException if the file cannot be read
   */
  fun format(path: Path, version: CompatVersion = CompatVersion.latest()): String {
    return format(Files.readString(path), version)
  }

  /**
   * Formats the given Pkl source code text.
   *
   * @param text the Pkl source code to format
   * @param version grammar compatibility version
   * @return the formatted Pkl source code as a string
   */
  fun format(text: String, version: CompatVersion = CompatVersion.latest()): String {
    val parser = GenericParser()
    val builder = Builder(text, version)
    val gen = Generator()
    val ast = parser.parseModule(text)
    val formatAst = builder.format(ast)
    // force a line at the end of the file
    gen.generate(Nodes(listOf(formatAst, ForceLine)))
    return gen.toString()
  }
}

/** Grammar compatibility version. */
enum class CompatVersion(val description: String, val latest: Boolean = false) {
  V1("From first version to 0.29 (no trailing commas)"),
  V2("From 0.30 on", latest = true);

  companion object {
    fun latest(): CompatVersion = entries.first { it.latest }
  }
}
