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
package org.pkl.cli

import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.core.packages.PackageResolver
import org.pkl.core.packages.PackageUri

class CliDownloadPackageCommand(
  baseOptions: CliBaseOptions,
  private val packageUris: List<PackageUri>,
  private val noTranstive: Boolean
) : CliCommand(baseOptions) {

  override fun doRun() {
    if (moduleCacheDir == null) {
      throw CliException("Cannot download packages because no cache directory is specified.")
    }
    val packageResolver = PackageResolver.getInstance(securityManager, moduleCacheDir)
    val errors = mutableMapOf<PackageUri, Throwable>()
    for (pkg in packageUris) {
      try {
        packageResolver.downloadPackage(pkg, pkg.checksums, noTranstive)
      } catch (e: Throwable) {
        errors[pkg] = e
      }
    }
    when (errors.size) {
      0 -> return
      1 -> throw CliException(errors.values.single().message!!)
      else ->
        throw CliException(
          buildString {
            appendLine("Failed to download some packages.")
            for ((uri, error) in errors) {
              appendLine()
              appendLine("Failed to download $uri because:")
              appendLine("${error.message}")
            }
          }
        )
    }
  }
}
