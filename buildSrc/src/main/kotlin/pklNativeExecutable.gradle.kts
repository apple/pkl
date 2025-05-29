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
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

plugins {
  id("pklGraalVm")
  id("pklJavaLibrary")
  id("pklNativeLifecycle")
  id("pklPublishLibrary")
  id("com.github.johnrengelman.shadow")
}

// assumes that `pklJavaExecutable` is also applied
val executableSpec = project.extensions.getByType<ExecutableSpec>()
val buildInfo = project.extensions.getByType<BuildInfo>()

val stagedMacAmd64Executable: Configuration by configurations.creating
val stagedMacAarch64Executable: Configuration by configurations.creating
val stagedLinuxAmd64Executable: Configuration by configurations.creating
val stagedLinuxAarch64Executable: Configuration by configurations.creating
val stagedAlpineLinuxAmd64Executable: Configuration by configurations.creating
val stagedWindowsAmd64Executable: Configuration by configurations.creating

dependencies {
  fun executableFile(machine: Machine) =
    files(
      layout.buildDirectory.dir("executable").map { dir ->
        dir.file(
          executableSpec.name.map { name ->
            if (machine.os.isWindows) "$name-${machine.targetName}.exe"
            else "$name-${machine.targetName}"
          }
        )
      }
    )
  stagedMacAarch64Executable(executableFile(Machine.MacosAarch64))
  stagedMacAmd64Executable(executableFile(Machine.MacosAmd64))
  stagedLinuxAarch64Executable(executableFile(Machine.LinuxAarch64))
  stagedLinuxAmd64Executable(executableFile(Machine.LinuxAmd64))
  stagedAlpineLinuxAmd64Executable(executableFile(Machine.AlpineLinuxAmd64))
  stagedWindowsAmd64Executable(executableFile(Machine.WindowsAmd64))
}

private fun NativeImageBuild.configure(machine: Machine) {
  arch = machine.arch

  outputName = executableSpec.name.map { "$it-${machine.targetName}" }
  mainClass = executableSpec.mainClass

  if (machine.arch == Machine.Arch.AARCH64) {
    dependsOn(":installGraalVmAarch64")
  } else {
    dependsOn(":installGraalVmAmd64")
  }

  classpath.from(sourceSets.main.map { it.output })
  classpath.from(
    project(":pkl-commons-cli").extensions.getByType(SourceSetContainer::class)["svm"].output
  )
  classpath.from(configurations.runtimeClasspath)
}

val macExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) { configure(Machine.MacosAmd64) }

val macExecutableAarch64 by
  tasks.registering(NativeImageBuild::class) { configure(Machine.MacosAarch64) }

val linuxExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) { configure(Machine.LinuxAmd64) }

val linuxExecutableAarch64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Machine.LinuxAarch64)
    // Ensure compatibility for kernels with page size set to 4k, 16k and 64k
    // (e.g. Raspberry Pi 5, Asahi Linux)
    extraNativeImageArgs.add("-H:PageSize=65536")
  }

val alpineExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Machine.AlpineLinuxAmd64)
    extraNativeImageArgs.addAll(listOf("--static", "--libc=musl"))
  }

val windowsExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Machine.WindowsAmd64)
    extraNativeImageArgs.add("-Dfile.encoding=UTF-8")
  }

val assembleNative by tasks.existing

val testStartNativeExecutable by
  tasks.registering {
    dependsOn(assembleNative)

    // dummy file for up-to-date checking
    val outputFile = project.layout.buildDirectory.file("testStartNativeExecutable/output.txt")
    outputs.file(outputFile)

    val execOutput =
      providers.exec { commandLine(assembleNative.get().outputs.files.singleFile, "--version") }

    @Suppress("DuplicatedCode")
    doLast {
      val outputText = execOutput.standardOutput.asText.get()
      if (!outputText.contains(buildInfo.pklVersionNonUnique)) {
        throw GradleException(
          "Expected version output to contain current version (${buildInfo.pklVersionNonUnique}), but got '$outputText'"
        )
      }
      outputFile.get().asFile.toPath().apply {
        try {
          parent.createDirectories()
        } catch (_: java.nio.file.FileAlreadyExistsException) {}
        writeText("OK")
      }
    }
  }

// Expose underlying task's outputs
private fun <T : Task> Task.wraps(other: TaskProvider<T>) {
  dependsOn(other)
  outputs.files(other)
}

@Suppress("unused") val testNative by tasks.existing { dependsOn(testStartNativeExecutable) }

@Suppress("unused") val assembleNativeMacOsAarch64 by tasks.existing { wraps(macExecutableAarch64) }

@Suppress("unused") val assembleNativeMacOsAmd64 by tasks.existing { wraps(macExecutableAmd64) }

@Suppress("unused")
val assembleNativeLinuxAarch64 by tasks.existing { wraps(linuxExecutableAarch64) }

@Suppress("unused") val assembleNativeLinuxAmd64 by tasks.existing { wraps(linuxExecutableAmd64) }

@Suppress("unused")
val assembleNativeAlpineLinuxAmd64 by tasks.existing { wraps(alpineExecutableAmd64) }

@Suppress("unused")
val assembleNativeWindowsAmd64 by tasks.existing { wraps(windowsExecutableAmd64) }

private fun MavenPublication.configurePublication(target: Machine, configuration: Configuration) {
  artifactId = "${executableSpec.publicationName.get()}-${target.targetName}"
  pom {
    name = "${executableSpec.publicationName.get()}-${target.targetName}"
    url = executableSpec.website
    artifact(configuration.singleFile) {
      classifier = null
      extension = if (target.os.isWindows) "exe" else "bin"
      builtBy(configuration)
    }
    description =
      executableSpec.documentationName.map { name ->
        buildString {
          append("Native $name executable for ${target.os.displayName}/${target.arch}")
          if (target.musl) {
            append(" and statically linked to musl")
          }
          append(".")
        }
      }
  }
}

publishing {
  publications {
    // need to put in `afterEvaluate` because `artifactId` cannot be set lazily.
    project.afterEvaluate {
      create<MavenPublication>("macExecutableAmd64") {
        configurePublication(Machine.MacosAmd64, stagedMacAmd64Executable)
      }

      create<MavenPublication>("macExecutableAarch64") {
        configurePublication(Machine.MacosAarch64, stagedMacAarch64Executable)
      }

      create<MavenPublication>("linuxExecutableAmd64") {
        configurePublication(Machine.LinuxAmd64, stagedLinuxAmd64Executable)
      }

      create<MavenPublication>("linuxExecutableAarch64") {
        configurePublication(Machine.LinuxAarch64, stagedLinuxAarch64Executable)
      }

      create<MavenPublication>("alpineLinuxExecutableAmd64") {
        configurePublication(Machine.AlpineLinuxAmd64, stagedAlpineLinuxAmd64Executable)
      }

      create<MavenPublication>("windowsExecutableAmd64") {
        configurePublication(Machine.WindowsAmd64, stagedWindowsAmd64Executable)
      }
    }
  }
}

signing {
  project.afterEvaluate {
    sign(publishing.publications["linuxExecutableAarch64"])
    sign(publishing.publications["linuxExecutableAmd64"])
    sign(publishing.publications["macExecutableAarch64"])
    sign(publishing.publications["macExecutableAmd64"])
    sign(publishing.publications["alpineLinuxExecutableAmd64"])
    sign(publishing.publications["windowsExecutableAmd64"])
  }
}
