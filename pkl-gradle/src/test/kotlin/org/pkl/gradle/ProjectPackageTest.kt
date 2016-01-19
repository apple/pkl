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
    writeBuildFile( """
      outputPath.set(file("thepackages"))
      skipPublishCheck.set(true)
    """)
    writeProjectContent()
    runTask("createMyPackages")
    assertThat(testProjectDir.resolve("thepackages/proj1@1.0.0.zip")).exists()
    assertThat(testProjectDir.resolve("thepackages/proj1@1.0.0")).exists()
  }

  @Test
  fun `junit dir`() {
    writeBuildFile("""
      junitReportsDir.set(file("test-reports"))
      skipPublishCheck.set(true)
    """.trimIndent())
    writeProjectContent()
    runTask("createMyPackages")
    assertThat(testProjectDir.resolve("test-reports")).isNotEmptyDirectory()
  }

  private fun writeBuildFile(additionalContents: String = "") {
    writeFile(
      "build.gradle", """
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
    writeFile("proj1/PklProject", """
      amends "pkl:Project"
      
      package {
        name = "proj1"
        baseUri = "package://localhost:12110/proj1"
        version = "1.0.0"
        packageZipUrl = "https://localhost:12110/proj1@\(version).zip"
        apiTests {
          "tests.pkl"
        }
      }
    """.trimIndent())
    writeFile("proj1/PklProject.deps.json", """
      {
        "schemaVersion": 1,
        "dependencies": {}
      }
    """.trimIndent())
    writeFile("proj1/foo.pkl", """
      module proj1.foo
      
      bar: String
    """.trimIndent())
    writeFile("proj1/tests.pkl", """
      amends "pkl:test"
      
      facts {
        ["it works"] {
          1 == 1
        }
      }
    """.trimIndent())
    writeFile("foo.txt", "The contents of foo.txt")
  }
}
