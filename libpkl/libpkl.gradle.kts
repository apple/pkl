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
plugins {
  pklAllProjects
  pklJavaLibrary
  pklKotlinLibrary
  pklNativeBuild
}

dependencies {
  implementation(projects.pklCore)
  implementation(projects.pklServer)

  implementation(libs.msgpack)
  implementation(libs.truffleApi)

  testImplementation(projects.pklCommonsTest)
}

fun Exec.configureLibrary(graalVm: BuildInfo.GraalVm, extraArgs: List<String> = listOf()) {
  inputs
    .files(sourceSets.main.map { it.output })
    .withPropertyName("mainSourceSets")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs
    .files(configurations.runtimeClasspath)
    .withPropertyName("runtimeClasspath")
    .withNormalizer(ClasspathNormalizer::class)
  val nativeImageCommandName = if (buildInfo.os.isWindows) "native-image.cmd" else "native-image"
  inputs
    .files(file(graalVm.baseDir).resolve("bin/$nativeImageCommandName"))
    .withPropertyName("graalVmNativeImage")
    .withPathSensitivity(PathSensitivity.ABSOLUTE)

  val outputDir = layout.buildDirectory.dir("libs/${graalVm.osName}-${graalVm.arch}")
  val outputFile: Provider<RegularFile> =
    outputDir.map { it.file("libpkl-${graalVm.osName}-${graalVm.arch}.dylib") }

  outputs.files(
    outputFile,
    // GraalVM shared headers.
    outputDir.map { it.file("graal_isolate.h") },
    outputDir.map { it.file("graal_isolate_dynamic.h") },
  )

  outputs.cacheIf { true }

  workingDir(outputDir)
  executable = "${graalVm.baseDir}/bin/$nativeImageCommandName"

  // For any system properties starting with `pkl.native`, strip off that prefix and pass the rest
  // through as arguments to native-image.
  //
  // Allow setting args using flags like
  // (-Dpkl.native-Dpolyglot.engine.userResourceCache=/my/cache/dir) when building through Gradle.
  val extraArgsFromProperties =
    System.getProperties()
      .filter { it.key.toString().startsWith("pkl.native") }
      .map { "${it.key}=${it.value}".substring("pkl.native".length) }

  // JARs to exclude from the class path for the native-image build.
  val exclusions = listOf(libs.graalSdk).map { it.get().module.name }
  // https://www.graalvm.org/22.0/reference-manual/native-image/Options/
  argumentProviders.add(
    CommandLineArgumentProvider {
      buildList {
        // must be emitted before any experimental options are used
        add("-H:+UnlockExperimentalVMOptions")
        // currently gives a deprecation warning, but we've been told
        // that the "initialize everything at build time" *CLI* option is likely here to stay
        add("--initialize-at-build-time=")
        // needed for messagepack-java (see https://github.com/msgpack/msgpack-java/issues/600)
        add("--initialize-at-run-time=org.msgpack.core.buffer.DirectBufferAccess")
        add("--no-fallback")
        add("-H:IncludeResources=org/pkl/core/stdlib/.*\\.pkl")
        add("-H:IncludeResourceBundles=org.pkl.core.errorMessages")
        add("-H:IncludeResources=org/pkl/commons/cli/PklCARoots.pem")
        add("-H:Features=org.pkl.nativeapi.InitFeature")

        add("-o")
        // Need te remove the extension, as that gets added by native-image.
        add(outputFile.get().asFile.name.substringBeforeLast("."))

        // Build our shared library
        add("--shared")

        // the actual limit (currently) used by native-image is this number + 1400 (idea is to
        // compensate for Truffle's own nodes)
        add("-H:MaxRuntimeCompileMethods=1800")
        add("-H:+EnforceMaxRuntimeCompileMethods")
        add("--enable-url-protocols=http,https")
        add("-H:+ReportExceptionStackTraces")
        // disable automatic support for JVM CLI options
        add("-H:-ParseRuntimeOptions")
        // quick build mode: 40% faster compilation, 20% smaller (but presumably also slower)
        // library
        if (!buildInfo.isReleaseBuild) {
          add("-Ob")
        }
        if (buildInfo.isNativeArch) {
          add("-march=native")
        } else {
          add("-march=compatibility")
        }
        // native-image rejects non-existing class path entries -> filter
        add("--class-path")
        val pathInput =
          sourceSets.main.get().output +
            configurations.runtimeClasspath.get().filter {
              it.exists() && !exclusions.any { exclude -> it.name.contains(exclude) }
            }
        add(pathInput.asPath)
        // make sure dev machine stays responsive (15% slowdown on my laptop)
        val processors =
          Runtime.getRuntime().availableProcessors() /
            if (buildInfo.os.isMacOsX && !buildInfo.isCiBuild) 4 else 1
        add("-J-XX:ActiveProcessorCount=${processors}")
        // Pass through all `HOMEBREW_` prefixed environment variables to allow build with shimmed
        // tools.
        addAll(environment.keys.filter { it.startsWith("HOMEBREW_") }.map { "-E$it" })
        addAll(extraArgs)
        addAll(extraArgsFromProperties)
      }
    }
  )
}

