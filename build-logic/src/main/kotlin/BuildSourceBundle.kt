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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class BuildSourceBundle : DefaultTask() {
  @get:InputFiles val inputJars: ConfigurableFileCollection = project.objects.fileCollection()

  @get:OutputFile val outputZip: RegularFileProperty = project.objects.fileProperty()

  @TaskAction
  @Suppress("unused")
  fun merge() {
    val zipFile = outputZip.asFile.get()
    if (!zipFile.getParentFile().exists()) zipFile.getParentFile().mkdirs()
    ZipOutputStream(zipFile.outputStream()).use { zip ->
      for (jar in inputJars) {
        val jarName = jar.toPath().fileName.toString().removeSuffix(".jar")
        project.zipTree(jar).visit {
          if (isDirectory) return@visit
          zip.putNextEntry(ZipEntry("$jarName/$relativePath"))
          copyTo(zip)
          zip.closeEntry()
        }
      }
    }
  }
}
