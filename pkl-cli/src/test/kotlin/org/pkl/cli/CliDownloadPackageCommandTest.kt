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

import java.nio.file.Path
import kotlin.test.Ignore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.core.packages.PackageUri

class CliDownloadPackageCommandTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      PackageServer.ensureStarted()
    }
  }

  @Test
  fun `download packages`(@TempDir tempDir: Path) {
    val cmd =
      CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:12110/birds@0.5.0"),
            PackageUri("package://localhost:12110/fruit@1.0.5"),
            PackageUri("package://localhost:12110/fruit@1.1.0")
          ),
        noTransitive = true
      )
    cmd.run()
    assertThat(tempDir.resolve("package-1/localhost:12110/birds@0.5.0/birds@0.5.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/birds@0.5.0/birds@0.5.0.json")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/fruit@1.0.5/fruit@1.0.5.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/fruit@1.0.5/fruit@1.0.5.json")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/fruit@1.1.0/fruit@1.1.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/fruit@1.1.0/fruit@1.1.0.json")).exists()
  }

  @Test
  fun `download packages with cache dir set by project`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "PklProject",
      """
      amends "pkl:Project"
      
      evaluatorSettings {
        moduleCacheDir = ".my-cache"
      }
    """
        .trimIndent()
    )

    val cmd =
      CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            workingDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris = listOf(PackageUri("package://localhost:12110/birds@0.5.0")),
        noTransitive = true
      )
    cmd.run()
    assertThat(tempDir.resolve(".my-cache/package-1/localhost:12110/birds@0.5.0/birds@0.5.0.zip"))
      .exists()
    assertThat(tempDir.resolve(".my-cache/package-1/localhost:12110/birds@0.5.0/birds@0.5.0.json"))
      .exists()
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `download package while specifying checksum`(@TempDir tempDir: Path) {
    val cmd =
      CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris =
          listOf(
            PackageUri(
              "package://localhost:12110/birds@0.5.0::sha256:3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            ),
          ),
        noTransitive = true
      )
    cmd.run()
    assertThat(tempDir.resolve("package-1/localhost:12110/birds@0.5.0/birds@0.5.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/birds@0.5.0/birds@0.5.0.json")).exists()
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `download package with invalid checksum`(@TempDir tempDir: Path) {
    val cmd =
      CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:12110/birds@0.5.0::sha256:intentionallyBogusChecksum"),
          ),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessage(
        """
      Cannot download package `package://localhost:12110/birds@0.5.0` because the computed checksum for package metadata does not match the expected checksum.

      Computed checksum: "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
      Expected checksum: "intentionallyBogusChecksum"
      Asset URL: "https://localhost:12110/birds@0.5.0"
    """
          .trimIndent()
      )
  }

  @Test
  fun `disabling caching is an error`(@TempDir tempDir: Path) {
    val cmd =
      CliDownloadPackageCommand(
        baseOptions = CliBaseOptions(workingDir = tempDir, noCache = true),
        packageUris = listOf(PackageUri("package://localhost:12110/birds@0.5.0")),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessage("Cannot download packages because no cache directory is specified.")
  }

  @Test
  fun `download packages with bad checksum`(@TempDir tempDir: Path) {
    val cmd =
      CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris = listOf(PackageUri("package://localhost:12110/badChecksum@1.0.0")),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessageStartingWith(
        "Cannot download package `package://localhost:12110/badChecksum@1.0.0` because the computed checksum does not match the expected checksum."
      )
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `download multiple failing packages`(@TempDir tempDir: Path) {
    val cmd =
      CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:12110/badChecksum@1.0.0"),
            PackageUri("package://bogus.domain/notAPackage@1.0.0")
          ),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessage(
        """
        Failed to download some packages.

        Failed to download package://localhost:12110/badChecksum@1.0.0 because:
        Cannot download package `package://localhost:12110/badChecksum@1.0.0` because the computed checksum does not match the expected checksum.

        Computed checksum: "0ec8a501e974802d0b71b8d58141e1e6eaa10bc2033e18200be3a978823d98aa"
        Expected checksum: "intentionally bogus checksum"
        Asset URL: "https://localhost:12110/badChecksum@1.0.0/badChecksum@1.0.0.zip"

        Failed to download package://bogus.domain/notAPackage@1.0.0 because:
        Exception when making request `GET https://bogus.domain/notAPackage@1.0.0`:
        bogus.domain

      """
          .trimIndent()
      )
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `download package, including transitive dependencies`(@TempDir tempDir: Path) {
    CliDownloadPackageCommand(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate)
          ),
        packageUris = listOf(PackageUri("package://localhost:12110/birds@0.5.0")),
        noTransitive = false
      )
      .run()
    assertThat(tempDir.resolve("package-1/localhost:12110/birds@0.5.0/birds@0.5.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/birds@0.5.0/birds@0.5.0.json")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/fruit@1.0.5/fruit@1.0.5.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:12110/fruit@1.0.5/fruit@1.0.5.json")).exists()
  }
}
