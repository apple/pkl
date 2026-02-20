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
package org.pkl.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.MissingArgument
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.net.URI
import org.pkl.cli.CliCommandRunner
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.BaseOptions
import org.pkl.commons.cli.commands.ProjectOptions

class RunCommand : BaseCommand(name = "run", helpLink = helpLink) {
  override val helpString = "Run a Pkl pkl:Command CLI tool"
  override val treatUnknownOptionsAsArgs = true

  init {
    context {
      // override clikt's built-in help behavior
      // the built-in --help is eager so any --help/-h would force printing of pkl run's help
      // which is not desired when a command module (or any of its subcommands) are present
      // since that would mean command-defined help is gated behind a non-obvious `-- --help`
      helpOptionNames = emptySet()
    }
  }

  private val showHelp by option("-h", "--help", help = "Show this message and exit").flag()

  val module: URI? by
    argument(
        name = "module",
        help = "Root pkl:Command module to invoke.",
        completionCandidates = CompletionCandidates.Path,
      )
      .convert { BaseOptions.parseModuleName(it) }
      .optional()

  val args: List<String> by argument(name = "args").multiple()

  private val projectOptions by ProjectOptions()

  override fun run() {
    // if no module is specified but --help is show help, otherwise error becuase module is missing
    if (module == null)
      if (showHelp) throw PrintHelpMessage(currentContext)
      else throw MissingArgument(registeredArguments().find { it.name == "module" }!!)

    val reservedFlagNames = mutableSetOf("help")
    val reservedFlagShortNames = mutableSetOf("h")
    registeredOptions().forEach { opt ->
      (opt.names + opt.secondaryNames).forEach {
        if (it.startsWith("--")) reservedFlagNames.add(it.trimStart('-'))
        else reservedFlagShortNames.add(it.trimStart('-'))
      }
    }
    CliCommandRunner(
        baseOptions.baseOptions(listOf(module!!), projectOptions),
        reservedFlagNames,
        reservedFlagShortNames,
        if (showHelp) args + listOf("--help") else args,
      )
      .run()
  }
}
