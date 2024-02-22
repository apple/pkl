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
package org.pkl.cli

import java.io.StringWriter
import java.nio.file.Path
import kotlin.test.Ignore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer

class CliProjectResolverTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      PackageServer.ensureStarted()
    }
  }

  @Test
  fun `missing PklProject when inferring a project dir`(@TempDir tempDir: Path) {
    val packager =
      CliProjectResolver(
        CliBaseOptions(workingDir = tempDir),
        emptyList(),
        consoleWriter = StringWriter(),
        errWriter = StringWriter()
      )
    val err = assertThrows<CliException> { packager.run() }
    assertThat(err).hasMessageStartingWith("No project visible to the working directory.")
  }

  @Test
  fun `missing PklProject when explicit dir is provided`(@TempDir tempDir: Path) {
    val packager =
      CliProjectResolver(
        CliBaseOptions(),
        listOf(tempDir),
        consoleWriter = StringWriter(),
        errWriter = StringWriter()
      )
    val err = assertThrows<CliException> { packager.run() }
    assertThat(err).hasMessageStartingWith("Directory $tempDir does not contain a PklProject file.")
  }

  @Test
  @Ignore("sgammon: Checksum failures")
  fun `basic project`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"

        dependencies {
          ["birds"] {
            uri = "package://localhost:12110/birds@0.5.0"
          }
        }
      """
        .trimIndent()
    )
    CliProjectResolver(
        CliBaseOptions(
          workingDir = tempDir,
          caCertificates = listOf(FileTestUtils.selfSignedCertificate)
        ),
        listOf(tempDir),
        consoleWriter = StringWriter(),
        errWriter = StringWriter()
      )
      .run()
    val expectedOutput = tempDir.resolve("PklProject.deps.json")
    assertThat(expectedOutput)
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.0.5",
            "checksums": {
              "sha256": "b4ea243de781feeab7921227591e6584db5d0673340f30fab2ffe8ad5c9f75f5"
            }
          }
        }
      }
    """
          .trimIndent()
      )
  }

  @Test
  @Ignore("sgammon: Checksum failures")
  fun `basic project, inferred from working dir`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"

        dependencies {
          ["birds"] {
            uri = "package://localhost:12110/birds@0.5.0"
          }
        }
      """
        .trimIndent()
    )
    CliProjectResolver(
        CliBaseOptions(
          workingDir = tempDir,
          caCertificates = listOf(FileTestUtils.selfSignedCertificate)
        ),
        emptyList(),
        consoleWriter = StringWriter(),
        errWriter = StringWriter()
      )
      .run()
    val expectedOutput = tempDir.resolve("PklProject.deps.json")
    assertThat(expectedOutput)
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.0.5",
            "checksums": {
              "sha256": "b4ea243de781feeab7921227591e6584db5d0673340f30fab2ffe8ad5c9f75f5"
            }
          }
        }
      }
    """
          .trimIndent()
      )
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `local dependencies`(@TempDir tempDir: Path) {
    val projectDir = tempDir.resolve("theproject")
    projectDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"

        dependencies {
          ["birds"] {
            uri = "package://localhost:12110/birds@0.5.0"
          }
          ["project2"] = import("../project2/PklProject")
        }
      """
        .trimIndent()
    )
    projectDir.writeFile(
      "../project2/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "project2"
          baseUri = "package://localhost:12110/package2"
          version = "5.0.0"
          packageZipUrl = "https://foo.com/package2.zip"
        }
        
        dependencies {
          ["fruit"] {
            uri = "package://localhost:12110/fruit@1.0.5"
          }
          ["project3"] = import("../project3/PklProject")
        }
      """
        .trimIndent()
    )

    projectDir.writeFile(
      "../project3/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "project3"
          baseUri = "package://localhost:12110/package3"
          version = "5.0.0"
          packageZipUrl = "https://foo.com/package3.zip"
        }
        
        dependencies {
          ["fruit"] {
            uri = "package://localhost:12110/fruit@1.1.0"
          }
        }
      """
        .trimIndent()
    )
    CliProjectResolver(
        CliBaseOptions(caCertificates = listOf(FileTestUtils.selfSignedCertificate)),
        listOf(projectDir),
        consoleWriter = StringWriter(),
        errWriter = StringWriter()
      )
      .run()
    val expectedOutput = projectDir.resolve("PklProject.deps.json")
    assertThat(expectedOutput)
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.1.0",
            "checksums": {
              "sha256": "98ad9fc407a79dc3fd5595e7a29c3803ade0a6957c18ec94b8a1624360b24f01"
            }
          },
          "package://localhost:12110/package2@5": {
            "type": "local",
            "uri": "projectpackage://localhost:12110/package2@5.0.0",
            "path": "../project2"
          },
          "package://localhost:12110/package3@5": {
            "type": "local",
            "uri": "projectpackage://localhost:12110/package3@5.0.0",
            "path": "../project3"
          }
        }
      }
    """
          .trimIndent()
      )
  }

  @Test
  @Ignore("sgammon: Checksum failures")
  fun `local dependency overridden by remote dependency`(@TempDir tempDir: Path) {
    val projectDir = tempDir.resolve("theproject")
    projectDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"

        dependencies {
          ["birds"] {
            uri = "package://localhost:12110/birds@0.5.0"
          }
          ["fruit"] = import("../fruit/PklProject")
        }
      """
        .trimIndent()
    )
    projectDir.writeFile(
      "../fruit/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "fruit"
          baseUri = "package://localhost:12110/fruit"
          version = "1.0.0"
          packageZipUrl = "https://foo.com/fruit.zip"
        }
      """
        .trimIndent()
    )
    val consoleOut = StringWriter()
    val errOut = StringWriter()
    CliProjectResolver(
        CliBaseOptions(caCertificates = listOf(FileTestUtils.selfSignedCertificate)),
        listOf(projectDir),
        consoleWriter = consoleOut,
        errWriter = errOut
      )
      .run()
    val expectedOutput = projectDir.resolve("PklProject.deps.json")
    assertThat(expectedOutput)
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.0.5",
            "checksums": {
              "sha256": "b4ea243de781feeab7921227591e6584db5d0673340f30fab2ffe8ad5c9f75f5"
            }
          }
        }
      }
    """
          .trimIndent()
      )
    assertThat(errOut.toString())
      .isEqualTo(
        "WARN: local dependency `package://localhost:12110/fruit@1.0.0` was overridden to remote dependency `package://localhost:12110/fruit@1.0.5`.\n"
      )
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `resolving multiple projects`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "project1/PklProject",
      """
      amends "pkl:Project"

      dependencies {
        ["birds"] {
          uri = "package://localhost:12110/birds@0.5.0"
        }
      }
    """
        .trimIndent()
    )

    tempDir.writeFile(
      "project2/PklProject",
      """
      amends "pkl:Project"

      dependencies {
        ["fruit"] {
          uri = "package://localhost:12110/fruit@1.1.0"
        }
      }
    """
        .trimIndent()
    )

    val consoleOut = StringWriter()
    val errOut = StringWriter()
    CliProjectResolver(
        CliBaseOptions(caCertificates = listOf(FileTestUtils.selfSignedCertificate)),
        listOf(tempDir.resolve("project1"), tempDir.resolve("project2")),
        consoleWriter = consoleOut,
        errWriter = errOut
      )
      .run()
    assertThat(consoleOut.toString())
      .isEqualTo(
        """
      $tempDir/project1/PklProject.deps.json
      $tempDir/project2/PklProject.deps.json
    
    """
          .trimIndent()
      )
    assertThat(tempDir.resolve("project1/PklProject.deps.json"))
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.0.5",
            "checksums": {
              "sha256": "b4ea243de781feeab7921227591e6584db5d0673340f30fab2ffe8ad5c9f75f5"
            }
          }
        }
      }
    """
          .trimIndent()
      )
    assertThat(tempDir.resolve("project2/PklProject.deps.json"))
      .hasContent(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.1.0",
            "checksums": {
              "sha256": "98ad9fc407a79dc3fd5595e7a29c3803ade0a6957c18ec94b8a1624360b24f01"
            }
          }
        }
      }
    """
          .trimIndent()
      )
  }
}
