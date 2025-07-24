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
  // TODO: re-enable maven publishing
  //  id("pklPublishLibrary")
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
  fun executableFile(suffix: String) =
    files(
      layout.buildDirectory.dir("executable").map { dir ->
        dir.file(executableSpec.name.map { "$it-$suffix" })
      }
    )
  stagedMacAarch64Executable(executableFile("macos-aarch64"))
  stagedMacAmd64Executable(executableFile("macos-amd64"))
  stagedLinuxAmd64Executable(executableFile("linux-amd64"))
  stagedLinuxAarch64Executable(executableFile("linux-aarch64"))
  stagedAlpineLinuxAmd64Executable(executableFile("alpine-linux-amd64"))
  stagedWindowsAmd64Executable(executableFile("windows-amd64.exe"))
}

private fun NativeImageBuild.amd64() {
  arch = Architecture.AMD64
  dependsOn(":installGraalVmAmd64")
}

private fun NativeImageBuild.aarch64() {
  arch = Architecture.AARCH64
  dependsOn(":installGraalVmAarch64")
}

private fun NativeImageBuild.setClasspath() {
  classpath.from(sourceSets.main.map { it.output })
  classpath.from(
    project(":pkl-commons-cli").extensions.getByType(SourceSetContainer::class)["svm"].output
  )
  classpath.from(configurations.runtimeClasspath)
}

val macExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) {
    imageName = executableSpec.name.map { "$it-macos-amd64" }
    mainClass = executableSpec.mainClass
    amd64()
    setClasspath()
  }

val macExecutableAarch64 by
  tasks.registering(NativeImageBuild::class) {
    imageName = executableSpec.name.map { "$it-macos-aarch64" }
    mainClass = executableSpec.mainClass
    aarch64()
    setClasspath()
  }

val linuxExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) {
    imageName = executableSpec.name.map { "$it-linux-amd64" }
    mainClass = executableSpec.mainClass
    amd64()
    setClasspath()
  }

val linuxExecutableAarch64 by
  tasks.registering(NativeImageBuild::class) {
    imageName = executableSpec.name.map { "$it-linux-aarch64" }
    mainClass = executableSpec.mainClass
    aarch64()
    setClasspath()
    // Ensure compatibility for kernels with page size set to 4k, 16k and 64k
    // (e.g. Raspberry Pi 5, Asahi Linux)
    extraNativeImageArgs.add("-H:PageSize=65536")
  }

val alpineExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) {
    imageName = executableSpec.name.map { "$it-alpine-linux-amd64" }
    mainClass = executableSpec.mainClass
    amd64()
    setClasspath()
    extraNativeImageArgs.addAll(listOf("--static", "--libc=musl"))
  }

val windowsExecutableAmd64 by
  tasks.registering(NativeImageBuild::class) {
    imageName = executableSpec.name.map { "$it-windows-amd64" }
    mainClass = executableSpec.mainClass
    amd64()
    setClasspath()
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
        } catch (ignored: java.nio.file.FileAlreadyExistsException) {}
        writeText("OK")
      }
    }
  }

// Expose underlying task's outputs
private fun <T : Task> Task.wraps(other: TaskProvider<T>) {
  dependsOn(other)
  outputs.files(other)
}

val testNative by tasks.existing { dependsOn(testStartNativeExecutable) }

val assembleNativeMacOsAarch64 by tasks.existing { wraps(macExecutableAarch64) }

val assembleNativeMacOsAmd64 by tasks.existing { wraps(macExecutableAmd64) }

val assembleNativeLinuxAarch64 by tasks.existing { wraps(linuxExecutableAarch64) }

val assembleNativeLinuxAmd64 by tasks.existing { wraps(linuxExecutableAmd64) }

val assembleNativeAlpineLinuxAmd64 by tasks.existing { wraps(alpineExecutableAmd64) }

val assembleNativeWindowsAmd64 by tasks.existing { wraps(windowsExecutableAmd64) }

