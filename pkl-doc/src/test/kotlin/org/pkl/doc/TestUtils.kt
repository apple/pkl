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
package org.pkl.doc

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.listFilesRecursively
import org.pkl.core.util.IoUtils

class TestUtils {
  val tempFileSystem: FileSystem by lazy { Jimfs.newFileSystem(Configuration.unix()) }

  val tmpOutputDir by lazy { tempFileSystem.getPath("/work/output").apply { createDirectories() } }

  val projectDir = FileTestUtils.rootProjectDir.resolve("pkl-doc")

  val inputDir: Path by lazy {
    projectDir.resolve("src/test/files/DocGeneratorTest/input").apply { assert(exists()) }
  }

  val docsiteModule: URI by lazy {
    inputDir.resolve("docsite-info.pkl").apply { assert(exists()) }.toUri()
  }

  internal val package1PackageModule: URI by lazy {
    inputDir.resolve("com.package1/doc-package-info.pkl").apply { assert(exists()) }.toUri()
  }

  val package2PackageModule: URI by lazy {
    inputDir.resolve("com.package2/doc-package-info.pkl").apply { assert(exists()) }.toUri()
  }

  internal val package1InputModules: List<URI> by lazy {
    inputDir
      .resolve("com.package1")
      .listFilesRecursively()
      .filter { it.fileName.toString() != "doc-package-info.pkl" }
      .map { it.toUri() }
  }

  val package2InputModules: List<URI> by lazy {
    inputDir
      .resolve("com.package2")
      .listFilesRecursively()
      .filter { it.fileName.toString() != "doc-package-info.pkl" }
      .map { it.toUri() }
  }

  val expectedOutputDir: Path by lazy {
    projectDir.resolve("src/test/files/DocGeneratorTest/output").createDirectories()
  }

  val expectedOutputFiles: List<Path> by lazy { expectedOutputDir.listFilesRecursively() }

  val actualOutputDir: Path by lazy { tempFileSystem.getPath("/work/DocGeneratorTest") }

  val actualOutputFiles: List<Path> by lazy { actualOutputDir.listFilesRecursively() }

  val expectedRelativeOutputFiles: List<String> by lazy {
    expectedOutputFiles.map { path ->
      IoUtils.toNormalizedPathString(expectedOutputDir.relativize(path)).let { str ->
        // Git will by default clone symlinks as shortcuts on Windows, and shortcuts have a
        // `.lnk` extension.
        if (IoUtils.isWindows() && str.endsWith(".lnk")) str.dropLast(4) else str
      }
    }
  }

  val actualRelativeOutputFiles: List<String> by lazy {
    actualOutputFiles.map { IoUtils.toNormalizedPathString(actualOutputDir.relativize(it)) }
  }

  val binaryFileExtensions = setOf("woff2", "png", "svg")
}
