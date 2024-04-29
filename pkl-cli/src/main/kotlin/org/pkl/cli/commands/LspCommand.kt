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
package org.pkl.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.pkl.commons.cli.commands.single
import org.pkl.lsp.PklLSP

class LspCommand(helpLink: String) :
  CliktCommand(
    name = "lsp",
    help = "Run a Language Server Protocol server that communicates over standard input/output",
    epilog = "For more information, visit $helpLink"
  ) {

  private val verbose: Boolean by
    option(names = arrayOf("--verbose"), help = "Send debug information to the client")
      .single()
      .flag(default = false)

  override fun run() {
    PklLSP.run(verbose)
  }
}
