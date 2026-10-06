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
import java.time.LocalDateTime
import java.time.Month
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class BuildSourceBundle : DefaultTask() {
  private companion object {
    // Same date Gradle uses in `org.gradle.api.internal.file.archive.ZipCopyAction`
    val ZIP_ENTRY_MTIME: LocalDateTime = LocalDateTime.of(1980, Month.FEBRUARY, 1, 0, 0)
  }

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val inputJars: ConfigurableFileCollection

  @get:OutputFile abstract val outputZip: RegularFileProperty

  @get:Inject protected abstract val archives: ArchiveOperations

  @TaskAction
  @Suppress("unused")
  fun merge() {
    ZipOutputStream(outputZip.asFile.get().outputStream()).use { zip ->
      for (jar in inputJars.files.sortedBy { it.name }) {
        val jarName = jar.nameWithoutExtension
        archives.zipTree(jar).visit {
          if (isDirectory) return@visit
          zip.putNextEntry(ZipEntry("$jarName/$relativePath").apply { timeLocal = ZIP_ENTRY_MTIME })
          copyTo(zip)
          zip.closeEntry()
        }
      }
    }
  }
}
