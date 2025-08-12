/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
@file:OptIn(ExperimentalPathApi::class)

package org.pkl.doc

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.assertj.core.api.AbstractPathAssert
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.listFilesRecursively
import org.pkl.commons.walk
import org.pkl.core.Version
import org.pkl.core.util.IoUtils
import org.pkl.doc.DocGenerator.Companion.determineCurrentPackages
import org.pkl.doc.DocTestUtils.testExpectedFile

class CliDocGeneratorTest {
  companion object {
    private val tempFileSystem: FileSystem by lazy { Jimfs.newFileSystem(Configuration.unix()) }

    private val tmpOutputDir by lazy {
      tempFileSystem.getPath("/work/output").apply { createDirectories() }
    }

    private val projectDir = FileTestUtils.rootProjectDir.resolve("pkl-doc")

    private val inputDir: Path by lazy {
      projectDir.resolve("src/test/files/DocGeneratorTest/input").apply { assert(exists()) }
    }

    private val docsiteModule: URI by lazy {
      inputDir.resolve("docsite-info.pkl").apply { assert(exists()) }.toUri()
    }

    internal val package1PackageModule: URI by lazy {
      inputDir.resolve("com.package1/doc-package-info.pkl").apply { assert(exists()) }.toUri()
    }

    private val package2PackageModule: URI by lazy {
      inputDir.resolve("com.package2/doc-package-info.pkl").apply { assert(exists()) }.toUri()
    }

    internal val package1InputModules: List<URI> by lazy {
      inputDir
        .resolve("com.package1")
        .listFilesRecursively()
        .filter { it.fileName.toString() != "doc-package-info.pkl" }
        .map { it.toUri() }
    }

    private val package2InputModules: List<URI> by lazy {
      inputDir
        .resolve("com.package2")
        .listFilesRecursively()
        .filter { it.fileName.toString() != "doc-package-info.pkl" }
        .map { it.toUri() }
    }

    private val expectedOutputDir: Path by lazy {
      projectDir.resolve("src/test/files/DocGeneratorTest/output").createDirectories()
    }

    private val expectedOutputFiles: List<Path> by lazy { expectedOutputDir.listFilesRecursively() }

    private val baseActualOutputDir: Path by lazy {
      tempFileSystem.getPath("/work/DocGeneratorTest")
    }
    private val actualOutputDir: Path by lazy { baseActualOutputDir.resolve("run-1") }
    private val actualOutputDir2: Path by lazy { baseActualOutputDir.resolve("run-2") }

    private val actualOutputFiles: List<Path> by lazy { baseActualOutputDir.listFilesRecursively() }

    private val expectedRelativeOutputFiles: List<String> by lazy {
      expectedOutputFiles.map { path ->
        IoUtils.toNormalizedPathString(expectedOutputDir.relativize(path)).let { str ->
          // Git will by default clone symlinks as shortcuts on Windows, and shortcuts have a
          // `.lnk` extension.
          if (IoUtils.isWindows() && str.endsWith(".lnk")) str.dropLast(4) else str
        }
      }
    }

    private val actualRelativeOutputFiles: List<String> by lazy {
      actualOutputFiles.map { IoUtils.toNormalizedPathString(baseActualOutputDir.relativize(it)) }
    }

    private fun runDocGenerator(
      outputDir: Path,
      cacheDir: Path?,
      sourceModules: List<URI>,
      noSymlinks: Boolean = false,
    ) {
      CliDocGenerator(
          CliDocGeneratorOptions(
            CliBaseOptions(
              sourceModules = sourceModules + docsiteModule,
              moduleCacheDir = cacheDir,
            ),
            outputDir = outputDir,
            isTestMode = true,
            noSymlinks = noSymlinks,
          )
        )
        .run()
    }

    // Run the doc generator twice; second time adds new versions for the `birds` package
    @JvmStatic
    private fun generateDocs(): List<String> {
      val cacheDir = Files.createTempDirectory("cli-doc-generator-test-cache")
      PackageServer.populateCacheDir(cacheDir)
      runDocGenerator(
        actualOutputDir,
        cacheDir,
        listOf(
          package1PackageModule,
          package2PackageModule,
          URI("package://localhost:0/birds@0.5.0"),
          URI("package://localhost:0/fruit@1.1.0"),
          URI("package://localhost:0/unlisted@1.0.0"),
          URI("package://localhost:0/deprecated@1.0.0"),
        ) + package1InputModules + package2InputModules,
      )

      // simulate running the doc generator again with new packages.
      actualOutputDir.copyToRecursively(actualOutputDir2, followLinks = false)
      runDocGenerator(
        actualOutputDir2,
        cacheDir,
        listOf(URI("package://localhost:0/birds@0.6.0"), URI("package://localhost:0/birds@0.7.0")),
      )

      val missingFiles = expectedRelativeOutputFiles - actualRelativeOutputFiles.toSet()
      if (missingFiles.isNotEmpty()) {
        Assertions.fail<Unit>(
          "The following expected files were not actually generated:\n" +
            missingFiles.joinToString("\n")
        )
      }

      return actualRelativeOutputFiles
    }
  }

