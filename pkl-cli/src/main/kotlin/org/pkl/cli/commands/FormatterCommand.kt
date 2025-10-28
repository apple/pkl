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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.cli.CliFormatterCommand
import org.pkl.formatter.GrammarVersion

class FormatterCommand : CliktCommand(name = "format") {
  override fun help(context: Context) =
    """
    Format or check formatting of Pkl files.
    
    Exit codes:
      - 0: All files were properly formatted
      - 1: A non-formatting error ocurred (Ex.: IO error)
      - 11: One or more files had formatting violations
    
    Examples:
    
    ```
    # Overwrite all Pkl files inside `my/folder/`, recursively.
    pkl format -w my/folder/
    
    # Write the paths of all files which have formatting violations to stdout.
    # Exit with exit code `11` if formatting violations were found.
    pkl format --names foo.pkl
    ```
  """
      .trimIndent()

  override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val paths: List<Path> by
    argument(name = "paths", help = "Files or directory to check.")
      .path(mustExist = true, canBeDir = true)
      .multiple()

  val grammarVersion: GrammarVersion by
    option(
        names = arrayOf("--grammar-version"),
        help =
          """
          The grammar compatibility version to use.$NEWLINE
          ${GrammarVersion.entries.joinToString("$NEWLINE", prefix = "  ") {
            val default = if (it == GrammarVersion.latest()) " `(default)`" else ""
            "`${it.version}`: ${it.versionSpan}$default"
          }}
          """
            .trimIndent(),
      )
      .enum<GrammarVersion> { "${it.version}" }
      .default(GrammarVersion.latest())

  val overwrite: Boolean by
    option(
        names = arrayOf("-w", "--write"),
        help = "Format files in place, overwriting them. Implies `--names`.",
      )
      .flag(default = false)

  val names: Boolean by
    option(
        names = arrayOf("--names"),
        help = "Write the path of files with formatting violations to stdout",
      )
      .flag(default = false)

  override fun run() {
    CliFormatterCommand(paths, grammarVersion, overwrite, names).run()
  }
}
