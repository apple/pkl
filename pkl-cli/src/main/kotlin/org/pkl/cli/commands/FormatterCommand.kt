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
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import org.pkl.formatter.Formatter
import org.pkl.parser.GenericParserError

class FormatterCommand : NoOpCliktCommand(name = "format") {
  override fun help(context: Context) = "Run commands related to formatting"

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  init {
    subcommands(CheckCommand(), ApplyCommand())
  }
}

abstract class FormatSubcommand(name: String) : CliktCommand(name = name) {
  protected fun format(file: Path, contents: String): Pair<String, Int> {
    try {
      return Formatter().format(contents) to 0
    } catch (pe: GenericParserError) {
      println("Could not format `$file`: $pe")
      return "" to 1
    }
  }
}

class CheckCommand : FormatSubcommand(name = "check") {
  override fun help(context: Context) =
    "Check if the given files are properly formatted, printing the file name to stdout in case they are not. Returns non-zero in case of failure."

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val path: Path by
    argument(name = "path", help = "File or directory to check.")
      .path(mustExist = true, canBeDir = true)

  @OptIn(ExperimentalPathApi::class)
  override fun run() {
    var status = 0
    val paths =
      if (path.isDirectory()) {
        path.walk().filter { it.extension == "pkl" || it.name == "PklProject" }
      } else sequenceOf(path)

    for (path in paths) {
      val contents = Files.readString(path)
      val (formatted, stat) = format(path, contents)
      status = maxOf(stat, status)
      if (contents != formatted) {
        println(path.toAbsolutePath().toString())
        status = 1
      }
    }
    exitProcess(status)
  }
}

class ApplyCommand : FormatSubcommand(name = "apply") {
  override fun help(context: Context) =
    "Overwrite all the files in place with the formatted version. Returns non-zero in case of failure."

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val path: Path by
    argument(name = "path", help = "File or directory to format.")
      .path(mustExist = true, canBeDir = true)

  val silent: Boolean by
    option(
        names = arrayOf("-s", "--silent"),
        help = "Do not write the name of the files that failed formatting to stdout.",
      )
      .flag()

  @OptIn(ExperimentalPathApi::class)
  override fun run() {
    var status = 0
    val paths =
      if (path.isDirectory()) {
        path.walk().filter { it.extension == "pkl" || it.name == "PklProject" }
      } else sequenceOf(path)

    for (path in paths) {
      val contents = Files.readString(path)
      val (formatted, stat) = format(path, contents)
      status = maxOf(stat, status)
      if (stat != 0) continue
      if (!silent && contents != formatted) {
        println(path.toAbsolutePath().toString())
      }
      try {
        path.writeText(formatted, Charsets.UTF_8)
      } catch (e: IOException) {
        println("Could not overwrite `$path`: ${e.message}")
        status = 1
      }
    }
    exitProcess(status)
  }
}
