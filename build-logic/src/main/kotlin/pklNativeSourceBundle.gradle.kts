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
import javax.inject.Inject
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations

// Source bundles for the native variants of a project, one for each `Target`.
//
// Each bundle consists of the sources of everything that is compiled into the native image or
// library of that target:
// * the project's own sources, and those of its first-party dependencies;
// * the sources of third-party dependencies that have classes in the image;
// * the Java sources of the JDK, SubstrateVM, and Graal classes that are in the image;
// * the C sources of the JDK and SubstrateVM native libraries that are linked into the image;
// * for statically linked musl executables, the sources of musl and zlib.
//
// Which classes are in the image is read from the lists in `build/compiled-classes` (download them
// with the `downloadCompiledClasses` task). native-image produces these lists, and CI uploads them
// as `compiled-classes-<project>-*` artifacts. This way, the source bundle of any target can be
// built on any machine.
//
// Sources of the JDK, SubstrateVM, and Graal come from the OpenJDK and Graal repositories at the
// tags matching the GraalVM that is used to build, because GraalVM doesn't ship all of the Java
// sources (or any of the C sources).
plugins { id("pklSourceBundle") }

val libs = the<LibrariesForLibs>()
val sourceBundleSpec = extensions.getByType<SourceBundleSpec>()

val openJdkSourcesTarball: Configuration = configurations.create("openJdkSourcesTarball")
val graalSourcesTarball: Configuration = configurations.create("graalSourcesTarball")

val muslSourcesTarball: Configuration = configurations.create("muslSourcesTarball")
val zlibSourcesTarball: Configuration = configurations.create("zlibSourcesTarball")

// CI builds musl and zlib from source for statically linked executables; use the same versions.
fun muslToolchainVersion(name: String): String {
  val script = rootProject.file(".github/scripts/install_musl.sh").readText()
  return Regex("${name}_VERSION=\"([^\"]+)\"").find(script)?.groupValues?.get(1)
    ?: throw GradleException("Could not find ${name}_VERSION in .github/scripts/install_musl.sh")
}

// The "GitHub tag archives" and "musl releases" repositories are declared in settings.gradle.kts;
// declaring repositories in a project would stop it from using the ones declared in settings.
dependencies {
  val jdkVersion = libs.versions.graalVmJdkVersion.get()
  openJdkSourcesTarball("openjdk:jdk${jdkVersion.substringBefore('.')}u:jdk-$jdkVersion-ga")
  graalSourcesTarball("oracle:graal:vm-${libs.versions.graalVm.get()}")
  muslSourcesTarball("musl:musl:${muslToolchainVersion("MUSL")}")
  zlibSourcesTarball("madler:zlib:v${muslToolchainVersion("ZLIB")}")
}

// native-image's class path can differ from the runtime class path (e.g. `nativeImageClasspath` in
// `pklNativeExecutable`), so resolve the sources of what's actually compiled in.
val resolveNativeSourcesJars =
  tasks.register<ResolveSourcesJars>("resolveNativeSourcesJars") {
    configuration.set(
      provider {
        configurations.findByName("nativeImageClasspath")
          ?: configurations.getByName("runtimeClasspath")
      }
    )
    outputDir.set(layout.buildDirectory.dir("resolveNativeSourcesJars"))
  }

// The JDK native libraries that native-image links into the image.
val openJdkNativeLibraries = listOf("java", "net", "nio", "zip", "extnet", "management_ext")

// Platform-specific directories in OpenJDK's `src/<module>/<platform>/`.
val Target.OS.openJdkPlatforms: List<String>
  get() = buildList {
    add("share")
    if (!isWindows) add("unix")
    add(
      when {
        isMacOS -> "macosx"
        isWindows -> "windows"
        else -> "linux"
      }
    )
  }

// The `substratevm/src/com.oracle.svm.native.*` projects that apply to the OS.
val Target.OS.svmNativeProjects: List<String>
  get() = buildList {
    add("libchelper")
    when {
      isMacOS -> {
        add("darwin")
        add("jvm.posix")
      }
      isLinux -> {
        add("libcontainer")
        add("jvm.posix")
      }
      isWindows -> add("jvm.windows")
    }
  }