  @Test
  fun `cannot have multiple docsite descriptors`() {
    val descriptor1 =
      tempFileSystem.getPath("/work/dir1/docsite-info.pkl").apply {
        createParentDirectories()
        createFile()
      }
    val descriptor2 =
      tempFileSystem.getPath("/work/dir2/docsite-info.pkl").apply {
        createParentDirectories()
        createFile()
      }

    val generator =
      CliDocGenerator(
        CliDocGeneratorOptions(
          CliBaseOptions(sourceModules = listOf(descriptor1.toUri(), descriptor2.toUri())),
          outputDir = tmpOutputDir,
          isTestMode = true,
        )
      )

    val e = assertThrows<CliException> { generator.run() }
    assertThat(e).hasMessageContaining("multiple", "docsite-info.pkl")
  }

  @Test
  fun `must have at least one package descriptor`() {
    val module1 =
      tempFileSystem.getPath("/work/module1.pkl").apply {
        createParentDirectories()
        createFile()
      }
    val generator =
      CliDocGenerator(
        CliDocGeneratorOptions(
          CliBaseOptions(sourceModules = listOf(module1.toUri())),
          outputDir = tmpOutputDir,
          isTestMode = true,
        )
      )

    val e = assertThrows<CliException> { generator.run() }
    assertThat(e).hasMessageContaining("at least one", "doc-package-info.pkl")
  }

  @Test
  fun `must have at least one module to generate documentation for`() {
    val descriptor1 =
      tempFileSystem.getPath("/work/package-info.pkl").apply {
        createParentDirectories()
        createFile()
      }
    val generator =
      CliDocGenerator(
        CliDocGeneratorOptions(
          CliBaseOptions(sourceModules = listOf(descriptor1.toUri())),
          outputDir = tmpOutputDir,
          isTestMode = true,
        )
      )

    val e = assertThrows<CliException> { generator.run() }
    assertThat(e).hasMessageContaining("at least one", "module")
  }

  @ParameterizedTest
  @MethodSource("generateDocs")
  fun test(relativeFilePath: String) {
    testExpectedFile(expectedOutputDir, baseActualOutputDir, relativeFilePath)
  }

  @Test
  fun `creates a symlink called current by default`(@TempDir tempDir: Path) {
    PackageServer.populateCacheDir(tempDir)
    runDocGenerator(actualOutputDir, tempDir, package1InputModules + package1PackageModule)

    val expectedSymlink = actualOutputDir.resolve("com.package1/current")
    val expectedDestination = actualOutputDir.resolve("com.package1/1.2.3")

    assertThat(expectedSymlink).isSymlinkPointingTo(expectedDestination)
  }

