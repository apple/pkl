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
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withNormalizer
import org.gradle.process.ExecOperations

abstract class NativeImageBuildService : BuildService<BuildServiceParameters.None>

abstract class NativeImageBuild : DefaultTask() {
  @get:Input abstract val outputName: Property<String>

  @get:Input abstract val extraNativeImageArgs: ListProperty<String>

  @get:Input abstract val arch: Property<Target.Arch>

  /**
   * The main class entrypoint for the executable.
   *
   * This option is not necessary if [sharedLibrary] is true.
   */
  @get:Optional @get:Input abstract val mainClass: Property<String>

  /** Create a shared library, instead of an executable. */
  @get:Input abstract val sharedLibrary: Property<Boolean>

  @get:InputFiles abstract val classpath: ConfigurableFileCollection

  @get:Input abstract val envVars: MapProperty<String, String>

  @get:InputFile @get:Optional abstract val nativeCompilerPath: RegularFileProperty

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @get:Inject protected abstract val execOperations: ExecOperations

  @get:Inject protected abstract val objectFactory: ObjectFactory

  private val graalVm: Provider<BuildInfo.GraalVm> =
    arch.map { a ->
      when (a) {
        Target.Arch.AMD64 -> buildInfo.graalVmAmd64
        Target.Arch.AARCH64 -> buildInfo.graalVmAarch64
      }
    }

  private val buildInfo: BuildInfo = project.extensions.getByType(BuildInfo::class.java)

  private val nativeImageCommandName =
    if (buildInfo.os.isWindows) "native-image.cmd" else "native-image"

  private val nativeImageExecutable = graalVm.map { "${it.baseDir}/bin/$nativeImageCommandName" }

  private val extraArgsFromProperties by lazy {
    System.getProperties()
      .filter { it.key.toString().startsWith("pkl.native") }
      .map { "${it.key}=${it.value}".substring("pkl.native".length) }
  }

  private val buildService =
    project.gradle.sharedServices.registerIfAbsent(
      "nativeImageBuildService",
      NativeImageBuildService::class,
    ) {
      maxParallelUsages.set(1)
    }

  init {
    // ensure native-image builds run in serial (prevent `gw buildNative` from consuming all host
    // CPU resources).
    usesService(buildService)

    sharedLibrary.convention(false)

    outputDir.convention(project.layout.buildDirectory.dir("executable"))

    group = "build"

    inputs
      .files(classpath)
      .withPropertyName("runtimeClasspath")
      .withNormalizer(ClasspathNormalizer::class)
    inputs
      .files(nativeImageExecutable)
      .withPropertyName("graalVmNativeImage")
      .withPathSensitivity(PathSensitivity.ABSOLUTE)
    if (nativeCompilerPath.isPresent) {
      inputs.file(nativeCompilerPath)
    }
  }

  @Suppress("unused")
  @OutputFiles
  fun getEffectiveOutputFiles(): FileCollection {
    return objectFactory
      .fileCollection()
      .from(
        sharedLibrary.map { isLibrary ->
          val dir = outputDir.get()
          val libraryName = outputName.get()
          // if building a library, native-image outputs five files (four headers, and dynamic lib)
          // otherwise, it outputs just the one file
          if (isLibrary) {
            val sharedLibraryExtension = buildInfo.os.sharedLibraryExtension
            dir.files(
              "$libraryName.$sharedLibraryExtension",
              "$libraryName.h",
              "${libraryName}_dynamic.h",
              "graal_isolate.h",
              "graal_isolate_dynamic.h",
            )
          } else {
            dir.file(libraryName)
          }
        }
      )
  }

  @TaskAction
  protected fun run() {
    execOperations.exec {
      val exclusions =
        listOf(buildInfo.libs.findLibrary("graalSdk").get()).map { it.get().module.name }

      executable = nativeImageExecutable.get()
      workingDir(outputDir)

      args = buildList {
        // must be emitted before any experimental options are used
        add("-H:+UnlockExperimentalVMOptions")
        // currently gives a deprecation warning, but we've been told
        // that the "initialize everything at build time" *CLI* option is likely here to stay
        add("--initialize-at-build-time=")
        // needed for messagepack-java (see https://github.com/msgpack/msgpack-java/issues/600)
        add("--initialize-at-run-time=org.msgpack.core.buffer.DirectBufferAccess")
        add("--no-fallback")
        add("-H:IncludeResources=org/pkl/core/stdlib/.*\\.pkl")
        add("-H:IncludeResources=org/jline/utils/.*")
        add("-H:IncludeResourceBundles=org.pkl.core.errorMessages")
        add("-H:IncludeResourceBundles=org.pkl.parser.errorMessages")
        add("-H:IncludeResources=org/pkl/commons/cli/PklCARoots.pem")
        if (mainClass.isPresent) {
          add("-H:Class=${mainClass.get()}")
        }
        if (sharedLibrary.get()) {
          add("--shared")
        }
        add("-o")
        add(outputName.get())
        // the actual limit (currently) used by native-image is this number + 1400 (idea is to
        // compensate for Truffle's own nodes)
        add("-H:MaxRuntimeCompileMethods=1800")
        add("-H:+EnforceMaxRuntimeCompileMethods")
        add("--enable-url-protocols=http,https")
        add("-H:+ReportExceptionStackTraces")
        // disable automatic support for JVM CLI options (puts our main class in full control of
        // argument parsing)
        add("-H:-ParseRuntimeOptions")
        // quick build mode: 40% faster compilation, 20% smaller (but presumably also slower)
        // executable
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
          classpath.filter {
            it.exists() && !exclusions.any { exclude -> it.name.contains(exclude) }
          }
        add(pathInput.asPath)
        // make sure dev machine stays responsive (15% slowdown on my laptop)
        val processors =
          Runtime.getRuntime().availableProcessors() /
            if (buildInfo.os.isMacOS && !buildInfo.isCiBuild) 4 else 1
        add("-J-XX:ActiveProcessorCount=${processors}")
        addAll(envVars.get().entries.map { "-E${it.key}=${it.value}" })
        if (nativeCompilerPath.isPresent) {
          add("--native-compiler-path=${nativeCompilerPath.get().asFile}")
        }
        // Pass through all `HOMEBREW_` prefixed environment variables to allow build with shimmed
        // tools.
        addAll(environment.keys.filter { it.startsWith("HOMEBREW_") }.map { "-E$it" })
        addAll(extraNativeImageArgs.get())
        addAll(extraArgsFromProperties)
      }
    }
  }
}
