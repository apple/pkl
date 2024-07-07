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

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.isRegularFile
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.AnsiMode
import org.pkl.core.*
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.ModulePathResolver
import org.pkl.core.project.Project
import org.pkl.core.resource.ResourceReader
import org.pkl.core.resource.ResourceReaders
import org.pkl.core.settings.PklSettings
import org.pkl.core.util.IoUtils

/** Building block for CLI commands. Configured programmatically to allow for embedding. */
abstract class CliCommand(protected val cliOptions: CliBaseOptions) {
  /** Runs this command. */
  fun run() {
    if (cliOptions.testMode) {
      IoUtils.setTestMode()
    }
    when (cliOptions.colors) {
      "never" -> {
        // Configure Jansi to not even inject Ansi codes
        System.setProperty(Ansi.DISABLE, "true")

        // But also strip anything that might end up in the output
        AnsiConsole.err().mode = AnsiMode.Strip
        AnsiConsole.out().mode = AnsiMode.Strip
      }
      "always" -> {
        AnsiConsole.err().mode = AnsiMode.Force
        AnsiConsole.out().mode = AnsiMode.Force
      }
    }

    try {
      proxyAddress?.let(IoUtils::setSystemProxy)
      doRun()
    } catch (e: PklException) {
      throw CliException(e.message!!)
    } catch (e: CliException) {
      throw e
    } catch (e: Exception) {
      throw CliBugException(e)
    }
  }

  /**
   * Implements this command. May throw [PklException] or [CliException]. Any other thrown exception
   * is treated as a bug.
   */
  protected abstract fun doRun()

  /** The Pkl settings used by this command. */
  @Suppress("MemberVisibilityCanBePrivate")
  protected val settings: PklSettings by lazy {
    try {
      if (cliOptions.normalizedSettingsModule != null) {
        PklSettings.load(ModuleSource.uri(cliOptions.normalizedSettingsModule))
      } else {
        PklSettings.loadFromPklHomeDir()
      }
    } catch (e: PklException) {
      // do not use `errorRenderer` because it depends on `settings`
      throw CliException(e.toString())
    }
  }

  /** The Project used by this command. */
  protected val project: Project? by lazy {
    if (cliOptions.noProject) {
      null
    } else {
      cliOptions.normalizedProjectFile?.let { loadProject(it) }
    }
  }

  protected fun loadProject(projectFile: Path): Project {
    val securityManager =
      SecurityManagers.standard(
        cliOptions.allowedModules ?: SecurityManagers.defaultAllowedModules,
        cliOptions.allowedResources ?: SecurityManagers.defaultAllowedResources,
        SecurityManagers.defaultTrustLevels,
        cliOptions.normalizedRootDir
      )
    val envVars = cliOptions.environmentVariables ?: System.getenv()
    val stackFrameTransformer =
      if (IoUtils.isTestMode()) StackFrameTransformers.empty
      else StackFrameTransformers.defaultTransformer
    return Project.loadFromPath(
      projectFile,
      securityManager,
      cliOptions.timeout,
      stackFrameTransformer,
      envVars
    )
  }

  private val evaluatorSettings: PklEvaluatorSettings? by lazy {
    if (cliOptions.omitProjectSettings) null else project?.evaluatorSettings
  }

  protected val allowedModules: List<Pattern> by lazy {
    cliOptions.allowedModules
      ?: evaluatorSettings?.allowedModules ?: SecurityManagers.defaultAllowedModules
  }

  protected val allowedResources: List<Pattern> by lazy {
    cliOptions.allowedResources
      ?: evaluatorSettings?.allowedResources ?: SecurityManagers.defaultAllowedResources
  }

  protected val rootDir: Path? by lazy {
    cliOptions.normalizedRootDir ?: evaluatorSettings?.rootDir
  }

  protected val environmentVariables: Map<String, String> by lazy {
    cliOptions.environmentVariables ?: evaluatorSettings?.env ?: System.getenv()
  }

  protected val externalProperties: Map<String, String> by lazy {
    cliOptions.externalProperties ?: evaluatorSettings?.externalProperties ?: emptyMap()
  }

  protected val moduleCacheDir: Path? by lazy {
    if (cliOptions.noCache) null
    else
      cliOptions.normalizedModuleCacheDir
        ?: evaluatorSettings?.let { settings ->
          if (settings.noCache == true) null else settings.moduleCacheDir
        }
          ?: IoUtils.getDefaultModuleCacheDir()
  }

