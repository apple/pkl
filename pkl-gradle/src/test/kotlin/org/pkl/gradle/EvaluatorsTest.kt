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

import java.nio.file.Path
import kotlin.io.path.readText
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.readString
import org.pkl.commons.test.PackageServer

class EvaluatorsTest : AbstractTest() {
  @Test
  fun `render Pcf`() {
    writeBuildFile("pcf")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile,
      """
      person {
        name = "Pigeon"
        age = 30
      }
    """
        .trimIndent()
    )
  }

  @Test
  fun `render YAML`() {
    writeBuildFile("yaml")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.yaml")
    checkFileContents(
      outputFile,
      """
      person:
        name: Pigeon
        age: 30
    """
        .trimIndent()
    )
  }

  @Test
  fun `render JSON`() {
    writeBuildFile("json")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.json")
    checkFileContents(
      outputFile,
      """
      {
        "person": {
          "name": "Pigeon",
          "age": 30
        }
      }
    """
        .trimIndent()
    )
  }

  @Test
  fun `render plist`() {
    writeBuildFile("plist")

    writePklFile()

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.plist")
    checkFileContents(
      outputFile,
      """
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
    """
        .trimIndent()
    )
  }

  @Test
  fun `set external properties`() {
    writeBuildFile(
      "pcf",
      """
      externalProperties = [prop1: "value1", prop2: "value2"]
    """
        .trimIndent()
    )

    writePklFile(
      """
      prop1 = read("prop:prop1")
      prop2 = read("prop:prop2")
      other = read?("prop:other")
    """
        .trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile,
      """
      prop1 = "value1"
      prop2 = "value2"
      other = null
    """
        .trimIndent()
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
    """
        .trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile,
      """
      prop1 = null
      prop2 = null
      prop3 = null
    """
        .trimIndent()
    )
  }

  @Test
  fun `set environment variables`() {
    writeBuildFile(
      "pcf",
      """
      environmentVariables = [VAR1: "value1", VAR2: "value2"]
    """
        .trimIndent()
    )

    writePklFile(
      """
      prop1 = read("env:VAR1")
      prop2 = read("env:VAR2")
      other = read?("env:OTHER")
    """
        .trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    checkFileContents(
      outputFile,
      """
      prop1 = "value1"
      prop2 = "value2"
      other = null
    """
        .trimIndent()
    )
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

  @Test
  fun `source module URIs`() {
    writeFile(
      "testDir/test.pkl",
      """
      person {
        name = "Pigeon"
        age = 20 + 10
      }
    """
    )

    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        evaluators {
          evalTest {
            sourceModules = [uri("modulepath:/test.pkl")]
            modulePath.from layout.projectDirectory.dir("testDir")
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
      outputFile,
      """
      person {
        name = "Pigeon"
        age = 30
      }
    """
        .trimIndent()
    )
  }

  @Test
  fun `cannot evaluate module located outside evalRootDir`() {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
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
    assertThat(result.output)
      .contains(
        "because it does not match any entry in the module allowlist (`--allowed-modules`)."
      )
  }

  @Test
  fun `evaluation timeout`() {
    // Gradle 4.10 doesn't automatically import Duration
    writeBuildFile("pcf", """
      evalTimeout = java.time.Duration.ofMillis(100)
    """)

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
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
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

    writeFile("test1.pkl", "foo = 1")
    writeFile("test2.pkl", "bar = 2")
    runTask("evalTask")

    checkFileContents(
      outputFile,
      """
      foo = 1
      // hello
      bar = 2
    """
        .trimIndent()
    )
  }

  @Test
  fun `compliant file URIs`() {
    writeBuildFile("pcf")
    writeFile(
      "test.pkl",
      """
      import "pkl:reflect"
      output {
        text = reflect.Module(module).uri
      }
    """
        .trimIndent()
    )

    runTask("evalTest")

    val outputFile = testProjectDir.resolve("test.pcf")
    assertThat(outputFile).exists()
    assertThat(outputFile.readString().trim()).startsWith("file:///")
  }

  @Test
  fun `multiple file output`() {
    writeBuildFile(
      "pcf",
      """
      multipleFileOutputDir = layout.projectDirectory.dir("my-output")
    """
        .trimIndent()
    )
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
      """
        .trimIndent()
    )
    runTask("evalTest")
    checkFileContents(testProjectDir.resolve("my-output/output-1.txt"), "My output 1")
    checkFileContents(testProjectDir.resolve("my-output/output-2.txt"), "My output 2")
  }

  @Test
  fun expression() {
    writeBuildFile(
      "yaml",
      """
      expression = "metadata.name"
      outputFile = layout.projectDirectory.file("output.txt")
    """
        .trimIndent()
    )
    writeFile(
      "test.pkl",
      """
        metadata {
          name = "Uni"
        }
      """
        .trimIndent()
    )
    runTask("evalTest")
    checkFileContents(testProjectDir.resolve("output.txt"), "Uni")
  }

  @Test
  fun `explicitly set cache dir`(@TempDir tempDir: Path) {
    writeBuildFile(
      "pcf",
      """
      moduleCacheDir = file("${tempDir.toUri()}")
    """
        .trimIndent()
    )
    writeFile(
      "test.pkl",
      """
        import "package://localhost:0/birds@0.5.0#/Bird.pkl"
        
        res = new Bird { name = "Wally"; favoriteFruit { name = "bananas" } }
      """
        .trimIndent()
    )
    PackageServer.populateCacheDir(tempDir)
    runTask("evalTest")
  }

  @Test
  fun `explicitly set project dir`() {
    writeBuildFile("pcf", "projectDir = file(\"proj1\")", listOf("proj1/foo.pkl"))

    writeFile(
      "proj1/PklProject",
      """
      amends "pkl:Project"
      
      dependencies {
        ["proj2"] = import("../proj2/PklProject")
      }
      
      package {
        name = "proj1"
        baseUri = "package://localhost:0/\(name)"
        version = "1.0.0"
        packageZipUrl = "https://localhost:0/\(name)@\(version).zip"
      }
    """
        .trimIndent()
    )

    writeFile(
      "proj2/PklProject",
      """
      amends "pkl:Project"
      
      package {
        name = "proj2"
        baseUri = "package://localhost:0/\(name)"
        version = "1.0.0"
        packageZipUrl = "https://localhost:0/\(name)@\(version).zip"
      }
    """
        .trimIndent()
    )

    writeFile(
      "proj1/PklProject.deps.json",
      """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {
          "package://localhost:0/proj2@1": {
            "type": "local",
            "uri": "projectpackage://localhost:0/proj2@1.0.0",
            "path": "../proj2"
          }
        }
      }
    """
        .trimIndent()
    )

    writeFile(
      "proj2/PklProject.deps.json",
      """
      {
        "schemaVersion": 1,
        "resolvedDependencies": {}
      }
    """
        .trimIndent()
    )

    writeFile(
      "proj1/foo.pkl",
      """
      module proj1.foo
      
      bar: String = import("@proj2/baz.pkl").qux
    """
        .trimIndent()
    )

    writeFile(
      "proj2/baz.pkl",
      """
      qux: String = "Contents of @proj2/qux"
    """
        .trimIndent()
    )

    runTask("evalTest")
    assertThat(testProjectDir.resolve("proj1/foo.pcf")).exists()
  }

  @Test
  fun `implicit dependency tracking for effective output files`() {
    writeFile("file1.pkl", "foo = 1")

    writeFile("file2.pkl", "bar = 1")

    writeFile(
      "build.gradle.kts",
      """
      import org.pkl.gradle.task.EvalTask

      plugins {
        id("org.pkl-lang")
      }

      pkl {
        evaluators {
          register("doEval") {
            sourceModules.set(files("file1.pkl", "file2.pkl"))
            outputFile.set(layout.projectDirectory.file("%{moduleName}.%{outputFormat}"))
            outputFormat.set("yaml")
          }
        }
      }

      val doEval by tasks.existing(EvalTask::class) {
        doLast {
          file("evalCounter.txt").appendText("doEval executed\n")
        }
      }

      val printEvalFiles by tasks.registering {
        inputs.files(doEval)
        doLast {
          println("evalCounter.txt")
          println(file("evalCounter.txt").readText())
          
          inputs.files.forEach {
            println(it.name)
            println(it.readText())
          }
        }
      }
    """
        .trimIndent()
    )

    val result1 = runTask("printEvalFiles")

    // `doEval` task is invoked transitively.
    assertThat(result1.task(":doEval")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    assertThat(result1.output)
      .containsIgnoringNewLines(
        """
      evalCounter.txt
      doEval executed
      
      file1.yaml
      foo: 1

      file2.yaml
      bar: 1
      """
          .trimIndent()
      )

    // Run the task again to check that it is cached.
    val result2 = runTask("printEvalFiles")

    assertThat(result2.task(":doEval")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    // evalCounter.txt content is the same as before.
    assertThat(result2.output)
      .containsIgnoringNewLines(
        """
      evalCounter.txt
      doEval executed
      
      file1.yaml
      foo: 1

      file2.yaml
      bar: 1
      """
          .trimIndent()
      )

    // Modify the input file.
    writeFile("file1.pkl", "foo = 7")

    // Run the build again - the evaluation task will not be cached.
    val result3 = runTask("printEvalFiles")

    assertThat(result3.task(":doEval")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // evalCounter.txt content is updated too.
    assertThat(result3.output)
      .containsIgnoringNewLines(
        """
      evalCounter.txt
      doEval executed
      doEval executed
      
      file1.yaml
      foo: 7

      file2.yaml
      bar: 1
      """
          .trimIndent()
      )
  }

  @Test
  fun `implicit dependency tracking for multiple output directory`() {
    writePklFile(
      """
      pigeon {
        name = "Pigeon"
        diet = "Seeds"
      }
      
      parrot {
        name = "Parrot"
        diet = "Seeds"
      }
      
      output {
        files {
          ["birds/pigeon.json"] {
            value = pigeon
            renderer = new JsonRenderer {}
          }
          ["birds/parrot.pcf"] {
            value = parrot
            renderer = new PcfRenderer {}
          }
        }
      }
    """
        .trimIndent()
    )

    writeBuildFile(
      "yaml",
      additionalContents =
        """
        multipleFileOutputDir = layout.projectDirectory.dir("%{moduleDir}/%{moduleName}-%{outputFormat}")
      """
          .trimIndent(),
      additionalBuildScript =
        """
        tasks.named('evalTest') {
          doLast {
            file("evalCounter.txt").append("evalTest executed\n")
          }
        }
        
        abstract class PrintTask extends DefaultTask {
          @InputFiles
          public abstract ConfigurableFileCollection getInputDirs();
        }
        
        // ensure that iteration order is the same across environments
        def sortByTypeThenName = { a, b ->
            a.isFile() != b.isFile() ? a.isFile() <=> b.isFile() : a.name <=> b.name
        }

        tasks.register('printEvalDirs', PrintTask) {
          inputDirs.from(tasks.named('evalTest'))
          
          doLast {
            println "evalCounter.txt"
            println file("evalCounter.txt").text

            inputDirs.forEach { f ->
              f.traverse(visitRoot: true, sort: sortByTypeThenName) {
                if (it.isDirectory()) {
                  println layout.projectDirectory.asFile.relativePath(it) + '/'
                  println()
                } else {
                  println layout.projectDirectory.asFile.relativePath(it)
                  println it.text
                }
              }
            }
          }
        }
      """
          .trimIndent()
    )

    val result1 = runTask("printEvalDirs")

    // `doEval` task is invoked transitively.
    assertThat(result1.task(":evalTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // NB: Configured output format, 'yaml', is only used to replace the placeholder in the path;
    // the output files themselves are formatted according to configuration
    // in the rendered module.

    assertThat(result1.output)
      .containsIgnoringNewLines(
        """
      evalCounter.txt
      evalTest executed
      
      test-yaml/birds/
      
      test-yaml/birds/parrot.pcf
      name = "Parrot"
      diet = "Seeds"
      
      test-yaml/birds/pigeon.json
      {
        "name": "Pigeon",
        "diet": "Seeds"
      }
      
      test-yaml/
      """
          .trimIndent()
      )

    // Run the task again to check that it is cached.
    val result2 = runTask("printEvalDirs")

    assertThat(result2.task(":evalTest")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    // evalCounter.txt content is the same as before.
    assertThat(result2.output)
      .containsIgnoringNewLines(
        """
      evalCounter.txt
      evalTest executed
      
      test-yaml/birds/
      
      test-yaml/birds/parrot.pcf
      name = "Parrot"
      diet = "Seeds"
      
      test-yaml/birds/pigeon.json
      {
        "name": "Pigeon",
        "diet": "Seeds"
      }
      
      test-yaml/
      """
          .trimIndent()
      )

    // Modify the input file.
    writePklFile(testProjectDir.resolve("test.pkl").readText().replace("Parrot", "Macaw"))

    // Run the build again - the evaluation task will not be cached.
    val result3 = runTask("printEvalDirs")

    assertThat(result3.task(":evalTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // evalCounter.txt content is updated too.
    assertThat(result3.output)
      .containsIgnoringNewLines(
        """
      evalCounter.txt
      evalTest executed
      evalTest executed
      
      test-yaml/birds/
      
      test-yaml/birds/parrot.pcf
      name = "Macaw"
      diet = "Seeds"
      
      test-yaml/birds/pigeon.json
      {
        "name": "Pigeon",
        "diet": "Seeds"
      }
      
      test-yaml/
      """
          .trimIndent()
      )
  }

  private fun writeBuildFile(
    // don't use `org.pkl.core.OutputFormat`
    // because test compile class path doesn't contain pkl-core
    outputFormat: String,
    additionalContents: String = "",
    sourceModules: List<String> = listOf("test.pkl"),
    additionalBuildScript: String = ""
  ) {
    writeFile(
      "build.gradle",
      """
      plugins {
        id "org.pkl-lang"
      }

      pkl {
        evaluators {
          evalTest {
            sourceModules = [${sourceModules.joinToString(separator = ", ") { "\"$it\"" }}]
            outputFormat = "$outputFormat"
            settingsModule = "pkl:settings"
            $additionalContents
          }
        }
      }
      
      $additionalBuildScript
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
