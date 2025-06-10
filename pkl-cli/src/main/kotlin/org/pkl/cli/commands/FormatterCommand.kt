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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.cli.CliFormatterRunner

class FormatterCommand : CliktCommand(name = "format") {
  override fun help(context: Context) =
    "Run the formatter with the given options on the given files."

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val files: List<Path> by
    argument(name = "paths", help = "File paths to format.")
      .path(mustExist = true, canBeDir = false)
      .multiple()
      .validate { files ->
        if (files.isEmpty()) {
          fail("No files provided.")
        }
      }

  private val list: Boolean by
    option(
        names = arrayOf("-l", "--list"),
        help =
          "Check if the inputs are properly formatted, printing the file name to stdout in case they are not. Returns non-zero in case of failure.",
      )
      .flag()
      .validate {
        if (overwrite) {
          fail("Option is mutually exclusive with -w.")
        }
      }

  private val overwrite: Boolean by
    option(
        names = arrayOf("-w", "--overwrite"),
        help = "Overwrite all the files in place with the formatted version.",
      )
      .flag()
      .validate {
        if (list) {
          fail("Option is mutually exclusive with -l.")
        }
      }

  override fun run() {
    val shouldList = if (!overwrite) true else list
    CliFormatterRunner(shouldList, overwrite, files).run()
  }
}
