/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.pkl.cli.CliEvaluatorOptions
import org.pkl.cli.CliRepl
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.ProjectOptions

class ReplCommand(helpLink: String) :
  BaseCommand(
    name = "repl",
    help = "Start a REPL session",
    helpLink = helpLink,
  ) {
  private val projectOptions by ProjectOptions()

  override fun run() {
    val options = CliEvaluatorOptions(base = baseOptions.baseOptions(emptyList(), projectOptions))
    CliRepl(options).run()
  }
}
