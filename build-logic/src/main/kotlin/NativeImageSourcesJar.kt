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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Collects the JDK, SubstrateVM, and Graal Java sources for every class that has methods compiled
 * into a native image.
 *
 * The classes are read from [compiledClasses], and their sources are looked up in the OpenJDK and
 * Graal repositories' tagged source archives. These archives contain the sources of every platform
 * and architecture, so this task does not need to run on the platform that it collects sources for.
 */
abstract class NativeImageSourcesJar : DefaultTask() {
  /**
   * Fully qualified top-level names of the classes compiled into the image, one per line.
   *
   * This is the list that native-image reports (via `-H:+PrintUniverse`) when building the image.
   */
  @get:InputFile abstract val compiledClasses: RegularFileProperty

  /** The gzipped tarball of the OpenJDK repository (e.g. `jdk25u`) at the JDK's release tag. */
  @get:InputFiles abstract val openJdkTarball: ConfigurableFileCollection

  /** The gzipped tarball of the `oracle/graal` repository at the GraalVM's release tag. */
  @get:InputFiles abstract val graalTarball: ConfigurableFileCollection

  /**
   * The platform-specific source roots to include from OpenJDK; any of `share`, `unix`, `linux`,
   * `macosx`, and `windows`.
   */
  @get:Input abstract val openJdkPlatforms: SetProperty<String>

  @get:OutputFile abstract val outputJar: RegularFileProperty

  @TaskAction
  @Suppress("unused")
  fun collect() {
    val wanted =
      compiledClasses
        .get()
        .asFile
        .readLines()
        .filter { it.isNotBlank() }
        .mapTo(hashSetOf()) { it.trim().replace('.', '/') + ".java" }
    if (wanted.isEmpty()) {
      throw GradleException("No compiled classes found in ${compiledClasses.get()}")
    }

    val platforms = openJdkPlatforms.get()
    // <repo>-<tag>/src/<module>/<platform>/classes/<package path>.java
    val openJdkSource = Regex("""^[^/]+/src/[^/]+/([^/]+)/classes/(.+\.java)$""")
    // <repo>-<tag>/<suite>/src/<project>/src/<package path>.java
    //
    // Truffle sources are intentionally omitted; they are already published as sources jars.
    val graalSource = Regex("""^[^/]+/(?:substratevm|compiler|sdk)/src/[^/]+/src/(.+\.java)$""")

    val out = outputJar.get().asFile
    out.parentFile.mkdirs()
    val written = hashSetOf<String>()
    ZipOutputStream(out.outputStream().buffered()).use { zip ->
      fun collectFrom(tarball: java.io.File, sourcePathOf: (String) -> String?) {
        project.tarTree(project.resources.gzip(tarball)).visit {
          if (isDirectory) return@visit
          val sourcePath = sourcePathOf(relativePath.pathString) ?: return@visit
          if (sourcePath !in wanted || !written.add(sourcePath)) return@visit
          zip.putNextEntry(ZipEntry(sourcePath))
          copyTo(zip)
          zip.closeEntry()
        }
      }
      collectFrom(openJdkTarball.singleFile) { path ->
        openJdkSource
          .matchEntire(path)
          ?.takeIf { it.groupValues[1] in platforms }
          ?.let { it.groupValues[2] }
      }
      collectFrom(graalTarball.singleFile) { path ->
        graalSource.matchEntire(path)?.groupValues?.get(1)
      }
    }
    logger.lifecycle(
      "Collected sources of ${written.size} of ${wanted.size} compiled classes into $out"
    )
  }
}
