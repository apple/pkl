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

import com.github.ajalt.clikt.core.ProgramResult
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.formatter.Formatter
import org.pkl.formatter.GrammarVersion
import org.pkl.parser.GenericParserError

class CliFormatterCommand
@JvmOverloads
constructor(
  private val paths: List<Path>,
  private val grammarVersion: GrammarVersion,
  private val overwrite: Boolean,
  private val names: Boolean,
  private val consoleWriter: Writer = System.out.writer(),
  private val errWriter: Writer = System.err.writer(),
) : CliCommand(CliBaseOptions()) {

  private fun format(contents: String): String {
    return Formatter().format(contents, grammarVersion)
  }

  private fun writeErr(error: String) {
    errWriter.write(error)
    errWriter.appendLine()
    errWriter.flush()
  }

  @OptIn(ExperimentalPathApi::class)
  private fun paths(): Set<Path> {
    val allPaths = mutableSetOf<Path>()
    for (path in paths) {
      if (path.isDirectory()) {
        allPaths.addAll(path.walk().filter { it.extension == "pkl" || it.name == "PklProject" })
      } else {
        allPaths.add(path)
      }
    }
    return allPaths
  }

  override fun doRun() {
    val status = Status(SUCCESS)

    for (path in paths()) {
      try {
        val contents = Files.readString(path)
        val formatted = format(contents)
        if (contents != formatted) {
          status.update(VALIDATION_ERROR)
          if (names || overwrite) {
            // if `--names` or `-w` is specified, only write file names
            consoleWriter.write(path.toAbsolutePath().toString())
          } else {
            consoleWriter.write(formatted)
          }
          consoleWriter.appendLine()
          consoleWriter.flush()

          if (overwrite) {
            path.writeText(formatted, Charsets.UTF_8)
          }
        }
      } catch (pe: GenericParserError) {
        writeErr("Could not format `$path`: $pe")
        status.update(ERROR)
      } catch (e: IOException) {
        writeErr("IO error while reading `$path`: ${e.message}")
        status.update(ERROR)
      }
    }

    throw ProgramResult(status.status)
  }

  companion object {
    private const val SUCCESS = 0
    private const val VALIDATION_ERROR = 11
    private const val ERROR = 1

    private class Status(var status: Int) {
      fun update(newStatus: Int) {
        status = if (newStatus == ERROR || newStatus > status) newStatus else status
      }
    }
  }
}
