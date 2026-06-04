/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withNormalizer
import org.gradle.process.ExecOperations

enum class Architecture {
  AMD64,
  AARCH64,
}

abstract class NativeImageBuildService : BuildService<BuildServiceParameters.None>

abstract class NativeImageBuild : DefaultTask() {
  @get:Input abstract val imageName: Property<String>

  @get:Input abstract val extraNativeImageArgs: ListProperty<String>

  @get:Input abstract val arch: Property<Architecture>

  @get:Input abstract val mainClass: Property<String>

  @get:InputFiles abstract val classpath: ConfigurableFileCollection

  /** Path to the `native-image` binary (e.g. `<graalVmBaseDir>/bin/native-image`). */
  @get:Input abstract val nativeImageExecutable: Property<String>

  @get:Input abstract val graalSdkLibraryName: Property<String>

  @get:Input abstract val releaseBuild: Property<Boolean>

  @get:Input abstract val nativeArch: Property<Boolean>

  /** Divisor applied to `availableProcessors` to throttle native-image CPU usage. */
  @get:Input abstract val processorDivisor: Property<Int>

  @get:Inject protected abstract val execOperations: ExecOperations

  @get:Inject protected abstract val layout: ProjectLayout

  private val outputDir
    get() = layout.buildDirectory.dir("executable")

  @get:OutputFile
  val outputFile
    get() = outputDir.flatMap { it.file(imageName) }

  @get:ServiceReference("nativeImageBuildService")
  abstract val buildService: Property<NativeImageBuildService>

  private val extraArgsFromProperties by lazy {
    System.getProperties()
      .filter { it.key.toString().startsWith("pkl.native") }
      .map { "${it.key}=${it.value}".substring("pkl.native".length) }
  }

  init {
    group = "build"

    inputs
      .files(classpath)
      .withPropertyName("runtimeClasspath")
      .withNormalizer(ClasspathNormalizer::class)
    inputs
      .files(nativeImageExecutable)
      .withPropertyName("graalVmNativeImage")
      .withPathSensitivity(PathSensitivity.ABSOLUTE)
  }

  @TaskAction
  @Suppress("unused")
  protected fun run() {
    execOperations.exec {
      val exclusions = listOf(graalSdkLibraryName.get())

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
        // needed for jline-terminal-jni
        add("--initialize-at-run-time=org.jline.nativ,org.jline.terminal.impl.jni")
        add("--no-fallback")
        add("-H:IncludeResources=org/pkl/core/stdlib/.*\\.pkl")
        add("-H:IncludeResources=org/jline/utils/.*")
        add("-H:IncludeResourceBundles=org.pkl.core.errorMessages")
        add("-H:IncludeResourceBundles=org.pkl.parser.errorMessages")
        add("-H:IncludeResources=org/pkl/commons/cli/PklCARoots.pem")
        add("-H:Class=${mainClass.get()}")
        add("-o")
        add(imageName.get())
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
        if (!releaseBuild.get()) {
          add("-Ob")
        }
        if (nativeArch.get()) {
          add("-march=native")
        } else {
          add("-march=compatibility")
        }
        // native-image rejects non-existing class path entries -> filter
        add("--class-path")
        val pathInput = classpath.filter {
          it.exists() && !exclusions.any { exclude -> it.name.contains(exclude) }
        }
        add(pathInput.asPath)
        // make sure dev machine stays responsive (15% slowdown on my laptop)
        val processors = Runtime.getRuntime().availableProcessors() / processorDivisor.get()
        add("-J-XX:ActiveProcessorCount=${processors}")
        // Pass through all `HOMEBREW_` prefixed environment variables to allow build with shimmed
        // tools.
        addAll(environment.keys.filter { it.startsWith("HOMEBREW_") }.map { "-E$it" })
        addAll(extraNativeImageArgs.get())
        addAll(extraArgsFromProperties)
      }
    }
  }
}
