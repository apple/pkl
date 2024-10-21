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

import java.io.File
import java.io.StringWriter
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.createDirectories
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.CliTestOptions
import org.pkl.commons.readString
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.writeString
import org.pkl.core.util.IoUtils

class CliProjectPackagerTest {
  companion object {
    private val packageServer = PackageServer()

    @AfterAll
    @JvmStatic
    fun afterAll() {
      packageServer.close()
    }
  }

  @Test
  fun `missing PklProject when inferring a project dir`(@TempDir tempDir: Path) {
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
      )
    val err = assertThrows<CliException> { packager.run() }
    assertThat(err).hasMessageStartingWith("No project visible to the working directory.")
  }

  @Test
  fun `missing PklProject when explicit dir is provided`(@TempDir tempDir: Path) {
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
      )
    val err = assertThrows<CliException> { packager.run() }
    assertThat(err).hasMessageStartingWith("Directory $tempDir does not contain a PklProject file.")
  }

  @Test
  fun `PklProject missing package section`(@TempDir tempDir: Path) {
    tempDir
      .resolve("PklProject")
      .writeString(
        """
      amends "pkl:Project"
    """
          .trimIndent()
      )
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
      )
    val err = assertThrows<CliException> { packager.run() }
    assertThat(err)
      .hasMessageStartingWith("No package was declared in project `${tempDir.toUri()}PklProject`.")
  }

  @Test
  fun `failing apiTests`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "myTest.pkl",
      """
      amends "pkl:test"
      
      facts {
        ["1 == 2"] {
          1 == 2
        }
      }
    """
        .trimIndent()
    )
    tempDir.writeFile(
      "PklProject",
      """
      amends "pkl:Project"
      
      package {
        name = "mypackage"
        version = "1.0.0"
        baseUri = "package://example.com/mypackage"
        packageZipUrl = "https://foo.com"
        apiTests { "myTest.pkl" }
      }
    """
        .trimIndent()
    )
    val buffer = StringWriter()
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = buffer
      )
    val err = assertThrows<CliException> { packager.run() }
    assertThat(err).hasMessageContaining("because its API tests are failing")
    assertThat(buffer.toString()).contains("1 == 2")
  }

  @Test
  fun `passing apiTests`(@TempDir tempDir: Path) {
    tempDir
      .resolve("myTest.pkl")
      .writeString(
        """
        amends "pkl:test"
        
        facts {
          ["1 == 1"] {
            1 == 1
          }
        }
      """
          .trimIndent()
      )
    tempDir
      .resolve("PklProject")
      .writeString(
        """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
          apiTests { "myTest.pkl" }
        }
      """
          .trimIndent()
      )
    val buffer = StringWriter()
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = buffer
      )
    packager.run()
  }

  @Test
  fun `apiTests that import dependencies`(@TempDir tempDir: Path) {
    val cacheDir = tempDir.resolve("cache")
    val projectDir = tempDir.resolve("myProject").createDirectories()
    PackageServer.populateCacheDir(cacheDir)
    projectDir
      .resolve("myTest.pkl")
      .writeString(
        """
        amends "pkl:test"
        import "@birds/Bird.pkl"
        
        examples {
          ["Bird"] {
            new Bird { name = "Finch"; favoriteFruit { name = "Tangerine" } }
          }
        }
      """
          .trimIndent()
      )
    projectDir
      .resolve("PklProject")
      .writeString(
        """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
          apiTests { "myTest.pkl" }
        }

        dependencies {
          ["birds"] {
            uri = "package://localhost:0/birds@0.5.0"
          }
        }
      """
          .trimIndent()
      )
    projectDir
      .resolve("PklProject.deps.json")
      .writeString(
        """
        {
          "schemaVersion": 1,
          "resolvedDependencies": {
            "package://localhost:0/birds@0": {
              "type": "remote",
              "uri": "projectpackage://localhost:0/birds@0.5.0",
              "checksums": {
                "sha256": "04eec465b217fb9779489525d26e9b587e5e47ff4d584c7673a450109715bc31"
              }
            },
            "package://localhost:0/fruit@1": {
              "type": "remote",
              "uri": "projectpackage://localhost:0/fruit@1.0.5",
              "checksums": {
                "sha256": "${PackageServer.FRUIT_SHA}"
              }
            }
          }
        }
      """
          .trimIndent()
      )
    val buffer = StringWriter()
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = projectDir, moduleCacheDir = cacheDir),
        listOf(projectDir),
        CliTestOptions(),
        ".out",
        skipPublishCheck = true,
        consoleWriter = buffer
      )
    packager.run()
  }

  @Test
  fun `generate package`(@TempDir tempDir: Path) {
    val fooPkl =
      tempDir.writeFile(
        "a/b/foo.pkl",
        """
        module foo
        
        name: String
      """
          .trimIndent()
      )

    val fooTxt =
      tempDir.writeFile(
        "c/d/foo.txt",
        """
        foo
        bar
        baz
      """
          .trimIndent()
      )

    tempDir
      .resolve("PklProject")
      .writeString(
        """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
      """
          .trimIndent()
      )
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = StringWriter()
      )
    packager.run()
    val expectedMetadata = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0")
    val expectedMetadataChecksum = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0.sha256")
    val expectedArchive = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0.zip")
    val expectedArchiveChecksum = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0.zip.sha256")
    assertThat(expectedMetadata).exists()
    assertThat(expectedMetadata)
      .hasContent(
        """
      {
        "name": "mypackage",
        "packageUri": "package://example.com/mypackage@1.0.0",
        "version": "1.0.0",
        "packageZipUrl": "https://foo.com",
        "packageZipChecksums": {
          "sha256": "e83b67722ea17ba41717ce6e99ae8ee02d66df6294bd319ce403075b1071c3e0"
        },
        "dependencies": {},
        "authors": []
      }
    """
          .trimIndent()
      )
    assertThat(expectedArchive).exists()
    assertThat(expectedArchive.zipFilePaths())
      .hasSameElementsAs(listOf("/", "/c", "/c/d", "/c/d/foo.txt", "/a", "/a/b", "/a/b/foo.pkl"))
    assertThat(expectedMetadataChecksum)
      .hasContent("72ab32b27393bde5f316b00f184faae919378e4d7643872c605f681b14b647bf")
    assertThat(expectedArchiveChecksum)
      .hasContent("e83b67722ea17ba41717ce6e99ae8ee02d66df6294bd319ce403075b1071c3e0")
    FileSystems.newFileSystem(URI("jar:" + expectedArchive.toUri()), mutableMapOf<String, String>())
      .use { fs ->
        assertThat(fs.getPath("a/b/foo.pkl")).hasSameTextualContentAs(fooPkl)
        assertThat(fs.getPath("c/d/foo.txt")).hasSameTextualContentAs(fooTxt)
      }
  }

  @Test
  fun `generate package with excludes`(@TempDir tempDir: Path) {
    tempDir.apply {
      writeEmptyFile("a/b/c/d.bin")
      writeEmptyFile("input/foo/bar.txt")
      writeEmptyFile("z.bin")
      writeEmptyFile("main.pkl")
      writeEmptyFile("main.test.pkl")
      writeEmptyFile("child/main.pkl")
      writeEmptyFile("child/main.test.pkl")
      writeEmptyFile("examples/Workflow.pkl")
      writeEmptyFile("examples/Ex1.pkl")
      writeEmptyFile("tests/Test1.pkl")
      writeEmptyFile("tests/integration/TestIng1.pkl")
    }

    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
          exclude {
            "*.bin"
            "child/main.pkl"
            "*.test.pkl"
            "examples/Ex1.pkl"
            "tests/**"
          }
        }
      """
        .trimIndent()
    )
    CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = StringWriter()
      )
      .run()
    val expectedArchive = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0.zip")
    assertThat(expectedArchive.zipFilePaths())
      .hasSameElementsAs(
        listOf(
          "/",
          "/examples",
          "/examples/Workflow.pkl",
          "/input",
          "/input/foo",
          "/input/foo/bar.txt",
          "/main.pkl"
        )
      )
  }

  @Test
  fun `generate packages with local dependencies`(@TempDir tempDir: Path) {
    val projectDir = tempDir.resolve("project")
    val project2Dir = tempDir.resolve("project2")
    projectDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
        
        dependencies {
          ["birds"] {
            uri = "package://localhost:0/birds@0.5.0"
          }
          ["project2"] = import("../project2/PklProject")
        }
      """
        .trimIndent()
    )
    projectDir.writeFile(
      "PklProject.deps.json",
      """
        {
          "schemaVersion": 1,
          "resolvedDependencies": {
            "package://localhost:0/birds@0": {
              "type": "remote",
              "uri": "projectpackage://localhost:0/birds@0.5.0",
              "checksums": {
                "sha256": "${PackageServer.BIRDS_SHA}"
              }
            },
            "package://localhost:0/project2@5": {
              "type": "local",
              "uri": "projectpackage://localhost:0/project2@5.0.0",
              "path": "../project2"
            }
          }
        }
      """
        .trimIndent()
    )

    project2Dir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "project2"
          baseUri = "package://localhost:0/project2"
          version = "5.0.0"
          packageZipUrl = "https://foo.com/project2.zip"
        }
      """
        .trimIndent()
    )
    project2Dir.writeFile(
      "PklProject.deps.json",
      """
        {
          "schemaVersion": 1,
          "resolvedDependencies": {}
        }
      """
        .trimIndent()
    )

    CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(projectDir, project2Dir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = StringWriter()
      )
      .run()
    val expectedMetadata = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0")
    assertThat(expectedMetadata).exists()
    assertThat(expectedMetadata)
      .hasContent(
        """
      {
        "name": "mypackage",
        "packageUri": "package://example.com/mypackage@1.0.0",
        "version": "1.0.0",
        "packageZipUrl": "https://foo.com",
        "packageZipChecksums": {
          "sha256": "8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85"
        },
        "dependencies": {
          "birds": {
            "uri": "package://localhost:0/birds@0.5.0",
            "checksums": {
              "sha256": "${PackageServer.BIRDS_SHA}"
            }
          },
          "project2": {
            "uri": "package://localhost:0/project2@5.0.0",
            "checksums": {
              "sha256": "981787869571330b2f609a94a5912466990ce00e3fa94e7f290c2f99a6d5e5ed"
            }
          }
        },
        "authors": []
      }
    """
          .trimIndent()
      )
    val project2Metadata = tempDir.resolve(".out/project2@5.0.0/project2@5.0.0")
    assertThat(project2Metadata).exists()
    assertThat(project2Metadata.readString())
      .isEqualTo(
        """
    {
      "name": "project2",
      "packageUri": "package://localhost:0/project2@5.0.0",
      "version": "5.0.0",
      "packageZipUrl": "https://foo.com/project2.zip",
      "packageZipChecksums": {
        "sha256": "8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85"
      },
      "dependencies": {},
      "authors": []
    }
    """
          .trimIndent()
      )
  }

  @Test
  fun `generate package with local dependencies fails if local dep is not included for packaging`(
    @TempDir tempDir: Path
  ) {
    val projectDir = tempDir.resolve("project")
    val project2Dir = tempDir.resolve("project2")
    projectDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
        
        dependencies {
          ["birds"] {
            uri = "package://localhost:0/birds@0.5.0"
          }
          ["project2"] = import("../project2/PklProject")
        }
      """
        .trimIndent()
    )
    projectDir.writeFile(
      "PklProject.deps.json",
      """
        {
          "schemaVersion": 1,
          "resolvedDependencies": {
            "package://localhost:0/birds@0": {
              "type": "remote",
              "uri": "projectpackage://localhost:0/birds@0.5.0",
              "checksums": {
                "sha256": "0a5ad2dc13f06f73f96ba94e8d01d48252bc934e2de71a837620ca0fef8a7453"
              }
            },
            "package://localhost:0/project2@5": {
              "type": "local",
              "uri": "projectpackage://localhost:0/project2@5.0.0",
              "path": "../project2"
            }
          }
        }
      """
        .trimIndent()
    )

    project2Dir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "project2"
          baseUri = "package://localhost:0/project2"
          version = "5.0.0"
          packageZipUrl = "https://foo.com/project2.zip"
        }
      """
        .trimIndent()
    )
    project2Dir.writeFile(
      "PklProject.deps.json",
      """
        {
          "schemaVersion": 1,
          "resolvedDependencies": {}
        }
      """
        .trimIndent()
    )
    assertThatCode {
        CliProjectPackager(
            CliBaseOptions(workingDir = tempDir),
            listOf(projectDir),
            CliTestOptions(),
            ".out/%{name}@%{version}",
            skipPublishCheck = true,
            consoleWriter = StringWriter()
          )
          .run()
      }
      .hasMessageContaining("which is not included for packaging")
  }

  @Test
  fun `import path verification -- relative path outside project dir`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "main.pkl",
      """
        import "../foo.pkl"
  
        res = foo
      """
        .trimIndent()
    )
    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val e =
      assertThrows<CliException> {
        CliProjectPackager(
            CliBaseOptions(workingDir = tempDir),
            listOf(tempDir),
            CliTestOptions(),
            ".out/%{name}@%{version}",
            skipPublishCheck = true,
            consoleWriter = StringWriter()
          )
          .run()
      }
    assertThat(e.message)
      .startsWith(
        """
      –– Pkl Error ––
      Path `../foo.pkl` includes path segments that are outside the project root directory.
      
      1 | import "../foo.pkl"
                 ^^^^^^^^^^^^
    """
          .trimIndent()
      )
  }

  // Absolute path imports on Windows must use an absolute URI (e.g. file:///C:/Foo/Bar), because
  // they must contain drive letters, which conflict with schemes.
  // We skip validation for absolute URIs, so effectively we skip this check on Windows.
  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `import path verification -- absolute import from root dir`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "main.pkl",
      """
        import "$tempDir/foo.pkl"
  
        res = foo
      """
        .trimIndent()
    )
    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val e =
      assertThrows<CliException> {
        CliProjectPackager(
            CliBaseOptions(workingDir = tempDir),
            listOf(tempDir),
            CliTestOptions(),
            ".out/%{name}@%{version}",
            skipPublishCheck = true,
            consoleWriter = StringWriter()
          )
          .run()
      }
    assertThat(e.message)
      .startsWith(
        """
      –– Pkl Error ––
      Path `$tempDir/foo.pkl` includes path segments that are outside the project root directory.
    """
          .trimIndent()
      )
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `import path verification -- absolute read from root dir`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "main.pkl",
      """
        res = read("$tempDir/foo.pkl")
      """
        .trimIndent()
    )
    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val e =
      assertThrows<CliException> {
        CliProjectPackager(
            CliBaseOptions(workingDir = tempDir),
            listOf(tempDir),
            CliTestOptions(),
            ".out/%{name}@%{version}",
            skipPublishCheck = true,
            consoleWriter = StringWriter()
          )
          .run()
      }
    assertThat(e.message)
      .startsWith(
        """
      –– Pkl Error ––
      Path `$tempDir/foo.pkl` includes path segments that are outside the project root directory.
    """
          .trimIndent()
      )
  }

  @Test
  fun `import path verification -- passing`(@TempDir tempDir: Path) {
    tempDir.writeFile(
      "foo/bar.pkl",
      """
        import "baz.pkl"
      """
        .trimIndent()
    )
    tempDir.writeFile(
      "PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = StringWriter()
      )
      .run()
  }

  @Test
  fun `multiple projects`(@TempDir tempDir: Path) {
    tempDir.writeFile("project1/main.pkl", "res = 1")
    tempDir.writeFile(
      "project1/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "project1"
          version = "1.0.0"
          baseUri = "package://example.com/project1"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    tempDir.writeFile("project2/main2.pkl", "res = 2")
    tempDir.writeFile(
      "project2/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "project2"
          version = "2.0.0"
          baseUri = "package://example.com/project2"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val out = StringWriter()
    CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir.resolve("project1"), tempDir.resolve("project2")),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = out
      )
      .run()
    val sep = File.separatorChar
    assertThat(out.toString())
      .isEqualToNormalizingNewlines(
        """
      .out${sep}project1@1.0.0${sep}project1@1.0.0.zip
      .out${sep}project1@1.0.0${sep}project1@1.0.0.zip.sha256
      .out${sep}project1@1.0.0${sep}project1@1.0.0
      .out${sep}project1@1.0.0${sep}project1@1.0.0.sha256
      .out${sep}project2@2.0.0${sep}project2@2.0.0.zip
      .out${sep}project2@2.0.0${sep}project2@2.0.0.zip.sha256
      .out${sep}project2@2.0.0${sep}project2@2.0.0
      .out${sep}project2@2.0.0${sep}project2@2.0.0.sha256

    """
          .trimIndent()
      )
    assertThat(tempDir.resolve(".out/project1@1.0.0/project1@1.0.0.zip").zipFilePaths())
      .hasSameElementsAs(listOf("/", "/main.pkl"))
    assertThat(tempDir.resolve(".out/project2@2.0.0/project2@2.0.0.zip").zipFilePaths())
      .hasSameElementsAs(listOf("/", "/main2.pkl"))
  }

  @Test
  fun `publish checks`(@TempDir tempDir: Path) {
    tempDir.writeFile("project/main.pkl", "res = 1")
    tempDir.writeFile(
      "project/PklProject",
      // intentionally conflict with localhost:0/birds@0.5.0 from our test fixtures
      """
        amends "pkl:Project"
        
        package {
          name = "birds"
          version = "0.5.0"
          baseUri = "package://localhost:0/birds"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val e =
      assertThrows<CliException> {
        CliProjectPackager(
            CliBaseOptions(
              workingDir = tempDir,
              caCertificates = listOf(FileTestUtils.selfSignedCertificate),
              testPort = packageServer.port
            ),
            listOf(tempDir.resolve("project")),
            CliTestOptions(),
            ".out/%{name}@%{version}",
            skipPublishCheck = false,
            consoleWriter = StringWriter()
          )
          .run()
      }
    assertThat(e)
      .hasMessageStartingWith(
        """
      Package `package://localhost:0/birds@0.5.0` was already published with different contents.
      
      Computed checksum: aa8c883841db22e92794f4708b01dc905b5da77645b7dfb5b22a73da8c347db1
      Published checksum: ${PackageServer.BIRDS_SHA}
    """
          .trimIndent()
      )
  }

  @Test
  fun `publish check when package is not yet published`(@TempDir tempDir: Path) {
    tempDir.writeFile("project/main.pkl", "res = 1")
    tempDir.writeFile(
      "project/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mangos"
          version = "1.0.0"
          baseUri = "package://localhost:0/mangos"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val out = StringWriter()
    CliProjectPackager(
        CliBaseOptions(
          workingDir = tempDir,
          caCertificates = listOf(FileTestUtils.selfSignedCertificate),
          testPort = packageServer.port
        ),
        listOf(tempDir.resolve("project")),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = false,
        consoleWriter = out
      )
      .run()
    val sep = File.separatorChar
    assertThat(out.toString())
      .isEqualToNormalizingNewlines(
        """
      .out${sep}mangos@1.0.0${sep}mangos@1.0.0.zip
      .out${sep}mangos@1.0.0${sep}mangos@1.0.0.zip.sha256
      .out${sep}mangos@1.0.0${sep}mangos@1.0.0
      .out${sep}mangos@1.0.0${sep}mangos@1.0.0.sha256

    """
          .trimIndent()
      )
  }

  @Test
  fun `generate annotations`(@TempDir tempDir: Path) {
    tempDir
      .resolve("PklProject")
      .writeString(
        """
        @Unlisted
        @Deprecated { since = "0.26.1"; message = "do not use" }
        @ModuleInfo { minPklVersion = "0.26.0" }
        amends "pkl:Project"
        
        package {
          name = "mypackage"
          version = "1.0.0"
          baseUri = "package://example.com/mypackage"
          packageZipUrl = "https://foo.com"
        }
      """
          .trimIndent()
      )
    val packager =
      CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = true,
        consoleWriter = StringWriter()
      )
    packager.run()
    val expectedMetadata = tempDir.resolve(".out/mypackage@1.0.0/mypackage@1.0.0")
    assertThat(expectedMetadata).exists()
    assertThat(expectedMetadata)
      .hasContent(
        """
      {
        "name": "mypackage",
        "packageUri": "package://example.com/mypackage@1.0.0",
        "version": "1.0.0",
        "packageZipUrl": "https://foo.com",
        "packageZipChecksums": {
          "sha256": "8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85"
        },
        "dependencies": {},
        "authors": [],
        "annotations": [
          {
            "moduleName": "pkl.base",
            "class": "Unlisted",
            "moduleUri": "pkl:base",
            "properties": {}
          },
          {
            "moduleName": "pkl.base",
            "class": "Deprecated",
            "moduleUri": "pkl:base",
            "properties": {
              "since": "0.26.1",
              "message": "do not use",
              "replaceWith": null
            }
          },
          {
            "moduleName": "pkl.base",
            "class": "ModuleInfo",
            "moduleUri": "pkl:base",
            "properties": {
              "minPklVersion": "0.26.0"
            }
          }
        ]
      }
    """
          .trimIndent()
      )
  }

  private fun Path.zipFilePaths(): List<String> {
    return FileSystems.newFileSystem(URI("jar:${toUri()}"), emptyMap<String, String>()).use { fs ->
      Files.walk(fs.getPath("/")).map(IoUtils::toNormalizedPathString).collect(Collectors.toList())
    }
  }
}
