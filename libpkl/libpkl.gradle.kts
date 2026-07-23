/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.kotlin.dsl.filter

plugins {
  id("pklAllProjects")
  id("pklGraalVm")
  id("pklJavaLibrary")
  id("pklNativeLifecycle")
}

val stagedMacAarch64NativeLibrary: Configuration =
  configurations.create("stagedMacAarch64NativeLibrary")
val stagedLinuxAmd64NativeLibrary: Configuration =
  configurations.create("stagedLinuxAmd64NativeLibrary")
val stagedLinuxAarch64NativeLibrary: Configuration =
  configurations.create("stagedLinuxAarch64NativeLibrary")
val stagedAlpineLinuxAmd64NativeLibrary: Configuration =
  configurations.create("stagedAlpineLinuxAmd64NativeLibrary")
val stagedWindowsAmd64NativeLibrary: Configuration =
  configurations.create("stagedWindowsAmd64NativeLibrary")

val nativeTestSourceSet: SourceSet = sourceSets.create("nativeTest")

val nativeTestImplementation: Configuration =
  configurations.getByName("nativeTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
  }

val nativeTestRuntimeOnly: Configuration =
  configurations.getByName("nativeTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
  }

dependencies {
  compileOnly(libs.graalSdk)

  implementation(projects.pklCore)
  implementation(projects.pklServer)

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

val macNativeImageAarch64 =
  tasks.register<NativeImageBuild>("macNativeImageAarch64") {
    configure(Target.MacosAarch64)
    // macOS only supports 16K page size
    extraNativeImageArgs =
      listOf(
        "-H:PageSize=16384",
        // increase memory available to native-image
        "-J-Xmx32g",
      )
  }

val linuxNativeImageAmd64 =
  tasks.register<NativeImageBuild>("linuxNativeImageAmd64") { configure(Target.LinuxAmd64) }

val linuxNativeImageAarch64 =
  tasks.register<NativeImageBuild>("linuxNativeImageAarch64") {
    configure(Target.LinuxAarch64)
    extraNativeImageArgs =
      listOf(
        // Ensure compatibility for kernels with page size set to 4k, 16k and 64k
        // (e.g. Raspberry Pi 5, Asahi Linux)
        "-H:PageSize=65536"
      )
  }

val alpineLinuxNativeImageAmd64 =
  tasks.register<NativeImageBuild>("alpineLinuxNativeImageAmd64") {
    configure(Target.AlpineLinuxAmd64)
    extraNativeImageArgs.addAll("--libc=musl")
  }

val windowsNativeImageAmd64 =
  tasks.register<NativeImageBuild>("windowsNativeImageAmd64") {
    configure(Target.WindowsAmd64)
    extraNativeImageArgs.addAll("-Dfile.encoding=UTF-8", "-H:-CheckToolchain")
  }

val buildNativeImageLibrary =
  tasks.register("buildNativeImageLibrary") {
    group = "build"
    val underlyingTask =
      when (buildInfo.targetMachine) {
        Target.MacosAarch64 -> macNativeImageAarch64
        Target.LinuxAarch64 -> linuxNativeImageAarch64
        Target.LinuxAmd64 -> linuxNativeImageAmd64
        Target.WindowsAmd64 -> windowsNativeImageAmd64
        Target.AlpineLinuxAmd64 -> alpineLinuxNativeImageAmd64
      }
    dependsOn(underlyingTask)
    outputs.files(underlyingTask)
  }

val buildPklObjectFile =
  tasks.register<CCompile>("buildPklObjectFile") {
    dependsOn(buildNativeImageLibrary)
    includeDirs.from(buildInfo.targetMachine.tempOutputDir)
    includeDirs.from(file("src/main/c/include/"))
    sourceFiles.from(file("src/main/c/pkl.c"))
    positionIndependentCode = true
    if (buildInfo.os.isWindows) {
      // Match the dynamic CRT (ucrt/msvcrt) that native-image's bundled JDK objects expect -
      // otherwise linking pkl.obj together with the merged libpkl_internal_s.lib archive hits a
      // LIBCMT/MSVCRT default-lib conflict and unresolved __imp_* CRT import symbols.
      runtimeLibrary = "MD"
    }

    outputDir =
      layout.buildDirectory.dir("tmp/compileC/${name}/${buildInfo.targetMachine.targetName}")

    val versionMacro =
      if (buildInfo.targetMachine.os.isWindows) "\\\"${buildInfo.pklVersion}\\\""
      else "\"${buildInfo.pklVersion}\""
    defines.put("PKL_VERSION", versionMacro)
  }

val buildStaticLibrary =
  tasks.register<CArchive>("buildStaticLibrary") {
    dependsOn(buildNativeImageLibrary, buildPklObjectFile)

    val targetMachine = buildInfo.targetMachine
    val objectExtension = targetMachine.os.objectFileExtension
    val pklObjectFile = buildPklObjectFile.flatMap {
      it.outputDir.map { dir -> dir.file("pkl.${objectExtension}") }
    }

    if (targetMachine.os.isWindows) {
      // build_windows.bat produces a single self-contained merged archive
      // (libpkl_internal_s.lib) directly, rather than extracting loose .obj members into an
      // "objects" directory the way build_unix.sh does - pull that in instead.
      objectFiles.from(
        buildNativeImageLibrary.map { task ->
          task.outputs.files.find {
            it.name == "libpkl_internal_s.${targetMachine.os.staticLibraryExtension}"
          }
        }
      )
    } else {
      objectFiles.from(
        fileTree(targetMachine.tempOutputDir.get().dir("objects")).matching {
          include("**/*.${objectExtension}")
        }
      )
    }
    objectFiles.from(pklObjectFile)

    outputFile =
      targetMachine.outputDir.map { dir ->
        dir.file("lib/libpkl.${targetMachine.os.staticLibraryExtension}")
      }
  }

val buildSharedLibrary =
  tasks.register<CCompile>("buildSharedLibrary") {
    dependsOn(buildNativeImageLibrary, buildPklObjectFile)

    link = true
    val targetMachine = buildInfo.targetMachine

    val libraryFile = buildPklObjectFile.flatMap { task ->
      task.outputDir.map { dir -> dir.file("pkl.${targetMachine.os.objectFileExtension}") }
    }

    val extension = targetMachine.os.sharedLibraryExtension
    val staticLib = buildNativeImageLibrary.map { task ->
      // link against the self-contained static archive (produced by build_unix.sh /
      // build_windows.bat) rather than the shared library, so libpkl doesn't carry a runtime
      // dependency on a separate libpkl_internal shared library.
      val fileName =
        if (targetMachine.os.isWindows)
          "libpkl_internal_s.${targetMachine.os.staticLibraryExtension}"
        else "libpkl_internal.${targetMachine.os.staticLibraryExtension}"
      task.outputs.files.find { it.name == fileName }
    }

    sourceFiles.from(libraryFile)
    includeDirs.from(file("src/main/c/"))
    libraryFiles.from(staticLib)
    positionIndependentCode = true
    sharedLibrary = true

    outputFile = targetMachine.outputDir.map { it.file("lib/libpkl.${extension}") }

    if (buildInfo.os.isMacOS) {
      frameworks.addAll("Foundation", "CoreServices")
      linkerFlags.addAll("-current_version", project.version.toString())
      linkerFlags.addAll("-compatibility_version", "0.1.0")

      // libpkl_internal_s bundles Native Image's own JNI-named implementations of JDK native
      // methods (e.g. Java_sun_nio_ch_UnixFileDispatcherImpl_write0), which by default get
      // exported with global visibility. Loading libpkl.dylib into a JVM process (e.g. via JNA)
      // then lets the host JVM's own native-method resolution bind to these internal
      // implementations instead of the JDK's real ones, silently corrupting unrelated file I/O.
      // Restrict the dylib's exported symbols to just the public pkl_* API to prevent this.
      val exportedSymbolsFile = file("src/main/c/pkl.exported_symbols")
      inputs.file(exportedSymbolsFile)
      linkerFlags.addAll("-exported_symbols_list", exportedSymbolsFile.absolutePath)
    }

    if (targetMachine.os.isWindows) {
      // Without an explicit /IMPLIB, MSVC would name the DLL's import library "libpkl.lib" —
      // the same path buildStaticLibrary writes its static archive to.
      linkerFlags.add(
        targetMachine.libraryDir.map { dir -> "/IMPLIB:${dir.file("libpkl_dll.lib").asFile}" }
      )
    } else {
      libraries.add("z")
      libraries.add("pthread")
    }

    if (targetMachine.os.isLinux) {
      libraries.add("dl")

      // Same symbol-collision hazard as the macOS case above (see comment there), but scoped via
      // a GNU ld version script instead of an exported-symbols list.
      val versionScriptFile = file("src/main/c/pkl.map")
      inputs.file(versionScriptFile)
      linkerFlags.add("--version-script=${versionScriptFile.absolutePath}")
    }
  }

val Target.outputDir
  get() = layout.buildDirectory.dir("native-libs/$targetName")

val Target.libraryDir
  get() = layout.buildDirectory.dir("native-libs/$targetName/lib")

val Target.tempOutputDir
  get() = layout.buildDirectory.dir("tmp/native-libs/$targetName")

val createArtifacts =
  tasks.register("createArtifacts") {
    dependsOn(buildSharedLibrary, buildStaticLibrary, tasks.processResources)
    doLast { copy { from(layout.buildDirectory.file("resources/")) } }
  }

val distTar =
  tasks.register<Tar>("distTar") {
    val isUnix = buildInfo.os.isMacOS || buildInfo.os.isLinux
    onlyIf { isUnix }
    dependsOn(buildSharedLibrary, buildStaticLibrary, processFiles)
    compression = Compression.GZIP
    val baseName = "libpkl-${buildInfo.pklVersionNonUnique}-${buildInfo.targetMachine.targetName}"
    archiveFileName = "${baseName}.tar.gz"
    from(buildInfo.targetMachine.outputDir)
    into(baseName)
  }

val distZip =
  tasks.register<Zip>("distZip") {
    val isWindows = buildInfo.os.isWindows
    onlyIf { isWindows }
    dependsOn(buildSharedLibrary, buildStaticLibrary, processFiles)
    val baseName = "libpkl-${buildInfo.pklVersionNonUnique}-${buildInfo.targetMachine.targetName}"
    archiveFileName = "${baseName}.tar.gz"
    from(buildInfo.targetMachine.outputDir)
    into(baseName)
  }

tasks.assembleNative { dependsOn(distZip, distTar) }

val processFiles =
  tasks.register<Copy>("processFiles") {
    inputs.property("version", buildInfo.pklVersion)
    inputs.dir("src/main/c")
    inputs.dir("src/main/files")
    from(fileTree("src/main/files"))
    from(fileTree("src/main/c") { exclude("*.{c,map,exported_symbols}") })
    includeEmptyDirs = true
    into(buildInfo.targetMachine.outputDir)

    filesMatching("libpkl.pc") {
      filter<ReplaceTokens>("tokens" to mapOf("version" to buildInfo.pklVersion))
    }
  }

val testNativeJava =
  tasks.register<Test>("testNativeJava") {
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
          "-Djna.library.path=" + buildInfo.targetMachine.libraryDir.get().asFile.absolutePath,
          "-Djava.library.path=" + buildInfo.targetMachine.libraryDir.get().asFile.absolutePath,
          "--enable-native-access=ALL-UNNAMED",
        )
      }
    )

    environment("LD_LIBRARY_PATH", buildInfo.targetMachine.libraryDir.get().asFile.absolutePath)

    useJUnitPlatform()
  }