/** Builds the pkl native library for macOS/amd64. */
val macNativeLibraryAmd64: TaskProvider<Exec> by
  tasks.registering(Exec::class) {
    dependsOn(":installGraalVmAmd64")

    configureLibrary(buildInfo.graalVmAmd64)
  }

/** Builds the pkl native library for macOS/aarch64. */
val macNativeLibraryAarch64: TaskProvider<Exec> by
  tasks.registering(Exec::class) {
    dependsOn(":installGraalVmAarch64")

    configureLibrary(
      buildInfo.graalVmAarch64,
      listOf("-H:+AllowDeprecatedBuilderClassesOnImageClasspath"),
    )
  }

/** Builds the pkl native library for linux/amd64. */
val linuxNativeLibraryAmd64: TaskProvider<Exec> by
  tasks.registering(Exec::class) {
    dependsOn(":installGraalVmAmd64")

    configureLibrary(buildInfo.graalVmAmd64)
  }

/**
 * Builds the pkl native library for linux/aarch64.
 *
 * Right now, this is built within a container on Mac using emulation because CI does not have ARM
 * instances.
 */
val linuxNativeLibraryAarch64: TaskProvider<Exec> by
  tasks.registering(Exec::class) {
    dependsOn(":installGraalVmAarch64")

    configureLibrary(
      buildInfo.graalVmAarch64,
      listOf(
        // Ensure compatibility for kernels with page size set to 4k, 16k and 64k
        // (e.g. Raspberry Pi 5, Asahi Linux)
        "-H:PageSize=65536"
      ),
    )
  }

/**
 * TODO(kushal): https://github.com/oracle/graal/issues/3053
 *
 * Builds a statically linked pkl native library for linux/amd64.
 *
 * Note: we don't publish the same for linux/aarch64 because native-image doesn't support this.
 * Details: https://www.graalvm.org/22.0/reference-manual/native-image/ARM64/
 */
val alpineNativeLibraryAmd64: TaskProvider<Exec> by
  tasks.registering(Exec::class) {
    dependsOn(":installGraalVmAmd64")

    configureLibrary(buildInfo.graalVmAmd64, listOf("--libc=musl"))
  }

val windowsNativeLibraryAmd64: TaskProvider<Exec> by
  tasks.registering(Exec::class) {
    dependsOn(":installGraalVmAmd64")

    configureLibrary(buildInfo.graalVmAmd64, listOf("-Dfile.encoding=UTF-8"))
  }

tasks.assembleNative {
  when {
    buildInfo.os.isMacOsX -> {
      dependsOn(macNativeLibraryAmd64)
      if (buildInfo.arch == "aarch64") {
        dependsOn(macNativeLibraryAarch64)
      }
    }

    buildInfo.os.isWindows -> {
      dependsOn(windowsNativeLibraryAmd64)
    }

    buildInfo.os.isLinux && buildInfo.arch == "aarch64" -> {
      dependsOn(linuxNativeLibraryAarch64)
    }

    buildInfo.os.isLinux && buildInfo.arch == "amd64" -> {
      dependsOn(linuxNativeLibraryAmd64)
      if (buildInfo.hasMuslToolchain) {
        dependsOn(alpineNativeLibraryAmd64)
      }
    }
  }
}
