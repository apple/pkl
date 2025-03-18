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

val nativeTestSourceSet: SourceSet = sourceSets.create("nativeTest")

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

tasks.withType(CCompile::class) {
  if (buildInfo.targetMachine.os.isMacOS) {
    arch = buildInfo.targetMachine.arch
  }
}

private fun NativeImageBuild.configure(target: Target) {
  arch = target.arch

  if (target.arch == Target.Arch.AARCH64) {
    dependsOn(":installGraalVmAarch64")
  } else {
    dependsOn(":installGraalVmAmd64")
  }

  outputDir = target.tempOutputDir
  outputName = "libpkl_internal"

  classpath.from(sourceSets.main.map { it.output })
  classpath.from(
    project(":pkl-commons-cli").extensions.getByType(SourceSetContainer::class)["svm"].output
  )
  classpath.from(configurations.runtimeClasspath)

  sharedLibrary = true
  val scriptName = if (buildInfo.os.isWindows) "build_windows.bat" else "build_unix.sh"
  nativeCompilerPath = projectDir.resolve("scripts/${scriptName}")
}

val buildPklObjectFile by
  tasks.registering(CCompile::class) {
    includeDirs.from(file("scripts/include/"))
    includeDirs.from(file("src/main/c/"))
    sourceFiles.from(file("src/main/c/pkl.c"))
    positionIndependentCode = true

    outputDir =
      layout.buildDirectory.dir("tmp/compileC/${name}/${buildInfo.targetMachine.targetName}")

    val versionMacro =
      if (buildInfo.targetMachine.os.isWindows) "\\\"${buildInfo.pklVersion}\\\""
      else "\"${buildInfo.pklVersion}\""
    defines.put("PKL_VERSION", versionMacro)
  }

val buildStaticLibrary by
  tasks.registering(CArchive::class) {
    dependsOn(buildNativeImageLibrary, buildPklObjectFile)

    val targetMachine = buildInfo.targetMachine
    val objectExtension = if (buildInfo.os.isWindows) "obj" else "o"
    val pklObjectFile =
      buildPklObjectFile.flatMap { it.outputDir.map { dir -> dir.file("pkl.${objectExtension}") } }

    objectFiles.from(
      fileTree(targetMachine.tempOutputDir.get().dir("objects")).matching {
        include("**/*.${objectExtension}")
      }
    )
    objectFiles.from(pklObjectFile)

    outputFile =
      targetMachine.outputDir.map { dir ->
        dir.file("libpkl.${targetMachine.os.staticLibraryExtension}")
      }
  }

val macNativeImageAmd64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Target.MacosAmd64)
    // macOS only supports 16K page size
    extraNativeImageArgs = listOf("-H:PageSize=16384")
  }

val macNativeImageAarch64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Target.MacosAarch64)
    // macOS only supports 16K page size
    extraNativeImageArgs = listOf("-H:PageSize=16384")
  }

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

val windowsNativeImageAmd64 by
  tasks.registering(NativeImageBuild::class) {
    configure(Target.WindowsAmd64)
    extraNativeImageArgs = listOf("-Dfile.encoding=UTF-8", "-H:-CheckToolchain")
  }

val buildNativeImageLibrary by
  tasks.registering {
    group = "build"
    val underlyingTask =
      when (buildInfo.targetMachine) {
        Target.MacosAarch64 -> macNativeImageAarch64
        Target.MacosAmd64 -> macNativeImageAmd64
        Target.LinuxAarch64 -> linuxNativeImageAarch64
        Target.LinuxAmd64 -> linuxNativeImageAmd64
        Target.WindowsAmd64 -> windowsNativeImageAmd64
        Target.AlpineLinuxAmd64 -> throw GradleException("Cannot build libpkl with musl")
      }
    dependsOn(underlyingTask)
    outputs.files(underlyingTask)
  }

val buildSharedLibrary by
  tasks.registering(CCompile::class) {
    dependsOn(buildNativeImageLibrary, buildPklObjectFile)

    link = true
    val targetMachine = buildInfo.targetMachine

    val objectFile =
      buildPklObjectFile.flatMap { task ->
        task.outputDir.map { it.file("pkl.${targetMachine.os.objectFileExtension}") }
      }

    val extension = targetMachine.os.sharedLibraryExtension
    val staticLib =
      buildNativeImageLibrary.map { task ->
        task.outputs.files.find { it.name.contains("libpkl_internal.${extension}") }
      }

    sourceFiles.from(objectFile)
    includeDirs.from(file("src/main/c/"))
    libraryFiles.from(staticLib)
    positionIndependentCode = true
    sharedLibrary = true

    outputFile = targetMachine.outputDir.map { it.file("libpkl.${extension}") }

    if (buildInfo.os.isMacOS) {
      frameworks.addAll("Foundation", "CoreServices")
    }
  }