// link to static library
val compileNativeTestStatic =
  tasks.register<CCompile>("compileNativeTestStatic") {
    description = "Build C test with static library"
    group = "verification"

    dependsOn(tasks.assembleNative)

    val libDir = buildInfo.targetMachine.libraryDir.get().asFile
    val testSrc = file("src/nativeTest/c/test_pkl.c")

    link = true
    sourceFiles.from(testSrc)
    includeDirs.from(buildInfo.targetMachine.outputDir.map { it.file("include") })
    // Match the dynamic CRT (ucrt/msvcrt) that libpkl.lib's bundled JDK objects expect -
    // otherwise this defaults to the static CRT (LIBCMT), causing a defaultlib conflict and
    // unresolved __imp_* CRT import symbols, same as buildPklObjectFile.
    if (buildInfo.os.isWindows) {
      runtimeLibrary = "MD"
    }
    outputFile =
      layout.buildDirectory.file(
        "native-test/${buildInfo.targetMachine.targetName}/test_pkl_static"
      )

    libraryFiles.from("${libDir.absolutePath}/libpkl.${buildInfo.os.staticLibraryExtension}")

    if (!buildInfo.os.isWindows) {
      libraries.add("pthread")
      libraries.add("z")
    }

    if (buildInfo.os.isLinux) {
      libraries.add("dl")
    }

    if (buildInfo.os.isMacOS) {
      frameworks.addAll("Foundation", "CoreServices")
    }
  }

