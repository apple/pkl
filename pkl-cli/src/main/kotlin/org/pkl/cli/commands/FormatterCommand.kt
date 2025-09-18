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

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.cli.CliFormatterApply
import org.pkl.cli.CliFormatterCheck
import org.pkl.commons.cli.commands.BaseCommand

class FormatterCommand : NoOpCliktCommand(name = "format") {
  override fun help(context: Context) = "Run commands related to formatting"

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  init {
    subcommands(FormatterCheckCommand(), FormatterApplyCommand())
  }
}

class FormatterCheckCommand : BaseCommand(name = "check", helpLink = helpLink) {
  override val helpString: String =
    "Check if the given files are properly formatted, printing the file name to stdout in case they are not. Returns non-zero in case of failure."

  val paths: List<Path> by
    argument(name = "paths", help = "Files or directory to check.")
      .path(mustExist = true, canBeDir = true)
      .multiple()

  override fun run() {
    CliFormatterCheck(baseOptions.baseOptions(emptyList()), paths).run()
  }
}

class FormatterApplyCommand : BaseCommand(name = "apply", helpLink = helpLink) {
  override val helpString: String =
    "Overwrite all the files in place with the formatted version. Returns non-zero in case of failure."

  val paths: List<Path> by
    argument(name = "paths", help = "Files or directory to format.")
      .path(mustExist = true, canBeDir = true)
      .multiple()

  val silent: Boolean by
    option(
        names = arrayOf("-s", "--silent"),
        help = "Do not write the name of the files that failed formatting to stdout.",
      )
      .flag()

  override fun run() {
    CliFormatterApply(baseOptions.baseOptions(emptyList()), paths, silent).run()
  }
}
