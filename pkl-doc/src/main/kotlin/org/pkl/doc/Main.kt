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
@file:JvmName("Main")

package org.pkl.doc

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import java.net.URI
import java.nio.file.Path
import org.pkl.commons.cli.cliMain
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.BaseOptions.Companion.parseModuleName
import org.pkl.commons.cli.commands.ProjectOptions
import org.pkl.core.Release

/** Main method for the Pkldoc CLI. */
internal fun main(args: Array<String>) {
  cliMain { DocCommand().main(args) }
}

class DocCommand :
  BaseCommand(name = "pkldoc", helpLink = Release.current().documentation().homepage(), help = "") {

  private val modules: List<URI> by
    argument(
        name = "<modules>",
        help = "Module paths/uris, or package uris to generate documentation for"
      )
      .convert { parseModuleName(it) }
      .multiple(required = true)

  private val outputDir: Path by
    option(
        names = arrayOf("-o", "--output-dir"),
        metavar = "<directory>",
        help = "Directory where generated documentation is placed."
      )
      .path()
      .required()

  private val currentDirectoryMode: DocGenerator.CurrentDirectoryMode by
    option(
        names = arrayOf("--current-directory-mode"),
        metavar = "<mode>",
        help = "How current directory should be created (as a symlink or as a full copy)"
      )
      .enum<DocGenerator.CurrentDirectoryMode>()
      .default(DocGenerator.CurrentDirectoryMode.SYMLINK)

  private val projectOptions by ProjectOptions()

  override fun run() {
    val options =
      CliDocGeneratorOptions(
        baseOptions.baseOptions(
          modules,
          projectOptions,
        ),
        outputDir,
        true,
        currentDirectoryMode
      )
    CliDocGenerator(options).run()
  }
}
