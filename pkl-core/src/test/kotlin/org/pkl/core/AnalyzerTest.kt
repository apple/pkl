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
package org.pkl.core

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.PackageServer
import org.pkl.commons.writeString
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.project.Project

class AnalyzerTest {
  private val simpleAnalyzer =
    Analyzer(
      StackFrameTransformers.defaultTransformer,
      false,
      SecurityManagers.defaultManager,
      listOf(ModuleKeyFactories.file, ModuleKeyFactories.standardLibrary, ModuleKeyFactories.pkg),
      null,
      null,
      HttpClient.dummyClient(),
    )

  @Test
  fun `simple case`(@TempDir tempDir: Path) {
    val file =
      tempDir
        .resolve("test.pkl")
        .writeString(
          """
            amends "pkl:base"

            import "pkl:json"

            myProp = import("pkl:xml")
          """
            .trimIndent()
        )
        .toUri()
    val result = simpleAnalyzer.importGraph(file)
    assertThat(result.imports)
      .containsEntry(
        file,
        setOf(
          ImportGraph.Import(URI("pkl:base")),
          ImportGraph.Import(URI("pkl:json")),
          ImportGraph.Import(URI("pkl:xml")),
        ),
      )
  }

  @Test
  fun `glob imports`(@TempDir tempDir: Path) {
    val file1 =
      tempDir
        .resolve("file1.pkl")
        .writeString(
          """
            import* "*.pkl"
          """
            .trimIndent()
        )
        .toUri()
    val file2 = tempDir.resolve("file2.pkl").writeString("foo = 1").toUri()
    val file3 = tempDir.resolve("file3.pkl").writeString("bar = 1").toUri()
    val result = simpleAnalyzer.importGraph(file1)
    assertThat(result.imports)
      .isEqualTo(
        mapOf(
          file1 to
            setOf(ImportGraph.Import(file1), ImportGraph.Import(file2), ImportGraph.Import(file3)),
          file2 to emptySet(),
          file3 to emptySet(),
        )
      )
  }

  @Test
  fun `cyclical imports`(@TempDir tempDir: Path) {
    val file1 = tempDir.resolve("file1.pkl").writeString("import \"file2.pkl\"").toUri()
    val file2 = tempDir.resolve("file2.pkl").writeString("import \"file1.pkl\"").toUri()
    val result = simpleAnalyzer.importGraph(file1)
    assertThat(result.imports)
      .isEqualTo(
        mapOf(file1 to setOf(ImportGraph.Import(file2)), file2 to setOf(ImportGraph.Import(file1)))
      )
  }

  @Test
  fun `package imports`(@TempDir tempDir: Path) {
    val analyzer =
      Analyzer(
        StackFrameTransformers.defaultTransformer,
        false,
        SecurityManagers.defaultManager,
        listOf(ModuleKeyFactories.file, ModuleKeyFactories.standardLibrary, ModuleKeyFactories.pkg),
        tempDir.resolve("packages"),
        null,
        HttpClient.dummyClient(),
      )
    PackageServer.populateCacheDir(tempDir.resolve("packages"))
    val file1 =
      tempDir
        .resolve("file1.pkl")
        .writeString("import \"package://localhost:0/birds@0.5.0#/Bird.pkl\"")
        .toUri()
    val result = analyzer.importGraph(file1)
    assertThat(result.imports)
      .isEqualTo(
        mapOf(
          file1 to setOf(ImportGraph.Import(URI("package://localhost:0/birds@0.5.0#/Bird.pkl"))),
          URI("package://localhost:0/birds@0.5.0#/Bird.pkl") to
            setOf(ImportGraph.Import(URI("package://localhost:0/fruit@1.0.5#/Fruit.pkl"))),
          URI("package://localhost:0/fruit@1.0.5#/Fruit.pkl") to emptySet(),
        )
      )
  }

