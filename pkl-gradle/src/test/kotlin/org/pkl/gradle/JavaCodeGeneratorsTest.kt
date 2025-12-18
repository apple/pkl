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

class JavaCodeGeneratorsTest : AbstractTest() {
  @Test
  fun `generate records code`() {
    writeBuildFile(isGenerateRecords = true, isUseWithers = true)
    writePklFile()

    runTask("configClasses")

    val commonCodeDir =
      testProjectDir.resolve("build/generated/java/org/pkl/codegen/java/common/code")
    val witherFile = commonCodeDir.resolve("Wither.java")

    assertThat(commonCodeDir.listDirectoryEntries().size).isEqualTo(1)
    assertThat(witherFile).exists()

    val witherText = witherFile.readText()

    assertThat(witherText)
      .isEqualToIgnoringWhitespace(
        """
          package org.pkl.codegen.java.common.code;
          
          import java.lang.Record;
          import java.util.function.Consumer;
          import org.jspecify.annotations.NonNull;
          
          public interface Wither<@NonNull R extends @NonNull Record, @NonNull S> {
            @NonNull R with(@NonNull Consumer<@NonNull S> setter);
          }
        """
          .trimIndent()
      )

    val baseDir = testProjectDir.resolve("build/generated/java/foo/bar")
    val moduleFile = baseDir.resolve("Mod.java")

    assertThat(baseDir.listDirectoryEntries().size).isEqualTo(1)
    assertThat(moduleFile).exists()

    val text = moduleFile.readText()

    // shading must not affect generated code
    assertThat(text).doesNotContain("org.pkl.thirdparty")

    checkTextContains(
      text,
      """
        public record Mod(@Named("other") @NonNull Object other) implements Wither<Mod, Mod.Memento> {
      """
        .trimIndent(),
    )

    checkTextContains(
      text,
      """
        public record Person(@Named("name") @NonNull String name,
            @Named("addresses") @NonNull List<Address> addresses) implements Wither<Person, Person.Memento> {
      """
        .trimIndent(),
    )

    checkTextContains(
      text,
      """
        public record Address(@Named("street") @NonNull String street,
            @Named("zip") long zip) implements Wither<Address, Address.Memento> {
      """
        .trimIndent(),
    )
  }

  @Test
  fun `generate code`() {
    writeBuildFile()
    writePklFile()

    runTask("configClasses")

    val baseDir = testProjectDir.resolve("build/generated/java/foo/bar")
    val moduleFile = baseDir.resolve("Mod.java")

    assertThat(baseDir.listDirectoryEntries().size).isEqualTo(1)
    assertThat(moduleFile).exists()

    val text = moduleFile.readText()

    // shading must not affect generated code
    assertThat(text).doesNotContain("org.pkl.thirdparty")

    checkTextContains(
      text,
      """
        public final class Mod {
          public final @NonNull Object other;
      """
        .trimIndent(),
    )

    checkTextContains(
      text,
      """
        public static final class Person {
          public final @NonNull String name;
      
          public final @NonNull List<Address> addresses;
      """
        .trimIndent(),
    )

    checkTextContains(
      text,
      """
        public static final class Address {
          public final @NonNull String street;
      
          public final long zip;
      """
        .trimIndent(),
    )
  }

  @Test
  fun `compile generated records code`() {
    writeBuildFile(isGenerateRecords = true, isUseWithers = true)
    writePklFile()

    runTask("compileJava")

    val classesDir = testProjectDir.resolve("build/classes/java/main")

    // common classes
    val witherClassFile = classesDir.resolve("org/pkl/codegen/java/common/code/Wither.class")

    // module classes
    val moduleClassFile = classesDir.resolve("foo/bar/Mod.class")
    val personClassFile = classesDir.resolve("foo/bar/Mod\$Person.class")
    val addressClassFile = classesDir.resolve("foo/bar/Mod\$Address.class")
    val modMementoClassFile = classesDir.resolve("foo/bar/Mod\$Memento.class")
    val modPersonMementoClassFile = classesDir.resolve("foo/bar/Mod\$Person\$Memento.class")
    val modAddressMementoClassFile = classesDir.resolve("foo/bar/Mod\$Address\$Memento.class")

    assertThat(witherClassFile).exists()

    assertThat(moduleClassFile).exists()
    assertThat(personClassFile).exists()
    assertThat(addressClassFile).exists()
    assertThat(modMementoClassFile).exists()
    assertThat(modPersonMementoClassFile).exists()
    assertThat(modAddressMementoClassFile).exists()
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
    """,
    )

    val result = runTask("evalTest", true)
    assertThat(result.output).contains("No source modules specified.")
  }

  private fun writeBuildFile(
    isGenerateRecords: Boolean = false,
    javaVersion: String = "21",
    isUseWithers: Boolean = false,
    isUseLombokBuilders: Boolean = false,
  ) {
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

      java {
          toolchain {
              languageVersion = JavaLanguageVersion.of($javaVersion)
          }
      }

      dependencies {
        implementation "javax.inject:javax.inject:1"
        implementation "org.jspecify:jspecify:1.0.0"
      }

      pkl {
        javaCodeGenerators {
          configClasses {
            sourceModules = ["mod.pkl"]
            outputDir = file("build/generated")
            paramsAnnotation = "javax.inject.Named"
            nonNullAnnotation = "org.jspecify.annotations.NonNull"
            generateRecords = $isGenerateRecords
            useWithers = $isUseWithers
            useLombokBuilders = $isUseLombokBuilders
            settingsModule = "pkl:settings"
            renames = [
              'org': 'foo.bar'
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
          addresses: List<Address?>
        }
  
        class Address {
          street: String
          zip: Int
        }
  
        other = 42
      """,
    )
  }
}