  @Test
  fun `does not overwrite the current version if generating an older version`(
    @TempDir tempDir: Path
  ) {
    PackageServer.populateCacheDir(tempDir)
    val outputDir = tempFileSystem.getPath("/doesNotOverwrite")
    runDocGenerator(outputDir, tempDir, listOf(URI("package://localhost:0/birds@0.6.0")))
    runDocGenerator(outputDir, tempDir, listOf(URI("package://localhost:0/birds@0.5.0")))

    val expectedSymlink = outputDir.resolve("localhost(3a)0/birds/current")
    val expectedDestination = outputDir.resolve("localhost(3a)0/birds/0.6.0")

    assertThat(expectedSymlink).isSymlinkPointingTo(expectedDestination)
  }

  private fun AbstractPathAssert<*>.isSymlinkPointingTo(
    expectedDestination: Path
  ): AbstractPathAssert<*> {
    if (!actual().isSymbolicLink()) {
      Assertions.fail<Unit>("Expected ${actual()} to be a symlink, but was not")
    }
    if (!Files.isSameFile(actual(), expectedDestination)) {
      Assertions.fail<Unit>(
        "Expected symbolic link ${actual()} should point to $expectedDestination, but points to ${actual().toRealPath()}"
      )
    }
    return this
  }

  @Test
  fun `creates a copy of the latest output called current when symlinks are disabled`(
    @TempDir tempDir: Path
  ) {
    PackageServer.populateCacheDir(tempDir)
    runDocGenerator(
      actualOutputDir,
      tempDir,
      noSymlinks = true,
      sourceModules = package1InputModules + package1PackageModule,
    )

    val currentDirectory = actualOutputDir.resolve("com.package1/current")
    val sourceDirectory = actualOutputDir.resolve("com.package1/1.2.3")

    assertThat(currentDirectory).isDirectory()
    assertThat(currentDirectory.isSymbolicLink()).isFalse()

    val expectedFiles = sourceDirectory.walk().map(sourceDirectory::relativize).toList()
    val actualFiles = currentDirectory.walk().map(currentDirectory::relativize).toList()

    assertThat(actualFiles).hasSameElementsAs(expectedFiles)
  }

  @Test
  fun `current() excludes prerelease versions`() {
    fun createPackageData(version: String) =
      PackageData(
        DocPackage(
          docPackageInfo =
            DocPackageInfo(
              name = "com.package",
              version = version,
              uri = null,
              importUri = "foo.pkl",
              authors = listOf("me"),
              sourceCode = URI.create("foo.pkl"),
              issueTracker = URI.create("https://github.com/apple/pkl/issues"),
              overview = "my overview",
              sourceCodeUrlScheme = "https://example.com/blob/$version%{path}#L%{line}-%{endLine}",
            ),
          modules = emptyList(),
        )
      )

    val packages: List<PackageData> =
      listOf(createPackageData("1.2.3"), createPackageData("1.3.0-SNAPSHOT"))
    val comparator =
      Comparator<String> { v1, v2 -> Version.parse(v1).compareTo(Version.parse(v2)) }.reversed()

    val newCurrentPackages = determineCurrentPackages(packages, comparator)

    assertThat(newCurrentPackages.map { it.ref.version }).isEqualTo(listOf("1.2.3"))
  }

  @Test
  fun `running generator on legacy docsite throws an error`(@TempDir tempDir: Path) {
    val outputDir =
      tempFileSystem.getPath("/work/runningGeneratorOnLegacyDocsite").apply { createDirectories() }
    val legacySiteDir = projectDir.resolve("src/test/files/DocMigratorTest/input/version-1")
    legacySiteDir.copyToRecursively(outputDir, followLinks = true)
    assertThatCode {
        runDocGenerator(outputDir, tempDir, package1InputModules + package1PackageModule)
      }
      .hasMessageContaining("pkldoc website model is too old")
  }
}
