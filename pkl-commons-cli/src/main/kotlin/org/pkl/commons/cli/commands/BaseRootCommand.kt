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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import org.pkl.core.Release

abstract class BaseRootCommand(name: String) : NoOpCliktCommand(name = name) {
  open val helpLink: String = "${Release.current().documentation.homepage}pkl-cli/index.html#usage"
  open val helpString: String = ""

  override fun help(context: Context) = helpString

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  override val printHelpOnEmptyArgs = true

  companion object {
    private val theme = Theme { styles["markdown.code.span"] = TextStyle(bold = true) }
  }

  init {
    versionOption(Release.current().versionInfo, names = setOf("-v", "--version"), message = { it })

    installMordantMarkdown()

    context {
      suggestTypoCorrection = { given, possible ->
        if (!given.startsWith("-")) {
          registeredSubcommands().map { it.commandName }
        } else possible
      }
      terminal = Terminal(theme = theme)
    }
  }
}
