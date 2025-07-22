/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
@file:Suppress("unused")

plugins {
  pklAllProjects
  pklGraalVm
  pklJavaLibrary
  pklNativeLifecycle
}

val stagedMacAmd64NativeLibrary: Configuration by configurations.creating
val stagedMacAarch64NativeLibrary: Configuration by configurations.creating
val stagedLinuxAmd64NativeLibrary: Configuration by configurations.creating
val stagedLinuxAarch64NativeLibrary: Configuration by configurations.creating
val stagedAlpineLinuxAmd64NativeLibrary: Configuration by configurations.creating
val stagedWindowsAmd64NativeLibrary: Configuration by configurations.creating

val nativeTestSourceSet = sourceSets.create("nativeTest")

val nativeTestImplementation: Configuration by
  configurations.getting { extendsFrom(configurations.testImplementation.get()) }

val nativeTestRuntimeOnly: Configuration by
  configurations.getting { extendsFrom(configurations.testRuntimeOnly.get()) }

dependencies {
  compileOnly(libs.graalSdk)

  implementation(projects.pklCore)
  implementation(projects.pklServer)

  api(projects.pklCommonsCli)

  implementation(libs.msgpack)
  implementation(libs.truffleApi)
  implementation(libs.truffleRuntime)

  nativeTestImplementation(projects.pklCommonsTest)
  nativeTestImplementation(libs.jna)
  nativeTestImplementation(libs.jnaPlatform)
  nativeTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

private fun NativeImageBuild.configure(target: Target) {
  arch = target.arch

  if (target.arch == Target.Arch.AARCH64) {
    dependsOn(":installGraalVmAarch64")
  } else {
    dependsOn(":installGraalVmAmd64")
  }

  outputDir = target.outputDir
  outputName = "libpkl_internal"

  classpath.from(sourceSets.main.map { it.output })
  classpath.from(
    project(":pkl-commons-cli").extensions.getByType(SourceSetContainer::class)["svm"].output
  )
  classpath.from(configurations.runtimeClasspath)

  sharedLibrary = true
}

val macNativeImageAmd64 by
  tasks.registering(NativeImageBuild::class) { configure(Target.MacosAmd64) }

val macNativeImageAarch64 by
  tasks.registering(NativeImageBuild::class) { configure(Target.MacosAarch64) }

val linuxNativeImageAmd64 by
  tasks.registering(NativeImageBuild::class) { configure(Target.LinuxAmd64) }

val linuxNativeImageAarch64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Target.LinuxAarch64)
    extraNativeImageArgs =
      listOf(
        // Ensure compatibility for kernels with page size set to 4k, 16k and 64k
        // (e.g. Raspberry Pi 5, Asahi Linux)
        "-H:PageSize=65536"
      )
  }

val alpineNativeImageAmd64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Target.AlpineLinuxAmd64)
    extraNativeImageArgs =
      listOf(
        // TODO(kushal): https://github.com/oracle/graal/issues/3053
        "--libc=musl"
      )
  }

val windowsNativeImageAmd64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Target.WindowsAmd64)
    extraNativeImageArgs = listOf("-Dfile.encoding=UTF-8")
  }

val Target.outputDir
  get() = layout.buildDirectory.dir("native-libs/$targetName")

fun Exec.configureCompile(target: Target) {
  val projectDir = project.layout.projectDirectory.asFile.path

  workingDir = target.outputDir.get().asFile

  enabled = buildInfo.targetMachine == target

  val outputFile = "libpkl.${buildInfo.os.sharedLibrarySuffix}"

  executable =
    when {
      target.musl -> "musl-gcc"
      else -> "gcc"
    }

  argumentProviders.add(
    CommandLineArgumentProvider {
      buildList {
        add("-shared")
        add("-o")
        add(outputFile)
        if (buildInfo.isReleaseBuild) {
          add("-O2")
        }
        add("-g")
        add("-Wall")
        add("$projectDir/src/main/c/pkl.c")
        add("-I$projectDir/src/main/c")
        add("-I${target.outputDir.get()}")
        add("-L${target.outputDir.get()}")
        add("-fPIC")
        add("-lpkl_internal")
        if (target.os.isMacOS) {
          val archStr =
            when (target.arch) {
              Target.Arch.AMD64 -> "x86_64"
              Target.Arch.AARCH64 -> "arm64"
            }
          add("-target")
          add("$archStr-apple-darwin")
        }
      }
    }
  )

  outputs.files(target.outputDir.map { it.files(outputFile, "pkl.h") })

  doLast {
    copy {
      from(file("src/main/c/pkl.h"))
      into(target.outputDir)
    }
  }
}

val macCCompileAarch64 by
  tasks.registering(Exec::class) {
    dependsOn(macNativeImageAarch64)
    configureCompile(Target.MacosAarch64)
  }

val macCCompileAmd64 by
  tasks.registering(Exec::class) {
    dependsOn(macNativeImageAmd64)
    configureCompile(Target.MacosAmd64)
  }

val linuxCCompileAmd64 by
  tasks.registering(Exec::class) {
    dependsOn(linuxNativeImageAmd64)
    configureCompile(Target.LinuxAmd64)
  }

val linuxCCompileAarch64 by
  tasks.registering(Exec::class) {
    dependsOn(linuxNativeImageAarch64)
    configureCompile(Target.LinuxAarch64)
  }

val alpineLinuxCCompileAmd64 by
  tasks.registering(Exec::class) {
    dependsOn(alpineNativeImageAmd64)
    configureCompile(Target.AlpineLinuxAmd64)
  }

val windowsCCompileAmd64 by
  tasks.registering(Exec::class) {
    dependsOn(windowsNativeImageAmd64)
    configureCompile(Target.WindowsAmd64)
  }

tasks.assembleNativeMacOsAarch64 { dependsOn(macCCompileAarch64) }

tasks.assembleNativeMacOsAmd64 { dependsOn(macCCompileAmd64) }

tasks.assembleNativeLinuxAmd64 { dependsOn(linuxCCompileAmd64) }

tasks.assembleNativeLinuxAarch64 { dependsOn(linuxCCompileAarch64) }

tasks.assembleNativeAlpineLinuxAmd64 { dependsOn(alpineLinuxCCompileAmd64) }

tasks.assembleNativeWindowsAmd64 { dependsOn(windowsCCompileAmd64) }

val nativeTest by
  tasks.registering(Test::class) {
    dependsOn(tasks.assembleNative)

    description = "Test native libraries"
    group = "verification"

    testClassesDirs = nativeTestSourceSet.output.classesDirs
    classpath = nativeTestSourceSet.runtimeClasspath

    // It's not good enough to just provide `jna.library.path` on Linux; need to also provide
    // `LD_LIBRARY_PATH` or `java.library.path` so that transitive libraries can be loaded.
    jvmArgumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
          "-Djna.library.path=" + buildInfo.targetMachine.outputDir.get().asFile.absolutePath,
          "-Djava.library.path=" + buildInfo.targetMachine.outputDir.get().asFile.absolutePath,
        )
      }
    )

    environment("LD_LIBRARY_PATH", buildInfo.targetMachine.outputDir.get().asFile.absolutePath)

    useJUnitPlatform()
  }

tasks.testNative { dependsOn(nativeTest) }

private val licenseHeaderFile by lazy {
  rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt")
}

spotless {
  cpp {
    licenseHeaderFile(licenseHeaderFile, "// ")
    target("src/main/c/*.c", "src/main/c/*.h")
  }
}
