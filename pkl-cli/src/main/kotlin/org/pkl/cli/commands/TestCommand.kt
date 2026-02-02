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

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.net.URI
import org.pkl.cli.CliTestRunner
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.BaseOptions
import org.pkl.commons.cli.commands.ProjectOptions
import org.pkl.commons.cli.commands.TestOptions

class TestCommand : BaseCommand(name = "test", helpLink = helpLink) {
  override val helpString = "Run tests within the given module(s)"

  val modules: List<URI> by
    argument(
        name = "modules",
        help = "Module paths or URIs to evaluate.",
        completionCandidates = CompletionCandidates.Path,
      )
      .convert { BaseOptions.parseModuleName(it) }
      .multiple()

  private val projectOptions by ProjectOptions()

  private val testOptions by TestOptions()

  private val powerAssertionsEnabled: Boolean by
    option(
        names = arrayOf("--power-assertions"),
        help = "Enable power assertions for detailed assertion failure messages.",
      )
      .flag("--no-power-assertions", default = true, defaultForHelp = "enabled")

  override fun run() {
    CliTestRunner(
        options =
          baseOptions.baseOptions(
            modules,
            projectOptions,
            powerAssertionsEnabled = powerAssertionsEnabled,
          ),
        testOptions = testOptions.cliTestOptions,
      )
      .run()
  }
}
