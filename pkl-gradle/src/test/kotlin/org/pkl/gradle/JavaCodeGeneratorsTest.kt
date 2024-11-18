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
package org.pkl.gradle

import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaCodeGeneratorsTest : AbstractTest() {
  @Test
  fun `generate code`() {
    writeBuildFile()
    writePklFile()

    runTask("configClasses")

    val baseDir = testProjectDir.resolve("build/generated/java/foo/bar")
    val moduleFile = baseDir.resolve("Mod.java")

    assertThat(baseDir.listDirectoryEntries().count()).isEqualTo(1)
    assertThat(moduleFile).exists()

    val text = moduleFile.readText()

    // shading must not affect generated code
    assertThat(text).doesNotContain("org.pkl.thirdparty")

    checkTextContains(
      text,
      """
      |public final class Mod {
      |  public final @Nonnull Object other;
    """
    )

    checkTextContains(
      text,
      """
      |  public static final class Person {
      |    public final @Nonnull String name;
      |
      |    public final @Nonnull List<Address> addresses;
    """
    )

    checkTextContains(
      text,
      """
      |  public static final class Address {
      |    public final @Nonnull String street;
      |
      |    public final long zip;
    """
    )
  }

  @Test
  fun `compile generated code`() {
    writeBuildFile()
    writePklFile()

    runTask("compileJava")

    val classesDir = testProjectDir.resolve("build/classes/java/main")
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
    """
    )

    val result = runTask("evalTest", true)
    assertThat(result.output).contains("No source modules specified.")
  }

  private fun writeBuildFile() {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "java"
        id "org.pkl-lang"
      }

      repositories {
        mavenCentral()
      }

      dependencies {
        implementation "javax.inject:javax.inject:1"
        implementation "com.google.code.findbugs:jsr305:3.0.2"
      }

      pkl {
        javaCodeGenerators {
          configClasses {
            sourceModules = ["mod.pkl"]
            outputDir = file("build/generated")
            paramsAnnotation = "javax.inject.Named"
            nonNullAnnotation = "javax.annotation.Nonnull"
            settingsModule = "pkl:settings"
            renames = [
              'org': 'foo.bar'
            ]
          }
        }
      }
    """
    )
  }

  private fun writePklFile() {
    writeFile(
      "mod.pkl",
      """
        module org.mod
  
        class Person {
          name: String
          addresses: List<Address?>
        }
  
        class Address {
          street: String
          zip: Int
        }
  
        other = 42
      """
    )
  }
}
