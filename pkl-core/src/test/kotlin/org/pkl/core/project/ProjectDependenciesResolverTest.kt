/*
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
package org.pkl.core.project

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.toPath
import org.pkl.core.PklException
import org.pkl.core.SecurityManagers
import org.pkl.core.http.HttpClient
import org.pkl.core.packages.PackageResolver

class ProjectDependenciesResolverTest {
  companion object {
    private val packageServer = PackageServer()

    @JvmStatic
    @AfterAll
    fun afterAll() {
      packageServer.close()
    }

    val httpClient: HttpClient by lazy {
      HttpClient.builder()
        .addCertificates(FileTestUtils.selfSignedCertificate)
        .setTestPort(packageServer.port)
        .build()
    }
  }

  @Test
  fun resolveDependencies() {
    val project2Path = javaClass.getResource("project2/PklProject")!!.toURI().toPath()
    val project = Project.loadFromPath(project2Path)
    val packageResolver =
      PackageResolver.getInstance(SecurityManagers.defaultManager, httpClient, null)
    val deps = ProjectDependenciesResolver(project, packageResolver, System.out.writer()).resolve()
    val strDeps =
      ByteArrayOutputStream()
        .apply { deps.writeTo(this) }
        .toByteArray()
        .toString(StandardCharsets.UTF_8)
    assertThat(strDeps)
      .isEqualTo(
        """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:0/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:0/birds@0.5.0",
            "checksums": {
              "sha256": "${PackageServer.BIRDS_SHA}"
            }
          },
          "package://localhost:0/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:0/fruit@1.1.0",
            "checksums": {
              "sha256": "${PackageServer.FRUIT_1_1_SHA}"
            }
          },
          "package://localhost:0/project3@1": {
            "type": "local",
            "uri": "projectpackage://localhost:0/project3@1.5.0",
            "path": "../project3"
          }
        }
      }
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `fails if project declares a package with an incorrect checksum`() {
    val projectPath = javaClass.getResource("badProjectChecksum/PklProject")!!.toURI().toPath()
    val project = Project.loadFromPath(projectPath)
    val packageResolver =
      PackageResolver.getInstance(SecurityManagers.defaultManager, httpClient, null)
    val e =
      assertThrows<PklException> {
        ProjectDependenciesResolver(project, packageResolver, System.err.writer()).resolve()
      }
    assertThat(e)
      .hasMessage(
        """
      Computed checksum did not match declared checksum for dependency `package://localhost:0/birds@0.5.0`.

      Computed: "${PackageServer.BIRDS_SHA}"
      Declared: "intentionally bogus value"
    """
          .trimIndent()
      )
  }
}
