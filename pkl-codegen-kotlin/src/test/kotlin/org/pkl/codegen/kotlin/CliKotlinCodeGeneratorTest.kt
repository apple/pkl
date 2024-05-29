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
package org.pkl.codegen.kotlin

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.readString

class CliKotlinCodeGeneratorTest {
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
      CliKotlinCodeGenerator(
        CliKotlinCodeGeneratorOptions(
          CliBaseOptions(listOf(module1File.toUri(), module2File.toUri())),
          outputDir
        )
      )

    generator.run()

    val module1KotlinFile = outputDir.resolve("kotlin/org/Mod1.kt")
    assertThat(module1KotlinFile).exists()

    val module2KotlinFile = outputDir.resolve("kotlin/org/Mod2.kt")
    assertThat(module2KotlinFile).exists()

    assertContains(
      """
      open class Mod1(
        open val pigeon: Person
      ) {
    """
        .trimIndent(),
      module1KotlinFile.readString()
    )

    assertContains(
      """
      class Mod2(
        pigeon: Mod1.Person,
        val parrot: Mod1.Person
      ) : Mod1(pigeon) {
    """
        .trimIndent(),
      module2KotlinFile.readString()
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
      CliKotlinCodeGenerator(
        CliKotlinCodeGeneratorOptions(
          CliBaseOptions(listOf(module1PklFile.toUri(), module2PklFile.toUri())),
          outputDir
        )
      )

    generator.run()

    val module2KotlinFile = outputDir.resolve("kotlin/org/Mod2.kt")
    assertContains(
      """
      data class Mod2(
        val person1: Mod1.Person,
        val person2: Person
      )
      """
        .trimIndent(),
      module2KotlinFile.readString()
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
      CliKotlinCodeGenerator(
        CliKotlinCodeGeneratorOptions(
          CliBaseOptions(listOf(module1PklFile, module2PklFile, module3PklFile).map { it.toUri() }),
          outputDir,
          packageMapping = mapOf("org.foo" to "com.foo.x", "org.baz" to "com.baz.a.b")
        )
      )

    generator.run()
    
    val module1KotlinFile = outputDir.resolve("kotlin/com/foo/x/Module1.kt")
    module1KotlinFile.readString().let { 
      assertContains("package com.foo.x", it)
      
      assertContains("object Module1 {", it)
      
      assertContains(
        """
        |  data class Person(
        |    val name: String
        |  )
        """,
        it
      )
    }

    val module2KotlinFile = outputDir.resolve("kotlin/org/bar/Module2.kt")
    module2KotlinFile.readString().let {
      assertContains("package org.bar", it)

      assertContains("import com.foo.x.Module1", it)

      assertContains("object Module2 {", it)

      assertContains(
        """
        |  data class Group(
        |    val owner: Module1.Person,
        |    val name: String
        |  )
        """,
        it
      )
    }
    
    val module3KotlinFile = outputDir.resolve("kotlin/com/baz/a/b/Module3.kt")
    module3KotlinFile.readString().let {
      assertContains("package com.baz.a.b", it)

      assertContains("import org.bar.Module2", it)

      assertContains("object Module3 {", it)

      assertContains(
        """
        |  data class Supergroup(
        |    val owner: Module2.Group
        |  )
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
