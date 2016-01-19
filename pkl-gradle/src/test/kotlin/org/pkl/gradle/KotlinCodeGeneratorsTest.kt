package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class KotlinCodeGeneratorsTest : AbstractTest() {
  @Test
  fun `generate code`() {
    writeBuildFile()
    writePklFile()

    runTask("configClasses")

    val baseDir = testProjectDir.resolve("build/generated/kotlin/org")
    val kotlinFile = baseDir.resolve("Mod.kt")

    assertThat(baseDir.listDirectoryEntries().count()).isEqualTo(1)
    assertThat(kotlinFile).exists()

    val text = kotlinFile.readText()

    // shading must not affect generated code
    assertThat(text).doesNotContain("org.pkl.thirdparty")

    checkTextContains(
      text, """
      |data class Mod(
      |  val other: Any?
      |)
    """
    )

    checkTextContains(
      text, """
      |  data class Person(
      |    val name: String,
      |    val addresses: List<Address>
      |  )
    """
    )

    checkTextContains(
      text, """
      |  open class Address(
      |    val street: String,
      |    val zip: Long
      |  )
    """
    )
  }

  @Test
  fun `compile generated code`() {
    writeBuildFile()
    writePklFile()
    runTask("compileKotlin")

    val classesDir = testProjectDir.resolve("build/classes/kotlin/main")
    val moduleClassFile = classesDir.resolve("org/Mod.class")
    val personClassFile = classesDir.resolve("org/Mod\$Person.class")
    val addressClassFile = classesDir.resolve("org/Mod\$Address.class")
    assertThat(moduleClassFile).exists()
    assertThat(personClassFile).exists()
    assertThat(addressClassFile).exists()
  }
  
  @Test
  fun `no source modules`() {
    writeFile(
      "build.gradle", """
      plugins {
        id "com.apple.pkl"
      }

      pkl {
        evaluators {
          evalTest {
            outputFormat = "pcf"
          }
        }
      }
    """
    )

    val result = runTask("evalTest", true)
    assertThat(result.output).contains("No source modules specified.")
  }

  private fun writeBuildFile() {
    val kotlinVersion = "1.6.0"

    writeFile(
      "build.gradle", """
      buildscript {
        repositories {
          mavenCentral()
        }

        dependencies {
          classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion") {
            exclude module: "kotlin-android-extensions"
          }
        }
      }

      plugins {
        id "com.apple.pkl"
      }

      apply plugin: "kotlin"

      repositories {
        mavenCentral()
      }

      dependencies {
        implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"
      }

      pkl {
        kotlinCodeGenerators {
          configClasses {
            sourceModules = ["mod.pkl"]
            outputDir = file("build/generated")
            settingsModule = "pkl:settings"
          }
        }
      }
    """
    )
  }
  
  private fun writePklFile() {
    writeFile(
      "mod.pkl", """
      module org.mod

      class Person {
        name: String
        addresses: List<Address>
      }

      // "open" to test generating regular class
      open class Address {
        street: String
        zip: Int
      }

      other = 42
    """
    )
  }
}
