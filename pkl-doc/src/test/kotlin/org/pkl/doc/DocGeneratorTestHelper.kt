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

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.fail
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.listFilesRecursively
import org.pkl.core.util.IoUtils

class DocGeneratorTestHelper {
  internal val tempDir by lazy { Files.createTempDirectory("ExecutableCliDocGeneratorTest") }

  internal val projectDir = FileTestUtils.rootProjectDir.resolve("pkl-doc")

  internal val inputDir: Path by lazy {
    projectDir.resolve("src/test/files/DocGeneratorTest/input").apply { assert(exists()) }
  }

  internal val docsiteModule: URI by lazy {
    inputDir.resolve("docsite-info.pkl").apply { assert(exists()) }.toUri()
  }

  internal val package1PackageModule: URI by lazy {
    inputDir.resolve("com.package1/doc-package-info.pkl").apply { assert(exists()) }.toUri()
  }

  internal val package2PackageModule: URI by lazy {
    inputDir.resolve("com.package2/doc-package-info.pkl").apply { assert(exists()) }.toUri()
  }

  internal val package1InputModules: List<URI> by lazy {
    inputDir
      .resolve("com.package1")
      .listFilesRecursively()
      .filter { it.fileName.toString() != "doc-package-info.pkl" }
      .map { it.toUri() }
  }

  internal val package2InputModules: List<URI> by lazy {
    inputDir
      .resolve("com.package2")
      .listFilesRecursively()
      .filter { it.fileName.toString() != "doc-package-info.pkl" }
      .map { it.toUri() }
  }

  internal val expectedOutputDir: Path by lazy {
    projectDir.resolve("src/test/files/DocGeneratorTest/output").createDirectories()
  }

  internal val expectedOutputFiles: List<Path> by lazy { expectedOutputDir.listFilesRecursively() }

  internal val actualOutputDir: Path by lazy {
    tempDir.resolve("work/DocGeneratorTest").createDirectories()
  }

  internal val actualOutputFiles: List<Path> by lazy { actualOutputDir.listFilesRecursively() }

  internal val cacheDir: Path by lazy { tempDir.resolve("cache") }

  internal val sourceModules =
    listOf(
      docsiteModule,
      package1PackageModule,
      package2PackageModule,
      URI("package://localhost:0/birds@0.5.0"),
      URI("package://localhost:0/fruit@1.1.0"),
      URI("package://localhost:0/unlisted@1.0.0"),
      URI("package://localhost:0/deprecated@1.0.0"),
    ) + package1InputModules + package2InputModules

  internal val expectedRelativeOutputFiles: List<String> by lazy {
    expectedOutputFiles.map { path ->
      IoUtils.toNormalizedPathString(expectedOutputDir.relativize(path)).let { str ->
        // Git will by default clone symlinks as shortcuts on Windows, and shortcuts have a
        // `.lnk` extension.
        if (IoUtils.isWindows() && str.endsWith(".lnk")) str.dropLast(4) else str
      }
    }
  }

  internal val actualRelativeOutputFiles: List<String> by lazy {
    actualOutputFiles.map { IoUtils.toNormalizedPathString(actualOutputDir.relativize(it)) }
  }

  fun runPklDocCli(executable: Path, options: CliDocGeneratorOptions) {
    val command = buildList {
      add(executable.toString())
      add("--output-dir")
      add(options.normalizedOutputDir.toString())
      add("--cache-dir")
      add(options.base.normalizedModuleCacheDir.toString())
      add("--test-mode")
      addAll(sourceModules.map { it.toString() })
    }
    val process =
      with(ProcessBuilder(command)) {
        redirectErrorStream(true)
        start()
      }
    try {
      val out = process.inputStream.reader().readText()
      val exitCode = process.waitFor()

      if (exitCode != 0) {
        fail(
          """
            Process exited with $exitCode.

            Output:
          """
            .trimIndent() + out
        )
      }
    } finally {
      process.destroy()
    }
  }

  private fun generateDocsWith(doGenerate: (CliDocGeneratorOptions) -> Unit): List<String> {
    PackageServer.populateCacheDir(cacheDir)
    val options =
      CliDocGeneratorOptions(
        CliBaseOptions(
          sourceModules =
            listOf(
              docsiteModule,
              package1PackageModule,
              package2PackageModule,
              URI("package://localhost:0/birds@0.5.0"),
              URI("package://localhost:0/fruit@1.1.0"),
              URI("package://localhost:0/unlisted@1.0.0"),
              URI("package://localhost:0/deprecated@1.0.0"),
            ) + package1InputModules + package2InputModules,
          moduleCacheDir = cacheDir,
        ),
        outputDir = actualOutputDir,
        isTestMode = true,
        noSymlinks = false,
      )
    doGenerate(options)
    val missingFiles = expectedRelativeOutputFiles - actualRelativeOutputFiles.toSet()
    if (missingFiles.isNotEmpty()) {
      Assertions.fail<Unit>(
        "The following expected files were not actually generated:\n" +
          missingFiles.joinToString("\n")
      )
    }

    return actualRelativeOutputFiles
  }

  fun generateDocsWithCli(executable: Path): List<String> {
    return generateDocsWith { runPklDocCli(executable, it) }
  }

  fun generateDocs(): List<String> {
    return generateDocsWith { CliDocGenerator(it).run() }
  }
}
