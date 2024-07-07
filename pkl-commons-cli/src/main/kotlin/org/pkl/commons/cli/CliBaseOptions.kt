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
package org.pkl.commons.cli

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import org.pkl.core.module.ProjectDependenciesManager
import org.pkl.core.util.IoUtils

/** Base options shared between CLI commands. */
data class CliBaseOptions(
  /** The source modules to evaluate. Relative URIs are resolved against [workingDir]. */
  private val sourceModules: List<URI> = listOf(),

  /**
   * The URI patterns that determine which modules can be loaded and evaluated. Patterns are matched
   * against the beginning of module URIs. At least one pattern needs to match for a module to be
   * loadable. Both [sourceModules] and module imports are subject to this check.
   */
  val allowedModules: List<Pattern>? = null,

  /**
   * The URI patterns that determine which external resources can be read. Patterns are matched
   * against the beginning of resource URIs. At least one pattern needs to match for a resource to
   * be readable.
   */
  val allowedResources: List<Pattern>? = null,

  /**
   * The environment variables to set. Pkl code can read environment variables with
   * `read("env:<NAME>")`.
   */
  val environmentVariables: Map<String, String>? = null,

  /**
   * The external properties to set. Pkl code can read external properties with
   * `read("prop:<name>")`.
   */
  val externalProperties: Map<String, String>? = null,

  /**
   * The directories, ZIP archives, or JAR archives to search when resolving `modulepath:` URIs.
   * Relative paths are resolved against [workingDir].
   */
  private val modulePath: List<Path>? = null,

  /**
   * The base path that relative module paths passed as command-line arguments are resolved against.
   */
  private val workingDir: Path = IoUtils.getCurrentWorkingDir(),

  /**
   * The root directory for `file:` modules and resources. If non-null, access to file-based modules
   * and resources is restricted to those located under [rootDir]. Any symlinks are resolved before
   * this check is performed.
   */
  private val rootDir: Path? = null,

  /**
   * The Pkl settings file to use. A settings file is a Pkl module amending the `pkl.settings`
   * standard library module. If `null`, `~/.pkl/settings.pkl` (if present) or the defaults
   * specified in the `pkl:settings` standard library module are used.
   */
  private val settings: URI? = null,

  /**
   * The root directory of the project. The directory must contain a `PklProject` that amends the
   * `pkl.Project` standard library module.
   *
   * If `null`, looks for a `PklProject` file in [workingDir], traversing up to [rootDir], or `/` if
   * [rootDir] is `null`.
   *
   * This can be disabled with [noProject].
   */
  private val projectDir: Path? = null,

  /**
   * The duration after which evaluation of a source module will be timed out. Note that a timeout
   * is treated the same as a program error in that any subsequent source modules will not be
   * evaluated.
   */
  val timeout: Duration? = null,

  /** The cache directory for storing packages. */
  private val moduleCacheDir: Path? = null,

  /** Whether to disable the module cache. */
  val noCache: Boolean = false,

  /** Ignores any evaluator settings set in the PklProject file. */
  val omitProjectSettings: Boolean = false,

  /** Disables all behavior related to projects. */
  val noProject: Boolean = false,

  /** Tells whether to run the CLI in test mode. This is an internal option. */
  val testMode: Boolean = false,

  /**
   * Unless -1, rewrites HTTP requests that specify port 0 to the given port. This is an internal
   * test option.
   */
  val testPort: Int = -1,

  /**
   * The CA certificates to trust.
   *
   * The given files must contain [X.509](https://en.wikipedia.org/wiki/X.509) certificates in PEM
   * format.
   *
   * If [caCertificates] is the empty list, the certificate files in `~/.pkl/cacerts/` are used. If
   * `~/.pkl/cacerts/` does not exist or is empty, Pkl's built-in CA certificates are used.
   */
  val caCertificates: List<Path> = listOf(),

  /** The proxy to connect to. */
  val httpProxy: URI? = null,

  /** Hostnames, IP addresses, or CIDR blocks to not proxy. */
  val httpNoProxy: List<String>? = null,
  val colors: String = "auto"
) {

  companion object {
    tailrec fun Path.getProjectFile(rootDir: Path?): Path? {
      val candidate = resolve(ProjectDependenciesManager.PKL_PROJECT_FILENAME)
      return when {
        Files.exists(candidate) -> candidate
        parent == null -> null
        rootDir != null && !parent.startsWith(rootDir) -> null
        else -> parent.getProjectFile(rootDir)
      }
    }
  }

  /** [workingDir] after normalization. */
  val normalizedWorkingDir: Path = IoUtils.getCurrentWorkingDir().resolve(workingDir)

  /** [rootDir] after normalization. */
  val normalizedRootDir: Path? = rootDir?.let(normalizedWorkingDir::resolve)

  /** [sourceModules] after normalization. */
  val normalizedSourceModules: List<URI> =
    sourceModules
      .map { uri ->
        if (uri.isAbsolute) uri else IoUtils.resolve(normalizedWorkingDir.toUri(), uri)
      }
      // sort modules to make cli output independent of source module order
      .sorted()

  val normalizedSettingsModule: URI? =
    settings?.let { uri ->
      if (uri.isAbsolute) uri else IoUtils.resolve(normalizedWorkingDir.toUri(), uri)
    }

  /** [modulePath] after normalization. */
  val normalizedModulePath: List<Path>? = modulePath?.map(normalizedWorkingDir::resolve)

  /** [moduleCacheDir] after normalization. */
  val normalizedModuleCacheDir: Path? = moduleCacheDir?.let(normalizedWorkingDir::resolve)

  /** The effective project directory, if exists. */
  val normalizedProjectFile: Path? by lazy {
    projectDir?.resolve(ProjectDependenciesManager.PKL_PROJECT_FILENAME)
      ?: normalizedWorkingDir.getProjectFile(rootDir)
  }

  /** [caCertificates] after normalization. */
  val normalizedCaCertificates: List<Path> = caCertificates.map(normalizedWorkingDir::resolve)
}
