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

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.cli.CliProjectPackager
import org.pkl.cli.CliProjectResolver
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.TestOptions
import org.pkl.commons.cli.commands.single

class ProjectCommand(helpLink: String) :
  NoOpCliktCommand(
    name = "project",
    help = "Run commands related to projects",
    epilog = "For more information, visit $helpLink"
  ) {
  init {
    subcommands(ResolveCommand(helpLink), PackageCommand(helpLink))
  }

  companion object {
    class ResolveCommand(helpLink: String) :
      BaseCommand(
        name = "resolve",
        helpLink = helpLink,
        help =
          """
        Resolve dependencies for project(s)
        
        This command takes the `dependencies` of `PklProject`s, and writes the
        resolved versions to `PklProject.deps.json` files.
        
        Examples:

        ```
        # Search the current working directory for a project, and resolve its dependencies.
        $ pkl project resolve

        # Resolve dependencies for all projects within the `packages/` directory.
        $ pkl project resolve packages/*/
        ```
        """,
      ) {
      private val projectDirs: List<Path> by
        argument("<dir>", "The project directories to resolve dependencies for").path().multiple()

      override fun run() {
        CliProjectResolver(baseOptions.baseOptions(emptyList()), projectDirs).run()
      }
    }

    private const val NEWLINE = '\u0085'

    class PackageCommand(helpLink: String) :
      BaseCommand(
        name = "package",
        helpLink = helpLink,
        help =
          """
          Verify package(s), and prepare package artifacts to be published.
  
          This command runs a project's api tests, as defined by `apiTests` in `PklProject`.
          Additionally, it verifies that all imports resolve to paths that are local to the project.
  
          Finally, this command writes the folowing artifacts into the output directory specified by the output path.
  
            - `name@version` - dependency metadata$NEWLINE
            - `name@version.sha256` - dependency metadata's SHA-256 checksum$NEWLINE
            - `name@version.zip` - package archive$NEWLINE
            - `name@version.zip.sha256` - package archive's SHA-256 checksum
  
          The output path option accepts the following placeholders:
  
            - %{name}: The display name of the package$NEWLINE
            - %{version}: The version of the package
  
          If a project has local project dependencies, the depended upon project directories must also
          be included as arguments to this command.
  
          Examples:
  
          ```
          # Search the current working directory for a project, and package it.
          $ pkl project package
  
          # Package all projects within the `packages/` directory.
          $ pkl project package packages/*/
          ```
          """
            .trimIndent(),
      ) {
      private val testOptions by TestOptions()

      private val projectDirs: List<Path> by
        argument("<dir>", "The project directories to package").path().multiple()

      private val outputPath: String by
        option(
            names = arrayOf("--output-path"),
            help = "The directory to write artifacts to",
            metavar = "<path>"
          )
          .single()
          .default(".out/%{name}@%{version}")

      private val skipPublishCheck: Boolean by
        option(
            names = arrayOf("--skip-publish-check"),
            help = "Skip checking if a package has already been published with different contents",
          )
          .single()
          .flag()

      override fun run() {
        CliProjectPackager(
            baseOptions.baseOptions(emptyList()),
            projectDirs,
            testOptions.cliTestOptions,
            outputPath,
            skipPublishCheck
          )
          .run()
      }
    }
  }
}
