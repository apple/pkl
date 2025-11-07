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
package org.pkl.gradle

import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinCodeGeneratorsTest : AbstractTest() {
  @Test
  fun `generate code`() {
    writeBuildFile()
    writePklFile()

    runTask("configClasses")

    val baseDir = testProjectDir.resolve("build/generated/kotlin/foo/bar")
    val kotlinFile = baseDir.resolve("Mod.kt")

    assertThat(baseDir.listDirectoryEntries().size).isEqualTo(1)
    assertThat(kotlinFile).exists()

    val text = kotlinFile.readText()

    // shading must not affect generated code
    assertThat(text).doesNotContain("org.pkl.thirdparty")

    checkTextContains(
      text,
      """
      |data class Mod(
      |  val other: Any?
      |)
    """,
    )

    checkTextContains(
      text,
      """
      |  data class Person(
      |    val name: String,
      |    val addresses: List<Address>
      |  )
    """,
    )

    checkTextContains(
      text,
      """
      |  open class Address(
      |    open val street: String,
      |    open val zip: Long
      |  )
    """,
    )
  }

  @Test
  fun `compile generated code`() {
    writeBuildFile()
    writePklFile()
    runTask("compileKotlin")

    val classesDir = testProjectDir.resolve("build/classes/kotlin/main")
    val moduleClassFile = classesDir.resolve("foo/bar/Mod.class")
    val personClassFile = classesDir.resolve("foo/bar/Mod\$Person.class")
    val addressClassFile = classesDir.resolve("foo/bar/Mod\$Address.class")
    assertThat(moduleClassFile).exists()
    assertThat(personClassFile).exists()
    assertThat(addressClassFile).exists()
  }

  @Test
  fun `no source modules`() {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        evaluators {
          evalTest {
            outputFormat = "pcf"
          }
        }
      }
    """,
    )

    val result = runTask("evalTest", true)
    assertThat(result.output).contains("No source modules specified.")
  }

  private fun writeBuildFile() {
    val kotlinVersion = "2.0.21"

    writeFile(
      "build.gradle",
      """
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
        id "org.pkl-lang"
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
            renames = [
              'org.': 'foo.bar.'
            ]
          }
        }
      }
    """,
    )
  }

  private fun writePklFile() {
    writeFile(
      "mod.pkl",
      """
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
    """,
    )
  }
}
