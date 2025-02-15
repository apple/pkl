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
package org.pkl.gradle

import java.nio.file.Path
import kotlin.io.path.readText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.test.PackageServer

class PkldocGeneratorsTest : AbstractTest() {
  @Test
  fun `generate docs`(@TempDir tempDir: Path) {
    PackageServer.populateCacheDir(tempDir)
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        pkldocGenerators {
          pkldoc {
            moduleCacheDir = file("${tempDir.toUri()}")
            sourceModules = ["package://localhost:0/birds@0.5.0", "person.pkl", "doc-package-info.pkl"]
            outputDir = file("build/pkldoc")
            settingsModule = "pkl:settings"
          }
        }
      }
    """,
    )
    writeFile(
      "doc-package-info.pkl",
      """
      /// A test package.
      amends "pkl:DocPackageInfo"
      name = "test"
      version = "1.0.0"
      importUri = "https://pkl-lang.org/"
      authors { "publisher@apple.com" }
      sourceCode = "sources.apple.com/"
      issueTracker = "issues.apple.com"
    """
        .trimIndent(),
    )
    writeFile(
      "person.pkl",
      """
      module test.person

      class Person {
        name: String
        addresses: List<Address>
      }

      class Address {
        street: String
        zip: Int
      }

      other = 42
    """
        .trimIndent(),
    )

    runTask("pkldoc")

    val baseDir = testProjectDir.resolve("build/pkldoc")
    val mainFile = baseDir.resolve("index.html")
    val packageFile = baseDir.resolve("test/1.0.0/index.html")
    val moduleFile = baseDir.resolve("test/1.0.0/person/index.html")
    val personFile = baseDir.resolve("test/1.0.0/person/Person.html")
    val addressFile = baseDir.resolve("test/1.0.0/person/Address.html")

    assertThat(mainFile).exists()
    assertThat(packageFile).exists()
    assertThat(moduleFile).exists()
    assertThat(personFile).exists()
    assertThat(addressFile).exists()

    checkTextContains(mainFile.readText(), "<html>", "test")
    checkTextContains(packageFile.readText(), "<html>", "test.person")
    checkTextContains(moduleFile.readText(), "<html>", "Person", "Address", "other")
    checkTextContains(personFile.readText(), "<html>", "name", "addresses")
    checkTextContains(addressFile.readText(), "<html>", "street", "zip")

    val birdsPackageFile = baseDir.resolve("localhost(3a)0/birds/0.5.0/index.html")
    assertThat(birdsPackageFile).exists()
  }

  @Test
  fun `generate docs only for package`(@TempDir tempDir: Path) {
    PackageServer.populateCacheDir(tempDir)
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        pkldocGenerators {
          pkldoc {
            moduleCacheDir = file("${tempDir.toUri()}")
            sourceModules = ["package://localhost:0/birds@0.5.0"]
            outputDir = file("build/pkldoc")
            settingsModule = "pkl:settings"
          }
        }
      }
    """,
    )

    runTask("pkldoc")

    val baseDir = testProjectDir.resolve("build/pkldoc")
    val birdsPackageFile = baseDir.resolve("localhost(3a)0/birds/0.5.0/index.html")
    assertThat(birdsPackageFile).exists()
  }

  @Test
  fun `no source modules`() {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        pkldocGenerators {
          pkldoc {
          }
        }
      }
    """
        .trimIndent(),
    )

    val result = runTask("pkldoc", true)
    assertThat(result.output).contains("No source modules specified.")
  }
}
