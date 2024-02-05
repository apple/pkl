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

import java.nio.file.Path
import java.util.regex.Pattern
import org.pkl.core.*
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeyFactory
import org.pkl.core.module.ModulePathResolver
import org.pkl.core.project.Project
import org.pkl.core.resource.ResourceReader
import org.pkl.core.resource.ResourceReaders
import org.pkl.core.runtime.CertificateUtils
import org.pkl.core.settings.PklSettings
import org.pkl.core.util.IoUtils

/** Building block for CLI commands. Configured programmatically to allow for embedding. */
abstract class CliCommand(protected val cliOptions: CliBaseOptions) {
    init {
        if (cliOptions.caCertificates.isNotEmpty()) {
            CertificateUtils.setupAllX509CertificatesGlobally(cliOptions.caCertificates)
        }
    }

    /** Runs this command. */
    fun run() {
        if (cliOptions.testMode) {
            IoUtils.setTestMode()
        }
        try {
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
     * Implements this command. May throw [PklException] or [CliException]. Any other thrown
     * exception is treated as a bug.
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
            return@lazy null
        }
        cliOptions.normalizedProjectFile?.let { loadProject(it) }
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

    private val projectSettings: Project.EvaluatorSettings? by lazy {
        if (cliOptions.omitProjectSettings) {
            return@lazy null
        }
        project?.settings
    }

    protected val allowedModules: List<Pattern> by lazy {
        cliOptions.allowedModules
            ?: projectSettings?.allowedModules ?: SecurityManagers.defaultAllowedModules
    }

    protected val allowedResources: List<Pattern> by lazy {
        cliOptions.allowedResources
            ?: projectSettings?.allowedResources ?: SecurityManagers.defaultAllowedResources
    }

    protected val rootDir: Path? by lazy {
        cliOptions.normalizedRootDir ?: projectSettings?.rootDir
    }

    protected val environmentVariables: Map<String, String> by lazy {
        cliOptions.environmentVariables ?: projectSettings?.env ?: System.getenv()
    }

    protected val externalProperties: Map<String, String> by lazy {
        cliOptions.externalProperties ?: projectSettings?.externalProperties ?: emptyMap()
    }

    protected val moduleCacheDir: Path? by lazy {
        if (cliOptions.noCache) null
        else
            cliOptions.normalizedModuleCacheDir
                ?: projectSettings?.let { settings ->
                    if (settings.isNoCache == true) null else settings.moduleCacheDir
                }
                    ?: IoUtils.getDefaultModuleCacheDir()
    }

    protected val modulePath: List<Path> by lazy {
        cliOptions.normalizedModulePath ?: projectSettings?.modulePath ?: emptyList()
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

    protected fun moduleKeyFactories(
        modulePathResolver: ModulePathResolver
    ): List<ModuleKeyFactory> {
        return buildList {
            add(ModuleKeyFactories.standardLibrary)
            add(ModuleKeyFactories.modulePath(modulePathResolver))
            add(ModuleKeyFactories.pkg)
            add(ModuleKeyFactories.projectpackage)
            addAll(ModuleKeyFactories.fromServiceProviders())
            add(ModuleKeyFactories.file)
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
     * Creates an [EvaluatorBuilder] preconfigured according to [cliOptions]. To avoid resource
     * leaks, `ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)` must be called once the
     * returned builder and evaluators built by it are no longer in use.
     */
    protected fun evaluatorBuilder(): EvaluatorBuilder {
        // indirectly closed by `ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)`
        val modulePathResolver = ModulePathResolver(modulePath)
        return EvaluatorBuilder.unconfigured()
            .setStackFrameTransformer(stackFrameTransformer)
            .apply { project?.let { setProjectDependencies(it.dependencies) } }
            .setSecurityManager(securityManager)
            .setExternalProperties(externalProperties)
            .setEnvironmentVariables(environmentVariables)
            .addModuleKeyFactories(moduleKeyFactories(modulePathResolver))
            .addResourceReaders(resourceReaders(modulePathResolver))
            .setLogger(Loggers.stdErr())
            .setTimeout(cliOptions.timeout)
            .setModuleCacheDir(moduleCacheDir)
    }
}
