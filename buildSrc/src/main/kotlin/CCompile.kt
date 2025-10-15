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
import Target.OS
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Task for compiling C source files to object files or executables. Supports both GCC/Clang and
 * MSVC compilers.
 */
abstract class CCompile : DefaultTask() {
  /** The C source files to compile. */
  @get:InputFiles abstract val sourceFiles: ConfigurableFileCollection

  /** The include directories for compilation. */
  @get:InputFiles abstract val includeDirs: ConfigurableFileCollection

  /** Preprocessor definitions. */
  @get:Input @get:Optional abstract val defines: MapProperty<String, String>

  /** C standard version (e.g., "c11", "c17", "gnu11"). */
  @get:Input @get:Optional abstract val cStandard: Property<String>

  /** Optimization level (e.g., "0", "1", "2", "3", "s", "fast"). */
  @get:Input @get:Optional abstract val optimizationLevel: Property<String>

  /** Whether to include debug symbols. */
  @get:Input abstract val debugSymbols: Property<Boolean>

  /** Whether to generate position-independent code (GCC/Clang only). */
  @get:Input abstract val positionIndependentCode: Property<Boolean>

  /** Warning flags (GCC/Clang: prefixed with -W, e.g., "all", "extra"). */
  @get:Input abstract val warningFlags: ListProperty<String>

  /** Warning level for MSVC (0-4). */
  @get:Input @get:Optional abstract val warningLevel: Property<Int>

  /** Feature flags (GCC/Clang only, prefixed with -f). */
  @get:Input abstract val featureFlags: ListProperty<String>

  /** Machine-specific flags (GCC/Clang only, prefixed with -m). */
  @get:Input abstract val machineFlags: ListProperty<String>

  /** Runtime library for MSVC (e.g., "MT", "MD", "MTd", "MDd"). */
  @get:Input @get:Optional abstract val runtimeLibrary: Property<String>

  /** Additional compiler arguments. */
  @get:Input abstract val compilerArgs: ListProperty<String>

  /** The architecture to compile for (macOS only). */
  @get:Input @get:Optional abstract val arch: Property<Target.Arch>

  // ===== Linking properties =====

  /** Whether to link into an executable (true) or just compile to object files (false). */
  @get:Input abstract val link: Property<Boolean>

  /** Library search paths. */
  @get:InputFiles @get:Optional abstract val libraryPaths: ConfigurableFileCollection

  /** Libraries to link by name. */
  @get:Input @get:Optional abstract val libraries: ListProperty<String>

  /** Direct library files to link. */
  @get:InputFiles @get:Optional abstract val libraryFiles: ConfigurableFileCollection

  /** Linker flags. */
  @get:Input @get:Optional abstract val linkerFlags: ListProperty<String>

  /** macOS frameworks to link (GCC/Clang only). */
  @get:Input @get:Optional abstract val frameworks: ListProperty<String>

  /** Output file when linking (executable name). */
  @get:OutputFile @get:Optional abstract val outputFile: RegularFileProperty

  /** Create a shared library (dll, dylib, so). */
  @get:Input abstract val sharedLibrary: Property<Boolean>

  /** The output directory for object files (when not linking). */
  @get:OutputDirectory @get:Optional abstract val outputDir: DirectoryProperty

  @get:Inject protected abstract val execOperations: ExecOperations

  private val buildInfo: BuildInfo = project.extensions.getByType(BuildInfo::class.java)

  init {
    group = "build"
    // Defaults
    link.convention(false)
    debugSymbols.convention(false)
    positionIndependentCode.convention(false)
    sharedLibrary.convention(false)
    warningFlags.convention(listOf("all", "extra"))
    warningLevel.convention(3)
    featureFlags.convention(emptyList())
    machineFlags.convention(emptyList())
    compilerArgs.convention(emptyList())
    defines.convention(emptyMap())
    libraries.convention(emptyList())
    linkerFlags.convention(emptyList())
    frameworks.convention(emptyList())
  }

  @TaskAction
  fun compile() {
    if (link.get()) {
      compileAndLink()
    } else {
      compileOnly()
    }
  }

  private fun compileOnly() {
    val outputDirectory = outputDir.get().asFile
    outputDirectory.mkdirs()

    val cFiles = sourceFiles.files.filter { it.extension == "c" }

    if (cFiles.isEmpty()) {
      logger.info("No C files to compile")
      return
    }

    cFiles.forEach { cFile ->
      when (buildInfo.targetMachine.os) {
        OS.Linux,
        OS.MacOS -> compileFileGCC(cFile, outputDirectory)
        OS.Windows -> compileFileMSVC(cFile, outputDirectory)
      }
    }
    cFiles.forEach { cFile ->
      val objectFile = outputDirectory.resolve("${cFile.nameWithoutExtension}.o")
      logger.info("Compiled ${cFile.name} -> ${objectFile.name}")
    }
  }

  private fun compileFileGCC(cFile: java.io.File, outputDirectory: java.io.File) {
    val objectFile = outputDirectory.resolve("${cFile.nameWithoutExtension}.o")

    val args = buildList {
      add("cc")
      add("-c")
      addAll(buildCompilerFlagsGCC())
      add(cFile.absolutePath)
      add("-o")
      add(objectFile.absolutePath)
    }

    execOperations.exec {
      workingDir = project.projectDir
      commandLine(args)
    }
  }

