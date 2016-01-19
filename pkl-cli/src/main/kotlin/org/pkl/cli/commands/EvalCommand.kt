/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import org.pkl.cli.CliEvaluator
import org.pkl.cli.CliEvaluatorOptions
import org.pkl.commons.cli.commands.ModulesCommand
import org.pkl.commons.cli.commands.single

class EvalCommand(helpLink: String) :
  ModulesCommand(
    name = "eval",
    help = "Render pkl module(s)",
    helpLink = helpLink,
  ) {
  private val outputPath: String? by
    option(
        names = arrayOf("-o", "--output-path"),
        metavar = "<path>",
        help = "File path where the output file is placed."
      )
      .single()

  private val moduleOutputSeparator: String by
    option(
        names = arrayOf("--module-output-separator"),
        metavar = "<string>",
        help =
          "Separator to use when multiple module outputs are written to the same file. (default: ---)"
      )
      .single()
      .default("---")

  private val expression: String? by
    option(
        names = arrayOf("-x", "--expression"),
        metavar = "<expression>",
        help = "Expression to be evaluated within the module."
      )
      .single()

  private val multipleFileOutputPath: String? by
    option(
        names = arrayOf("-m", "--multiple-file-output-path"),
        metavar = "<path>",
        help = "Directory where a module's multiple file output is placed."
      )
      .single()
      .validate {
        if (outputPath != null || expression != null) {
          fail("Option is mutually exclusive with -o, --output-path and -x, --expression.")
        }
      }

  // hidden option used by the native tests
  private val testMode: Boolean by
    option(names = arrayOf("--test-mode"), help = "Internal test mode", hidden = true).flag()

  override fun run() {
    val options =
      CliEvaluatorOptions(
        base = baseOptions.baseOptions(modules, projectOptions, testMode = testMode),
        outputPath = outputPath,
        outputFormat = baseOptions.format,
        moduleOutputSeparator = moduleOutputSeparator,
        multipleFileOutputPath = multipleFileOutputPath,
        expression = expression ?: CliEvaluatorOptions.defaults.expression
      )
    CliEvaluator(options).run()
  }
}
