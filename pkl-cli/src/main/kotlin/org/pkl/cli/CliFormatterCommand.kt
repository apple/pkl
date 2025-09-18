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
package org.pkl.cli

import java.io.Writer
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.formatter.Formatter
import org.pkl.parser.GenericParserError

abstract class CliFormatterCommand
@JvmOverloads
constructor(
  options: CliBaseOptions,
  protected val paths: List<Path>,
  protected val consoleWriter: Writer = System.out.writer(),
) : CliCommand(options) {
  protected fun format(file: Path, contents: String): Pair<String, Int> {
    try {
      return Formatter().format(contents) to 0
    } catch (pe: GenericParserError) {
      consoleWriter.write("Could not format `$file`: $pe")
      consoleWriter.appendLine()
      consoleWriter.flush()
      return "" to 1
    }
  }

  @OptIn(ExperimentalPathApi::class)
  protected fun paths(): Set<Path> {
    val allPaths = mutableSetOf<Path>()
    for (path in paths) {
      if (path.isDirectory()) {
        allPaths.addAll(
          path.walk().filter { it.extension == "pkl" || it.name == "PklProject" }
        )
      } else {
        allPaths.add(path)
      }
    }
    return allPaths
  }
}
