/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.cli.CliImportAnalyzer
import org.pkl.cli.CliImportAnalyzerOptions
import org.pkl.commons.cli.commands.ModulesCommand
import org.pkl.commons.cli.commands.NoOpCommand
import org.pkl.commons.cli.commands.single

class AnalyzeCommand : NoOpCommand(name = "analyze") {
  override fun help(context: Context) = "Commands related to static analysis"

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  init {
    subcommands(AnalyzeImportsCommand())
  }
}

class AnalyzeImportsCommand : ModulesCommand(name = "imports", helpLink = helpLink) {
  override val helpString = "Prints the graph of modules imported by the input module(s)."

  private val outputPath: Path? by
    option(
        names = arrayOf("-o", "--output-path"),
        metavar = "path",
        help = "File path where the output file is placed.",
      )
      .path()
      .single()

  override fun run() {
    val options =
      CliImportAnalyzerOptions(
        base = baseOptions.baseOptions(modules, projectOptions),
        outputFormat = baseOptions.format,
        outputPath = outputPath,
      )
    CliImportAnalyzer(options).run()
  }
}
