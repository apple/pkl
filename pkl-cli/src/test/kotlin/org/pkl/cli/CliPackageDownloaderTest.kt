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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.core.packages.PackageUri

class CliPackageDownloaderTest {
  companion object {
    val server = PackageServer()

    @AfterAll
    @JvmStatic
    fun afterAll() {
      server.close()
    }
  }

  @Test
  fun `download packages`(@TempDir tempDir: Path) {
    val cmd =
      CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:0/birds@0.5.0"),
            PackageUri("package://localhost:0/fruit@1.0.5"),
            PackageUri("package://localhost:0/fruit@1.1.0")
          ),
        noTransitive = true
      )
    cmd.run()
    assertThat(tempDir.resolve("package-1/localhost:0/birds@0.5.0/birds@0.5.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/birds@0.5.0/birds@0.5.0.json")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/fruit@1.0.5/fruit@1.0.5.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/fruit@1.0.5/fruit@1.0.5.json")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/fruit@1.1.0/fruit@1.1.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/fruit@1.1.0/fruit@1.1.0.json")).exists()
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
      CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            workingDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris = listOf(PackageUri("package://localhost:0/birds@0.5.0")),
        noTransitive = true
      )
    cmd.run()
    assertThat(tempDir.resolve(".my-cache/package-1/localhost:0/birds@0.5.0/birds@0.5.0.zip"))
      .exists()
    assertThat(tempDir.resolve(".my-cache/package-1/localhost:0/birds@0.5.0/birds@0.5.0.json"))
      .exists()
  }

  @Test
  fun `download package while specifying checksum`(@TempDir tempDir: Path) {
    val cmd =
      CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:0/birds@0.5.0::sha256:${PackageServer.BIRDS_SHA}"),
          ),
        noTransitive = true
      )
    cmd.run()
    assertThat(tempDir.resolve("package-1/localhost:0/birds@0.5.0/birds@0.5.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/birds@0.5.0/birds@0.5.0.json")).exists()
  }

  @Test
  fun `download package with invalid checksum`(@TempDir tempDir: Path) {
    val cmd =
      CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:0/birds@0.5.0::sha256:intentionallyBogusChecksum"),
          ),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessage(
        """
      Cannot download package `package://localhost:0/birds@0.5.0` because the computed checksum for package metadata does not match the expected checksum.

      Computed checksum: "${PackageServer.BIRDS_SHA}"
      Expected checksum: "intentionallyBogusChecksum"
      Asset URL: "https://localhost:0/birds@0.5.0"
    """
          .trimIndent()
      )
  }

  @Test
  fun `disabling caching is an error`(@TempDir tempDir: Path) {
    val cmd =
      CliPackageDownloader(
        baseOptions = CliBaseOptions(workingDir = tempDir, noCache = true),
        packageUris = listOf(PackageUri("package://localhost:0/birds@0.5.0")),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessage("Cannot download packages because no cache directory is specified.")
  }

  @Test
  fun `download packages with bad checksum`(@TempDir tempDir: Path) {
    val cmd =
      CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris = listOf(PackageUri("package://localhost:0/badChecksum@1.0.0")),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessageStartingWith(
        "Cannot download package `package://localhost:0/badChecksum@1.0.0` because the computed checksum does not match the expected checksum."
      )
  }

  @Test
  fun `download multiple failing packages`(@TempDir tempDir: Path) {
    val cmd =
      CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris =
          listOf(
            PackageUri("package://localhost:0/badChecksum@1.0.0"),
            PackageUri("package://bogus.domain/notAPackage@1.0.0")
          ),
        noTransitive = true
      )
    assertThatCode { cmd.run() }
      .hasMessage(
        """
        Failed to download some packages.

        Failed to download package://localhost:0/badChecksum@1.0.0 because:
        Cannot download package `package://localhost:0/badChecksum@1.0.0` because the computed checksum does not match the expected checksum.

        Computed checksum: "a6bf858cdd1c09da475c2abe50525902580910ee5cc1ff624999170591bf8f69"
        Expected checksum: "intentionally bogus checksum"
        Asset URL: "https://localhost:0/badChecksum@1.0.0/badChecksum@1.0.0.zip"

        Failed to download package://bogus.domain/notAPackage@1.0.0 because:
        Exception when making request `GET https://bogus.domain/notAPackage@1.0.0`:
        Error connecting to host `bogus.domain`.

      """
          .trimIndent()
      )
  }

  @Test
  fun `download package, including transitive dependencies`(@TempDir tempDir: Path) {
    CliPackageDownloader(
        baseOptions =
          CliBaseOptions(
            moduleCacheDir = tempDir,
            caCertificates = listOf(FileTestUtils.selfSignedCertificate),
            testPort = server.port
          ),
        packageUris = listOf(PackageUri("package://localhost:0/birds@0.5.0")),
        noTransitive = false
      )
      .run()
    assertThat(tempDir.resolve("package-1/localhost:0/birds@0.5.0/birds@0.5.0.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/birds@0.5.0/birds@0.5.0.json")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/fruit@1.0.5/fruit@1.0.5.zip")).exists()
    assertThat(tempDir.resolve("package-1/localhost:0/fruit@1.0.5/fruit@1.0.5.json")).exists()
  }
}
