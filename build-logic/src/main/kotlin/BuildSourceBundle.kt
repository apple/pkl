/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class BuildSourceBundle : DefaultTask() {
  /** Sources jars that are included in full. */
  @get:InputFiles val inputJars: ConfigurableFileCollection = project.objects.fileCollection()

  /**
   * Sources jars that are only included in part, if [compiledClasses] is set.
   *
   * Only `.java` files are dropped, if their class is not in [compiledClasses]. Everything else is
   * kept: other languages (e.g. Kotlin) can't be matched by name, because the classes that they
   * compile to don't necessarily relate to the file name or package (e.g. with `@file:JvmName` or
   * `@file:JvmPackageName`), and licenses and other metadata must be retained.
   *
   * If [compiledClasses] is not set, these jars are included in full.
   */
  @get:InputFiles
  val filteredInputJars: ConfigurableFileCollection = project.objects.fileCollection()

  /** Fully qualified names of top-level classes, one per line. */
  @get:[InputFile Optional]
  val compiledClasses: RegularFileProperty = project.objects.fileProperty()

  @get:OutputFile val outputZip: RegularFileProperty = project.objects.fileProperty()

  @TaskAction
  @Suppress("unused")
  fun merge() {
    val zipFile = outputZip.asFile.get()
    if (!zipFile.getParentFile().exists()) zipFile.getParentFile().mkdirs()
    val classes =
      if (compiledClasses.isPresent) {
        compiledClasses
          .get()
          .asFile
          .readLines()
          .map { it.trim() }
          .filterTo(hashSetOf()) { it.isNotEmpty() }
      } else null
    val packages = classes?.mapTo(hashSetOf()) { it.substringBeforeLast('.', "").replace('.', '/') }
    var dropped = 0
    ZipOutputStream(zipFile.outputStream()).use { zip ->
      fun copyJar(jar: File, keep: (String) -> Boolean) {
        val jarName = jar.toPath().fileName.toString().removeSuffix(".jar")
        project.zipTree(jar).visit {
          if (isDirectory) return@visit
          val path = relativePath.pathString
          if (!keep(path)) {
            dropped++
            return@visit
          }
          zip.putNextEntry(ZipEntry("$jarName/$path"))
          copyTo(zip)
          zip.closeEntry()
        }
      }
      for (jar in inputJars) copyJar(jar) { true }
      for (jar in filteredInputJars) {
        copyJar(jar) { path ->
          classes == null || packages == null || isEvident(path, classes, packages)
        }
      }
    }
    if (classes != null) {
      logger.lifecycle("Dropped $dropped source files that are not in ${compiledClasses.get()}")
    }
  }

  private fun isEvident(path: String, classes: Set<String>, packages: Set<String>): Boolean {
    // sources of multi-release jars have the same classes as the unversioned ones
    val sourcePath = path.replace(multiReleasePrefix, "")
    val packagePath = sourcePath.substringBeforeLast('/', "")
    val fileName = sourcePath.substringAfterLast('/')
    return when (sourcePath.substringAfterLast('.', "")) {
      "java" ->
        when (fileName) {
          "package-info.java" -> packagePath in packages
          "module-info.java" -> true
          else -> sourcePath.removeSuffix(".java").replace('/', '.') in classes
        }
      else -> true
    }
  }

  private val multiReleasePrefix = Regex("^META-INF/versions/\\d+/")
}
