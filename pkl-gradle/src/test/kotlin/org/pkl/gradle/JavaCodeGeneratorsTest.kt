package org.pkl.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class JavaCodeGeneratorsTest : AbstractTest() {
  @Test
  fun `generate code`() {
    writeBuildFile()
    writePklFile()

    runTask("configClasses")
    
    val baseDir = testProjectDir.resolve("build/generated/java/org")
    val moduleFile = baseDir.resolve("Mod.java")

    assertThat(baseDir.listDirectoryEntries().count()).isEqualTo(1)
    assertThat(moduleFile).exists()

    val text = moduleFile.readText()

    // shading must not affect generated code
    assertThat(text).doesNotContain("org.pkl.thirdparty")
    
    checkTextContains(
      text, """
      |public final class Mod {
      |  public final @Nonnull Object other;
    """
    )

    checkTextContains(
      text, """
      |  public static final class Person {
      |    public final @Nonnull String name;
      |
      |    public final @Nonnull List<@Nonnull Address> addresses;
    """
    )

    checkTextContains(
      text, """
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
    writeFile("mod.pkl", """
      module org.mod
      
      class Person {
        name: String
        addresses: List<Address?>
      }
      
      class Address {
        street: String
        zip: Int
      }
      
      other: Any = 42
    """.trimIndent())
    
    runTask("compileJava")

    val classesDir = testProjectDir.resolve("build/classes/java/main")
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
      "build.gradle", """
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
          }
        }
      }
    """
    )
  }

  private fun writeGradlePropertiesFile() {
    writeFile("gradle.properties", """
      systemProp.http.proxyHost=proxy.config.pcp.local
      systemProp.http.proxyPort=3128
      systemProp.http.nonProxyHosts=localhost|*.apple.com
      
      systemProp.https.proxyHost=proxy.config.pcp.local
      systemProp.https.proxyPort=3128
      systemProp.https.nonProxyHosts=localhost|*.apple.com
    """)
  }
  
  private fun writePklFile() {
    writeFile(
      "mod.pkl", """
        module org.mod
  
        class Person {
          name: String
          addresses: List<Address>
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
