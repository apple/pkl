package org.pkl.gradle

import org.pkl.commons.readString
import org.pkl.commons.test.PackageServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EvaluatorsTest : AbstractTest() {
  @Test
  fun `render Pcf`() {
    writeBuildFile("pcf")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile, """
      person {
        name = "Pigeon"
        age = 30
      }
    """.trimIndent()
    )
  }

  @Test
  fun `render YAML`() {
    writeBuildFile("yaml")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.yaml")
    checkFileContents(
      outputFile, """
      person:
        name: Pigeon
        age: 30
    """.trimIndent()
    )
  }

  @Test
  fun `render JSON`() {
    writeBuildFile("json")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.json")
    checkFileContents(
      outputFile, """
      {
        "person": {
          "name": "Pigeon",
          "age": 30
        }
      }
    """.trimIndent()
    )
  }

  @Test
  fun `render plist`() {
    writeBuildFile("plist")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.plist")
    checkFileContents(
      outputFile, """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
      <plist version="1.0">
      <dict>
        <key>person</key>
        <dict>
          <key>name</key>
          <string>Pigeon</string>
          <key>age</key>
          <integer>30</integer>
        </dict>
      </dict>
      </plist>
    """.trimIndent()
    )
  }

  @Test
  fun `set external properties`() {
    writeBuildFile(
      "pcf", """
      externalProperties = [prop1: "value1", prop2: "value2"]
    """.trimIndent()
    )

    writePklFile(
      """
      prop1 = read("prop:prop1")
      prop2 = read("prop:prop2")
      other = read?("prop:other")
    """.trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile, """
      prop1 = "value1"
      prop2 = "value2"
      other = null
    """.trimIndent()
    )
  }

  @Test
  fun `defaults to empty environment variables`() {
    writeBuildFile("pcf")

    writePklFile(
      """
      prop1 = read?("env:USER")
      prop2 = read?("env:PATH")
      prop3 = read?("env:JAVA_HOME")
    """.trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile, """
      prop1 = null
      prop2 = null
      prop3 = null
    """.trimIndent()
    )
  }

  @Test
  fun `set environment variables`() {
    writeBuildFile(
      "pcf", """
      environmentVariables = [VAR1: "value1", VAR2: "value2"]
    """.trimIndent()
    )

    writePklFile(
      """
      prop1 = read("env:VAR1")
      prop2 = read("env:VAR2")
      other = read?("env:OTHER")
    """.trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile, """
      prop1 = "value1"
      prop2 = "value2"
      other = null
    """.trimIndent()
    )
  }

  @Test
  fun `no source modules`() {
    writeFile(
      "build.gradle", """
      plugins {
        id "org.pkl"
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

  @Test
  fun `source module URIs`() {
    val pklFile = writeFile(
      "test.pkl", """
      person {
        name = "Pigeon"
        age = 20 + 10
      }
    """
    )

    writeFile(
      "build.gradle", """
      plugins {
        id "org.pkl"
      }

      pkl {
        evaluators {
          evalTest {
            sourceModules = [uri("modulepath:/test.pkl")]
            modulePath.from "${pklFile.parent}"
            outputFile = layout.projectDirectory.file("test.pcf")
            settingsModule = "pkl:settings"
          }
        }
      }
    """
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile, """
      person {
        name = "Pigeon"
        age = 30
      }
    """.trimIndent()
    )
  }

  @Test
  fun `cannot evaluate module located outside evalRootDir`() {
    writeFile(
      "build.gradle", """
      plugins {
        id "org.pkl"
      }

      pkl {
        evaluators {
          evalTest {
            evalRootDir = file("/non/existing")
            sourceModules = ["test.pkl"]
            settingsModule = "pkl:settings"
          }
        }
      }
    """
    )

    val result = runTask("evalTest", expectFailure = true)
    assertThat(result.output).contains("Refusing to load module")
    assertThat(result.output).contains("because it does not match any entry in the module allowlist (`--allowed-modules`).")
  }

  // TODO: add a test for the cache configuration as soon as we have https cacheing (rdar://110056600)

  @Test
  fun `evaluation timeout`() {
    // Gradle 4.10 doesn't automatically import Duration
    writeBuildFile(
      "pcf", """
      evalTimeout = java.time.Duration.ofMillis(100)
    """
    )

    writePklFile(
      """
      function fib(n) = if (n < 2) 0 else fib(n - 1) + fib(n - 2)
      x = fib(100)
    """
    )

    val result = runTask("evalTest", expectFailure = true)
    assertThat(result.output).contains("timed out")
  }

  @Test
  fun `module output separator`() {
    val outputFile = testProjectDir.resolve("test.pcf")
    writeFile(
      "build.gradle", """
      plugins {
        id "org.pkl"
      }

      pkl {
        evaluators {
          evalTask {
            moduleOutputSeparator = "// hello"
            sourceModules = ["test1.pkl", "test2.pkl"]
            settingsModule = "pkl:settings"
            outputFile = layout.projectDirectory.file("test.pcf")
          }
        }
      }
    """
    )

    writeFile(
      "test1.pkl",
      "foo = 1"
    )
    writeFile(
      "test2.pkl",
      "bar = 2"
    )
    runTask("evalTask")

    checkFileContents(outputFile, """
      foo = 1
      // hello
      bar = 2
    """.trimIndent())
  }
  
  @Test
  fun `compliant file URIs`() {
    writeBuildFile("pcf")
    writeFile("test.pkl", """
      import "pkl:reflect"
      output {
        text = reflect.Module(module).uri
      }
    """.trimIndent())

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    assertThat(outputFile).exists()
    assertThat(outputFile.readString().trim()).startsWith("file:///")
  }

  @Test
  fun `multiple file output`() {
    writeBuildFile("pcf", """
      multipleFileOutputDir = layout.projectDirectory.dir("my-output")
    """.trimIndent())
    writeFile(
      "test.pkl",
      """
        output {
          files {
            ["output-1.txt"] {
              text = "My output 1"
            }
            ["output-2.txt"] {
              text = "My output 2"
            }
          }
        }
      """.trimIndent()
    )
    runTask("evalTest")
    checkFileContents(testProjectDir.resolve("my-output/output-1.txt"), "My output 1")
    checkFileContents(testProjectDir.resolve("my-output/output-2.txt"), "My output 2")
  }

  @Test
  fun expression() {
    writeBuildFile("yaml", """
      expression = "metadata.name"
      outputFile = layout.projectDirectory.file("output.txt")
    """.trimIndent())
    writeFile(
      "test.pkl",
      """
        metadata {
          name = "Uni"
        }
      """.trimIndent()
    )
    runTask("evalTest")
    checkFileContents(testProjectDir.resolve("output.txt"), "Uni")
  }

  @Test
  fun `explicitly set cache dir`(@TempDir tempDir: Path) {
    writeBuildFile("pcf", """
      moduleCacheDir = file("$tempDir")
    """.trimIndent())
    writeFile(
      "test.pkl",
      """
        import "package://localhost:12110/birds@0.5.0#/Bird.pkl"
        
        res = new Bird { name = "Wally"; favoriteFruit { name = "bananas" } }
      """.trimIndent()
    )
    PackageServer.populateCacheDir(tempDir)
    runTask("evalTest")
  }

  private fun writeBuildFile(
    // don't use `org.pkl.core.OutputFormat` 
    // because test compile class path doesn't contain pkl-core
    outputFormat: String, 
    additionalContents: String = ""
  ) {
    writeFile(
      "build.gradle", """
      plugins {
        id "org.pkl"
      }

      pkl {
        evaluators {
          evalTest {
            sourceModules = ["test.pkl"]
            outputFormat = "$outputFormat"
            settingsModule = "pkl:settings"
            $additionalContents
          }
        }
      }
    """
    )
  }

  private fun writePklFile(
    contents: String = """
    person {
      name = "Pigeon"
      age = 20 + 10
    }
  """
  ) {
    writeFile("test.pkl", contents)
  }
}
