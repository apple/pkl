/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.Ignore
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.createParentDirectories
import org.pkl.commons.readString
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.listFilesRecursively
import org.pkl.commons.toPath
import org.pkl.core.Version
import org.pkl.doc.DocGenerator.Companion.current

class CliDocGeneratorTest {
  companion object {
    private val tempFileSystem by lazy { Jimfs.newFileSystem(Configuration.unix()) }

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

    private val actualOutputDir: Path by lazy { tempFileSystem.getPath("/work/DocGeneratorTest") }

    private val actualOutputFiles: List<Path> by lazy { actualOutputDir.listFilesRecursively() }

    private val expectedRelativeOutputFiles: List<String> by lazy {
      expectedOutputFiles.map { expectedOutputDir.relativize(it).toString() }
    }

    private val actualRelativeOutputFiles: List<String> by lazy {
      actualOutputFiles.map { actualOutputDir.relativize(it).toString() }
    }

    private val binaryFileExtensions =
      setOf(
        "woff2",
        "png",
        "svg",
      )

    private fun runDocGenerator(outputDir: Path, cacheDir: Path?) {
      CliDocGenerator(
          CliDocGeneratorOptions(
            CliBaseOptions(
              sourceModules =
                listOf(
                  docsiteModule,
                  package1PackageModule,
                  package2PackageModule,
                  URI("package://localhost:12110/birds@0.5.0"),
                  URI("package://localhost:12110/fruit@1.1.0")
                ) + package1InputModules + package2InputModules,
              moduleCacheDir = cacheDir
            ),
            outputDir = outputDir,
            isTestMode = true
          )
        )
        .run()
    }

    @JvmStatic
    private fun generateDocs(): List<String> {
      val cacheDir = Files.createTempDirectory("cli-doc-generator-test-cache")
      PackageServer.populateCacheDir(cacheDir)
      runDocGenerator(actualOutputDir, cacheDir)

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
          isTestMode = true
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
          isTestMode = true
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
          isTestMode = true
        )
      )

    val e = assertThrows<CliException> { generator.run() }
    assertThat(e).hasMessageContaining("at least one", "module")
  }

  @Ignore("sgammon: Broken docgen (probably checksums)")
  @ParameterizedTest
  @MethodSource("generateDocs")
  fun test(relativeFilePath: String) {
    val actualFile = actualOutputDir.resolve(relativeFilePath)
    assertThat(actualFile)
      .withFailMessage("Test bug: $actualFile should exist but does not.")
      .exists()

    val expectedFile = expectedOutputDir.resolve(relativeFilePath)
    if (expectedFile.exists()) {
      when {
        expectedFile.isSymbolicLink() -> {
          assertThat(actualFile).isSymbolicLink
          assertThat(expectedFile.readSymbolicLink().toString().toPath())
            .isEqualTo(actualFile.readSymbolicLink().toString().toPath())
        }
        expectedFile.extension in binaryFileExtensions ->
          assertThat(actualFile.readBytes()).isEqualTo(expectedFile.readBytes())
        else -> assertThat(actualFile.readString()).isEqualTo(expectedFile.readString())
      }
    } else {
      expectedFile.createParentDirectories()
      if (actualFile.isSymbolicLink()) {
        // needs special handling because `copyTo` can't copy symlinks between file systems
        val linkTarget = actualFile.readSymbolicLink()
        assertThat(linkTarget).isRelative
        Files.createSymbolicLink(expectedFile, linkTarget.toString().toPath())
      } else {
        actualFile.copyTo(expectedFile)
      }
      Assertions.fail("Created missing expected file `$relativeFilePath`.")
    }
  }

  @Test
  fun `creates a symlink called current`(@TempDir tempDir: Path) {
    PackageServer.populateCacheDir(tempDir)
    runDocGenerator(actualOutputDir, tempDir)
    val expectedSymlink = actualOutputDir.resolve("com.package1/current")
    val expectedDestination = actualOutputDir.resolve("com.package1/1.2.3")
    org.junit.jupiter.api.Assertions.assertTrue(Files.isSymbolicLink(expectedSymlink))
    org.junit.jupiter.api.Assertions.assertTrue(
      Files.isSameFile(expectedSymlink, expectedDestination)
    )
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
              sourceCodeUrlScheme = "https://example.com/blob/$version%{path}#L%{line}-%{endLine}"
            ),
          modules = emptyList()
        ),
      )

    val packages: List<PackageData> =
      listOf(
        createPackageData("1.2.3"),
        createPackageData("1.3.0-SNAPSHOT"),
      )
    val comparator =
      Comparator<String> { v1, v2 -> Version.parse(v1).compareTo(Version.parse(v2)) }.reversed()

    assertThat(packages.current(comparator).map { it.ref.version }).isEqualTo(listOf("1.2.3"))
  }
}