  private fun compileFileMSVC(cFile: java.io.File, outputDirectory: java.io.File) {
    val objectFile = outputDirectory.resolve("${cFile.nameWithoutExtension}.obj")

    val args = buildList {
      add("cl.exe")
      add("/c")
      addAll(buildCompilerFlagsMSVC())
      add("/Fo${objectFile.absolutePath}")
      add(cFile.absolutePath)
    }

    execOperations.exec {
      workingDir = project.projectDir
      commandLine(args)
    }
  }

  private fun compileAndLink() {
    when (buildInfo.targetMachine.os) {
      OS.Linux,
      OS.MacOS -> compileAndLinkGCC()
      OS.Windows -> compileAndLinkMSVC()
    }

    logger.info("Compiled and linked -> ${outputFile.get().asFile.name}")
  }

  private fun compileAndLinkGCC() {
    val output = outputFile.get().asFile

    val args = buildList {
      add("cc")
      addAll(buildCompilerFlagsGCC())

      // Add source/object files
      sourceFiles.files.forEach { file -> add(file.absolutePath) }

      // Library search paths
      libraryPaths.files.forEach { dir -> add("-L${dir.absolutePath}") }

      // Libraries by name
      libraries.get().forEach { lib -> add("-l${lib}") }

      // Direct library files
      libraryFiles.files.forEach { file -> add(file.absolutePath) }

      // Linker flags
      if (linkerFlags.get().isNotEmpty()) {
        add("-Wl,${linkerFlags.get().joinToString(",")}")
      }

      // macOS frameworks
      if (buildInfo.os.isMacOS) {
        frameworks.get().forEach { framework ->
          add("-framework")
          add(framework)
        }
      }

      if (sharedLibrary.get()) {
        add("-shared")
      }

      // Output file
      add("-o")
      add(output.absolutePath)
    }

    output.parentFile.mkdirs()

    execOperations.exec {
      workingDir = project.projectDir
      commandLine(args)
    }
  }

  private fun compileAndLinkMSVC() {
    val output = outputFile.get().asFile
    output.parentFile.mkdirs()

    val args = buildList {
      add("cl.exe")
      addAll(buildCompilerFlagsMSVC())

      // Output executable
      add("/Fe${output.absolutePath}")

      // Add source/object files
      sourceFiles.files.forEach { file -> add(file.absolutePath) }

      // Linker section
      if (
        libraryPaths.files.isNotEmpty() ||
          libraries.get().isNotEmpty() ||
          libraryFiles.files.isNotEmpty() ||
          linkerFlags.get().isNotEmpty()
      ) {
        add("/link")

        if (sharedLibrary.get()) {
          add("/DLL")
        }

        // Library search paths
        libraryPaths.files.forEach { dir -> add("/LIBPATH:${dir.absolutePath}") }

        // Library files by name
        libraries.get().forEach { lib -> add(lib) }

        // Direct library files
        libraryFiles.files.forEach { file -> add(file.absolutePath) }

        // Additional linker flags
        addAll(linkerFlags.get())
      }
    }

    execOperations.exec {
      workingDir = project.projectDir
      commandLine(args)
    }
  }

  private fun buildCompilerFlagsGCC(): List<String> = buildList {
    // Warning flags
    warningFlags.get().forEach { flag -> add("-W${flag}") }

    // Include directories
    includeDirs.files.forEach { dir -> add("-I${dir.absolutePath}") }

    // Preprocessor definitions
    defines.get().forEach { (key, value) ->
      if (value.isEmpty()) {
        add("-D${key}")
      } else {
        add("-D${key}=${value}")
      }
    }

    // C standard
    if (cStandard.isPresent) {
      add("-std=${cStandard.get()}")
    }

    // Optimization level
    if (optimizationLevel.isPresent) {
      add("-O${optimizationLevel.get()}")
    }

    // Debug symbols
    if (debugSymbols.get()) {
      add("-g")
    }

    // Position-independent code
    if (positionIndependentCode.get()) {
      add("-fPIC")
    }

    // Feature flags
    featureFlags.get().forEach { flag -> add("-f${flag}") }

    // Machine flags
    machineFlags.get().forEach { flag -> add("-m${flag}") }

    // Platform-specific flags
    if (buildInfo.os.isMacOS && arch.isPresent) {
      add("-arch")
      add(arch.get().cCompilerName)
    }

    // Additional compiler arguments
    addAll(compilerArgs.get())
  }

  private fun buildCompilerFlagsMSVC(): List<String> = buildList {
    // Warning level
    if (warningLevel.isPresent) {
      add("/W${warningLevel.get()}")
    }

    // Include directories
    includeDirs.files.forEach { dir -> add("/I${dir.absolutePath}") }

    // Preprocessor definitions
    defines.get().forEach { (key, value) ->
      if (value.isEmpty()) {
        add("/D${key}")
      } else {
        add("/D${key}=${value}")
      }
    }

    // C standard
    if (cStandard.isPresent) {
      add("/std:${cStandard.get()}")
    }

    // Optimization level
    if (optimizationLevel.isPresent) {
      when (optimizationLevel.get().lowercase()) {
        "0",
        "d" -> add("/Od")
        "1" -> add("/O1")
        "2" -> add("/O2")
        "x",
        "fast" -> add("/Ox")
        "s" -> add("/Os")
        "t" -> add("/Ot")
      }
    }

    // Debug symbols
    if (debugSymbols.get()) {
      add("/Zi")
    }

    // Runtime library
    if (runtimeLibrary.isPresent) {
      add("/${runtimeLibrary.get()}")
    }

    // Additional compiler arguments
    addAll(compilerArgs.get())
  }
}