  @Test
  fun `project dependency imports`(@TempDir tempDir: Path) {
    tempDir
      .resolve("PklProject")
      .writeString(
        """
          amends "pkl:Project"

          dependencies {
            ["birds"] { uri = "package://localhost:0/birds@0.5.0" }
          }
        """
          .trimIndent()
      )
    tempDir
      .resolve("PklProject.deps.json")
      .writeString(
        """
          {
            "schemaVersion": 1,
            "resolvedDependencies": {
              "package://localhost:0/birds@0": {
                "type": "remote",
                "uri": "projectpackage://localhost:0/birds@0.5.0",
                "checksums": {
                  "sha256": "${'$'}skipChecksumVerification"
                }
              },
              "package://localhost:0/fruit@1": {
                "type": "remote",
                "uri": "projectpackage://localhost:0/fruit@1.0.5",
                "checksums": {
                  "sha256": "${'$'}skipChecksumVerification"
                }
              }
            }
          }
        """
          .trimIndent()
      )
    val project = Project.loadFromPath(tempDir.resolve("PklProject"))
    PackageServer.populateCacheDir(tempDir.resolve("packages"))
    val analyzer =
      Analyzer(
        StackFrameTransformers.defaultTransformer,
        false,
        SecurityManagers.defaultManager,
        listOf(
          ModuleKeyFactories.file,
          ModuleKeyFactories.standardLibrary,
          ModuleKeyFactories.pkg,
          ModuleKeyFactories.projectpackage,
        ),
        tempDir.resolve("packages"),
        project.dependencies,
        HttpClient.dummyClient(),
      )
    val file1 =
      tempDir
        .resolve("file1.pkl")
        .writeString(
          """
            import "@birds/Bird.pkl"
          """
            .trimIndent()
        )
        .toUri()
    val result = analyzer.importGraph(file1)
    assertThat(result.imports)
      .isEqualTo(
        mapOf(
          file1 to
            setOf(ImportGraph.Import(URI("projectpackage://localhost:0/birds@0.5.0#/Bird.pkl"))),
          URI("projectpackage://localhost:0/birds@0.5.0#/Bird.pkl") to
            setOf(ImportGraph.Import(URI("projectpackage://localhost:0/fruit@1.0.5#/Fruit.pkl"))),
          URI("projectpackage://localhost:0/fruit@1.0.5#/Fruit.pkl") to emptySet(),
        )
      )
    assertThat(result.resolvedImports)
      .isEqualTo(
        mapOf(
          file1 to file1.realPath(),
          URI("projectpackage://localhost:0/birds@0.5.0#/Bird.pkl") to
            URI("projectpackage://localhost:0/birds@0.5.0#/Bird.pkl"),
          URI("projectpackage://localhost:0/fruit@1.0.5#/Fruit.pkl") to
            URI("projectpackage://localhost:0/fruit@1.0.5#/Fruit.pkl"),
        )
      )
  }

  @Test
  fun `local project dependency import`(@TempDir tempDir: Path) {
    val pklProject =
      tempDir
        .resolve("project1/PklProject")
        .createParentDirectories()
        .writeString(
          """
            amends "pkl:Project"

            dependencies {
              ["birds"] = import("../birds/PklProject")
            }
          """
            .trimIndent()
        )

    tempDir
      .resolve("birds/PklProject")
      .createParentDirectories()
      .writeString(
        """
          amends "pkl:Project"

          package {
            name = "birds"
            version = "1.0.0"
            packageZipUrl = "https://localhost:0/foo.zip"
            baseUri = "package://localhost:0/birds"
          }
        """
          .trimIndent()
      )

    val birdModule = tempDir.resolve("birds/bird.pkl").writeString("name = \"Warbler\"")

    pklProject.parent
      .resolve("PklProject.deps.json")
      .writeString(
        """
          {
            "schemaVersion": 1,
            "resolvedDependencies": {
              "package://localhost:0/birds@1": {
                "type": "local",
                "uri": "projectpackage://localhost:0/birds@1.0.0",
                "path": "../birds"
              }
            }
          }
        """
          .trimIndent()
      )
    val mainPkl =
      pklProject.parent
        .resolve("main.pkl")
        .writeString(
          """
            import "@birds/bird.pkl"
          """
            .trimIndent()
        )

    val project = Project.loadFromPath(pklProject)
    val analyzer =
      Analyzer(
        StackFrameTransformers.defaultTransformer,
        false,
        SecurityManagers.defaultManager,
        listOf(
          ModuleKeyFactories.file,
          ModuleKeyFactories.standardLibrary,
          ModuleKeyFactories.pkg,
          ModuleKeyFactories.projectpackage,
        ),
        tempDir.resolve("packages"),
        project.dependencies,
        HttpClient.dummyClient(),
      )
    val result = analyzer.importGraph(mainPkl.toUri())
    val birdUri = URI("projectpackage://localhost:0/birds@1.0.0#/bird.pkl")
    assertThat(result.imports)
      .isEqualTo(
        mapOf(mainPkl.toUri() to setOf(ImportGraph.Import(birdUri)), birdUri to emptySet())
      )
    assertThat(result.resolvedImports)
      .isEqualTo(
        mapOf(
          mainPkl.toUri() to mainPkl.toRealPath().toUri(),
          birdUri to birdModule.toRealPath().toUri(),
        )
      )
  }

  private fun URI.realPath() = Path.of(this).toRealPath().toUri()
}