val sourceBundleTasks =
  Target.entries.map { target ->
    val variant = target.name
    val outputDir = layout.buildDirectory.dir("tmp/nativeImageSources/${target.targetName}")
    val compiledClassesFile =
      layout.buildDirectory.file("compiled-classes/${target.targetName}.txt")

    val nativeImageSourcesJar =
      tasks.register<NativeImageSourcesJar>("nativeImageSourcesJar$variant") {
        compiledClasses = compiledClassesFile
        openJdkTarball.from(openJdkSourcesTarball)
        graalTarball.from(graalSourcesTarball)
        openJdkPlatforms.addAll(target.os.openJdkPlatforms)
        outputJar = outputDir.map { it.file("native-image-sources.jar") }
      }

    val openJdkNativeSourcesJar =
      tasks.register<Zip>("openJdkNativeSourcesJar$variant") {
        archiveFileName = "openjdk-native-sources.jar"
        destinationDirectory = outputDir
        includeEmptyDirs = false

        from(provider { tarTree(resources.gzip(openJdkSourcesTarball.singleFile)) }) {
          for (platform in target.os.openJdkPlatforms) {
            include("*/src/*/$platform/native/include/**")
            for (library in openJdkNativeLibraries) {
              include("*/src/*/$platform/native/lib$library/**")
            }
          }
          // drop the `<repo>-<tag>/` root directory
          eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
          }
        }
      }

    val graalNativeSourcesJar =
      tasks.register<Zip>("graalNativeSourcesJar$variant") {
        archiveFileName = "graal-native-sources.jar"
        destinationDirectory = outputDir
        includeEmptyDirs = false

        from(provider { tarTree(resources.gzip(graalSourcesTarball.singleFile)) }) {
          for (svmProject in target.os.svmNativeProjects) {
            include("*/substratevm/src/com.oracle.svm.native.$svmProject/**")
          }
          eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
          }
        }
      }

    // Executables for musl targets are statically linked, so musl and zlib are part of them.
    val staticLibcSources =
      if (target.musl) {
        val muslArch =
          when (target.arch) {
            Target.Arch.AMD64 -> "x86_64"
            Target.Arch.AARCH64 -> "aarch64"
          }

        val muslSourcesJar =
          tasks.register<Zip>("muslSourcesJar$variant") {
            archiveFileName = "musl-sources.jar"
            destinationDirectory = outputDir
            includeEmptyDirs = false

            from(provider { tarTree(resources.gzip(muslSourcesTarball.singleFile)) }) {
              // skip the other architectures
              include("*/src/**", "*/include/**", "*/crt/**", "*/ldso/**", "*/compat/**")
              include("*/arch/generic/**", "*/arch/$muslArch/**")
              include("*/COPYRIGHT", "*/VERSION", "*/WHATSNEW")
              eachFile {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
              }
            }
          }

        val zlibSourcesJar =
          tasks.register<Zip>("zlibSourcesJar$variant") {
            archiveFileName = "zlib-sources.jar"
            destinationDirectory = outputDir
            includeEmptyDirs = false

            from(provider { tarTree(resources.gzip(zlibSourcesTarball.singleFile)) }) {
              exclude("*/.github/**")
              eachFile {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
              }
            }
          }

        sourceBundleSpec.staticMuslLibc.map { isStatic ->
          if (isStatic) files(muslSourcesJar, zlibSourcesJar) else files()
        }
      } else {
        provider { files() }
      }

    tasks.register<BuildSourceBundle>("sourceBundle$variant") {
      group = "build"
      description = "Builds the source bundle of ${project.name} for ${target.targetName}."
      inputJars.from(
        sourceBundleSpec.firstPartyJars,
        sourceBundleSpec.extraNativeSources,
        nativeImageSourcesJar,
        openJdkNativeSourcesJar,
        graalNativeSourcesJar,
        staticLibcSources,
      )
      // Only include sources of dependency classes that are compiled into the image.
      filteredInputJars.from(resolveNativeSourcesJars.map { fileTree(it.outputDir) })
      compiledClasses = compiledClassesFile
      outputZip =
        layout.buildDirectory.file(
          "sourceBundle/${project.name}-${project.version}-${target.targetName}-sourcebundle.zip"
        )
    }
  }

tasks.named("sourceBundle") { dependsOn(sourceBundleTasks) }

/**
 * Downloads the lists of classes compiled into native images, which CI uploads as
 * `compiled-classes-<project>-<os>-<arch>` artifacts, into `build/compiled-classes`.
 *
 * Requires the GitHub CLI (`gh`) to be installed and authenticated.
 */
abstract class DownloadCompiledClasses : DefaultTask() {
  @get:Option(option = "run-id", description = "ID of the GitHub Actions run to download from.")
  @get:Input
  abstract val runId: Property<String>

  @get:Option(option = "repo", description = "GitHub repository that the run belongs to.")
  @get:Input
  abstract val repo: Property<String>

  /** The project whose artifacts to download (named `compiled-classes-<project>-*`). */
  @get:Input abstract val projectName: Property<String>

  @get:OutputDirectory abstract val destinationDir: DirectoryProperty

  @get:Inject protected abstract val execOperations: ExecOperations

  init {
    group = "build"
    description = "Downloads the compiled classes lists that CI produced into the build directory."
    repo.convention("apple/pkl")
    // always re-download; this task is meant to be run manually
    outputs.upToDateWhen { false }
  }

  @TaskAction
  @Suppress("unused")
  fun download() {
    if (!runId.isPresent) {
      throw GradleException("Specify the run to download from, e.g. `--run-id=<id>`")
    }
    val name = projectName.get()
    val downloadDir = temporaryDir.resolve("download")
    downloadDir.deleteRecursively()
    downloadDir.mkdirs()

    execOperations.exec {
      commandLine(
        "gh",
        "run",
        "download",
        runId.get(),
        "--repo",
        repo.get(),
        "--pattern",
        "compiled-classes-$name-*",
        "--dir",
        downloadDir.absolutePath,
      )
    }

    // Files are named `<project>-<version>-<target name>.txt`; keep only the target name.
    val fileName = Regex("^${Regex.escape(name)}-\\d+(?:\\.\\d+)*(?:-SNAPSHOT)?-(.+\\.txt)$")
    val downloaded =
      downloadDir
        .walkTopDown()
        .filter { it.isFile }
        .associate { file ->
          val target =
            fileName.matchEntire(file.name)?.groupValues?.get(1)
              ?: throw GradleException("Unexpected file in downloaded artifacts: $file")
          target to file
        }
    if (downloaded.isEmpty()) {
      throw GradleException("Run ${runId.get()} has no `compiled-classes-$name-*` artifacts")
    }

    val destination = destinationDir.get().asFile
    destination.mkdirs()
    // remove lists of targets that are no longer built
    destination.listFiles { f -> f.extension == "txt" }?.forEach { it.delete() }
    for ((target, file) in downloaded) {
      file.copyTo(destination.resolve(target))
    }
    logger.lifecycle("Downloaded ${downloaded.keys.sorted()} into $destination")
  }
}

tasks.register<DownloadCompiledClasses>("downloadCompiledClasses") {
  projectName = project.name
  destinationDir = layout.buildDirectory.dir("compiled-classes")
}