// publishing {
//  publications {
//    // need to put in `afterEvaluate` because `artifactId` cannot be set lazily.
//    project.afterEvaluate {
//      create<MavenPublication>("macExecutableAmd64") {
//        artifactId = "${executableSpec.publicationName.get()}-macos-amd64"
//        artifact(stagedMacAmd64Executable.singleFile) {
//          classifier = null
//          extension = "bin"
//          builtBy(stagedMacAmd64Executable)
//        }
//        pom {
//          name = "${executableSpec.publicationName.get()}-macos-amd64"
//          url = executableSpec.website
//          description =
//            executableSpec.documentationName.map { name ->
//              "Native $name executable for macOS/amd64."
//            }
//        }
//      }
//
//      create<MavenPublication>("macExecutableAarch64") {
//        artifactId = "${executableSpec.publicationName.get()}-macos-aarch64"
//        artifact(stagedMacAarch64Executable.singleFile) {
//          classifier = null
//          extension = "bin"
//          builtBy(stagedMacAarch64Executable)
//        }
//        pom {
//          name = "${executableSpec.publicationName.get()}-macos-aarch64"
//          url = executableSpec.website
//          description =
//            executableSpec.documentationName.map { name ->
//              "Native $name executable for macOS/aarch64."
//            }
//        }
//      }
//
//      create<MavenPublication>("linuxExecutableAmd64") {
//        artifactId = "${executableSpec.publicationName.get()}-linux-amd64"
//        artifact(stagedLinuxAmd64Executable.singleFile) {
//          classifier = null
//          extension = "bin"
//          builtBy(stagedLinuxAmd64Executable)
//        }
//        pom {
//          name = "${executableSpec.publicationName.get()}-linux-amd64"
//          url = executableSpec.website
//          description =
//            executableSpec.documentationName.map { name ->
//              "Native $name executable for linux/amd64."
//            }
//        }
//      }
//
//      create<MavenPublication>("linuxExecutableAarch64") {
//        artifactId = "${executableSpec.publicationName.get()}-linux-aarch64"
//        artifact(stagedLinuxAarch64Executable.singleFile) {
//          classifier = null
//          extension = "bin"
//          builtBy(stagedLinuxAarch64Executable)
//        }
//        pom {
//          name = "${executableSpec.publicationName.get()}-linux-aarch64"
//          url = executableSpec.website
//          description =
//            executableSpec.documentationName.map { name ->
//              "Native $name executable for linux/aarch64."
//            }
//        }
//      }
//
//      create<MavenPublication>("alpineLinuxExecutableAmd64") {
//        artifactId = "${executableSpec.publicationName.get()}-alpine-linux-amd64"
//        artifact(stagedAlpineLinuxAmd64Executable.singleFile) {
//          classifier = null
//          extension = "bin"
//          builtBy(stagedAlpineLinuxAmd64Executable)
//        }
//        pom {
//          name = "${executableSpec.publicationName.get()}-alpine-linux-amd64"
//          url = executableSpec.website
//          description =
//            executableSpec.documentationName.map { name ->
//              "Native $name executable for linux/amd64 and statically linked to musl."
//            }
//        }
//      }
//
//      create<MavenPublication>("windowsExecutableAmd64") {
//        artifactId = "${executableSpec.publicationName.get()}-windows-amd64"
//        artifact(stagedWindowsAmd64Executable.singleFile) {
//          classifier = null
//          extension = "exe"
//          builtBy(stagedWindowsAmd64Executable)
//        }
//        pom {
//          name = "${executableSpec.publicationName.get()}-windows-amd64"
//          url = executableSpec.website
//          description =
//            executableSpec.documentationName.map { name ->
//              "Native $name executable for windows/amd64."
//            }
//        }
//      }
//    }
//  }
// }
//
// signing {
//  project.afterEvaluate {
//    sign(publishing.publications["linuxExecutableAarch64"])
//    sign(publishing.publications["linuxExecutableAmd64"])
//    sign(publishing.publications["macExecutableAarch64"])
//    sign(publishing.publications["macExecutableAmd64"])
//    sign(publishing.publications["alpineLinuxExecutableAmd64"])
//    sign(publishing.publications["windowsExecutableAmd64"])
//  }
// }