  protected val modulePath: List<Path> by lazy {
    cliOptions.normalizedModulePath ?: evaluatorSettings?.modulePath ?: emptyList()
  }

  protected val stackFrameTransformer: StackFrameTransformer by lazy {
    if (cliOptions.testMode) {
      StackFrameTransformers.empty
    } else {
      StackFrameTransformers.createDefault(settings)
    }
  }

  protected val securityManager: SecurityManager by lazy {
    SecurityManagers.standard(
      allowedModules,
      allowedResources,
      SecurityManagers.defaultTrustLevels,
      rootDir
    )
  }

  private val proxyAddress by lazy {
    cliOptions.httpProxy
      ?: project?.evaluatorSettings?.http?.proxy?.address ?: settings.http?.proxy?.address
  }

  private val noProxy by lazy {
    cliOptions.httpNoProxy
      ?: project?.evaluatorSettings?.http?.proxy?.noProxy ?: settings.http?.proxy?.noProxy
  }

  private fun HttpClient.Builder.addDefaultCliCertificates() {
    val caCertsDir = IoUtils.getPklHomeDir().resolve("cacerts")
    var certsAdded = false
    if (Files.isDirectory(caCertsDir)) {
      Files.list(caCertsDir)
        .filter { it.isRegularFile() }
        .forEach { cert ->
          certsAdded = true
          addCertificates(cert)
        }
    }
    if (!certsAdded) {
      val defaultCerts =
        javaClass.classLoader.getResourceAsStream("org/pkl/commons/cli/PklCARoots.pem")
          ?: throw CliException("Could not find bundled certificates")
      addCertificates(defaultCerts.readAllBytes())
    }
  }

  /**
   * The HTTP client used for this command.
   *
   * To release resources held by the HTTP client in a timely manner, call [HttpClient.close].
   */
  val httpClient: HttpClient by lazy {
    with(HttpClient.builder()) {
      setTestPort(cliOptions.testPort)
      if (cliOptions.normalizedCaCertificates.isEmpty()) {
        addDefaultCliCertificates()
      } else {
        for (file in cliOptions.normalizedCaCertificates) addCertificates(file)
      }
      if ((proxyAddress ?: noProxy) != null) {
        setProxy(proxyAddress, noProxy ?: listOf())
      }
      // Lazy building significantly reduces execution time of commands that do minimal work.
      // However, it means that HTTP client initialization errors won't surface until an HTTP
      // request is made.
      buildLazily()
    }
  }

  protected fun moduleKeyFactories(modulePathResolver: ModulePathResolver): List<ModuleKeyFactory> {
    return buildList {
      add(ModuleKeyFactories.standardLibrary)
      add(ModuleKeyFactories.modulePath(modulePathResolver))
      add(ModuleKeyFactories.pkg)
      add(ModuleKeyFactories.projectpackage)
      addAll(ModuleKeyFactories.fromServiceProviders())
      add(ModuleKeyFactories.file)
      add(ModuleKeyFactories.http)
      add(ModuleKeyFactories.genericUrl)
    }
  }

  private fun resourceReaders(modulePathResolver: ModulePathResolver): List<ResourceReader> {
    return buildList {
      add(ResourceReaders.environmentVariable())
      add(ResourceReaders.externalProperty())
      add(ResourceReaders.modulePath(modulePathResolver))
      add(ResourceReaders.pkg())
      add(ResourceReaders.projectpackage())
      add(ResourceReaders.file())
      add(ResourceReaders.http())
      add(ResourceReaders.https())
    }
  }

  /**
   * Creates an [EvaluatorBuilder] preconfigured according to [cliOptions]. To avoid resource leaks,
   * `ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)` must be called once the returned
   * builder and evaluators built by it are no longer in use.
   */
  protected fun evaluatorBuilder(): EvaluatorBuilder {
    // indirectly closed by `ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)`
    val modulePathResolver = ModulePathResolver(modulePath)
    return EvaluatorBuilder.unconfigured()
      .setStackFrameTransformer(stackFrameTransformer)
      .apply { project?.let { setProjectDependencies(it.dependencies) } }
      .setSecurityManager(securityManager)
      .setHttpClient(httpClient)
      .setExternalProperties(externalProperties)
      .setEnvironmentVariables(environmentVariables)
      .addModuleKeyFactories(moduleKeyFactories(modulePathResolver))
      .addResourceReaders(resourceReaders(modulePathResolver))
      .setLogger(Loggers.stdErr())
      .setTimeout(cliOptions.timeout)
      .setModuleCacheDir(moduleCacheDir)
  }
}
