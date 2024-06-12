/**
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
package org.pkl.codegen.java

import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.readString

class CliJavaCodeGeneratorTest {

  private val dollar = "$"

  @Test
  fun `module inheritance`(@TempDir tempDir: Path) {
    val module1 =
      PklModule(
        "org.mod1",
        """
      open module org.mod1

      pigeon: Person

      class Person {
        name: String
        age: Int
      }
      """
      )

    val module2 =
      PklModule(
        "org.mod2",
        """
      module org.mod2

      extends "mod1.pkl"

      parrot: Person
      """
      )

    val module1File = module1.writeToDisk(tempDir.resolve("org/mod1.pkl"))
    val module2File = module2.writeToDisk(tempDir.resolve("org/mod2.pkl"))
    val outputDir = tempDir.resolve("output")

    val generator =
      CliJavaCodeGenerator(
        CliJavaCodeGeneratorOptions(
          CliBaseOptions(listOf(module1File.toUri(), module2File.toUri())),
          outputDir
        )
      )

    generator.run()

    val javaDir = outputDir.resolve("java")

    val moduleJavaFiles = javaDir.resolve("org").listDirectoryEntries()
    assertThat(moduleJavaFiles.map { it.fileName.toString() })
      .containsExactlyInAnyOrder("Mod1.java", "Mod2.java")

    val module1JavaFile = javaDir.resolve("org/Mod1.java")
    assertContains(
      """
      |public class Mod1 {
      |  public final @NonNull Person pigeon;
    """,
      module1JavaFile.readString()
    )

    val module2JavaFile = javaDir.resolve("org/Mod2.java")
    assertContains(
      """
      |public final class Mod2 extends Mod1 {
      |  public final Mod1. @NonNull Person parrot;
    """,
      module2JavaFile.readString()
    )
    val resourcesDir = outputDir.resolve("resources/META-INF/org/pkl/config/java/mapper/classes/")

    val module1PropertiesFile = resourcesDir.resolve("org.mod1.properties")

    val module1PropertiesString = module1PropertiesFile.readString()
    // use two assertions because java.util.Properties doesn't guarantee order
    assertContains(
      """org.pkl.config.java.mapper.org.mod1\#Person=org.Mod1${dollar}Person""",
      module1PropertiesString
    )
    assertContains(
      """org.pkl.config.java.mapper.org.mod1\#ModuleClass=org.Mod1""",
      module1PropertiesString
    )

    val module2PropertiesFile = resourcesDir.resolve("org.mod2.properties")

    assertContains(
      """org.pkl.config.java.mapper.org.mod2\#ModuleClass=org.Mod2""",
      module2PropertiesFile.readString()
    )
  }

  @Test
  fun `class name clashes`(@TempDir tempDir: Path) {
    val module1 =
      PklModule(
        "org.mod1",
        """
      module org.mod1

      class Person {
        name: String
      }
      """
      )

    val module2 =
      PklModule(
        "org.mod2",
        """
      module org.mod2

      import "mod1.pkl"

      person1: mod1.Person
      person2: Person

      class Person {
        age: Int
      }
      """
      )

    val module1PklFile = module1.writeToDisk(tempDir.resolve("org/mod1.pkl"))
    val module2PklFile = module2.writeToDisk(tempDir.resolve("org/mod2.pkl"))
    val outputDir = tempDir.resolve("output")

    val generator =
      CliJavaCodeGenerator(
        CliJavaCodeGeneratorOptions(
          CliBaseOptions(listOf(module1PklFile.toUri(), module2PklFile.toUri())),
          outputDir
        )
      )

    generator.run()

    val module2JavaFile = outputDir.resolve("java/org/Mod2.java")
    assertContains(
      """
      |public final class Mod2 {
      |  public final Mod1. @NonNull Person person1;
      |
      |  public final @NonNull Person person2;
      """,
      module2JavaFile.readString()
    )
  }

  @Test
  fun `custom package names`(@TempDir tempDir: Path) {
    val module1 =
      PklModule(
        "org.foo.Module1",
        """
          module org.foo.Module1

          class Person {
            name: String
          }
        """
      )

    val module2 =
      PklModule(
        "org.bar.Module2",
        """
          module org.bar.Module2
          
          import "../../org/foo/Module1.pkl"

          class Group {
            owner: Module1.Person
            name: String
          }
        """
      )

    val module3 =
      PklModule(
        "org.baz.Module3",
        """
          module org.baz.Module3
          
          import "../../org/bar/Module2.pkl"

          class Supergroup {
            owner: Module2.Group
          }
        """
      )

    val module1PklFile = module1.writeToDisk(tempDir.resolve("org/foo/Module1.pkl"))
    val module2PklFile = module2.writeToDisk(tempDir.resolve("org/bar/Module2.pkl"))
    val module3PklFile = module3.writeToDisk(tempDir.resolve("org/baz/Module3.pkl"))
    val outputDir = tempDir.resolve("output")

    val generator =
      CliJavaCodeGenerator(
        CliJavaCodeGeneratorOptions(
          CliBaseOptions(listOf(module1PklFile, module2PklFile, module3PklFile).map { it.toUri() }),
          outputDir,
          renames = mapOf("org.foo" to "com.foo.x", "org.baz" to "com.baz.a.b")
        )
      )

    generator.run()

    val module1JavaFile = outputDir.resolve("java/com/foo/x/Module1.java")
    module1JavaFile.readString().let {
      assertContains("package com.foo.x;", it)

      assertContains("public final class Module1 {", it)

      assertContains(
        """
        |  public static final class Person {
        |    public final @NonNull String name;
        """,
        it
      )
    }

    val module2JavaFile = outputDir.resolve("java/org/bar/Module2.java")
    module2JavaFile.readString().let {
      assertContains("package org.bar;", it)

      assertContains("import com.foo.x.Module1;", it)

      assertContains("public final class Module2 {", it)

      assertContains(
        """
        |  public static final class Group {
        |    public final Module1. @NonNull Person owner;
        """,
        it
      )
    }

    val module3JavaFile = outputDir.resolve("java/com/baz/a/b/Module3.java")
    module3JavaFile.readString().let {
      assertContains("package com.baz.a.b;", it)

      assertContains("import org.bar.Module2;", it)

      assertContains("public final class Module3 {", it)

      assertContains(
        """
        |  public static final class Supergroup {
        |    public final Module2. @NonNull Group owner;
        """,
        it
      )
    }
  }

  private fun assertContains(part: String, code: String) {
    val trimmedPart = part.trim().trimMargin()
    if (!code.contains(trimmedPart)) {
      // check for equality to get better error output (ide diff dialog)
      assertThat(code).isEqualTo(trimmedPart)
    }
  }
}
