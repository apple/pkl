/**
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
@file:JvmName("Main")

package org.pkl.cli

import com.github.ajalt.clikt.core.subcommands
import org.pkl.cli.commands.*
import org.pkl.commons.cli.cliMain
import org.pkl.core.Release

/** Main method of the Pkl CLI (command-line evaluator and REPL). */
internal fun main(args: Array<String>) {
  cliMain {
    val version = Release.current().versionInfo()
    val helpLink = "${Release.current().documentation().homepage()}pkl-cli/index.html#usage"
    RootCommand("pkl", version, helpLink)
      .subcommands(
        EvalCommand(helpLink),
        ReplCommand(helpLink),
        ServerCommand(helpLink),
        TestCommand(helpLink),
        ProjectCommand(helpLink),
        DownloadPackageCommand(helpLink),
        LspCommand(helpLink)
      )
      .main(args)
  }
}
