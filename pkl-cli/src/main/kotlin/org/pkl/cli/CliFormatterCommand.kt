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

import java.io.File
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
import org.pkl.commons.cli.CliTestException
import org.pkl.core.ModuleSource
import org.pkl.core.runtime.VmUtils
import org.pkl.core.util.IoUtils
import org.pkl.formatter.Formatter
import org.pkl.formatter.GrammarVersion
import org.pkl.parser.GenericParserError

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

  private fun writeErr(error: String) {
    errWriter.write(error)
    errWriter.appendLine()
    errWriter.flush()
  }

  private fun write(message: String) {
    if (silent) return
    consoleWriter.write(message)
    consoleWriter.appendLine()
    consoleWriter.flush()
  }

  private fun allSources(): Stream<ModuleSource> {
    return paths.distinct().stream().flatMap { path ->
      when {
        path.toString() == "-" -> Stream.of(ModuleSource.text(IoUtils.readString(System.`in`)))
        path.isDirectory() ->
          Files.walk(path)
            .filter { it.extension == "pkl" || it.name == "PklProject" }
            .map(ModuleSource::path)
        else -> Stream.of(ModuleSource.path(path))
      }
    }
  }

  override fun doRun() {
    val status = Status(SUCCESS)

    handleSources(status)

    when (status.status) {
      FORMATTING_VIOLATION -> {
        // using CliTestException instead of CliException because we want full control on how to
        // print errors
        throw CliTestException("", status.status)
      }
      ERROR -> {
        if (!silent) {
          writeErr("An error occurred during formatting.")
        }
        throw CliTestException("", status.status)
      }
    }
  }

  private fun handleSources(status: Status) {
    for (source in allSources()) {
      val path = if (source.uri == VmUtils.REPL_TEXT_URI) Path.of("-") else Path.of(source.uri)
      try {
        val contents =
          if (source.contents != null) {
            if (overwrite) {
              writeErr("Cannot write to stdin.")
              throw CliTestException("", ERROR)
            }
            source.contents!!
          } else {
            File(source.uri).readText()
          }

        val formatted = format(contents)
        if (contents != formatted) {
          status.update(FORMATTING_VIOLATION)
          if (diffNameOnly || overwrite) {
            // if `--diff-name-only` or `-w` is specified, only write file names
            write(path.toAbsolutePath().toString())
          }

          if (overwrite) {
            path.writeText(formatted, Charsets.UTF_8)
          }
        }

        if (!diffNameOnly && !overwrite) {
          write(formatted)
        }
      } catch (pe: GenericParserError) {
        writeErr("Could not format `$path`: $pe")
        status.update(ERROR)
      } catch (e: IOException) {
        writeErr("IO error while reading `$path`: ${e.message}")
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
