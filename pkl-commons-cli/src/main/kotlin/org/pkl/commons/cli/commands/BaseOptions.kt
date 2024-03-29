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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.core.util.IoUtils

@Suppress("MemberVisibilityCanBePrivate")
class BaseOptions : OptionGroup() {
  private val defaults = CliBaseOptions()

  private val output =
    arrayOf("json", "jsonnet", "pcf", "properties", "plist", "textproto", "xml", "yaml")

  val allowedModules: List<Pattern> by
    option(
        names = arrayOf("--allowed-modules"),
        help = "URI patterns that determine which modules can be loaded and evaluated."
      )
      .convert("<pattern1,pattern2>") { Pattern.compile(it) }
      .splitAll(",")

  val allowedResources: List<Pattern> by
    option(
        names = arrayOf("--allowed-resources"),
        help = "URI patterns that determine which external resources can be read."
      )
      .convert("<pattern1,pattern2>") { Pattern.compile(it) }
      .splitAll(",")

  val rootDir: Path? by
    option(
        names = arrayOf("--root-dir"),
        help =
          "Restricts access to file-based modules and resources to those located under the root directory."
      )
      .single()
      .path()

  val cacheDir: Path? by
    option(names = arrayOf("--cache-dir"), help = "The cache directory for storing packages.")
      .single()
      .path()

  val workingDir: Path by
    option(
        names = arrayOf("-w", "--working-dir"),
        help = "Base path that relative module paths are resolved against."
      )
      .single()
      .path()
      .default(defaults.normalizedWorkingDir)

  val properties: Map<String, String> by
    option(
        names = arrayOf("-p", "--property"),
        metavar = "<name=value>",
        help = "External property to set (repeatable)."
      )
      .associate()

  val noCache: Boolean by
    option(names = arrayOf("--no-cache"), help = "Disable caching of packages")
      .single()
      .flag(default = false)

  val format: String? by
    option(
        names = arrayOf("-f", "--format"),
        help = "Output format to generate. <${output.joinToString()}>"
      )
      .single()

  val envVars: Map<String, String> by
    option(
        names = arrayOf("-e", "--env-var"),
        metavar = "<name=value>",
        help = "Environment variable to set (repeatable)."
      )
      .associate()

  val modulePath: List<Path> by
    option(
        names = arrayOf("--module-path"),
        metavar = "<path1${File.pathSeparator}path2>",
        help =
          "Directories, ZIP archives, or JAR archives to search when resolving `modulepath:` URIs."
      )
      .path()
      .splitAll(File.pathSeparator)

  val settings: URI? by
    option(names = arrayOf("--settings"), help = "Pkl settings module to use.").single().convert {
      IoUtils.toUri(it)
    }

  val timeout: Duration? by
    option(
        names = arrayOf("-t", "--timeout"),
        metavar = "<number>",
        help = "Duration, in seconds, after which evaluation of a source module will be timed out."
      )
      .single()
      .long()
      .convert { Duration.ofSeconds(it) }

  val caCertificates: List<Path> by
    option(
        names = arrayOf("--ca-certificates"),
        metavar = "<path>",
        help = "Only trust CA certificates from the provided path(s)."
      )
      .path()
      .multiple()

  // hidden option used by native tests
  private val testPort: Int by
    option(names = arrayOf("--test-port"), help = "Internal test option", hidden = true)
      .single()
      .int()
      .default(-1)

  fun baseOptions(
    modules: List<URI>,
    projectOptions: ProjectOptions? = null,
    testMode: Boolean = false
  ): CliBaseOptions {
    return CliBaseOptions(
      sourceModules = modules,
      allowedModules = allowedModules.ifEmpty { null },
      allowedResources = allowedResources.ifEmpty { null },
      environmentVariables = envVars.ifEmpty { null },
      externalProperties = properties.mapValues { it.value.ifBlank { "true" } }.ifEmpty { null },
      modulePath = modulePath.ifEmpty { null },
      workingDir = workingDir,
      settings = settings,
      rootDir = rootDir,
      projectDir = projectOptions?.projectDir,
      timeout = timeout,
      moduleCacheDir = cacheDir ?: defaults.normalizedModuleCacheDir,
      noCache = noCache,
      testMode = testMode,
      testPort = testPort,
      omitProjectSettings = projectOptions?.omitProjectSettings ?: false,
      noProject = projectOptions?.noProject ?: false,
      caCertificates = caCertificates
    )
  }
}
