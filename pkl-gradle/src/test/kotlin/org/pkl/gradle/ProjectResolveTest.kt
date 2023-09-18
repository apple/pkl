package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectResolveTest : AbstractTest() {
  @Test
  fun basic() {
    writeBuildFile()
    writeProjectContent()
    runTask("resolveMyProj")
    assertThat(testProjectDir.resolve("proj1/PklProject.deps.json")).hasContent("""
      {
        "schemaVersion": 1,
        "resolvedDependencies": {}
      }
    """.trimIndent())
  }

  private fun writeBuildFile(additionalContents: String = "") {
    writeFile(
      "build.gradle", """
      plugins {
        id "org.pkl"
      }

      pkl {
        project {
          resolvers {
            resolveMyProj {
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
    writeFile("proj1/PklProject", """
      amends "pkl:Project"
    """.trimIndent())
  }
}
