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
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import javax.inject.Inject
import kotlin.io.path.createDirectories
import org.gradle.api.DefaultTask
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class InstallGraalVm
@Inject
constructor(
  private val fileOperations: FileOperations,
  private val execOperations: ExecOperations
) : DefaultTask() {
  @get:Input abstract val graalVm: Property<BuildInfo.GraalVm>

  init {
    @Suppress("LeakingThis") onlyIf("GraalVM not installed") { !graalVm.get().installDir.exists() }
  }

  @TaskAction
  @Suppress("unused")
  fun run() {
    // minimize chance of corruption by extract-to-random-dir-and-flip-symlink
    val distroDir = Paths.get(graalVm.get().homeDir, UUID.randomUUID().toString())
    try {
      distroDir.createDirectories()
      println("Extracting ${graalVm.get().downloadFile} into $distroDir")
      // faster and more reliable than Gradle's `copy { from tarTree() }`
      execOperations.exec {
        workingDir = distroDir.toFile()
        executable = "tar"
        args("--strip-components=1", "-xzf", graalVm.get().downloadFile)
      }

      val os = org.gradle.internal.os.OperatingSystem.current()
      val distroBinDir =
        if (os.isMacOsX) distroDir.resolve("Contents/Home/bin") else distroDir.resolve("bin")

      println("Installing native-image into $distroDir")
      val gvmVersionMajor =
        requireNotNull(graalVm.get().version.split(".").first().toIntOrNull()) {
          "Invalid GraalVM JDK version: ${graalVm.get().graalVmJdkVersion}"
        }
      if (gvmVersionMajor < 24) {
        execOperations.exec {
          val executableName = if (os.isWindows) "gu.cmd" else "gu"
          executable = distroBinDir.resolve(executableName).toString()
          args("install", "--no-progress", "native-image")
        }
      }

      println("Creating symlink ${graalVm.get().installDir} for $distroDir")
      val tempLink = Paths.get(graalVm.get().homeDir, UUID.randomUUID().toString())
      Files.createSymbolicLink(tempLink, distroDir)
      try {
        Files.move(tempLink, graalVm.get().installDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
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