val Target.outputDir
  get() = layout.buildDirectory.dir("native-libs/$targetName")

val Target.tempOutputDir
  get() = layout.buildDirectory.dir("tmp/native-libs/$targetName")

tasks.assembleNative {
  dependsOn(buildSharedLibrary, buildStaticLibrary)
  doLast {
    copy {
      from("src/main/c/pkl.h")
      into(buildInfo.targetMachine.outputDir)
    }
  }
}

val testNativeJava by
  tasks.registering(Test::class) {
    dependsOn(tasks.assembleNative)

    description = "Test native libraries from Java"
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

// link to static library
val compileNativeTestStatic by
  tasks.registering(CCompile::class) {
    description = "Build C test with static library"
    group = "verification"

    dependsOn(tasks.assembleNative)

    val libDir = buildInfo.targetMachine.outputDir.get().asFile
    val testSrc = file("src/nativeTest/c/test_pkl.c")

    link = true
    sourceFiles.from(testSrc)
    includeDirs.from(libDir)
    outputFile =
      layout.buildDirectory.file(
        "native-test/${buildInfo.targetMachine.targetName}/test_pkl_static"
      )

    libraryFiles.from("${libDir.absolutePath}/libpkl.${buildInfo.os.staticLibraryExtension}")

    if (!buildInfo.os.isWindows) {
      libraries.add("pthread")
      libraries.add("z")
    }

    if (buildInfo.os.isMacOS) {
      frameworks.addAll("Foundation", "CoreServices")
    }
  }

// link to dynamic library
val compileNativeTestDynamic by
  tasks.registering(CCompile::class) {
    description = "Build C test with dynamic library"
    group = "verification"

    dependsOn(tasks.assembleNative)

    val testSrc = file("src/nativeTest/c/test_pkl.c")

    link = true
    sourceFiles.from(testSrc)
    includeDirs.from(buildInfo.targetMachine.outputDir)
    outputFile =
      layout.buildDirectory.file(
        "native-test/${buildInfo.targetMachine.targetName}/test_pkl_dynamic"
      )

    libraryPaths.from(buildInfo.targetMachine.outputDir)

    libraries.addAll("pkl", "z")

    if (!buildInfo.os.isWindows) {
      libraries.add("pthread")

      // Add rpath for runtime linking
      linkerFlags.add("-rpath")
      linkerFlags.add(buildInfo.targetMachine.outputDir.map { it.asFile.absolutePath })
    }
  }

val buildNativeTestC by
  tasks.registering {
    description = "Build both static and dynamic C tests"
    group = "verification"

    dependsOn(compileNativeTestStatic, compileNativeTestDynamic)
  }

fun Task.configureDummyOutput() {
  group = "verification"

  // dummy output to satisfy up-to-date check
  val outputFile = layout.buildDirectory.file(name)
  outputs.file(outputFile)

  doFirst { outputFile.get().asFile.delete() }
  doLast { outputFile.get().asFile.writeText("OK") }
}

val testNativeCStatic by
  tasks.registering(Exec::class) {
    configureDummyOutput()
    group = "verification"
    dependsOn(compileNativeTestStatic)

    val testDir = layout.buildDirectory.dir("native-test").get().asFile

    workingDir = projectDir
    commandLine(compileNativeTestStatic.get().outputFile.get().asFile.absolutePath)
  }

val testNativeCDynamic by
  tasks.registering(Exec::class) {
    configureDummyOutput()
    group = "verification"

    dependsOn(compileNativeTestDynamic)

    val testDir = layout.buildDirectory.dir("native-test").get().asFile

    workingDir = projectDir
    commandLine(compileNativeTestDynamic.get().outputFile.get().asFile.absolutePath)
  }

tasks.testNative {
  dependsOn(testNativeCStatic, testNativeCDynamic)

  // can't run libraries from other arch in Java
  if (!buildInfo.isCrossArch) {
    dependsOn(testNativeJava)
  }
}

spotless {
  cpp {
    licenseHeaderFile(
      rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"),
      "// ",
    )
    target("src/*/c/*.c", "src/*/c/*.h")
    eclipseCdt(libs.versions.eclipseCdtFormat.get())
  }
  shell {
    licenseHeaderFile(
        rootProject.file("buildSrc/src/main/resources/license-header.hash-comment.txt"),
        "## build_",
      )
      // skip shebang
      .skipLinesMatching("^#!.+?$")

    target("scripts/*.sh")
  }
  format("bat") {
    target("scripts/*.bat")
    licenseHeaderFile(
      rootProject.file("buildSrc/src/main/resources/license-header.batch-script.txt"),
      "@echo",
    )
  }
}
