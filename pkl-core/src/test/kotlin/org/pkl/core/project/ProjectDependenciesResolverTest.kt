package org.pkl.core.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.core.http.HttpClient
import org.pkl.core.PklException
import org.pkl.core.SecurityManagers
import org.pkl.core.packages.PackageResolver
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

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
        .testPort(packageServer.port)
        .build()
    }
  }

  @Test
  fun resolveDependencies() {
    val project2Path = Path.of(javaClass.getResource("project2/PklProject")!!.path)
    val project = Project.loadFromPath(project2Path)
    val packageResolver = PackageResolver.getInstance(SecurityManagers.defaultManager, httpClient, null)
    val deps = ProjectDependenciesResolver(project, packageResolver, System.out.writer()).resolve()
    val strDeps = ByteArrayOutputStream()
      .apply { deps.writeTo(this) }
      .toByteArray()
      .toString(StandardCharsets.UTF_8)
    assertThat(strDeps).isEqualTo("""
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:12110/birds@0": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "0a5ad2dc13f06f73f96ba94e8d01d48252bc934e2de71a837620ca0fef8a7453"
            }
          },
          "package://localhost:12110/fruit@1": {
            "type": "remote",
            "uri": "projectpackage://localhost:12110/fruit@1.1.0",
            "checksums": {
              "sha256": "a82e92e0c259591111d09d18a14f5ad66e2b6e13d827ee3e6f7ce06f5d0fbe0c"
            }
          },
          "package://localhost:12110/project3@1": {
            "type": "local",
            "uri": "projectpackage://localhost:12110/project3@1.5.0",
            "path": "../project3"
          }
        }
      }
    """.trimIndent())
  }

  @Test
  fun `fails if project declares a package with an incorrect checksum`() {
    val projectPath = Path.of(javaClass.getResource("badProjectChecksum/PklProject")!!.path)
    val project = Project.loadFromPath(projectPath)
    val packageResolver = PackageResolver.getInstance(SecurityManagers.defaultManager, httpClient, null)
    val e = assertThrows<PklException> {
      ProjectDependenciesResolver(project, packageResolver, System.err.writer()).resolve()
    }
    assertThat(e).hasMessage("""
      Computed checksum did not match declared checksum for dependency `package://localhost:12110/birds@0.5.0`.

      Computed: "0a5ad2dc13f06f73f96ba94e8d01d48252bc934e2de71a837620ca0fef8a7453"
      Declared: "intentionally bogus value"
    """.trimIndent())
  }
}
