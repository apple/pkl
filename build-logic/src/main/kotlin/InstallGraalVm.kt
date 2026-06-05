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
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import javax.inject.Inject
import kotlin.io.path.createDirectories
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class InstallGraalVm
@Inject
constructor(
  private val fileOperations: FileOperations,
  private val execOperations: ExecOperations,
) : DefaultTask() {
  @get:Input abstract val homeDir: Property<String>

  @get:InputFile abstract val downloadFile: RegularFileProperty

  @get:Input abstract val version: Property<String>

  @get:Input abstract val graalVmJdkVersion: Property<String>

  @get:Internal abstract val installDir: DirectoryProperty

  init {
    @Suppress("LeakingThis") onlyIf("GraalVM not installed") { !installDir.get().asFile.exists() }
  }

  @TaskAction
  @Suppress("unused")
  fun run() {
    val distroDir = Paths.get(homeDir.get(), UUID.randomUUID().toString())
    try {
      distroDir.createDirectories()
      println("Extracting ${downloadFile.get().asFile} into $distroDir")
      // faster and more reliable than Gradle's `copy { from tarTree() }`
      execOperations.exec {
        workingDir = distroDir.toFile()
        executable = "tar"
        args("--strip-components=1", "-xzf", downloadFile.get().asFile)
      }

      val os = org.gradle.internal.os.OperatingSystem.current()
      val distroBinDir =
        if (os.isMacOsX) distroDir.resolve("Contents/Home/bin") else distroDir.resolve("bin")

      println("Installing native-image into $distroDir")
      val gvmVersionMajor =
        requireNotNull(version.get().split(".").first().toIntOrNull()) {
          "Invalid GraalVM JDK version: ${graalVmJdkVersion.get()}"
        }
      if (gvmVersionMajor < 24) {
        execOperations.exec {
          val executableName = if (os.isWindows) "gu.cmd" else "gu"
          executable = distroBinDir.resolve(executableName).toString()
          args("install", "--no-progress", "native-image")
        }
      }

      println("Creating symlink ${installDir.get().asFile} for $distroDir")
      val tempLink = Paths.get(homeDir.get(), UUID.randomUUID().toString())
      Files.createSymbolicLink(tempLink, distroDir)
      try {
        Files.move(tempLink, installDir.get().asFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
      } catch (e: Exception) {
        try {
          fileOperations.delete(tempLink.toFile())
        } catch (ignored: Exception) {}
        throw e
      }
    } catch (e: Exception) {
      try {
        fileOperations.delete(distroDir)
      } catch (ignored: Exception) {}
      throw e
    }
  }
}
