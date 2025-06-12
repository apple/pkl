/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import org.pkl.commons.cli.commands.installCommonOptions
import org.pkl.core.Release

internal val helpLink = "${Release.current().documentation.homepage}pkl-cli/index.html#usage"

class RootCommand : NoOpCliktCommand(name = "pkl") {
  override val printHelpOnEmptyArgs = true

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  init {
    context {
      suggestTypoCorrection = { given, possible ->
        if (!given.startsWith("-")) {
          registeredSubcommands().map { it.commandName }
        } else possible
      }
    }

    installCommonOptions()

    subcommands(
      EvalCommand(),
      ReplCommand(),
      ServerCommand(),
      TestCommand(),
      ProjectCommand(),
      DownloadPackageCommand(),
      AnalyzeCommand(),
      CompletionCommand(
        name = "shell-completion",
        help = "Generate a completion script for the given shell",
        epilog = "For more information, visit $helpLink",
      ),
    )
  }
}
