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
@file:JvmName("Main")

package org.pkl.doc

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.net.URI
import java.nio.file.Path
import org.pkl.commons.cli.cliMain
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.BaseOptions.Companion.parseModuleName
import org.pkl.commons.cli.commands.ProjectOptions
import org.pkl.commons.cli.commands.installCommonOptions
import org.pkl.commons.cli.commands.single
import org.pkl.core.Release

/** Main method for the Pkldoc CLI. */
internal fun main(args: Array<String>) {
  cliMain { DocCommand().main(args) }
}

val helpLink = "${Release.current().documentation.homepage}pkl-doc/index.html#cli"

class DocCommand : BaseCommand(name = "pkldoc", helpLink = helpLink) {
  private val modules: List<URI> by
    argument(
        name = "modules",
        help = "Module paths/uris, or package uris to generate documentation for",
      )
      .convert { parseModuleName(it) }
      .multiple(required = true)

  private val outputDir: Path by
    option(
        names = arrayOf("-o", "--output-dir"),
        metavar = "directory",
        help = "Directory where generated documentation is placed.",
      )
      .path()
      .required()

  private val noSymlinks: Boolean by
    option(
        names = arrayOf("--no-symlinks"),
        help = "Create copies of directories and files instead of symbolic links.",
      )
      .single()
      .flag(default = false)

  private val projectOptions by ProjectOptions()

  override val helpString: String = "Generate HTML documentation from Pkl modules and packages."

  override fun run() {
    val options =
      CliDocGeneratorOptions(
        baseOptions.baseOptions(modules, projectOptions),
        outputDir,
        true,
        noSymlinks,
      )
    CliDocGenerator(options).run()
  }

  init {
    installCommonOptions()
  }
}
