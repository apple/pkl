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
package org.pkl.cli

import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.math.max
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.CliTestException
import org.pkl.core.util.IoUtils
import org.pkl.formatter.Formatter
import org.pkl.formatter.GrammarVersion
import org.pkl.parser.GenericParserError
import org.pkl.parser.ParserError

class CliFormatterCommand
@JvmOverloads
constructor(
  private val paths: List<Path>,
  private val grammarVersion: GrammarVersion,
  private val overwrite: Boolean,
  private val diffNameOnly: Boolean,
  private val silent: Boolean,
  private val consoleWriter: Writer = System.out.writer(),
  private val errWriter: Writer = System.err.writer(),
) : CliCommand(CliBaseOptions()) {
  private fun format(contents: String): String {
    return Formatter().format(contents, grammarVersion)
  }

  private fun writeErrLine(error: String) {
    errWriter.write(error)
    errWriter.appendLine()
    errWriter.flush()
  }

  private fun writeLine(message: String) {
    if (silent) return
    consoleWriter.write(message)
    consoleWriter.appendLine()
    consoleWriter.flush()
  }

  private fun allPaths(): Stream<Path> {
    return paths.distinct().stream().flatMap { path ->
      when {
        path.toString() == "-" -> Stream.of(path)
        path.isDirectory() ->
          Files.walk(path)
            .filter { it.extension == "pkl" || it.name == "PklProject" }
            .map { it.normalize() }
        else -> Stream.of(path.normalize())
      }
    }
  }

  override fun doRun() {
    val status = Status(SUCCESS)

    handlePaths(status)

    when (status.status) {
      FORMATTING_VIOLATION -> {
        // using CliTestException instead of CliException because we want full control on how to
        // print errors
        throw CliTestException("", status.status)
      }
      ERROR -> {
        if (!silent) {
          writeErrLine("An error occurred during formatting.")
        }
        throw CliTestException("", status.status)
      }
    }
  }

  private fun handlePaths(status: Status) {
    for (path in allPaths()) {
      val pathStr = path.toString()
      try {
        val contents =
          when {
            pathStr == "-" -> IoUtils.readString(System.`in`)
            else -> Files.readString(path)
          }
        if (pathStr == "-" && overwrite) {
          throw CliException("Cannot write to stdin", ERROR)
        }

        val formatted = format(contents)
        if (contents != formatted) {
          if (diffNameOnly || overwrite) {
            // if `--diff-name-only` or `-w` is specified, only write file names
            writeLine(pathStr)
          }

          if (overwrite) {
            path.writeText(formatted, Charsets.UTF_8)
          } else {
            // only exit on violation for "check" operations, not when overwriting
            status.update(FORMATTING_VIOLATION)
          }
        }

        if (!diffNameOnly && !overwrite) {
          consoleWriter.write(formatted)
          consoleWriter.flush()
        }
      } catch (pe: ParserError) { // thrown by the lexer
        writeErrLine("Could not format `$pathStr`: $pe")
        status.update(ERROR)
      } catch (pe: GenericParserError) { // thrown by the generic parser
        writeErrLine("Could not format `$pathStr`: $pe")
        status.update(ERROR)
      } catch (e: IOException) {
        writeErrLine("IO error while reading `$pathStr`: ${e.message}")
        status.update(ERROR)
      }
    }
  }

  companion object {
    private const val SUCCESS = 0
    private const val FORMATTING_VIOLATION = 11
    private const val ERROR = 1

    private class Status(var status: Int) {
      fun update(newStatus: Int) {
        status =
          when {
            status == ERROR -> status
            newStatus == ERROR -> newStatus
            else -> max(status, newStatus)
          }
      }
    }
  }
}
