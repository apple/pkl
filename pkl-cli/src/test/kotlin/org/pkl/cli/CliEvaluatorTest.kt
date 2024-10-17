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
package org.pkl.cli

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import java.io.StringReader
import java.io.StringWriter
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern
import kotlin.io.path.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.pkl.commons.*
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.core.OutputFormat
import org.pkl.core.SecurityManagers
import org.pkl.core.util.IoUtils

@WireMockTest(httpsEnabled = true, proxyMode = true)
class CliEvaluatorTest {
  companion object {
    private val defaultContents =
      """
      person {
        name = "pigeon"
        age = 20 + 10
      }
    """
        .trimIndent()

    private val packageServer = PackageServer()

    @AfterAll
    @JvmStatic
    fun afterAll() {
      packageServer.close()
    }
  }

  // use manually constructed temp dir instead of @TempDir to work around
  // https://forums.developer.apple.com/thread/118358
  private val tempDir: Path = run {
    val baseDir = FileTestUtils.rootProjectDir.resolve("pkl-cli/build/tmp/CliEvaluatorTest")
    baseDir.createDirectories()
    Files.createTempDirectory(baseDir, null)
  }

  @AfterEach
  fun afterEach() {
    tempDir.deleteRecursively()
  }

  @Test
  fun `generate Pcf`() {
    val sourceFiles = listOf(writePklFile("test.pkl"))
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          outputFormat = "pcf",
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(outputFiles[0], "test.pcf", """
person {
  name = "pigeon"
  age = 30
}
    """)
  }

  @Test
  fun `generate JSON`() {
    val sourceFiles = listOf(writePklFile("test.pkl"))
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          outputFormat = "json",
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(
      outputFiles[0],
      "test.json",
      """
{
  "person": {
    "name": "pigeon",
    "age": 30
  }
}
    """
    )
  }

  @Test
  fun `generate YAML`() {
    val sourceFiles = listOf(writePklFile("test.pkl"))
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          outputFormat = "yaml",
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(outputFiles[0], "test.yaml", """
person:
  name: pigeon
  age: 30
    """)
  }

