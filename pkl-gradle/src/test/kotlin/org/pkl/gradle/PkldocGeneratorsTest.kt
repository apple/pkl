package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.readText

class PkldocGeneratorsTest : AbstractTest() {
  @Test
  fun `generate docs`() {
    writeFile(
      "build.gradle", """
      plugins {
        id "com.apple.pkl"
      }

      pkl {
        pkldocGenerators {
          pkldoc {
            sourceModules = ["person.pkl", "doc-package-info.pkl"]
            outputDir = file("build/pkldoc")
            settingsModule = "pkl:settings"
          }
        }
      }
    """
    )
    writeFile(
      "doc-package-info.pkl", """
      /// A test package.
      amends "pkl:DocPackageInfo"
      name = "test"
      version = "1.0.0"
      importUri = "https://pkl-lang.org/"
      authors { "publisher@apple.com" }
      sourceCode = "sources.apple.com/"
      issueTracker = "issues.apple.com"
    """.trimIndent()
    )
    writeFile(
      "person.pkl", """
      module test.person

      class Person {
        name: String
        addresses: List<Address>
      }

      class Address {
        street: String
        zip: Int
      }

      other = 42
    """.trimIndent()
    )

    runTask("pkldoc")

    val baseDir = testProjectDir.resolve("build/pkldoc")
    val mainFile = baseDir.resolve("index.html")
    val packageFile = baseDir.resolve("test/1.0.0/index.html")
    val moduleFile = baseDir.resolve("test/1.0.0/person/index.html")
    val personFile = baseDir.resolve("test/1.0.0/person/Person.html")
    val addressFile = baseDir.resolve("test/1.0.0/person/Address.html")

    assertThat(mainFile).exists()
    assertThat(packageFile).exists()
    assertThat(moduleFile).exists()
    assertThat(personFile).exists()
    assertThat(addressFile).exists()

    checkTextContains(mainFile.readText(), "<html>", "test")
    checkTextContains(packageFile.readText(), "<html>", "test.person")
    checkTextContains(moduleFile.readText(), "<html>", "Person", "Address", "other")
    checkTextContains(personFile.readText(), "<html>", "name", "addresses")
    checkTextContains(addressFile.readText(), "<html>", "street", "zip")
  }

  @Test
  fun `no source modules`() {
    writeFile(
      "build.gradle", """
      plugins {
        id "com.apple.pkl"
      }

      pkl {
        pkldocGenerators {
          pkldoc {
          }
        }
      }
    """.trimIndent()
    )

    val result = runTask("pkldoc", true)
    assertThat(result.output).contains("No source modules specified.")
  }
}
