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

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.Nodes
import org.pkl.parser.GenericParser

class Formatter {
  fun format(path: Path): String {
    try {
      return format(Files.readString(path))
    } catch (e: IOException) {
      throw RuntimeException("Could not format $path:", e)
    }
  }

  fun format(text: String): String {
    val parser = GenericParser()
    val builder = Builder(text)
    val gen = Generator()
    val ast = parser.parseModule(text)
    val formatAst = builder.format(ast)
    // force a line at the end of the file
    gen.generate(Nodes(listOf(formatAst, ForceLine)))
    return gen.toString()
  }
}
