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
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.core.runtime.VmUtils
import org.pkl.core.util.IoUtils

@Suppress("MemberVisibilityCanBePrivate")
class BaseOptions : OptionGroup() {
  companion object {
    /**
     * Parses [moduleName] into a URI. If scheme is not present, we expect that this is a file path
     * and encode any possibly invalid characters, and also normalize directory separators. If a
     * scheme is present, we expect that this is a valid URI.
     */
    fun parseModuleName(moduleName: String): URI =
      when (moduleName) {
        "-" -> VmUtils.REPL_TEXT_URI
        else ->
          // Don't use `IoUtils.toUri` here becaus we need to normalize `\` paths to `/` on Windows.
          try {
            if (IoUtils.isUriLike(moduleName)) URI(moduleName)
            // Can't just use URI constructor, because URI(null, null, "C:/foo/bar", null) turns
            // into `URI("C", null, "/foo/bar", null)`.
            else if (IoUtils.isWindowsAbsolutePath(moduleName)) Path.of(moduleName).toUri()
            else URI(null, null, IoUtils.toNormalizedPathString(Path.of(moduleName)), null)
          } catch (e: URISyntaxException) {
            val message = buildString {
              append("Module URI `$moduleName` has invalid syntax (${e.reason}).")
              if (e.index > -1) {
                append("\n\n")
                append(moduleName)
                append("\n")
                append(" ".repeat(e.index))
                append("^")
              }
            }
            throw CliException(message)
          }
      }
  }

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
      parseModuleName(it)
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
        help = "Only trust CA certificates from the provided file(s)."
      )
      .path()
      .multiple()

  @Suppress("HttpUrlsUsage")
  val proxy: URI? by
    option(
        names = arrayOf("--http-proxy"),
        metavar = "<address>",
        help = "Proxy to use for HTTP(S) connections."
      )
      .single()
      .convert { URI(it) }
      .validate { uri ->
        require(
          uri.scheme == "http" && uri.host != null && uri.path.isEmpty() && uri.userInfo == null
        ) {
          "Malformed proxy URI (expecting `http://<host>[:<port>]`)"
        }
      }

  val noProxy: List<String>? by
    option(
        names = arrayOf("--http-no-proxy"),
        metavar = "<pattern1,pattern2>",
        help = "Hostnames that should not be connected to via a proxy."
      )
      .single()
      .split(",")

  val colors: String by
    option(names = arrayOf("--colors"), help = "Enable or disable colour output in the terminal")
      .choice("auto", "never", "always")
      .default("auto")

  // hidden option used by native tests
  private val testPort: Int by
    option(names = arrayOf("--test-port"), help = "Internal test option", hidden = true)
      .single()
      .int()
      .default(-1)

  fun baseOptions(
    modules: List<URI>,
    projectOptions: ProjectOptions? = null,
    testMode: Boolean = false,
    disableColors: Boolean = false,
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
      caCertificates = caCertificates,
      httpProxy = proxy,
      httpNoProxy = noProxy ?: emptyList(),
      colors = if (disableColors) "never" else colors,
    )
  }
}
