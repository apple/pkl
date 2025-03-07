/*
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

import java.nio.file.Files
import java.nio.file.Path
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliBaseOptions.Companion.getProjectFile
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.core.module.ProjectDependenciesManager.PKL_PROJECT_FILENAME

abstract class CliProjectCommand(cliOptions: CliBaseOptions, private val projectDirs: List<Path>) :
  CliCommand(cliOptions) {

  protected val normalizedProjectFiles: List<Path> by lazy {
    if (projectDirs.isEmpty()) {
      val projectFile =
        cliOptions.normalizedWorkingDir.getProjectFile(cliOptions.normalizedRootDir)
          ?: throw CliException(
            "No project visible to the working directory. Ensure there is a PklProject file in the workspace, or provide an explicit project directory as an argument."
          )
      return@lazy listOf(projectFile.normalize())
    }
    projectDirs.map(cliOptions.normalizedWorkingDir::resolve).map { dir ->
      val projectFile = dir.resolve(PKL_PROJECT_FILENAME)
      if (!Files.exists(projectFile)) {
        throw CliException("Directory $dir does not contain a PklProject file.")
      }
      projectFile.normalize()
    }
  }
}
