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
package org.pkl.cli.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.pkl.cli.CliPackageDownloader
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.ProjectOptions
import org.pkl.commons.cli.commands.single
import org.pkl.core.packages.PackageUri

class DownloadPackageCommand : BaseCommand(name = "download-package") {
  override val helpString =
    """
  Download package(s)
  
  This command downloads the specified packages to the cache directory.
  If the package already exists in the cache directory, this command is a no-op.
  
  Examples:
  
  ```
  # Download two packages
  $ pkl download-package package://example.com/package1@1.0.0 package://example.com/package2@1.0.0 
  ```
  """
      .trimIndent()

  private val projectOptions by ProjectOptions()

  private val packageUris: List<PackageUri> by
    argument("package", "The package URIs to download")
      .convert { PackageUri(it) }
      .multiple(required = true)

  private val noTransitive: Boolean by
    option(
        names = arrayOf("--no-transitive"),
        help = "Skip downloading transitive dependencies of a package",
      )
      .single()
      .flag()

  override fun run() {
    CliPackageDownloader(
        baseOptions.baseOptions(emptyList(), projectOptions),
        packageUris,
        noTransitive,
      )
      .run()
  }
}
