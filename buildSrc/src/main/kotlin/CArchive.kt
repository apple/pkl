/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import Target.OS
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Task for creating static libraries from object files.
 *
 * Supports both Unix `ar` and Windows `lib.exe`.
 */
abstract class CArchive : DefaultTask() {
  /** The object files to archive. */
  @get:InputFiles abstract val objectFiles: ConfigurableFileCollection

  /** The output static library file. */
  @get:OutputFile abstract val outputFile: RegularFileProperty

  @get:Inject protected abstract val execOperations: ExecOperations

  private val buildInfo: BuildInfo = project.extensions.getByType(BuildInfo::class.java)

  init {
    group = "build"
  }

  @TaskAction
  fun archive() {
    outputFile.get().asFile.delete()
    when (buildInfo.os) {
      OS.Linux,
      OS.MacOS -> archiveWithAr()
      OS.Windows -> archiveWithLib()
    }
    logger.info("Created static library -> ${outputFile.get().asFile.name}")
  }

  private fun archiveWithAr() {
    val output = outputFile.get().asFile
    output.parentFile.mkdirs()

    val args = buildList {
      add("ar")
      add("-rcs")
      add(output.absolutePath)
      objectFiles.files.forEach { file -> add(file.absolutePath) }
    }

    execOperations.exec {
      workingDir = project.projectDir
      commandLine(args)
    }
  }

  private fun archiveWithLib() {
    val output = outputFile.get().asFile
    output.parentFile.mkdirs()

    val args = buildList {
      add("lib.exe")
      add("/OUT:${output.absolutePath}")
      objectFiles.files.forEach { file -> add(file.absolutePath) }
    }

    execOperations.exec {
      workingDir = project.projectDir
      commandLine(args)
    }
  }
}
