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
package org.pkl.core.packages

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.*
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.pkl.commons.readString
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.test.listFilesRecursively
import org.pkl.core.SecurityManagers
import org.pkl.core.http.HttpClient
import org.pkl.core.module.PathElement

class PackageResolversTest {
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  abstract class AbstractPackageResolverTest {
    abstract val resolver: PackageResolver

    private val packageRoot =
      FileTestUtils.rootProjectDir.resolve("pkl-commons-test/src/main/files/packages")

    // Each subclass gets its own PackageServer instance (instance property, Lifecycle.PER_CLASS).
    // This is important because closePackageServer() is called once per subclass.
    private val packageServer = PackageServer()

    val httpClient: HttpClient by lazy {
      HttpClient.builder()
        .addCertificates(FileTestUtils.selfSignedCertificate)
        .setTestPort(packageServer.port)
        .build()
    }

    @AfterAll
    fun closePackageServer() {
      packageServer.close()
    }

    // execute test 3 times to check concurrent writes
    @RepeatedTest(3)
    @Execution(ExecutionMode.CONCURRENT)
    fun `get module bytes`() {
      val expectedBirdModule =
        packageRoot.resolve("birds@0.5.0/package/Bird.pkl").readString(StandardCharsets.UTF_8)
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/Bird.pkl")
      val birdModule = resolver.getBytes(assetUri, false, null).toString(StandardCharsets.UTF_8)
      assertThat(birdModule).isEqualTo(expectedBirdModule)
    }

    @Test
    fun `get directory`() {
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/")
      val err =
        assertThrows<IOException> {
          resolver.getBytes(assetUri, false, null).toString(StandardCharsets.UTF_8)
        }
      assertThat(err).hasMessage("Is a directory")
    }

    @Test
    fun `get directory, allowing directory reads`() {
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/")
      val bytes = resolver.getBytes(assetUri, true, null).toString(StandardCharsets.UTF_8)
      assertThat(bytes)
        .isEqualTo(
          """
        Bird.pkl
        allFruit.pkl
        catalog
        catalog.pkl
        some

      """
            .trimIndent()
        )
    }

    @Test
    fun `get module bytes resolving path`() {
      val expectedBirdModule =
        packageRoot.resolve("birds@0.5.0/package/Bird.pkl").readString(StandardCharsets.UTF_8)
      val assetUri = PackageAssetUri("package://localhost:0/birds@0.5.0#/foo/../Bird.pkl")
      val birdModule = resolver.getBytes(assetUri, false, null).toString(StandardCharsets.UTF_8)
      assertThat(birdModule).isEqualTo(expectedBirdModule)
    }

    @Test
    fun `list path elements at root`() {
      // cast to set to avoid sort issues
      val elements =
        resolver.listElements(PackageAssetUri("package://localhost:0/birds@0.5.0#/"), null).toSet()
      assertThat(elements)
        .isEqualTo(
          setOf(
            PathElement("some", true),
            PathElement("catalog", true),
            PathElement("Bird.pkl", false),
            PathElement("allFruit.pkl", false),
            PathElement("catalog.pkl", false),
          )
        )
    }

    @Test
    fun `get multiple assets`() {
      val bird =
        resolver.getBytes(
          PackageAssetUri("package://localhost:0/birds@0.5.0#/Bird.pkl"),
          false,
          null,
        )
      val swallow =
        resolver.getBytes(
          PackageAssetUri("package://localhost:0/birds@0.5.0#/catalog/Swallow.pkl"),
          false,
          null,
        )
      assertThat(bird).isEqualTo(packageRoot.resolve("birds@0.5.0/package/Bird.pkl").readBytes())
      assertThat(swallow)
        .isEqualTo(packageRoot.resolve("birds@0.5.0/package/catalog/Swallow.pkl").readBytes())
    }

    @Test
    fun `list path elements in nested directory`() {
      // cast to set to avoid sort issues
      val elements =
        resolver
          .listElements(PackageAssetUri("package://localhost:0/birds@0.5.0#/catalog/"), null)
          .toSet()
      assertThat(elements)
        .isEqualTo(setOf(PathElement("Ostrich.pkl", false), PathElement("Swallow.pkl", false)))
    }

    @Test
    fun `getBytes() throws FileNotFound if package exists but path does not`() {
      assertThrows<FileNotFoundException> {
        resolver
          .getBytes(PackageAssetUri("package://localhost:0/birds@0.5.0#/Horse.pkl"), false, null)
          .toString(StandardCharsets.UTF_8)
      }
    }

    @Test
    fun `getBytes() throws PackageLoadError if package does not exist`() {
      assertThrows<PackageLoadError> {
        resolver
          .getBytes(
            PackageAssetUri("package://localhost:0/not-a-package@0.5.0#/Horse.pkl"),
            false,
            null,
          )
          .toString(StandardCharsets.UTF_8)
      }
    }

    @Test
    fun `requires package zip to be an HTTPS URI`() {
      assertThatCode {
          resolver.getBytes(
            PackageAssetUri("package://localhost:0/badPackageZipUrl@1.0.0#/Bug.pkl"),
            false,
            null,
          )
        }
        .hasMessage(
          "Expected the zip asset for package `package://localhost:0/badPackageZipUrl@1.0.0` to be an HTTPS URI, but got `ftp://wait/a/minute`."
        )
    }

    @Test
    fun `throws if package checksum is invalid`() {
      val error =
        assertThrows<PackageLoadError> {
          resolver.getBytes(
            PackageAssetUri("package://localhost:0/badChecksum@1.0.0#/Bug.pkl"),
            false,
            null,
          )
        }
      assertThat(error)
        .hasMessageContaining(
          """
        Computed checksum: "a6bf858cdd1c09da475c2abe50525902580910ee5cc1ff624999170591bf8f69"
        Expected checksum: "intentionally bogus checksum"
      """
            .trimIndent()
        )
    }
  }

  @ExperimentalPathApi
  class DiskCachedPackageResolverTest : AbstractPackageResolverTest() {
    private val cacheDir = FileTestUtils.rootProjectDir.resolve("pkl-core/build/test-cache")

    @BeforeAll
    fun deleteCacheDir() {
      cacheDir.deleteRecursively()
    }

    @AfterAll
    fun checkCacheDir() {
      assertThat(cacheDir.exists())
      assertThat(cacheDir.listFilesRecursively()).isNotEmpty
    }

    override val resolver: PackageResolver =
      PackageResolvers.DiskCachedPackageResolver(
        SecurityManagers.defaultManager,
        httpClient,
        cacheDir,
      )
  }

  class InMemoryPackageResolverTest : AbstractPackageResolverTest() {
    override val resolver: PackageResolver =
      PackageResolvers.InMemoryPackageResolver(SecurityManagers.defaultManager, httpClient)
  }
}
