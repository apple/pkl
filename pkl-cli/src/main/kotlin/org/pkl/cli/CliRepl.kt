/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.pkl.cli.repl.Repl
import org.pkl.commons.cli.CliCommand
import org.pkl.core.Loggers
import org.pkl.core.SecurityManagers
import org.pkl.core.evaluatorSettings.TraceMode
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModulePathResolver
import org.pkl.core.repl.ReplServer
import org.pkl.core.resource.ResourceReaders

internal class CliRepl(private val options: CliEvaluatorOptions) : CliCommand(options.base) {
  override fun doRun() {
    ModulePathResolver(modulePath).use { modulePathResolver ->
      // TODO: send options as command
      val server =
        ReplServer(
          SecurityManagers.standard(
            allowedModules,
            allowedResources,
            SecurityManagers.defaultTrustLevels,
            rootDir,
          ),
          httpClient,
          Loggers.stdErr(),
          listOf(
            ModuleKeyFactories.standardLibrary,
            ModuleKeyFactories.modulePath(modulePathResolver),
          ) +
            ModuleKeyFactories.fromServiceProviders() +
            listOf(
              ModuleKeyFactories.file,
              ModuleKeyFactories.http,
              ModuleKeyFactories.pkg,
              ModuleKeyFactories.projectpackage,
              ModuleKeyFactories.genericUrl,
            ),
          listOf(
            ResourceReaders.environmentVariable(),
            ResourceReaders.externalProperty(),
            ResourceReaders.modulePath(modulePathResolver),
            ResourceReaders.file(),
            ResourceReaders.http(),
            ResourceReaders.https(),
            ResourceReaders.pkg(),
            ResourceReaders.projectpackage(),
          ),
          environmentVariables,
          externalProperties,
          moduleCacheDir,
          project?.dependencies,
          options.outputFormat,
          options.base.normalizedWorkingDir,
          stackFrameTransformer,
          options.base.color?.hasColor() ?: false,
          options.base.traceMode ?: TraceMode.COMPACT,
        )
      Repl(options.base.normalizedWorkingDir, server, options.base.color?.hasColor() ?: false).run()
    }
  }
}
