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
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.CliTestException
import org.pkl.commons.cli.CliTestOptions
import org.pkl.core.project.Project
import org.pkl.core.project.ProjectPackager
import org.pkl.core.util.ErrorMessages

class CliProjectPackager(
    baseOptions: CliBaseOptions,
    projectDirs: List<Path>,
    private val testOptions: CliTestOptions,
    private val outputPath: String,
    private val skipPublishCheck: Boolean,
    private val consoleWriter: Writer = System.out.writer(),
    private val errWriter: Writer = System.err.writer()
) : CliAbstractProjectCommand(baseOptions, projectDirs) {

    private fun runApiTests(project: Project) {
        val apiTests = project.`package`!!.apiTests
        if (apiTests.isEmpty()) return
        val normalizeApiTests = apiTests.map { project.projectDir.resolve(it).toUri() }
        val testRunner =
            CliTestRunner(
                cliOptions.copy(sourceModules = normalizeApiTests, projectDir = project.projectDir),
                testOptions = testOptions,
                consoleWriter = consoleWriter,
                errWriter = errWriter,
            )
        try {
            testRunner.run()
        } catch (e: CliTestException) {
            throw CliException(ErrorMessages.create("packageTestsFailed", project.`package`!!.uri))
        }
    }

    override fun doRun() {
        val projects = buildList {
            for (projectFile in normalizedProjectFiles) {
                val project = loadProject(projectFile)
                project.`package`
                    ?: throw CliException(
                        ErrorMessages.create("noPackageDefinedByProject", project.projectFileUri)
                    )
                runApiTests(project)
                add(project)
            }
        }
        // Require that all local projects are included
        projects.forEach { proj ->
            proj.dependencies.localDependencies.values.forEach { localDep ->
                val projectDir = Path.of(localDep.projectFileUri).parent
                if (projects.none { it.projectDir == projectDir }) {
                    throw CliException(
                        ErrorMessages.create(
                            "missingProjectInPackageCommand",
                            proj.projectDir,
                            projectDir
                        )
                    )
                }
            }
        }
        ProjectPackager(
                projects,
                cliOptions.normalizedWorkingDir,
                outputPath,
                stackFrameTransformer,
                securityManager,
                skipPublishCheck,
                consoleWriter
            )
            .createPackages()
    }
}