// link to dynamic library
val compileNativeTestDynamic =
  tasks.register<CCompile>("compileNativeTestDynamic") {
    description = "Build C test with dynamic library"
    group = "verification"

    dependsOn(tasks.assembleNative)

    val testSrc = file("src/nativeTest/c/test_pkl.c")

    link = true
    sourceFiles.from(testSrc)
    includeDirs.from(buildInfo.targetMachine.outputDir.map { it.file("include") })
    outputFile =
      layout.buildDirectory.file(
        "native-test/${buildInfo.targetMachine.targetName}/test_pkl_dynamic"
      )

    libraryPaths.from(buildInfo.targetMachine.libraryDir)

    if (buildInfo.os.isWindows) {
      // "pkl" would resolve to libpkl.lib, which is buildStaticLibrary's static archive, not
      // the DLL's import library — link against the import library directly instead.
      libraryFiles.from(buildInfo.targetMachine.libraryDir.map { it.file("libpkl_dll.lib") })
    } else {
      libraries.add("pkl")
      libraries.add("z")
      libraries.add("pthread")
      // Bake the library's location into the test executable so the dynamic loader can find
      // libpkl.so/libpkl.dylib without needing LD_LIBRARY_PATH/DYLD_LIBRARY_PATH set at run time.
      linkerFlags.add("-rpath")
      linkerFlags.add(buildInfo.targetMachine.libraryDir.get().asFile.absolutePath)
    }

    if (buildInfo.os.isLinux) {
      libraries.add("dl")
    }
  }

