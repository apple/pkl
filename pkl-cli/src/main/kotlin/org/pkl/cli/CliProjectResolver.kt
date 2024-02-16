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
package org.pkl.cli

import java.io.Writer
import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.core.SecurityManagers
import org.pkl.core.module.ProjectDependenciesManager
import org.pkl.core.packages.PackageResolver
import org.pkl.core.project.ProjectDependenciesResolver

class CliProjectResolver(
  baseOptions: CliBaseOptions,
  projectDirs: List<Path>,
  private val consoleWriter: Writer = System.out.writer(),
  private val errWriter: Writer = System.err.writer()
) : CliProjectCommand(baseOptions, projectDirs) {
  override fun doRun() {
    for (projectFile in normalizedProjectFiles) {
      val project = loadProject(projectFile)
      val packageResolver =
        PackageResolver.getInstance(
          SecurityManagers.standard(
            allowedModules,
            allowedResources,
            SecurityManagers.defaultTrustLevels,
            rootDir
          ),
          moduleCacheDir
        )
      val dependencies = ProjectDependenciesResolver(project, packageResolver, errWriter).resolve()
      val depsFile =
        projectFile.parent.resolve(ProjectDependenciesManager.PKL_PROJECT_DEPS_FILENAME).toFile()
      depsFile.outputStream().use { dependencies.writeTo(it) }
      consoleWriter.appendLine(depsFile.toString())
      consoleWriter.flush()
    }
  }
}
