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
package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectPackageTest : AbstractTest() {
  @Test
  fun basic() {
    writeBuildFile("skipPublishCheck.set(true)")
    writeProjectContent()
    runTask("createMyPackages")
    assertThat(testProjectDir.resolve("build/generated/pkl/packages/proj1@1.0.0.zip")).exists()
    assertThat(testProjectDir.resolve("build/generated/pkl/packages/proj1@1.0.0")).exists()
  }

  @Test
  fun `custom output dir`() {
    writeBuildFile(
      """
      outputPath.set(file("thepackages"))
      skipPublishCheck.set(true)
    """
    )
    writeProjectContent()
    runTask("createMyPackages")
    assertThat(testProjectDir.resolve("thepackages/proj1@1.0.0.zip")).exists()
    assertThat(testProjectDir.resolve("thepackages/proj1@1.0.0")).exists()
  }

  @Test
  fun `junit dir`() {
    writeBuildFile(
      """
      junitReportsDir.set(file("test-reports"))
      skipPublishCheck.set(true)
    """
        .trimIndent()
    )
    writeProjectContent()
    runTask("createMyPackages")
    assertThat(testProjectDir.resolve("test-reports")).isNotEmptyDirectory()
  }

  private fun writeBuildFile(additionalContents: String = "") {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        project {
          packagers {
            createMyPackages {
              projectDirectories.from(file("proj1"))
              settingsModule = "pkl:settings"
              $additionalContents
            }
          }
        }
      }
    """
    )
  }

  private fun writeProjectContent() {
    writeFile(
      "proj1/PklProject",
      """
      amends "pkl:Project"
      
      package {
        name = "proj1"
        baseUri = "package://localhost:0/proj1"
        version = "1.0.0"
        packageZipUrl = "https://localhost:0/proj1@\(version).zip"
        apiTests {
          "tests.pkl"
        }
      }
    """
        .trimIndent()
    )
    writeFile(
      "proj1/PklProject.deps.json",
      """
      {
        "schemaVersion": 1,
        "dependencies": {}
      }
    """
        .trimIndent()
    )
    writeFile(
      "proj1/foo.pkl",
      """
      module proj1.foo
      
      bar: String
    """
        .trimIndent()
    )
    writeFile(
      "proj1/tests.pkl",
      """
      amends "pkl:test"
      
      facts {
        ["it works"] {
          1 == 1
        }
      }
    """
        .trimIndent()
    )
    writeFile("foo.txt", "The contents of foo.txt")
  }
}