val buildNativeTestC =
  tasks.register("buildNativeTestC") {
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

val testNativeCStatic =
  tasks.register<Exec>("testNativeCStatic") {
    configureDummyOutput()
    group = "verification"
    dependsOn(compileNativeTestStatic)

    val testDir = layout.buildDirectory.dir("native-test").get().asFile

    workingDir = projectDir
    commandLine(compileNativeTestStatic.get().outputFile.get().asFile.absolutePath)
  }

val testNativeCDynamic =
  tasks.register<Exec>("testNativeCDynamic") {
    configureDummyOutput()
    group = "verification"

    dependsOn(compileNativeTestDynamic)

    val testDir = layout.buildDirectory.dir("native-test").get().asFile

    workingDir = projectDir
    commandLine(compileNativeTestDynamic.get().outputFile.get().asFile.absolutePath)

    if (buildInfo.os.isWindows) {
      // Windows has no rpath equivalent — the DLL loader only searches the exe's own
      // directory and PATH, so libpkl.dll must be made findable via PATH.
      doFirst {
        environment(
          "PATH",
          "${buildInfo.targetMachine.libraryDir.get().asFile.absolutePath};${System.getenv("PATH")}",
        )
      }
    }
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
      rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"),
      "// ",
    )
    target("src/*/c/*.c", "src/*/c/*.h")
    eclipseCdt(libs.versions.eclipseCdtFormat.get())
  }
  shell {
    licenseHeaderFile(
        rootProject.file("build-logic/src/main/resources/license-header.hash-comment.txt"),
        "## build_",
      )
      // skip shebang
      .skipLinesMatching("^#!.+?$")

    target("scripts/*.sh")
  }
  format("bat") {
    target("scripts/*.bat")
    licenseHeaderFile(
      rootProject.file("build-logic/src/main/resources/license-header.batch-script.txt"),
      "@echo",
    )
  }
}
