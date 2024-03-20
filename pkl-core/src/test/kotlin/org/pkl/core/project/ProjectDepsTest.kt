package org.pkl.core.project

import org.pkl.core.packages.Checksums
import org.pkl.core.packages.Dependency
import org.pkl.core.packages.PackageUri
import org.pkl.core.util.EconomicMaps
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path

class ProjectDepsTest {
  private val projectDepsStr = """
        {
          "schemaVersion": 1,
          "resolvedDependencies": {
            "package://localhost:0/birds@0": {
              "type": "remote",
              "uri": "package://localhost:0/birds@0.5.0",
              "checksums": {
                "sha256": "abc123"
              }
            },
            "package://localhost:0/fruit@1": {
              "type": "local",
              "uri": "package://localhost:0/fruit@1.1.0",
              "path": "../fruit"
            }
          }
        }
      """.trimIndent()

  private val projectDeps = let {
    val projectDepsMap = EconomicMaps.of<CanonicalPackageUri, Dependency>(
      CanonicalPackageUri.of("package://localhost:0/birds@0"), Dependency.RemoteDependency(
        PackageUri.create("package://localhost:0/birds@0.5.0"),
        Checksums("abc123")
      ),
      CanonicalPackageUri.of("package://localhost:0/fruit@1"), Dependency.LocalDependency(
        PackageUri.create("package://localhost:0/fruit@1.1.0"),
        Path.of("../fruit")
      )
    )
    ProjectDeps(projectDepsMap)
  }

  @Test
  fun writeTo() {
    val str = ByteArrayOutputStream()
      .apply { projectDeps.writeTo(this) }
      .toString(Charsets.UTF_8)
    assertThat(str).isEqualTo(projectDepsStr)
  }

  @Test
  fun parse() {
    val parsedProjectDeps = ProjectDeps.parse(projectDepsStr)
    assertThat(parsedProjectDeps).isEqualTo(projectDeps)
  }
}
