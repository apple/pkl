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
@file:OptIn(ExperimentalPathApi::class)

package org.pkl.doc

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.commons.copyRecursively
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.listFilesRecursively
import org.pkl.core.Version
import org.pkl.core.util.IoUtils

class DocMigratorTest {
  companion object {
    private val projectDir = FileTestUtils.rootProjectDir.resolve("pkl-doc")

    private val inputDir: Path by lazy {
      projectDir.resolve("src/test/files/DocMigratorTest/input/version-1").apply {
        assert(exists())
      }
    }

    private val expectedOutputDir: Path by lazy {
      projectDir.resolve("src/test/files/DocMigratorTest/output/").also { it.createDirectories() }
    }

    private val actualOutputDir: Path by lazy { Files.createTempDirectory("docMigratorTest") }

    private val actualOutputFiles: List<Path> by lazy { actualOutputDir.listFilesRecursively() }

    private val actualRelativeOutputFiles: List<String> by lazy {
      actualOutputFiles.map { IoUtils.toNormalizedPathString(actualOutputDir.relativize(it)) }
    }

    private val expectedOutputFiles: List<Path> by lazy { expectedOutputDir.listFilesRecursively() }

    private val expectedRelativeOutputFiles: List<String> by lazy {
      expectedOutputFiles.map { path ->
        IoUtils.toNormalizedPathString(expectedOutputDir.relativize(path)).let { str ->
          // Git will by default clone symlinks as shortcuts on Windows, and shortcuts have a
          // `.lnk` extension.
          if (IoUtils.isWindows() && str.endsWith(".lnk")) str.dropLast(4) else str
        }
      }
    }

    @JvmStatic
    private fun migrateDocs(): List<String> {
      val cacheDir = Files.createTempDirectory("cli-doc-generator-test-cache")
      PackageServer.populateCacheDir(cacheDir)
      inputDir.copyRecursively(actualOutputDir)
      val migrator =
        DocMigrator(actualOutputDir, OutputStream.nullOutputStream()) { v1, v2 ->
          Version.parse(v1).compareTo(Version.parse(v2))
        }
      migrator.run()
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
  fun parseLegacyRuntimeData(@TempDir tempDir: Path) {
    val file =
      tempDir.resolve("index.js").also { f ->
        f.writeText(
          """
        runtimeData.links('known-versions','[{"text":"1.2.3","classes":"current-version"}]');
        runtimeData.links('known-usages','[{"text":"Foo","href":"../moduleTypes2/Foo.html"},{"text":"moduleTypes2","href":"../moduleTypes2/index.html"}]');
        runtimeData.links('known-subtypes','[{"text":"Foo","href":"../moduleTypes2/Foo.html"}]');
      """
            .trimIndent()
        )
      }
    val data = DocMigrator.parseLegacyRuntimeData(file, "../1.2.3/index.html")
    assertThat(data)
      .isEqualTo(
        RuntimeData(
          knownVersions = setOf(RuntimeDataLink(text = "1.2.3", href = "../1.2.3/index.html")),
          knownUsages =
            setOf(
              RuntimeDataLink(text = "Foo", href = "../moduleTypes2/Foo.html"),
              RuntimeDataLink(text = "moduleTypes2", href = "../moduleTypes2/index.html"),
            ),
          knownSubtypes = setOf(RuntimeDataLink(text = "Foo", href = "../moduleTypes2/Foo.html")),
        )
      )
  }

  @ParameterizedTest
  @MethodSource("migrateDocs")
  fun test(relativeFilePath: String) {
    DocTestUtils.testExpectedFile(expectedOutputDir, actualOutputDir, relativeFilePath)
  }
}
