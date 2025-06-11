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
package org.pkl.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import org.pkl.commons.cli.CliException
import org.pkl.formatter.Formatter

class FormatterCommand : NoOpCliktCommand(name = "format") {
  override fun help(context: Context) = "Run commands related to formatting"

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  init {
    subcommands(CheckCommand(), ApplyCommand())
  }
}

abstract class FormatSubcommand(name: String) : CliktCommand(name = name) {
  protected val writer = System.out.writer()
}

class CheckCommand : FormatSubcommand(name = "check") {
  override fun help(context: Context) =
    "Check if the given files are properly formatted, printing the file name to stdout in case they are not. Returns non-zero in case of failure."

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val paths: List<Path> by
    argument(name = "paths", help = "File paths to check.")
      .path(mustExist = true, canBeDir = false)
      .multiple()
      .validate { files ->
        if (files.isEmpty()) {
          fail("No files provided.")
        }
      }

  override fun run() {
    var status = 0
    for (path in paths) {
      val contents = path.readText()
      val formatted = Formatter().format(contents)
      if (contents != formatted) {
        writer.appendLine(path.toAbsolutePath().toString())
        status = 1
      }
      writer.flush()
    }
    exitProcess(status)
  }
}

class ApplyCommand : FormatSubcommand(name = "apply") {
  override fun help(context: Context) =
    "Overwrite all the files in place with the formatted version."

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val paths: List<Path> by
    argument(name = "paths", help = "File paths to format.")
      .path(mustExist = true, canBeDir = false)
      .multiple()
      .validate { files ->
        if (files.isEmpty()) {
          fail("No files provided.")
        }
      }

  val silent: Boolean by
    option(
        names = arrayOf("-s", "--silent"),
        help = "Do not write the name of the files that failed formatting to stdout.",
      )
      .flag()

  override fun run() {
    for (path in paths) {
      val contents = path.readText()
      val formatted = Formatter().format(contents)
      if (!silent && contents != formatted) {
        writer.appendLine(path.toAbsolutePath().toString())
      }
      writer.flush()
      try {
        path.writeText(formatted, Charsets.UTF_8)
      } catch (e: IOException) {
        throw CliException("Could not overwrite `$path`: ${e.message}")
      }
    }
  }
}