  @Test
  fun `generate plist`() {
    val sourceFiles = listOf(writePklFile("test.pkl"))
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          outputFormat = "plist",
        )
      )

    assertThat(outputFiles).hasSize(1)
    @Suppress("HttpUrlsUsage")
    checkOutputFile(
      outputFiles[0],
      "test.plist",
      """
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>person</key>
  <dict>
    <key>name</key>
    <string>pigeon</string>
    <key>age</key>
    <integer>30</integer>
  </dict>
</dict>
</plist>
    """
    )
  }

  @Test
  fun `generate XML`() {
    val sourceFiles = listOf(writePklFile("test.pkl"))
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          outputFormat = "xml",
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(
      outputFiles[0],
      "test.xml",
      """
<?xml version="1.0" encoding="UTF-8"?>
<root>
  <person>
    <name>pigeon</name>
    <age>30</age>
  </person>
</root>
    """
    )
  }

  @Test
  fun `unknown output format`() {
    val sourceFiles = listOf(writePklFile("test.pkl"))

    val e =
      assertThrows<CliException> {
        evalToFiles(
          CliEvaluatorOptions(CliBaseOptions(sourceModules = sourceFiles), outputFormat = "unknown")
        )
      }

    assertThat(e).hasMessageContaining("Unknown output format: `unknown`. ")
  }

  @Test
  fun `generate multiple files`() {
    val sourceFiles =
      listOf(
        writePklFile("file1.pkl", "x = 1 + 1"),
        writePklFile("file2.pkl", "x = 2 + 2"),
        writePklFile("file3.pkl", "x = 3 + 3")
      )
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          outputFormat = "pcf",
        )
      )

    assertThat(outputFiles).hasSize(3)
    checkOutputFile(outputFiles[0], "file1.pcf", "x = 2")
    checkOutputFile(outputFiles[1], "file2.pcf", "x = 4")
    checkOutputFile(outputFiles[2], "file3.pcf", "x = 6")
  }

  @Test
  fun `module path module as source module`() {
    val dir = tempDir.resolve("foo").resolve("bar").createDirectories()
    dir.resolve("test.pkl").writeString(defaultContents)
    // check relative imports too
    dir
      .resolve("test2.pkl")
      .writeString(
        """
      amends "test.pkl"
      
      person {
        name = "barn owl"
      }
      """
          .trimIndent()
      )

    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(
            sourceModules =
              listOf(URI("modulepath:/foo/bar/test.pkl"), URI("modulepath:/foo/bar/test2.pkl")),
            modulePath = listOf(tempDir)
          ),
          outputFormat = "pcf",
          outputPath = "$tempDir/%{moduleName}.%{outputFormat}"
        )
      )

    assertThat(outputFiles).hasSize(2)
    checkOutputFile(outputFiles[0], "test.pcf", """
person {
  name = "pigeon"
  age = 30
}
    """)
    checkOutputFile(
      outputFiles[1],
      "test2.pcf",
      """
person {
  name = "barn owl"
  age = 30
}
    """
    )
  }

  @Test
  fun `external properties`() {
    val sourceFiles =
      listOf(
        writePklFile(
          "test.pkl",
          """
person {
  name = read("prop:name")
  age = read("prop:age").toInt()
}
    """
        )
      )

    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(
            sourceModules = sourceFiles,
            externalProperties = mapOf("name" to "pigeon", "age" to "30")
          ),
          outputFormat = "pcf",
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(outputFiles[0], "test.pcf", """
person {
  name = "pigeon"
  age = 30
}
    """)
  }

  @Test
  fun `custom working directory given as absolute path`() {
    customWorkingDirectory(relativePath = false)
  }

  @Test
  fun `custom working directory given as relative path (the norm when using cli)`() {
    customWorkingDirectory(relativePath = true)
  }

  private fun customWorkingDirectory(relativePath: Boolean) {
    val dir = tempDir.resolve("foo").resolve("bar").createDirectories()
    val file = dir.resolve("test.pkl").writeString(defaultContents)

    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(
            sourceModules = listOf(file.toUri()),
            workingDir =
              if (relativePath) IoUtils.getCurrentWorkingDir().relativize(dir.parent)
              else dir.parent
          ),
          outputFormat = "pcf",
          outputPath = "baz/%{moduleName}.pcf"
        )
      )

    assertThat(outputFiles).hasSize(1)
    assertThat(outputFiles[0].normalize()).isEqualTo(dir.parent.resolve("baz/test.pcf"))
    checkOutputFile(outputFiles[0], "test.pcf", """
person {
  name = "pigeon"
  age = 30
}
    """)
  }

  @Test
  fun `source module with relative path`() {
    val dir = tempDir.resolve("foo").createDirectories()
    dir.resolve("test.pkl").writeString(defaultContents)

    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(URI("foo/test.pkl")), workingDir = tempDir),
          outputFormat = "pcf"
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(outputFiles[0], "test.pcf", """
person {
  name = "pigeon"
  age = 30
}
    """)
  }

  @Test
  fun `module path element with relative path`() {
    val libDir = tempDir.resolve("lib").resolve("foo").createDirectories()
    libDir.resolve("someLib.pkl").writeString("x = 1")

    val pklScript =
      writePklFile("test.pkl", """
import "modulepath:/foo/someLib.pkl"
result = someLib.x
    """)

    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(
            sourceModules = listOf(pklScript),
            workingDir = tempDir,
            modulePath = listOf("lib".toPath())
          ),
          outputFormat = "pcf"
        )
      )

    assertThat(outputFiles).hasSize(1)
    checkOutputFile(outputFiles[0], "test.pcf", "result = 1")
  }

  @Test
  fun `moduleDir is relative to workingDir even if not descendant`() {
    val contents = "foo = 42"
    val file = writePklFile("some/nested/structure.pkl", contents)
    val workingDir = tempDir.resolve("another/structure").createDirectories()
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(file), workingDir = workingDir),
          outputPath = "%{moduleDir}/result.pcf",
          outputFormat = "pcf"
        )
      )
    assertThat(outputFiles).hasSize(1)
    assertThat(outputFiles[0]).isEqualTo(tempDir.resolve("some/nested/result.pcf"))
    checkOutputFile(outputFiles[0], "result.pcf", contents)
  }

  // Can't reliably create symlinks on Windows.
  // Might get errors like "A required privilege is not held by the client".
  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `moduleDir is relative to workingDir even through symlinks`() {
    val contents = "foo = 42"
    val realWorkingDir = tempDir.resolve("workingDir").createDirectories()
    val symlinkToTempDir = Files.createSymbolicLink(tempDir.resolve("symlinkToTempDir"), tempDir)
    val workingDir = symlinkToTempDir.resolve("workingDir")
    val file = realWorkingDir.resolve("test.pkl").writeString(contents).toUri()
    val outputFiles =
      evalToFiles(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(file), workingDir = workingDir),
          outputFormat = "pcf"
        )
      )
    assertThat(outputFiles).hasSize(1)
    assertThat(outputFiles[0].toString()).doesNotContain("symlinkToTempDir")
    checkOutputFile(outputFiles[0], "test.pcf", contents)
  }

  @Test
  fun `take input from stdin`() {
    val stdin = StringReader(defaultContents)
    val stdout = StringWriter()
    val evaluator =
      CliEvaluator(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(URI("repl:text"))),
          outputFormat = "pcf"
        ),
        stdin,
        stdout
      )
    evaluator.run()
    assertThat(stdout.toString().trim()).isEqualTo(defaultContents.replace("20 + 10", "30").trim())
  }

  @Test
  fun `write output to console`() {
    val module1 = writePklFile("mod1.pkl", "x = 21 + 21")
    val module2 = writePklFile("mod2.pkl", "y = 11 + 11")

    val output =
      evalToConsole(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(module1, module2)),
        )
      )

    assertThat(output).isEqualTo("x = 42\n---\ny = 22\n")
  }

  @Test
  fun `evaluation timeout`() {
    val sourceFiles =
      listOf(
        writePklFile(
          "test.pkl",
          """
      function fib(n) = if (n < 2) 0 else fib(n - 1) + fib(n - 2)
      x = fib(100)
    """
        )
      )

    val e =
      assertThrows<CliException> {
        evalToFiles(
          CliEvaluatorOptions(
            CliBaseOptions(sourceModules = sourceFiles, timeout = Duration.ofMillis(100)),
            outputFormat = "pcf"
          )
        )
      }
    assertThat(e.message).contains("timed out")
  }

  @Test
  fun `cannot import module located outside root dir`() {
    val sourceFiles = listOf(writePklFile("test.pkl", """
      amends "/non/existing.pkl"
    """))

    val e =
      assertThrows<CliException> {
        evalToFiles(
          CliEvaluatorOptions(
            CliBaseOptions(sourceModules = sourceFiles, rootDir = tempDir),
          )
        )
      }

    assertThat(e.message).contains("Refusing to load module `file:///non/existing.pkl`")
  }

  @Test
  fun `concatenate file outputs`() {
    val sourceFiles =
      listOf(
        writePklFile("test1.pkl", "x = 1"),
        writePklFile("test2.pkl", "x = 2"),
        writePklFile("test3.pkl", "x = 3")
      )

    val outputFile = tempDir.resolve("output.yaml")

    evalToFiles(
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = sourceFiles),
        outputFile.toString(),
        "yaml"
      )
    )

    checkOutputFile(outputFile, "output.yaml", "x: 1\n---\nx: 2\n---\nx: 3")
  }

  @Test
  fun `concatenate file outputs - some empty YAML streams`() {
    val sourceFiles =
      listOf(
        writePklFile(
          "test0.pkl",
          "output { value = List(); renderer = new YamlRenderer { isStream = true } }"
        ),
        writePklFile("test1.pkl", "x = 1"),
        writePklFile(
          "test2.pkl",
          "output { value = List(); renderer = new YamlRenderer { isStream = true } }"
        ),
        writePklFile("test3.pkl", "x = 3"),
        writePklFile(
          "test4.pkl",
          "output { value = List(); renderer = new YamlRenderer { isStream = true } }"
        )
      )

    val outputFile = tempDir.resolve("output.yaml")

    evalToFiles(
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = sourceFiles),
        outputFile.toString(),
        "yaml"
      )
    )

    checkOutputFile(outputFile, "output.yaml", "x: 1\n---\nx: 3")
  }

  @Test
  fun `concatenate module outputs with custom separator`() {
    val sourceFiles =
      listOf(
        writePklFile("test1.pkl", "x = 1"),
        writePklFile("test2.pkl", "x = 2"),
        writePklFile("test3.pkl", "x = 3")
      )

    val outputFile = tempDir.resolve("output.pcf")

    evalToFiles(
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = sourceFiles),
        outputFile.toString(),
        outputFormat = "pcf",
        moduleOutputSeparator = "// my module separator"
      )
    )

    checkOutputFile(
      outputFile,
      "output.pcf",
      """
      x = 1
      // my module separator
      x = 2
      // my module separator
      x = 3
      """
        .trimIndent()
    )
  }

  @Test
  fun `concatenate module outputs with empty custom separator`() {
    val sourceFiles =
      listOf(
        writePklFile("test1.pkl", "x = 1"),
        writePklFile("test2.pkl", "y = 2"),
        writePklFile("test3.pkl", "z = 3")
      )

    val outputFile = tempDir.resolve("output.pcf")

    evalToFiles(
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = sourceFiles),
        outputFile.toString(),
        outputFormat = "pcf",
        moduleOutputSeparator = ""
      )
    )

    checkOutputFile(
      outputFile,
      "output.pcf",
      """
      x = 1
      
      y = 2
      
      z = 3
      """
        .trimIndent()
    )
  }

  @Test
  fun `concatenate console outputs`() {
    val sourceFiles =
      listOf(
        writePklFile("test1.pkl", "x = 1"),
        writePklFile("test2.pkl", "x = 2"),
        writePklFile("test3.pkl", "x = 3")
      )

    val output =
      evalToConsole(CliEvaluatorOptions(CliBaseOptions(sourceModules = sourceFiles), null, "yaml"))

    assertThat(output).isEqualTo("x: 1\n---\nx: 2\n---\nx: 3\n")
  }

  @Test
  fun `concatenate console outputs - some empty YAML streams`() {
    val sourceFiles =
      listOf(
        writePklFile(
          "test0.pkl",
          "output { value = List(); renderer = new YamlRenderer { isStream = true } }"
        ),
        writePklFile("test1.pkl", "x = 1"),
        writePklFile(
          "test2.pkl",
          "output { value = List(); renderer = new YamlRenderer { isStream = true } }"
        ),
        writePklFile("test3.pkl", "x = 3"),
        writePklFile(
          "test4.pkl",
          "output { value = List(); renderer = new YamlRenderer { isStream = true } }"
        )
      )

    val output =
      evalToConsole(CliEvaluatorOptions(CliBaseOptions(sourceModules = sourceFiles), null, "yaml"))

    assertThat(output).isEqualTo("x: 1\n---\nx: 3\n")
  }

  // prototext can't render `Dynamic`.
  @EnumSource(names = ["TEXTPROTO"], mode = EnumSource.Mode.EXCLUDE)
  @ParameterizedTest(name = "{0} console output ends with newline")
  fun `console output ends with newline`(outputFormat: OutputFormat) {
    val sourceFiles = listOf(writePklFile("test0.pkl", "foo = 0\nbar=\"Baz\""))
    val output =
      evalToConsole(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = sourceFiles),
          null,
          outputFormat.toString()
        )
      )
    assertThat(output).endsWith("\n")
  }

  @Test
  fun `multiple file output writes multiple files to the provided directory`() {
    val contents =
      """
      output {
        files {
          ["foo.pcf"] {
            value = new Dynamic {
              ["bar"] = "baz"
            }
          }
          ["bar/baz.pcf"] {
            value = new Dynamic {
              ["baz"] = "biz"
            }
          }
          ["buz.txt"] {
            text = "buz"
          }
        }
      }
      """
        .trimIndent()
    val sourceFile = writePklFile("test.pkl", contents)
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(sourceFile), workingDir = tempDir),
        outputPath = "my-outputs",
        multipleFileOutputPath = ".my-output/",
      )
    val evaluator = CliEvaluator(options)
    evaluator.run()
    checkOutputFile(
      tempDir.resolve(".my-output/foo.pcf"),
      "foo.pcf",
      """
      ["bar"] = "baz"
      """
        .trimIndent()
    )
    checkOutputFile(
      tempDir.resolve(".my-output/bar/baz.pcf"),
      "baz.pcf",
      """
      ["baz"] = "biz"
      """
        .trimIndent()
    )
    checkOutputFile(tempDir.resolve(".my-output/buz.txt"), "buz.txt", "buz")
  }

  @Test
  fun `multiple file output writes multiple modules to the output path`() {
    val sourceModules =
      listOf(
        writePklFile(
          "test0.pkl",
          """
        output {
          files {
            ["foo.pcf"] {
              value = new Dynamic {
                ["bar"] = "baz"
              }
            }
          }
        }
        """
            .trimIndent(),
        ),
        writePklFile(
          "test1.pkl",
          """
        output {
          files {
            ["bar.pcf"] {
              value = new Dynamic {
                ["bar"] = "baz"
              }
            }
          }
        }
        """
            .trimIndent(),
        )
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = sourceModules, workingDir = tempDir),
        multipleFileOutputPath = ".",
      )
    CliEvaluator(options).run()
    assertThat(tempDir.resolve("foo.pcf")).isRegularFile.hasFileName("foo.pcf")
    assertThat(tempDir.resolve("bar.pcf")).isRegularFile.hasFileName("bar.pcf")
  }

  @Test
  fun `multiple file output throws in case of conflict`() {
    val sourceModules =
      listOf(
        writePklFile(
          "bar.pkl",
          """
        output {
          files {
            ["foo.pcf"] {
              text = "myBar"
            }
          }
        }
      """
            .trimIndent()
        ),
        writePklFile(
          "foo.pkl",
          """
        output {
          files {
            ["foo.pcf"] {
              text = "myFoo"
            }
          }
        }
      """
            .trimIndent()
        ),
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = sourceModules, workingDir = tempDir),
        multipleFileOutputPath = ".",
      )
    assertThrows<CliException> { CliEvaluator(options).run() }
  }

  @Test
  fun `multiple file output writes nothing if output files is null`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        "",
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        multipleFileOutputPath = ".output",
      )
    val output = evalToConsole(options)
    assertThat(output).isEqualTo("")
    assertThat(tempDir.listDirectoryEntries()).hasSize(1)
  }

  @Test
  fun `multiple file output throws if files are written outside the base path`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
        output {
          files {
            ["../foo.txt"] {
              text = "bar"
            }
          }
        }
      """
          .trimIndent()
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        multipleFileOutputPath = ".output"
      )
    assertThatCode { evalToConsole(options) }
      .hasMessageStartingWith("Output file conflict:")
      .hasMessageContaining("which is outside output directory")
  }

  @Test
  fun `multiple file output throws if file path is a directory`() {
    tempDir.resolve(".output/myDir").createDirectories()
    val moduleUris =
      listOf(
        writePklFile(
          "test1.pkl",
          """
        output {
          files {
            ["."] { text = "bar" }
          }
        }
      """
            .trimIndent()
        ),
        writePklFile(
          "test2.pkl",
          """
        output {
          files {
            ["myDir"] { text = "bar" }
          }
        }
      """
            .trimIndent()
        )
      )
    for (moduleUri in moduleUris) {
      val options =
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
          multipleFileOutputPath = ".output"
        )
      assertThatCode { evalToConsole(options) }
        .hasMessageStartingWith("Output file conflict:")
        .hasMessageContaining("which is a directory")
    }
  }

  @Test
  fun `multiple file output throws on conflicting files`() {
    val moduleUris =
      listOf(
        writePklFile(
          "test1.pkl",
          """
        output {
          files {
            ["foo.txt"] { text = "bar" }
          }
        }
      """
            .trimIndent()
        ),
        writePklFile(
          "test2.pkl",
          """
        output {
          files {
            ["foo.txt"] { text = "bar" }
          }
        }
      """
            .trimIndent()
        )
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = moduleUris, workingDir = tempDir),
        multipleFileOutputPath = ".output"
      )
    assertThatCode { evalToConsole(options) }
      .hasMessageContaining("Output file conflict:")
      .hasMessageContaining("resolve to the same file path")
  }

  @Test
  fun `multi-output throws on conflicting files within the same module`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      output {
        files {
          ["foo.txt"] { text = "bar" }
          ["./foo.txt"] { text = "bar" }
        }
      }
    """
          .trimIndent()
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        multipleFileOutputPath = ".output"
      )
    assertThatCode { evalToConsole(options) }
      .hasMessageContaining("Output file conflict:")
      .hasMessageContaining("resolve to the same file path")
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `multiple-file output throws when using invalid Windows characters`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      output {
        files {
          ["foo:bar"] { text = "bar" }
        }
      }
    """
          .trimIndent()
      )

    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        multipleFileOutputPath = ".output"
      )
    assertThatCode { evalToConsole(options) }
      .hasMessageContaining("Path spec `foo:bar` contains illegal character `:`.")
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `multiple-file output - cannot use backslash as dir separator on Windows`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      output {
        files {
          ["foo\\bar"] { text = "bar" }
        }
      }
    """
          .trimIndent()
      )

    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        multipleFileOutputPath = ".output"
      )
    assertThatCode { evalToConsole(options) }
      .hasMessageContaining("Path spec `foo\\bar` contains illegal character `\\`.")
  }

  @Test
  fun `evaluate output expression`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      foo {
        bar = 1
      }
    """
          .trimIndent()
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        expression = "foo",
      )
    val buffer = StringWriter()
    CliEvaluator(options, consoleWriter = buffer).run()
    assertThat(buffer.toString())
      .isEqualTo(
        """
      new Dynamic { bar = 1 }
    """
          .trimIndent()
      )
  }

  @Test
  fun `evaluate output expression - custom toString()`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      class Person {
        name: String

        function toString() = "Person(\(name))"
      }
      person: Person = new { name = "Frodo" }
    """
          .trimIndent()
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        expression = "person",
      )
    val buffer = StringWriter()
    CliEvaluator(options, consoleWriter = buffer).run()
    assertThat(buffer.toString()).isEqualTo("Person(Frodo)")
  }

  @Test
  fun `evaluate output expression - nested structure`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      person {
        friend { name = "Bilbo" }
      }
    """
          .trimIndent()
      )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
        expression = "person",
      )
    val buffer = StringWriter()
    CliEvaluator(options, consoleWriter = buffer).run()
    assertThat(buffer.toString()).isEqualTo("new Dynamic { friend { name = \"Bilbo\" } }")
  }

  @Test
  fun `skip PklProject file`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      res = 1
    """
          .trimIndent()
      )
    writePklFile(
      "PklProject",
      """
      amends "pkl:Project"
      
      package = throw("invalid project package")
    """
        .trimIndent()
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir, noProject = true),
      )
    val buffer = StringWriter()
    CliEvaluator(options, consoleWriter = buffer).run()
    assertThat(buffer.toString()).isEqualTo("res = 1\n")
  }

  @Test
  fun `settings from PklProject file`() {

    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      res = read*("env:**")
    """
          .trimIndent()
      )
    writePklFile(
      "PklProject",
      // language=Pkl
      """
      amends "pkl:Project"
      
      evaluatorSettings {
        env {
          ["foo"] = "foo"
          ["bar"] = "bar"
        }
      }
    """
        .trimIndent()
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(sourceModules = listOf(moduleUri), workingDir = tempDir),
      )
    val buffer = StringWriter()
    CliEvaluator(options, consoleWriter = buffer).run()
    assertThat(buffer.toString())
      .isEqualTo(
        """
      res {
        ["env:bar"] = "bar"
        ["env:foo"] = "foo"
      }
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `setting noCache will skip writing to the cache dir`() {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      import "package://localhost:0/birds@0.5.0#/catalog/Swallow.pkl"
      
      res = Swallow
    """
          .trimIndent()
      )
    val buffer = StringWriter()
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(moduleUri),
          workingDir = tempDir,
          moduleCacheDir = tempDir,
          noCache = true,
          caCertificates = listOf(FileTestUtils.selfSignedCertificate),
          testPort = packageServer.port
        ),
      )
    CliEvaluator(options, consoleWriter = buffer).run()
    assertThat(buffer.toString())
      .isEqualTo(
        """
      res {
        name = "Swallow"
        favoriteFruit {
          name = "Apple"
        }
      }
      
    """
          .trimIndent()
      )
    assertThat(tempDir.resolve("package-2")).doesNotExist()
  }

  @Test
  fun `gives decent error message if certificate file contains random text`() {
    val certsFile = tempDir.writeFile("random.pem", "RANDOM")
    val err = assertThrows<CliException> { evalModuleThatImportsPackage(certsFile) }
    assertThat(err)
      .hasMessageContaining("Error parsing CA certificate file `${certsFile.pathString}`:")
      .hasMessageContaining("No certificate data found")
      .hasMessageNotContainingAny("java.", "sun.") // class names have been filtered out
  }

  @Test
  fun `gives decent error message if certificate file is emtpy`(@TempDir tempDir: Path) {
    val emptyCerts = tempDir.writeEmptyFile("empty.pem")
    val err = assertThrows<CliException> { evalModuleThatImportsPackage(emptyCerts) }
    assertThat(err).hasMessageContaining("CA certificate file `${emptyCerts.pathString}` is empty.")
  }

  @Test
  fun `gives decent error message if certificate cannot be parsed`(@TempDir tempDir: Path) {
    val invalidCerts = FileTestUtils.writeCertificateWithMissingLines(tempDir)
    val err = assertThrows<CliException> { evalModuleThatImportsPackage(invalidCerts) }
    assertThat(err)
      // no assert for detail message because it differs between JDK implementations
      .hasMessageContaining("Error parsing CA certificate file `${invalidCerts.pathString}`:")
      .hasMessageNotContainingAny("java.", "sun.") // class names have been filtered out
  }

  @Test
  fun `gives decent error message if CLI doesn't have the required CA certificate`() {
    val err = assertThrows<CliException> { evalModuleThatImportsPackage(null, packageServer.port) }
    assertThat(err)
      .hasMessageContaining("Error during SSL handshake with host `localhost`:")
      .hasMessageContaining("unable to find valid certification path to requested target")
      .hasMessageNotContainingAny("java.", "sun.") // class names have been filtered out
  }

  @Test
  fun `eval http module from proxy`(wwRuntimeInfo: WireMockRuntimeInfo) {
    stubFor(
      get(urlEqualTo("/bar.pkl")).withHost(equalTo("not.a.valid.host")).willReturn(ok("foo = 1"))
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(URI("http://not.a.valid.host/bar.pkl")),
          httpProxy = URI("http://localhost:${wwRuntimeInfo.httpPort}"),
          allowedModules = SecurityManagers.defaultAllowedModules + Pattern.compile("http:"),
        ),
      )
    val output = evalToConsole(options)
    assertThat(output).isEqualTo("foo = 1\n")
  }

  @Test
  fun `eval https -- no proxy`(wwRuntimeInfo: WireMockRuntimeInfo) {
    // pick an address on the local machine so we can be sure this test is not making any outbound
    // connections.
    val openPort = ServerSocket(0).use { it.localPort }
    val targetAddress = "https://127.0.0.1:$openPort"
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          // use loopback address to prevent test from making outbound http connection.
          sourceModules = listOf(URI("$targetAddress/foo.pkl")),
          httpProxy = URI(wwRuntimeInfo.httpBaseUrl),
          httpNoProxy = listOf("*"),
          allowedModules = SecurityManagers.defaultAllowedModules + Pattern.compile("http:"),
        )
      )
    assertThatCode { evalToConsole(options) }
      .hasMessageContaining("I/O error loading module `$targetAddress/foo.pkl`")
  }

  @Test
  @Disabled // TODO: figure out why this is failing.
  fun `eval package from proxy`(wwRuntimeInfo: WireMockRuntimeInfo) {
    stubFor(
      any(anyUrl()).willReturn(aResponse().proxiedFrom("https://localhost:${packageServer.port}"))
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(URI("package://localhost:1/birds@0.5.0#/catalog/Ostritch.pkl")),
          noCache = true,
          httpProxy = URI(wwRuntimeInfo.httpBaseUrl),
          caCertificates = listOf(FileTestUtils.selfSignedCertificate),
          allowedModules = SecurityManagers.defaultAllowedModules + Pattern.compile("http:")
        )
      )
    val output = evalToConsole(options)
    assertThat(output)
      .isEqualTo(
        """
      name = "Ostritch"

      favoriteFruit {
        name = "Orange"
      }

    """
          .trimIndent()
      )
    verify(getRequestedFor(urlEqualTo("birds@0.5.0")))
    verify(getRequestedFor(urlEqualTo("fruit@1.0.5")))
  }

  @Test
  fun `eval http module from proxy -- configured in settings`(
    @TempDir tempDir: Path,
    wwRuntimeInfo: WireMockRuntimeInfo
  ) {
    val settingsModule =
      tempDir.writeFile(
        "settings.pkl",
        """
      amends "pkl:settings"

      http {
        proxy {
          address = "${wwRuntimeInfo.httpBaseUrl}"
        }
      }
      """
          .trimIndent()
      )

    stubFor(
      get(urlEqualTo("/bar.pkl")).withHost(equalTo("not.a.valid.host")).willReturn(ok("foo = 1"))
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(URI("http://not.a.valid.host/bar.pkl")),
          settings = settingsModule.toUri(),
          allowedModules = SecurityManagers.defaultAllowedModules + Pattern.compile("http:"),
        ),
      )
    val output = evalToConsole(options)
    assertThat(output).isEqualTo("foo = 1\n")
  }

  @Test
  fun `eval http module from proxy -- configured in PklProject`(
    @TempDir tempDir: Path,
    wwRuntimeInfo: WireMockRuntimeInfo
  ) {
    tempDir.writeFile(
      "PklProject",
      """
      amends "pkl:Project"

      evaluatorSettings {
        http {
          proxy {
            address = "${wwRuntimeInfo.httpBaseUrl}"
          }
        }
      }
      """
        .trimIndent()
    )

    stubFor(
      get(urlEqualTo("/bar.pkl")).withHost(equalTo("not.a.valid.host")).willReturn(ok("foo = 1"))
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(URI("http://not.a.valid.host/bar.pkl")),
          allowedModules = SecurityManagers.defaultAllowedModules + Pattern.compile("http:"),
          projectDir = tempDir
        ),
      )
    val output = evalToConsole(options)
    assertThat(output).isEqualTo("foo = 1\n")
  }

  @Test
  fun `eval http module from proxy -- PklProject beats user settings`(
    @TempDir tempDir: Path,
    wwRuntimeInfo: WireMockRuntimeInfo
  ) {
    val projectDir = tempDir.resolve("my-project")
    projectDir.writeFile(
      "PklProject",
      """
      amends "pkl:Project"

      evaluatorSettings {
        http {
          proxy {
            address = "${wwRuntimeInfo.httpBaseUrl}"
          }
        }
      }
      """
        .trimIndent()
    )
    val homeDir = tempDir.resolve("my-home")
    homeDir.writeFile(
      "settings.pkl",
      """
        amends "pkl:settings"

        http {
          proxy {
            address = "http://invalid.proxy.address"
          }
        }
      """
        .trimIndent()
    )
    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(URI("http://not.a.valid.host/bar.pkl")),
          allowedModules = SecurityManagers.defaultAllowedModules + Pattern.compile("http:"),
          projectDir = projectDir,
          settings = homeDir.resolve("settings.pkl").toUri()
        ),
      )
    stubFor(get(anyUrl()).willReturn(ok("result = 1")))
    val output = evalToConsole(options)
    assertThat(output).isEqualTo("result = 1\n")
  }

  @Test
  fun `eval file with non-ASCII name`() {
    val dir = tempDir.resolve("🤬").createDirectory()
    val file = writePklFile(dir.resolve("日本語.pkl").toString(), """
      日本語 = "Japanese language"
      readDir = read(".").text
      readDirFile = read("file:$tempDir/🤬").text
      readOne = read("日本語.pkl").text.split("\n").first
      readOneFile = read("file:$tempDir/🤬/日本語.pkl").text.split("\n").first
      readGlob = read*("./日*.pkl").keys
      readGlobFile = read*("file:$tempDir/**/*.pkl").keys.map((it) -> it.replaceAll("$tempDir", ""))
      importOne = import("日本語.pkl").readOne
      importOneFile = import("file:$tempDir/🤬/日本語.pkl").日本語
      importGlob = import*("./日*.pkl").keys
      importGlobFile = import*("file:$tempDir/**/*.pkl").keys.map((it) -> it.replaceAll("$tempDir", ""))
    """.trimIndent())
    val output = 
      evalToConsole(
        CliEvaluatorOptions(
          CliBaseOptions(sourceModules = listOf(file)),
        )
      )

    assertThat(output).isEqualTo("""日本語 = "Japanese language"
readDir = ""${'"'}
  日本語.pkl
  
  ""${'"'}
readDirFile = ""${'"'}
  日本語.pkl
  
  ""${'"'}
readOne = "日本語 = \"Japanese language\""
readOneFile = "日本語 = \"Japanese language\""
readGlob = Set("./日本語.pkl")
readGlobFile = Set("file:/🤬/日本語.pkl")
importOne = "日本語 = \"Japanese language\""
importOneFile = "Japanese language"
importGlob = Set("./日本語.pkl")
importGlobFile = Set("file:/🤬/日本語.pkl")
""")
  }

  private fun evalModuleThatImportsPackage(certsFile: Path?, testPort: Int = -1) {
    val moduleUri =
      writePklFile(
        "test.pkl",
        """
      import "package://localhost:0/birds@0.5.0#/catalog/Swallow.pkl"
      
      res = Swallow
    """
      )

    val options =
      CliEvaluatorOptions(
        CliBaseOptions(
          sourceModules = listOf(moduleUri),
          caCertificates = buildList { if (certsFile != null) add(certsFile) },
          workingDir = tempDir,
          noCache = true,
          testPort = testPort
        ),
      )
    CliEvaluator(options).run()
  }

  private fun writePklFile(fileName: String, contents: String = defaultContents): URI {
    tempDir.resolve(fileName).createParentDirectories()
    return tempDir.resolve(fileName).writeString(contents).toUri()
  }

  private fun evalToFiles(options: CliEvaluatorOptions): List<Path> {
    val evaluator =
      CliEvaluator(
        options.copy(
          outputPath = options.outputPath ?: "%{moduleDir}/%{moduleName}.%{outputFormat}"
        )
      )

    evaluator.run()
    return evaluator.fileOutputPaths!!.values.toList()
  }

  private fun evalToConsole(options: CliEvaluatorOptions): String {
    val reader = StringReader("")
    val writer = StringWriter()
    CliEvaluator(options, reader, writer).run()
    return writer.toString()
  }

  private fun checkOutputFile(file: Path, name: String, contents: String) {
    assertThat(file).isRegularFile.hasFileName(name)
    assertThat(file.readString().trim()).isEqualTo(contents.trim())
  }
}
