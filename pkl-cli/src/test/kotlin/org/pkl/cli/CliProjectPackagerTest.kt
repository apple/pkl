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

import java.io.StringWriter
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.createDirectories
import kotlin.test.Ignore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliException
import org.pkl.commons.cli.CliTestOptions
import org.pkl.commons.readString
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.writeString
import org.pkl.core.runtime.CertificateUtils

class CliProjectPackagerTest {
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
            uri = "package://localhost:12110/birds@0.5.0"
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
            "package://localhost:12110/birds@0": {
              "type": "remote",
              "uri": "projectpackage://localhost:12110/birds@0.5.0",
              "checksums": {
                "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
              }
            },
            "package://localhost:12110/fruit@1": {
              "type": "remote",
              "uri": "projectpackage://localhost:12110/fruit@1.0.5",
              "checksums": {
                "sha256": "b4ea243de781feeab7921227591e6584db5d0673340f30fab2ffe8ad5c9f75f5"
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
          "sha256": "7f515fbc4b229ba171fac78c7c3f2c2e68e2afebae8cfba042b12733943a2813"
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
      .hasContent("203ef387f217a3caee7f19819ef2b50926929269241cb7b3a29d95237b2c7f4b")
    assertThat(expectedArchiveChecksum)
      .hasContent("7f515fbc4b229ba171fac78c7c3f2c2e68e2afebae8cfba042b12733943a2813")
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
          "/a",
          "/a/b",
          "/a/b/c",
          "/child",
          "/input",
          "/input/foo",
          "/input/foo/bar.txt",
          "/main.pkl",
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
            uri = "package://localhost:12110/birds@0.5.0"
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
            "package://localhost:12110/birds@0": {
              "type": "remote",
              "uri": "projectpackage://localhost:12110/birds@0.5.0",
              "checksums": {
                "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
              }
            },
            "package://localhost:12110/project2@5": {
              "type": "local",
              "uri": "projectpackage://localhost:12110/project2@5.0.0",
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
          baseUri = "package://localhost:12110/project2"
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
          "sha256": "7d08a65078e0bfc382c16fe1bb94546ab9a11e6f551087f362a4515ca98102fc"
        },
        "dependencies": {
          "birds": {
            "uri": "package://localhost:12110/birds@0.5.0",
            "checksums": {
              "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
            }
          },
          "project2": {
            "uri": "package://localhost:12110/project2@5.0.0",
            "checksums": {
              "sha256": "ddebb806e5b218ebb1a2baa14ad206b46e7a0c1585fa9863a486c75592bc8b16"
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
      "packageUri": "package://localhost:12110/project2@5.0.0",
      "version": "5.0.0",
      "packageZipUrl": "https://foo.com/project2.zip",
      "packageZipChecksums": {
        "sha256": "7d08a65078e0bfc382c16fe1bb94546ab9a11e6f551087f362a4515ca98102fc"
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
            uri = "package://localhost:12110/birds@0.5.0"
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
            "package://localhost:12110/birds@0": {
              "type": "remote",
              "uri": "projectpackage://localhost:12110/birds@0.5.0",
              "checksums": {
                "sha256": "3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118"
              }
            },
            "package://localhost:12110/project2@5": {
              "type": "local",
              "uri": "projectpackage://localhost:12110/project2@5.0.0",
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
          baseUri = "package://localhost:12110/project2"
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

  @Test
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
    assertThat(out.toString())
      .isEqualTo(
        """
      .out/project1@1.0.0/project1@1.0.0.zip
      .out/project1@1.0.0/project1@1.0.0.zip.sha256
      .out/project1@1.0.0/project1@1.0.0
      .out/project1@1.0.0/project1@1.0.0.sha256
      .out/project2@2.0.0/project2@2.0.0.zip
      .out/project2@2.0.0/project2@2.0.0.zip.sha256
      .out/project2@2.0.0/project2@2.0.0
      .out/project2@2.0.0/project2@2.0.0.sha256

    """
          .trimIndent()
      )
    assertThat(tempDir.resolve(".out/project1@1.0.0/project1@1.0.0.zip").zipFilePaths())
      .hasSameElementsAs(listOf("/", "/main.pkl"))
    assertThat(tempDir.resolve(".out/project2@2.0.0/project2@2.0.0.zip").zipFilePaths())
      .hasSameElementsAs(listOf("/", "/main2.pkl"))
  }

  @Test
  @Ignore("sgammon: Broken checksums")
  fun `publish checks`(@TempDir tempDir: Path) {
    PackageServer.ensureStarted()
    CertificateUtils.setupAllX509CertificatesGlobally(listOf(FileTestUtils.selfSignedCertificate))
    tempDir.writeFile("project/main.pkl", "res = 1")
    tempDir.writeFile(
      "project/PklProject",
      // intentionally conflict with localhost:12110/birds@0.5.0 from our test fixtures
      """
        amends "pkl:Project"
        
        package {
          name = "birds"
          version = "0.5.0"
          baseUri = "package://localhost:12110/birds"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val e =
      assertThrows<CliException> {
        CliProjectPackager(
            CliBaseOptions(workingDir = tempDir),
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
      Package `package://localhost:12110/birds@0.5.0` was already published with different contents.
      
      Computed checksum: 7324e17214b6dcda63ebfb57d5a29b077af785c13bed0dc22b5138628a3f8d8f
      Published checksum: 3f19ab9fcee2f44f93a75a09e531db278c6d2cd25206836c8c2c4071cd7d3118
    """
          .trimIndent()
      )
  }

  @Test
  fun `publish check when package is not yet published`(@TempDir tempDir: Path) {
    PackageServer.ensureStarted()
    CertificateUtils.setupAllX509CertificatesGlobally(listOf(FileTestUtils.selfSignedCertificate))
    tempDir.writeFile("project/main.pkl", "res = 1")
    tempDir.writeFile(
      "project/PklProject",
      """
        amends "pkl:Project"
        
        package {
          name = "mangos"
          version = "1.0.0"
          baseUri = "package://localhost:12110/mangos"
          packageZipUrl = "https://foo.com"
        }
      """
        .trimIndent()
    )
    val out = StringWriter()
    CliProjectPackager(
        CliBaseOptions(workingDir = tempDir),
        listOf(tempDir.resolve("project")),
        CliTestOptions(),
        ".out/%{name}@%{version}",
        skipPublishCheck = false,
        consoleWriter = out
      )
      .run()
    assertThat(out.toString())
      .isEqualTo(
        """
      .out/mangos@1.0.0/mangos@1.0.0.zip
      .out/mangos@1.0.0/mangos@1.0.0.zip.sha256
      .out/mangos@1.0.0/mangos@1.0.0
      .out/mangos@1.0.0/mangos@1.0.0.sha256

    """
          .trimIndent()
      )
  }

  private fun Path.zipFilePaths(): List<String> {
    return FileSystems.newFileSystem(URI("jar:${toUri()}"), emptyMap<String, String>()).use { fs ->
      Files.walk(fs.getPath("/")).map { it.toString() }.collect(Collectors.toList())
    }
  }
}
